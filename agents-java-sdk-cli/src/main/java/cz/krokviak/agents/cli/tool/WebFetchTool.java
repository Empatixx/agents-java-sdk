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

public class WebFetchTool implements ExecutableTool {
    private final HttpClient httpClient;
    private final ToolDefinition toolDefinition;

    public WebFetchTool() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        this.toolDefinition = new ToolDefinition("web_fetch",
            "Fetch the content of a URL. Returns the response body as text. " +
                "Useful for reading web pages, API endpoints, documentation, or any HTTP resource.",
            Map.of("type", "object", "properties", Map.of(
                "url", Map.of("type", "string", "description", "The URL to fetch"),
                "headers", Map.of("type", "object", "description", "Optional HTTP headers as key-value pairs")
            ), "required", List.of("url")));
    }

    @Override public String name() { return "web_fetch"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) throws Exception {
        String url = args.get("url", String.class);
        if (url == null || url.isBlank()) return ToolOutput.text("Error: url is required");

        try {
            var requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "KrokAI/1.0")
                .GET();

            @SuppressWarnings("unchecked")
            Map<String, String> headers = args.get("headers", Map.class);
            if (headers != null) {
                headers.forEach(requestBuilder::header);
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString());

            StringBuilder sb = new StringBuilder();
            sb.append("HTTP ").append(response.statusCode()).append("\n");
            sb.append("Content-Type: ").append(
                response.headers().firstValue("content-type").orElse("unknown")).append("\n\n");

            String body = response.body();
            // Truncate very large responses
            if (body.length() > 100_000) {
                sb.append(body, 0, 100_000);
                sb.append("\n\n... (truncated, ").append(body.length()).append(" total chars)");
            } else {
                sb.append(body);
            }
            return ToolOutput.text(sb.toString());
        } catch (Exception e) {
            return ToolOutput.text("Error fetching " + url + ": " + e.getMessage());
        }
    }
}
