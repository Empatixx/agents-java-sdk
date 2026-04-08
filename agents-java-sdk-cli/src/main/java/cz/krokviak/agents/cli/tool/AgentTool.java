package cz.krokviak.agents.cli.tool;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.mailbox.MailboxManager;
import cz.krokviak.agents.cli.task.TaskManager;
import cz.krokviak.agents.cli.task.TaskState;
import cz.krokviak.agents.context.ToolContext;
import cz.krokviak.agents.model.*;
import cz.krokviak.agents.runner.InputItem;
import cz.krokviak.agents.tool.*;

import java.util.*;

public class AgentTool implements ExecutableTool {
    private final CliContext ctx;
    private final List<ExecutableTool> tools;
    private final TaskManager taskManager;
    private final ToolDefinition toolDefinition;

    private static final String AGENT_PROMPT = """
        You are a sub-agent spawned to handle a specific task. You have access to file system tools. \
        Complete your task thoroughly and return clear, structured results. Be concise but complete.""";

    public AgentTool(CliContext ctx, List<ExecutableTool> tools, TaskManager taskManager) {
        this.ctx = ctx; this.tools = tools; this.taskManager = taskManager;
        this.toolDefinition = new ToolDefinition("agent",
            "Spawn a sub-agent to handle a task. Use run_in_background=true for long-running tasks.",
            Map.of("type", "object", "properties", Map.of(
                "prompt", Map.of("type", "string", "description", "The task for the sub-agent"),
                "description", Map.of("type", "string", "description", "Short 3-5 word description"),
                "run_in_background", Map.of("type", "boolean", "description", "Run asynchronously (default false)"),
                "model", Map.of("type", "string", "description", "Model override")
            ), "required", List.of("prompt", "description")));
    }

    @Override public String name() { return "agent"; }
    @Override public String description() { return toolDefinition.description(); }
    @Override public ToolDefinition definition() { return toolDefinition; }

    @Override
    public ToolOutput execute(ToolArgs args, ToolContext<?> ctx2) throws Exception {
        String prompt = args.get("prompt", String.class);
        String desc = args.getOrDefault("description", String.class, "sub-agent");
        Boolean bg = args.getOrDefault("run_in_background", Boolean.class, false);
        String modelOverride = args.get("model", String.class);
        if (prompt == null || prompt.isBlank()) return ToolOutput.text("Error: prompt required");

        Model model = ctx.model();
        if (modelOverride != null && !modelOverride.isBlank())
            model = new cz.krokviak.agents.model.AnthropicModel(ctx.apiKey(), ctx.baseUrl(), modelOverride);

        if (Boolean.TRUE.equals(bg)) return spawnBackground(prompt, desc, model);
        else return runForeground(prompt, desc, model, null);
    }

    private ToolOutput runForeground(String prompt, String desc, Model model, String agentId) {
        ctx.output().println("\033[2m  [Agent: " + desc + "]\033[0m");
        List<ToolDefinition> defs = tools.stream().filter(t -> !t.name().equals("agent")).map(ExecutableTool::definition).toList();
        List<InputItem> history = new ArrayList<>(List.of(new InputItem.UserMessage(prompt)));
        StringBuilder response = new StringBuilder();

        for (int turn = 0; turn < 15; turn++) {
            injectMailbox(history, agentId);
            ModelResponse resp = model.call(new LlmContext(AGENT_PROMPT, List.copyOf(history), defs, null,
                ModelSettings.builder().maxTokens(8192).build()), ModelSettings.builder().maxTokens(8192).build());
            boolean hasTools = false;
            List<InputItem.ToolCall> tcs = new ArrayList<>();
            for (var out : resp.output()) {
                switch (out) {
                    case ModelResponse.OutputItem.Message msg -> response.append(msg.content());
                    case ModelResponse.OutputItem.ToolCallRequest tc -> { hasTools = true; tcs.add(new InputItem.ToolCall(tc.id(), tc.name(), tc.arguments())); }
                }
            }
            if (!hasTools) break;
            history.add(new InputItem.AssistantMessage(response.isEmpty() ? null : response.toString(), tcs));
            response.setLength(0);
            for (var tc : tcs) {
                ExecutableTool tool = tools.stream().filter(t -> t.name().equals(tc.name()) && !t.name().equals("agent")).findFirst().orElse(null);
                String result = tool != null ? executeSafe(tool, tc.arguments()) : "Error: unknown tool " + tc.name();
                history.add(new InputItem.ToolResult(tc.id(), tc.name(), result));
            }
        }
        ctx.output().println("\033[2m  [Agent completed]\033[0m");
        return ToolOutput.text(response.isEmpty() ? "(no response)" : response.toString());
    }

    private ToolOutput spawnBackground(String prompt, String desc, Model model) {
        String taskId = taskManager.nextId();
        TaskState task = new TaskState(taskId, desc);
        taskManager.register(task);
        Thread thread = Thread.startVirtualThread(() -> {
            try {
                ToolOutput result = runForeground(prompt, desc, model, taskId);
                String text = result instanceof ToolOutput.Text t ? t.content() : result.toString();
                task.complete(text);
                taskManager.addNotification(new TaskManager.TaskNotification(taskId, desc, TaskState.Status.COMPLETED,
                    text.length() > 200 ? text.substring(0, 200) + "..." : text));
            } catch (Exception e) {
                task.fail(e.getMessage());
                taskManager.addNotification(new TaskManager.TaskNotification(taskId, desc, TaskState.Status.FAILED, e.getMessage()));
            }
        });
        task.setThread(thread);
        return ToolOutput.text("Background agent started: " + taskId + " (" + desc + ")\nUse /tasks to check status.");
    }

    private void injectMailbox(List<InputItem> history, String agentId) {
        if (agentId == null) return;

        MailboxManager mailboxManager = ctx.mailboxManager();
        if (mailboxManager == null) return;

        for (var message : mailboxManager.drain(agentId)) {
            String content = String.format("<mailbox-message>\n  from: %s\n  to: %s\n  content: %s\n</mailbox-message>",
                message.sender(), message.recipient(), message.content());
            history.add(new InputItem.SystemMessage(content));
        }
    }

    private String executeSafe(ExecutableTool tool, Map<String, Object> args) {
        try { ToolOutput o = tool.execute(new ToolArgs(args), null);
            return switch(o) { case ToolOutput.Text t -> t.content(); default -> o.toString(); };
        } catch (Exception e) { return "Error: " + e.getMessage(); }
    }
}
