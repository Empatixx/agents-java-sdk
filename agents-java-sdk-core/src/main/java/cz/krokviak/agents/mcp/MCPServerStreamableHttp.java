package cz.krokviak.agents.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import cz.krokviak.agents.tool.ToolDefinition;
import cz.krokviak.agents.tool.ToolOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MCP server transport that communicates over HTTP using Streamable HTTP transport.
 * Each request is a POST with a JSON-RPC body, the response is a JSON-RPC response.
 */
public final class MCPServerStreamableHttp implements MCPServer {
    private static final Logger log = LoggerFactory.getLogger(MCPServerStreamableHttp.class);

    private final String url;
    private final Map<String, String> headers;
    private final ObjectMapper objectMapper;
    private final AtomicInteger requestIdCounter;
    private final HttpClient httpClient;

    private String sessionId;

    public MCPServerStreamableHttp(String url) {
        this(url, Map.of());
    }

    public MCPServerStreamableHttp(String url, Map<String, String> headers) {
        this.url = url;
        this.headers = Map.copyOf(headers);
        this.objectMapper = new ObjectMapper();
        this.requestIdCounter = new AtomicInteger(0);
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    @Override
    public void connect() throws Exception {
        String initId = nextId();
        Map<String, Object> initParams = Map.of(
            "protocolVersion", "2024-11-05",
            "capabilities", Map.of(),
            "clientInfo", Map.of("name", "agents-java-sdk", "version", "0.1.0")
        );
        Map<String, Object> result = sendRequest(initId, "initialize", initParams);
        log.debug("MCP HTTP initialize response: {}", result);

        // Send initialized notification
        sendNotification("notifications/initialized", Map.of());
    }

    @Override
    public List<ToolDefinition> listTools() throws Exception {
        String id = nextId();
        Map<String, Object> result = sendRequest(id, "tools/list", Map.of());

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
        Map<String, Object> result = sendRequest(id, "tools/call", params);

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
        // HTTP transport is stateless per-request; nothing to close
        log.debug("MCP HTTP server connection closed");
    }

    private String nextId() {
        return String.valueOf(requestIdCounter.incrementAndGet());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sendRequest(String id, String method, Map<String, Object> params) throws Exception {
        Map<String, Object> jsonRpc = new LinkedHashMap<>();
        jsonRpc.put("jsonrpc", "2.0");
        jsonRpc.put("id", id);
        jsonRpc.put("method", method);
        if (params != null) {
            jsonRpc.put("params", params);
        }

        String body = objectMapper.writeValueAsString(jsonRpc);
        log.debug("MCP HTTP send: {}", body);

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));

        // Add custom headers
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            reqBuilder.header(entry.getKey(), entry.getValue());
        }

        // Add session id if we have one
        if (sessionId != null) {
            reqBuilder.header("Mcp-Session-Id", sessionId);
        }

        HttpResponse<String> httpResponse = httpClient.send(
            reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

        if (httpResponse.statusCode() >= 400) {
            throw new MCPException(-1, "HTTP " + httpResponse.statusCode() + ": " + httpResponse.body());
        }

        // Capture session id from response headers
        httpResponse.headers().firstValue("Mcp-Session-Id").ifPresent(sid -> this.sessionId = sid);

        log.debug("MCP HTTP recv: {}", httpResponse.body());

        Map<String, Object> response = objectMapper.readValue(
            httpResponse.body(), new TypeReference<>() {});

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

    private void sendNotification(String method, Map<String, Object> params) throws Exception {
        Map<String, Object> jsonRpc = new LinkedHashMap<>();
        jsonRpc.put("jsonrpc", "2.0");
        jsonRpc.put("method", method);
        if (params != null) {
            jsonRpc.put("params", params);
        }

        String body = objectMapper.writeValueAsString(jsonRpc);
        log.debug("MCP HTTP send notification: {}", body);

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            reqBuilder.header(entry.getKey(), entry.getValue());
        }
        if (sessionId != null) {
            reqBuilder.header("Mcp-Session-Id", sessionId);
        }

        httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
