package cz.krokviak.agents.agent.mailbox;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MailboxManager {
    public record MailMessage(String sender, String recipient, String content, Instant createdAt) {}

    private final ConcurrentHashMap<String, Queue<MailMessage>> inboxes = new ConcurrentHashMap<>();

    public void send(String sender, String recipient, String content) {
        inboxes.computeIfAbsent(recipient, ignored -> new ConcurrentLinkedQueue<>())
            .add(new MailMessage(sender, recipient, content, Instant.now()));
    }

    public List<MailMessage> drain(String recipient) {
        Queue<MailMessage> queue = inboxes.get(recipient);
        if (queue == null || queue.isEmpty()) return List.of();

        List<MailMessage> messages = new ArrayList<>();
        for (MailMessage message; (message = queue.poll()) != null; ) {
            messages.add(message);
        }
        return messages;
    }
}
