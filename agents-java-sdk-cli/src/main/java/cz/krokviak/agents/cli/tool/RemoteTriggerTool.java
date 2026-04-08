package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class RemoteTriggerTool implements ExecutableTool {
    private final HttpClient httpClient;
    private final ToolDefinition toolDefinition;

    public RemoteTriggerTool() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.toolDefinition = new ToolDefinition("remote_trigger",
            "Trigger a remote HTTP endpoint. Supports GET and POST methods with optional headers and body.",
            Map.of("type", "object", "properties", Map.of(
                "url", Map.of("type", "string", "description", "The URL to call"),
                "method", Map.of("type", "string", "description", "HTTP method: GET or POST (default GET)"),
                "headers", Map.of("type", "string", "description", "Optional headers as 'Key: Value' lines"),
                "body", Map.of("type", "string", "description", "Optional request body for POST requests")
            ), "required", List.of("url")));
    }

    @Override public String name() { return "remote_trigger"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) {
        String url = args.get("url", String.class);
        String method = args.getOrDefault("method", String.class, "GET");
        String headers = args.getOrDefault("headers", String.class, null);
        String body = args.getOrDefault("body", String.class, null);

        if (url == null || url.isBlank()) return ToolOutput.text("Error: url required");

        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30));

            // Parse and add headers
            if (headers != null && !headers.isBlank()) {
                for (String line : headers.split("\n")) {
                    int colon = line.indexOf(':');
                    if (colon > 0) {
                        String name = line.substring(0, colon).trim();
                        String value = line.substring(colon + 1).trim();
                        requestBuilder.header(name, value);
                    }
                }
            }

            // Set method and body
            switch (method.toUpperCase()) {
                case "GET" -> requestBuilder.GET();
                case "POST" -> {
                    String requestBody = body != null ? body : "";
                    requestBuilder.POST(HttpRequest.BodyPublishers.ofString(requestBody));
                }
                default -> { return ToolOutput.text("Error: unsupported method: " + method + ". Use GET or POST."); }
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            String responseBody = response.body();
            String preview = responseBody.length() > 2000 ? responseBody.substring(0, 2000) + "...[truncated]" : responseBody;

            return ToolOutput.text("HTTP " + status + " " + method + " " + url + "\n" + preview);
        } catch (Exception e) {
            return ToolOutput.text("Error: " + e.getMessage());
        }
    }
}
