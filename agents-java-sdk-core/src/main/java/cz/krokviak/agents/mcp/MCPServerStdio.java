package cz.krokviak.agents.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import cz.krokviak.agents.tool.ToolDefinition;
import cz.krokviak.agents.tool.ToolOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MCP server transport that communicates with a subprocess via stdin/stdout.
 * Each message is a single JSON object per line (newline-delimited JSON-RPC).
 */
public final class MCPServerStdio implements MCPServer {
    private static final Logger log = LoggerFactory.getLogger(MCPServerStdio.class);

    private final String command;
    private final List<String> args;
    private final Map<String, String> env;
    private final ObjectMapper objectMapper;
    private final AtomicInteger requestIdCounter;

    private Process process;
    private BufferedWriter writer;
    private BufferedReader reader;

    public MCPServerStdio(String command, String... args) {
        this(command, List.of(args), Map.of());
    }

    public MCPServerStdio(String command, List<String> args, Map<String, String> env) {
        this.command = command;
        this.args = List.copyOf(args);
        this.env = Map.copyOf(env);
        this.objectMapper = new ObjectMapper();
        this.requestIdCounter = new AtomicInteger(0);
    }

    @Override
    public void connect() throws Exception {
        List<String> cmdLine = new ArrayList<>();
        cmdLine.add(command);
        cmdLine.addAll(args);

        ProcessBuilder pb = new ProcessBuilder(cmdLine);
        pb.environment().putAll(env);
        pb.redirectErrorStream(false);
        process = pb.start();

        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        // Send initialize request
        String initId = nextId();
        Map<String, Object> initParams = Map.of(
            "protocolVersion", "2024-11-05",
            "capabilities", Map.of(),
            "clientInfo", Map.of("name", "agents-java-sdk", "version", "0.1.0")
        );
        MCPMessage.Request initRequest = new MCPMessage.Request(initId, "initialize", initParams);
        Map<String, Object> response = sendRequest(initRequest);
        log.debug("MCP initialize response: {}", response);

        // Send initialized notification
        MCPMessage.Notification initialized = new MCPMessage.Notification("notifications/initialized", Map.of());
        sendNotification(initialized);
    }

    @Override
    public List<ToolDefinition> listTools() throws Exception {
        String id = nextId();
        MCPMessage.Request request = new MCPMessage.Request(id, "tools/list", Map.of());
        Map<String, Object> result = sendRequest(request);

        List<ToolDefinition> tools = new ArrayList<>();
        Object toolsObj = result.get("tools");
        if (toolsObj instanceof List<?> toolList) {
            for (Object item : toolList) {
                if (item instanceof Map<?, ?> toolMap) {
                    String name = (String) toolMap.get("name");
                    String description = toolMap.get("description") != null
                        ? (String) toolMap.get("description") : "";
                    @SuppressWarnings("unchecked")
                    Map<String, Object> inputSchema = toolMap.get("inputSchema") != null
                        ? (Map<String, Object>) toolMap.get("inputSchema")
                        : Map.of("type", "object", "properties", Map.of());
                    tools.add(new ToolDefinition(name, description, inputSchema));
                }
            }
        }
        return tools;
    }

    @Override
    public ToolOutput callTool(String toolName, Map<String, Object> arguments) throws Exception {
        String id = nextId();
        Map<String, Object> params = Map.of(
            "name", toolName,
            "arguments", arguments != null ? arguments : Map.of()
        );
        MCPMessage.Request request = new MCPMessage.Request(id, "tools/call", params);
        Map<String, Object> result = sendRequest(request);

        // Parse content array from result
        Object contentObj = result.get("content");
        if (contentObj instanceof List<?> contentList && !contentList.isEmpty()) {
            Object first = contentList.getFirst();
            if (first instanceof Map<?, ?> contentMap) {
                String type = (String) contentMap.get("type");
                if ("text".equals(type)) {
                    return ToolOutput.text((String) contentMap.get("text"));
                }
            }
        }

        // Fallback: serialize the entire result as text
        return ToolOutput.text(objectMapper.writeValueAsString(result));
    }

    @Override
    public void close() {
        try {
            if (writer != null) {
                // Send shutdown notification
                MCPMessage.Notification shutdown = new MCPMessage.Notification("notifications/cancelled", Map.of());
                sendNotification(shutdown);
                writer.close();
            }
        } catch (Exception e) {
            log.debug("Error sending shutdown notification", e);
        }
        try {
            if (reader != null) reader.close();
        } catch (Exception e) {
            log.debug("Error closing reader", e);
        }
        if (process != null) {
            process.destroyForcibly();
        }
    }

    private String nextId() {
        return String.valueOf(requestIdCounter.incrementAndGet());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sendRequest(MCPMessage.Request request) throws Exception {
        Map<String, Object> jsonRpc = new LinkedHashMap<>();
        jsonRpc.put("jsonrpc", "2.0");
        jsonRpc.put("id", request.id());
        jsonRpc.put("method", request.method());
        if (request.params() != null) {
            jsonRpc.put("params", request.params());
        }

        String json = objectMapper.writeValueAsString(jsonRpc);
        log.debug("MCP send: {}", json);
        writer.write(json);
        writer.newLine();
        writer.flush();

        // Read response line(s), skipping notifications
        while (true) {
            String line = reader.readLine();
            if (line == null) {
                throw new IOException("MCP server closed stdout unexpectedly");
            }
            log.debug("MCP recv: {}", line);

            Map<String, Object> response = objectMapper.readValue(line, new TypeReference<>() {});
            // Skip notifications (no id field)
            if (!response.containsKey("id")) {
                continue;
            }
            // Check for error
            if (response.containsKey("error")) {
                Map<String, Object> error = (Map<String, Object>) response.get("error");
                int code = error.get("code") instanceof Number n ? n.intValue() : -1;
                String message = (String) error.get("message");
                throw new MCPException(code, message);
            }
            return response.containsKey("result")
                ? (Map<String, Object>) response.get("result")
                : Map.of();
        }
    }

    private void sendNotification(MCPMessage.Notification notification) throws IOException {
        Map<String, Object> jsonRpc = new LinkedHashMap<>();
        jsonRpc.put("jsonrpc", "2.0");
        jsonRpc.put("method", notification.method());
        if (notification.params() != null) {
            jsonRpc.put("params", notification.params());
        }

        String json = objectMapper.writeValueAsString(jsonRpc);
        log.debug("MCP send notification: {}", json);
        writer.write(json);
        writer.newLine();
        writer.flush();
    }

    // Visible for testing
    static Map<String, Object> buildJsonRpcRequest(String id, String method, Map<String, Object> params) {
        Map<String, Object> jsonRpc = new LinkedHashMap<>();
        jsonRpc.put("jsonrpc", "2.0");
        jsonRpc.put("id", id);
        jsonRpc.put("method", method);
        if (params != null) {
            jsonRpc.put("params", params);
        }
        return jsonRpc;
    }

    // Visible for testing
    static Map<String, Object> buildJsonRpcNotification(String method, Map<String, Object> params) {
        Map<String, Object> jsonRpc = new LinkedHashMap<>();
        jsonRpc.put("jsonrpc", "2.0");
        jsonRpc.put("method", method);
        if (params != null) {
            jsonRpc.put("params", params);
        }
        return jsonRpc;
    }
}
