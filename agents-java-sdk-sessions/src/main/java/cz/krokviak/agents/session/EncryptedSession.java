package cz.krokviak.agents.session;

import cz.krokviak.agents.exception.SessionException;
import cz.krokviak.agents.runner.InputItem;
import cz.krokviak.agents.runner.RunItem;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

/**
 * Decorator that encrypts session data using AES/GCM before delegating to the wrapped Session.
 */
public final class EncryptedSession implements Session {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final Session delegate;
    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public EncryptedSession(Session delegate, SecretKey key) {
        this.delegate = delegate;
        this.key = key;
    }

    public EncryptedSession(Session delegate, String base64Key) {
        this.delegate = delegate;
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Creates an EncryptedSession with a freshly generated random AES-256 key.
     */
    public static EncryptedSession withGeneratedKey(Session delegate) {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            SecretKey secretKey = keyGen.generateKey();
            return new EncryptedSession(delegate, secretKey);
        } catch (Exception e) {
            throw new SessionException("Failed to generate AES key", e);
        }
    }

    public SecretKey getKey() {
        return key;
    }

    /**
     * Returns the key encoded as Base64 (useful for storing/sharing the key).
     */
    public String getKeyAsBase64() {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    @Override
    public List<InputItem> getHistory(String sessionId) {
        // The delegate stores EncryptedRunItem wrappers; we decrypt them here.
        // However, the Session interface works with RunItem for save and InputItem for getHistory.
        // Since we encrypt at save time and delegate getHistory returns InputItems,
        // we need to store encrypted data at a layer that is transparent.
        // Strategy: wrap in a shadow session that stores encrypted strings via the delegate's save.
        // We delegate to EncryptedBackingSession which stores EncryptedStringItem entries.
        return delegate.getHistory(sessionId).stream()
            .map(this::decryptInputItem)
            .toList();
    }

    @Override
    public void save(String sessionId, List<RunItem> newItems) {
        List<RunItem> encrypted = newItems.stream()
            .map(this::encryptRunItem)
            .toList();
        delegate.save(sessionId, encrypted);
    }

    // ---- Encryption helpers ----

    private RunItem encryptRunItem(RunItem item) {
        String plaintext = serializeRunItem(item);
        String ciphertext = encrypt(plaintext);
        return new RunItem.MessageOutput("__encrypted__", ciphertext);
    }

    private InputItem decryptInputItem(InputItem item) {
        if (item instanceof InputItem.AssistantMessage msg) {
            String ciphertext = msg.content();
            try {
                String plaintext = decrypt(ciphertext);
                return deserializeInputItem(plaintext);
            } catch (Exception e) {
                // If decryption fails, return as-is (may be unencrypted legacy data)
                return item;
            }
        }
        return item;
    }

    private String serializeRunItem(RunItem item) {
        return switch (item) {
            case RunItem.UserInput msg -> "UserInput|" + msg.content();
            case RunItem.MessageOutput msg -> "MessageOutput|" + msg.agentName() + "|" + msg.content();
            case RunItem.ToolCallItem call -> "ToolCallItem|" + call.agentName() + "|" + call.toolCallId()
                + "|" + call.toolName() + "|" + call.arguments();
            case RunItem.ToolOutputItem out -> "ToolOutputItem|" + out.agentName() + "|" + out.toolCallId()
                + "|" + out.toolName();
            case RunItem.HandoffItem h -> "HandoffItem|" + h.fromAgent() + "|" + h.toAgent();
        };
    }

    private InputItem deserializeInputItem(String plaintext) {
        if (plaintext.startsWith("UserInput|")) {
            String content = plaintext.substring("UserInput|".length());
            return new InputItem.UserMessage(content);
        }
        if (plaintext.startsWith("MessageOutput|")) {
            String[] parts = plaintext.split("\\|", 3);
            String content = parts.length >= 3 ? parts[2] : "";
            return new InputItem.AssistantMessage(content);
        }
        if (plaintext.startsWith("ToolCallItem|")) {
            String[] parts = plaintext.split("\\|", 5);
            String toolCallId = parts.length >= 3 ? parts[2] : "";
            String toolName = parts.length >= 4 ? parts[3] : "";
            return new InputItem.AssistantMessage("",
                java.util.List.of(new InputItem.ToolCall(toolCallId, toolName, java.util.Map.of())));
        }
        if (plaintext.startsWith("ToolOutputItem|")) {
            String[] parts = plaintext.split("\\|", 5);
            String toolCallId = parts.length >= 3 ? parts[2] : "";
            String toolName = parts.length >= 4 ? parts[3] : "";
            return new InputItem.ToolResult(toolCallId, toolName, "");
        }
        if (plaintext.startsWith("HandoffItem|")) {
            String[] parts = plaintext.split("\\|", 3);
            String from = parts.length >= 2 ? parts[1] : "?";
            String to = parts.length >= 3 ? parts[2] : "?";
            return new InputItem.SystemMessage("[Handoff: " + from + " → " + to + "]");
        }
        return new InputItem.SystemMessage(plaintext);
    }

    private String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(iv.length + ciphertext.length);
            buf.put(iv);
            buf.put(ciphertext);
            return Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception e) {
            throw new SessionException("Encryption failed", e);
        }
    }

    private String decrypt(String base64Ciphertext) {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Ciphertext);
            ByteBuffer buf = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buf.get(iv);
            byte[] ciphertext = new byte[buf.remaining()];
            buf.get(ciphertext);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new SessionException("Decryption failed", e);
        }
    }
}
