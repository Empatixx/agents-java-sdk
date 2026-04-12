package cz.krokviak.agents.cli.command.builtin;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;

public class TasksCommand implements Command {
    @Override public String name() { return "tasks"; }
    @Override public String description() { return "List background tasks (use: /tasks, /tasks kill <id>, /tasks stop <id>)"; }

    @Override
    public void execute(String args, CliContext ctx) {
        if (args != null && (args.startsWith("kill ") || args.startsWith("stop "))) {
            String taskId = args.substring(args.indexOf(' ') + 1).trim();
            ctx.agent().stopTask(taskId);
            ctx.output().println("Task killed.");
            return;
        }
        var tasks = ctx.agent().listTasks();
        if (args != null && !args.isBlank()) {
            String id = args.trim();
            var t = tasks.stream().filter(x -> x.id().equals(id)).findFirst().orElse(null);
            if (t == null) { ctx.output().printError("No task: " + id); return; }
            ctx.output().println("Task: " + t.id() + " — " + t.description());
            ctx.output().println("  Status: " + t.status());
            if (t.summary() != null) ctx.output().println("  Summary: " + t.summary());
            return;
        }
        if (tasks.isEmpty()) { ctx.output().println("No background tasks."); return; }
        ctx.output().println("Background tasks:");
        for (var t : tasks) {
            String icon = switch (t.status()) {
                case "PENDING" -> "\033[2m○\033[0m";
                case "RUNNING" -> "\033[33m●\033[0m";
                case "COMPLETED" -> "\033[32m✓\033[0m";
                case "FAILED" -> "\033[31m✗\033[0m";
                case "KILLED" -> "\033[2m⊘\033[0m";
                default -> "?";
            };
            ctx.output().println("  " + icon + " " + t.id() + " — " + t.description() + " [" + t.status() + "]");
        }
    }
}
