package cz.krokviak.agents.cli.command.builtin;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;
import cz.krokviak.agents.cli.task.TaskManager;
import cz.krokviak.agents.cli.task.TaskState;

public class TasksCommand implements Command {
    private final TaskManager taskManager;
    public TasksCommand(TaskManager taskManager) { this.taskManager = taskManager; }

    @Override public String name() { return "tasks"; }
    @Override public String description() { return "List background tasks (use: /tasks, /tasks kill <id>, /tasks stop <id>, /tasks <id>)"; }

    @Override
    public void execute(String args, CliContext ctx) {
        if (args != null && (args.startsWith("kill ") || args.startsWith("stop "))) {
            String taskId = args.substring(args.indexOf(' ') + 1).trim();
            taskManager.killTask(taskId);
            ctx.output().println("Task killed."); return;
        }
        if (args != null && !args.isBlank()) {
            TaskState t = taskManager.get(args.trim());
            if (t == null) { ctx.output().printError("No task: " + args.trim()); return; }
            ctx.output().println("Task: " + t.id() + " — " + t.description());
            ctx.output().println("  Status: " + t.status() + " | Duration: " + t.formatDuration());
            if (t.result() != null) ctx.output().println("  Result: " + t.result());
            if (t.error() != null) ctx.output().println("  Error: " + t.error());
            return;
        }
        var tasks = taskManager.all();
        if (tasks.isEmpty()) { ctx.output().println("No background tasks."); return; }
        ctx.output().println("Background tasks:");
        for (TaskState t : tasks) {
            String icon = switch (t.status()) { case PENDING -> "\033[2m○\033[0m"; case RUNNING -> "\033[33m●\033[0m"; case COMPLETED -> "\033[32m✓\033[0m";
                case FAILED -> "\033[31m✗\033[0m"; case KILLED -> "\033[2m⊘\033[0m"; };
            ctx.output().println("  " + icon + " " + t.id() + " — " + t.description() + " [" + t.status() + ", " + t.formatDuration() + "]");
        }
    }
}
