package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.cli.mailbox.MailboxManager;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.tool.ExecutableTool;
import cz.krokviak.agents.tool.ToolArgs;
import cz.krokviak.agents.tool.ToolDefinition;
import cz.krokviak.agents.tool.ToolOutput;

import java.util.List;
import java.util.Map;

public class SendMessageTool implements ExecutableTool {
    private final MailboxManager mailboxManager;
    private final ToolDefinition toolDefinition;

    public SendMessageTool(MailboxManager mailboxManager) {
        this.mailboxManager = mailboxManager;
        this.toolDefinition = new ToolDefinition("send_message",
            "Send a message to the main agent or a background task mailbox.",
            Map.of("type", "object", "properties", Map.of(
                "recipient", Map.of("type", "string", "description", "Mailbox recipient, for example main or task-1"),
                "message", Map.of("type", "string", "description", "Message content"),
                "sender", Map.of("type", "string", "description", "Optional sender label")
            ), "required", List.of("recipient", "message")));
    }

    @Override public String name() { return "send_message"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx) {
        String recipient = args.get("recipient", String.class);
        String message = args.get("message", String.class);
        String sender = args.getOrDefault("sender", String.class, "main");

        if (recipient == null || recipient.isBlank()) return ToolOutput.text("Error: recipient required");
        if (message == null || message.isBlank()) return ToolOutput.text("Error: message required");

        String normalizedRecipient = switch (recipient) {
            case "parent" -> "main";
            default -> recipient;
        };
        mailboxManager.send(sender, normalizedRecipient, message);
        return ToolOutput.text("Message sent to " + normalizedRecipient + " from " + sender);
    }
}
