package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Web search tool. Uses DuckDuckGo HTML search (no API key needed).
 * For production, swap to a proper search API (Google, Bing, Brave).
 */
public class WebSearchTool implements ExecutableTool {
    private final HttpClient httpClient;
    private final ToolDefinition toolDefinition;

    public WebSearchTool() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        this.toolDefinition = new ToolDefinition("web_search",
            "Search the web for information. Returns search results with titles, URLs, and snippets. " +
                "Useful for finding documentation, looking up error messages, or researching topics.",
            Map.of("type", "object", "properties", Map.of(
                "query", Map.of("type", "string", "description", "The search query"),
                "max_results", Map.of("type", "integer", "description", "Maximum results to return (default 5)")
            ), "required", List.of("query")));
    }

    @Override public String name() { return "web_search"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) throws Exception {
        String query = args.get("query", String.class);
        if (query == null || query.isBlank()) return ToolOutput.text("Error: query is required");

        int maxResults = 5;
        Object maxObj = args.raw().get("max_results");
        if (maxObj instanceof Number n) maxResults = n.intValue();

        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://html.duckduckgo.com/html/?q=" + encoded;

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "Mozilla/5.0 (compatible; Claude-Code-CLI/1.0)")
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String html = response.body();

            // Parse results from DuckDuckGo HTML
            List<SearchResult> results = parseResults(html, maxResults);

            if (results.isEmpty()) {
                return ToolOutput.text("No results found for: " + query);
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Search results for: ").append(query).append("\n\n");
            int i = 1;
            for (SearchResult r : results) {
                sb.append(i++).append(". ").append(r.title()).append("\n");
                sb.append("   ").append(r.url()).append("\n");
                if (r.snippet() != null && !r.snippet().isBlank()) {
                    sb.append("   ").append(r.snippet()).append("\n");
                }
                sb.append("\n");
            }
            return ToolOutput.text(sb.toString());
        } catch (Exception e) {
            return ToolOutput.text("Search error: " + e.getMessage());
        }
    }

    private record SearchResult(String title, String url, String snippet) {}

    private List<SearchResult> parseResults(String html, int maxResults) {
        List<SearchResult> results = new java.util.ArrayList<>();
        // Simple HTML parsing for DuckDuckGo results
        String[] blocks = html.split("class=\"result__a\"");
        for (int i = 1; i < blocks.length && results.size() < maxResults; i++) {
            String block = blocks[i];
            String href = extractAttr(block, "href=\"", "\"");
            String title = extractText(block);
            // Look for snippet in next section
            String snippet = "";
            int snippetIdx = block.indexOf("result__snippet");
            if (snippetIdx > 0) {
                snippet = stripHtml(block.substring(snippetIdx, Math.min(snippetIdx + 500, block.length())));
            }
            if (href != null && !href.isBlank() && title != null && !title.isBlank()) {
                // DuckDuckGo wraps URLs in redirect
                if (href.contains("uddg=")) {
                    int start = href.indexOf("uddg=") + 5;
                    int end = href.indexOf("&", start);
                    if (end < 0) end = href.length();
                    href = java.net.URLDecoder.decode(href.substring(start, end), StandardCharsets.UTF_8);
                }
                results.add(new SearchResult(stripHtml(title).trim(), href, snippet.trim()));
            }
        }
        return results;
    }

    private String extractAttr(String html, String start, String end) {
        int s = html.indexOf(start);
        if (s < 0) return null;
        s += start.length();
        int e = html.indexOf(end, s);
        if (e < 0) return null;
        return html.substring(s, e);
    }

    private String extractText(String html) {
        int end = html.indexOf("</a>");
        if (end < 0) end = Math.min(200, html.length());
        return html.substring(0, end);
    }

    private String stripHtml(String html) {
        return html.replaceAll("<[^>]+>", "").replaceAll("&amp;", "&")
            .replaceAll("&lt;", "<").replaceAll("&gt;", ">")
            .replaceAll("&quot;", "\"").replaceAll("&#39;", "'")
            .replaceAll("\\s+", " ");
    }
}
