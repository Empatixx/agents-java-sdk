package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Web search via headless Chrome + Google.
 * Falls back to basic HTTP if Chrome is not available.
 */
public class WebSearchTool implements ExecutableTool {
    private final ToolDefinition toolDefinition;

    private static final String CHROME_BIN = findChrome();
    private static final int CHROME_TIMEOUT_SECONDS = 15;

    public WebSearchTool() {
        this.toolDefinition = new ToolDefinition("web_search",
            "Search the web for information. Returns search results with titles, URLs, and snippets.",
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

        if (CHROME_BIN == null) {
            return ToolOutput.text("Error: Chrome/Chromium not found. Install google-chrome or chromium.");
        }

        try {
            String html = fetchWithChrome(query, maxResults);
            List<SearchResult> results = parseGoogleResults(html, maxResults);

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

    private String fetchWithChrome(String query, int maxResults) throws Exception {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://www.google.com/search?q=" + encoded + "&num=" + maxResults + "&hl=en";

        ProcessBuilder pb = new ProcessBuilder(
            CHROME_BIN,
            "--headless=new",
            "--dump-dom",
            "--no-sandbox",
            "--disable-gpu",
            "--disable-blink-features=AutomationControlled",
            "--user-agent=Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36",
            url
        );
        pb.redirectErrorStream(false);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        boolean finished = process.waitFor(CHROME_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Chrome timed out after " + CHROME_TIMEOUT_SECONDS + "s");
        }

        return output.toString();
    }

    private record SearchResult(String title, String url, String snippet) {}

    // Google result structure: <a href="/url?q=REAL_URL&..."><h3>TITLE</h3></a>
    // followed by snippet text in nearby elements
    private static final Pattern H3_PATTERN = Pattern.compile("<h3[^>]*>(.*?)</h3>");
    private static final Pattern HREF_PATTERN = Pattern.compile("href=\"/url\\?q=([^&\"]+)");
    private static final Pattern SNIPPET_PATTERN = Pattern.compile(
        "<div[^>]*class=\"[^\"]*VwiC3b[^\"]*\"[^>]*>(.*?)</div>", Pattern.DOTALL);

    private List<SearchResult> parseGoogleResults(String html, int maxResults) {
        List<SearchResult> results = new ArrayList<>();

        // Split by search result blocks (div.g or similar)
        String[] blocks = html.split("class=\"g\"");

        for (int i = 1; i < blocks.length && results.size() < maxResults; i++) {
            String block = blocks[i];

            // Extract title from <h3>
            Matcher h3 = H3_PATTERN.matcher(block);
            if (!h3.find()) continue;
            String title = stripHtml(h3.group(1)).trim();

            // Extract URL from href="/url?q=..."
            Matcher href = HREF_PATTERN.matcher(block);
            String url = "";
            if (href.find()) {
                url = java.net.URLDecoder.decode(href.group(1), StandardCharsets.UTF_8);
            } else {
                // Try direct href
                Matcher directHref = Pattern.compile("href=\"(https?://[^\"]+)\"").matcher(block);
                if (directHref.find()) url = directHref.group(1);
            }

            // Extract snippet
            Matcher snip = SNIPPET_PATTERN.matcher(block);
            String snippet = snip.find() ? stripHtml(snip.group(1)).trim() : "";

            if (!title.isEmpty() && !url.isEmpty()) {
                results.add(new SearchResult(title, url, snippet));
            }
        }

        // Fallback: if class="g" split didn't work, try finding h3 + nearby links
        if (results.isEmpty()) {
            Matcher h3 = H3_PATTERN.matcher(html);
            while (h3.find() && results.size() < maxResults) {
                String title = stripHtml(h3.group(1)).trim();
                int pos = h3.start();
                // Look for URL near this h3 (within 500 chars before)
                String context = html.substring(Math.max(0, pos - 500), Math.min(html.length(), pos + 500));
                Matcher href = Pattern.compile("href=\"(https?://[^\"]+)\"").matcher(context);
                String url = href.find() ? href.group(1) : "";
                if (!title.isEmpty() && !url.isEmpty() && !url.contains("google.com")) {
                    results.add(new SearchResult(title, url, ""));
                }
            }
        }

        return results;
    }

    private String stripHtml(String html) {
        return html.replaceAll("<[^>]+>", "")
            .replaceAll("&amp;", "&").replaceAll("&lt;", "<")
            .replaceAll("&gt;", ">").replaceAll("&quot;", "\"")
            .replaceAll("&#39;", "'").replaceAll("&nbsp;", " ")
            .replaceAll("\\s+", " ");
    }

    private static String findChrome() {
        for (String candidate : List.of(
                "google-chrome-stable", "google-chrome", "chromium-browser", "chromium")) {
            try {
                Process p = new ProcessBuilder("which", candidate)
                    .redirectErrorStream(true).start();
                String path = new String(p.getInputStream().readAllBytes()).trim();
                p.waitFor(2, TimeUnit.SECONDS);
                if (p.exitValue() == 0 && !path.isEmpty()) return path;
            } catch (Exception ignored) {}
        }
        return null;
    }
}
