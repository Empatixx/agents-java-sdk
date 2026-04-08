package cz.krokviak.agents.model;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class AnthropicHttpClient {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;

    public AnthropicHttpClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    public <T> T post(String path, Object body, Class<T> responseType) {
        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("content-type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
            }

            return objectMapper.readValue(response.body(), responseType);
        } catch (Exception e) {
            throw new RuntimeException("HTTP request failed: " + path, e);
        }
    }

    public InputStream postStream(String path, Object body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("content-type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            HttpResponse<InputStream> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() >= 400) {
                throw new RuntimeException("HTTP " + response.statusCode());
            }

            return response.body();
        } catch (Exception e) {
            throw new RuntimeException("HTTP stream request failed: " + path, e);
        }
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }
}
