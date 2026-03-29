package cz.krokviak.agents.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import cz.krokviak.agents.exception.SessionException;
import cz.krokviak.agents.runner.InputItem;
import cz.krokviak.agents.runner.RunItem;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Redis-backed session using Jedis via reflection to avoid a hard compile-time dependency.
 * Users must add Jedis to their classpath at runtime.
 * <p>
 * Each session is stored as a Redis list keyed by the session ID.
 * Each entry in the list is a JSON-serialised RunItem type + content pair.
 */
public class RedisSession implements Session {

    private final Object jedisClient;
    private final Class<?> jedisClass;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Reflected methods
    private final Method lpushMethod;
    private final Method lrangeMethod;
    private final Method delMethod;

    /**
     * Creates a RedisSession by connecting to the given host and port.
     * Requires Jedis on the classpath.
     *
     * @param host Redis host
     * @param port Redis port
     */
    public RedisSession(String host, int port) {
        this(createJedisClient(host, port));
    }

    /**
     * Creates a RedisSession wrapping an existing Jedis client object.
     * The client may be any object whose class exposes {@code lpush}, {@code lrange}, and {@code del} methods
     * compatible with the Jedis API.
     *
     * @param jedisClient a Jedis (or compatible) client instance
     */
    public RedisSession(Object jedisClient) {
        if (jedisClient == null) {
            throw new SessionException("Jedis client must not be null", null);
        }
        this.jedisClient = jedisClient;
        this.jedisClass = jedisClient.getClass();

        try {
            this.lpushMethod = jedisClass.getMethod("lpush", String.class, String[].class);
            this.lrangeMethod = jedisClass.getMethod("lrange", String.class, long.class, long.class);
            this.delMethod = jedisClass.getMethod("del", String[].class);
        } catch (NoSuchMethodException e) {
            throw new SessionException(
                "The provided client does not expose required Redis methods (lpush, lrange, del). "
                    + "Please provide a Jedis client instance.", e);
        }
    }

    @Override
    public List<InputItem> getHistory(String sessionId) {
        try {
            @SuppressWarnings("unchecked")
            List<String> entries = (List<String>) lrangeMethod.invoke(jedisClient, sessionId, 0L, -1L);
            List<InputItem> items = new ArrayList<>();
            if (entries == null) return items;
            // Items were pushed with lpush (prepend), so the list is in reverse order — reverse it back
            for (int i = entries.size() - 1; i >= 0; i--) {
                items.add(deserialize(entries.get(i)));
            }
            return items;
        } catch (Exception e) {
            throw new SessionException("Failed to get history from Redis for session " + sessionId, e);
        }
    }

    @Override
    public void save(String sessionId, List<RunItem> newItems) {
        try {
            for (RunItem item : newItems) {
                String json = serialize(item);
                lpushMethod.invoke(jedisClient, sessionId, new String[]{json});
            }
        } catch (Exception e) {
            throw new SessionException("Failed to save session items to Redis for session " + sessionId, e);
        }
    }

    // ---- Serialization helpers ----

    private String serialize(RunItem item) {
        try {
            String type = item.getClass().getSimpleName();
            String content = switch (item) {
                case RunItem.MessageOutput msg -> msg.content();
                case RunItem.ToolCallItem call -> call.toolName() + ": " + objectMapper.writeValueAsString(call.arguments());
                case RunItem.ToolOutputItem out -> out.toolName();
                case RunItem.HandoffItem h -> h.fromAgent() + "->" + h.toAgent();
            };
            return type + "|" + content;
        } catch (JsonProcessingException e) {
            throw new SessionException("Failed to serialize RunItem", e);
        }
    }

    private InputItem deserialize(String entry) {
        if (entry == null) return new InputItem.AssistantMessage("");
        int sep = entry.indexOf('|');
        if (sep < 0) return new InputItem.AssistantMessage(entry);
        String type = entry.substring(0, sep);
        String content = entry.substring(sep + 1);
        return switch (type) {
            case "MessageOutput" -> new InputItem.AssistantMessage(content);
            default -> new InputItem.AssistantMessage("[" + type + "] " + content);
        };
    }

    // ---- Factory helper ----

    private static Object createJedisClient(String host, int port) {
        try {
            Class<?> jedisClass = Class.forName("redis.clients.jedis.Jedis");
            return jedisClass.getConstructor(String.class, int.class).newInstance(host, port);
        } catch (ClassNotFoundException e) {
            throw new SessionException(
                "Jedis is not on the classpath. Add 'redis.clients:jedis' as a dependency to use RedisSession.", e);
        } catch (Exception e) {
            throw new SessionException("Failed to create Jedis client for " + host + ":" + port, e);
        }
    }
}
