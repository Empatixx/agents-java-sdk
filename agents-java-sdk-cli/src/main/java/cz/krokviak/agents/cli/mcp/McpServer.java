package cz.krokviak.agents.cli.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages a single MCP server process via stdio JSON-RPC transport.
 *
 * Protocol:
 * - Messages are JSON-RPC 2.0 over stdin/stdout
 * - Each message prefixed with "Content-Length: N\r\n\r\n"
 * - Initialize → tools/list → tools/call
 */
public class McpServer implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(McpServer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int TIMEOUT_SECONDS = 30;

    private final McpServerConfig config;
    private Process process;
    private BufferedWriter writer;
    private final AtomicInteger requestId = new AtomicInteger(0);
    private final Map<Integer, CompletableFuture<Map<String, Object>>> pending = new ConcurrentHashMap<>();
    private final List<McpTool> tools = new ArrayList<>();
    private volatile boolean running;

    public McpServer(McpServerConfig config) {
        this.config = config;
    }

    public String name() { return config.name(); }
    public List<McpTool> tools() { return List.copyOf(tools); }

    /**
     * Start the server process, initialize, and discover tools.
     */
    @SuppressWarnings("unchecked")
    public void start() throws IOException {
        var cmd = new ArrayList<String>();
        cmd.add(config.command());
        cmd.addAll(config.args());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);

        // Merge env
        if (!config.env().isEmpty()) {
            pb.environment().putAll(config.env());
        }

        process = pb.start();
        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        running = true;

        // Start reader thread
        Thread.ofVirtual().name("mcp-reader-" + config.name()).start(this::readLoop);

        // Drain stderr in background
        Thread.ofVirtual().name("mcp-stderr-" + config.name()).start(() -> {
            try (var err = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = err.readLine()) != null) {
                    log.debug("MCP [{}] stderr: {}", config.name(), line);
                }
            } catch (IOException ignored) {}
        });

        // Initialize
        var initResult = call("initialize", Map.of(
            "protocolVersion", "2024-11-05",
            "capabilities", Map.of(),
            "clientInfo", Map.of("name", "krok-ai", "version", "1.0.0")
        ));

        log.info("MCP server '{}' initialized: {}", config.name(),
            initResult.getOrDefault("serverInfo", "unknown"));

        // Send initialized notification
        notify("notifications/initialized", Map.of());

        // List tools
        var toolsResult = call("tools/list", Map.of());
        if (toolsResult.get("tools") instanceof List<?> toolList) {
            for (var t : toolList) {
                if (t instanceof Map<?, ?> tm) {
                    tools.add(new McpTool(
                        config.name(),
                        (String) tm.get("name"),
                        tm.get("description") instanceof String d ? d : "",
                        tm.get("inputSchema") instanceof Map<?, ?> schema
                            ? (Map<String, Object>) schema : Map.of()
                    ));
                }
            }
        }

        log.info("MCP server '{}' provides {} tools", config.name(), tools.size());
    }

    /**
     * Call an MCP tool by name.
     */
    @SuppressWarnings("unchecked")
    public String callTool(String toolName, Map<String, Object> arguments) {
        try {
            var result = call("tools/call", Map.of(
                "name", toolName,
                "arguments", arguments
            ));

            // Parse result content
            if (result.get("content") instanceof List<?> contentList) {
                var sb = new StringBuilder();
                for (var item : contentList) {
                    if (item instanceof Map<?, ?> m) {
                        if ("text".equals(m.get("type"))) {
                            sb.append(m.get("text"));
                        }
                    }
                }
                boolean isError = Boolean.TRUE.equals(result.get("isError"));
                String text = sb.toString();
                return isError ? "Error: " + text : text;
            }

            return result.toString();
        } catch (Exception e) {
            return "MCP error: " + e.getMessage();
        }
    }

    // ---- JSON-RPC ----

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(String method, Map<String, Object> params) {
        int id = requestId.incrementAndGet();
        var future = new CompletableFuture<Map<String, Object>>();
        pending.put(id, future);

        try {
            var request = new LinkedHashMap<String, Object>();
            request.put("jsonrpc", "2.0");
            request.put("id", id);
            request.put("method", method);
            request.put("params", params);
            sendMessage(request);

            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            pending.remove(id);
            throw new RuntimeException("MCP call failed: " + method + " — " + e.getMessage(), e);
        }
    }

    private void notify(String method, Map<String, Object> params) {
        var message = new LinkedHashMap<String, Object>();
        message.put("jsonrpc", "2.0");
        message.put("method", method);
        message.put("params", params);
        try {
            sendMessage(message);
        } catch (IOException e) {
            log.warn("Failed to send MCP notification: {}", method, e);
        }
    }

    private synchronized void sendMessage(Map<String, Object> message) throws IOException {
        String json = MAPPER.writeValueAsString(message);
        String header = "Content-Length: " + json.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n";
        writer.write(header);
        writer.write(json);
        writer.flush();
    }

    @SuppressWarnings("unchecked")
    private void readLoop() {
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            while (running) {
                // Read Content-Length header
                String headerLine = reader.readLine();
                if (headerLine == null) break;
                if (!headerLine.startsWith("Content-Length:")) continue;
                int length = Integer.parseInt(headerLine.substring("Content-Length:".length()).trim());

                // Skip empty line
                reader.readLine();

                // Read body
                char[] body = new char[length];
                int read = 0;
                while (read < length) {
                    int n = reader.read(body, read, length - read);
                    if (n < 0) break;
                    read += n;
                }

                var message = MAPPER.readValue(new String(body, 0, read), Map.class);

                // Route response to pending future
                if (message.containsKey("id") && message.get("id") instanceof Number id) {
                    var future = pending.remove(id.intValue());
                    if (future != null) {
                        if (message.containsKey("error")) {
                            var error = (Map<String, Object>) message.get("error");
                            future.completeExceptionally(new RuntimeException(
                                "MCP error " + error.get("code") + ": " + error.get("message")));
                        } else {
                            future.complete(message.containsKey("result")
                                ? (Map<String, Object>) message.get("result")
                                : Map.of());
                        }
                    }
                }
                // Notifications from server — log and ignore
            }
        } catch (Exception e) {
            if (running) log.warn("MCP reader error for '{}': {}", config.name(), e.getMessage());
        }
    }

    @Override
    public void close() {
        running = false;
        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException ignored) {}
        }
        pending.values().forEach(f -> f.completeExceptionally(new RuntimeException("Server closed")));
        pending.clear();
    }
}
