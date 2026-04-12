package cz.krokviak.agents.cli;

import cz.krokviak.agents.agent.spawn.AgentRegistry;
import cz.krokviak.agents.agent.spawn.TeamManager;
import cz.krokviak.agents.cli.cron.CronScheduler;
import cz.krokviak.agents.agent.engine.ToolDispatcher;
import cz.krokviak.agents.agent.hook.Hooks;
import cz.krokviak.agents.agent.mailbox.MailboxManager;
import cz.krokviak.agents.cli.memory.MemoryStore;
import cz.krokviak.agents.agent.plan.PlanStore;
import cz.krokviak.agents.cli.skill.SkillRegistry;
import cz.krokviak.agents.agent.task.TaskManager;
import cz.krokviak.agents.cli.tool.*;
import cz.krokviak.agents.model.Model;
import cz.krokviak.agents.tool.ExecutableTool;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds and wires all tools, hooks, and the dispatcher.
 * Extracted from CLI.main() for clarity and testability.
 */
public final class ToolRegistry {

    private final List<ExecutableTool> tools;
    private final ToolDispatcher dispatcher;

    private ToolRegistry(List<ExecutableTool> tools, ToolDispatcher dispatcher) {
        this.tools = tools;
        this.dispatcher = dispatcher;
    }

    public ToolDispatcher dispatcher() { return dispatcher; }
    public List<ExecutableTool> tools() { return tools; }

    public static ToolRegistry create(
            CliContext ctx, Path cwd, Model model,
            TaskManager taskManager, MailboxManager mailboxManager,
            MemoryStore memoryStore, SkillRegistry skillRegistry,
            CronScheduler cronScheduler, AgentRegistry agentRegistry,
            TeamManager teamManager, PlanStore planStore, Hooks hooks) {

        List<ExecutableTool> toolList = new ArrayList<>();

        // File system
        toolList.add(new ReadFileTool(cwd));
        toolList.add(new WriteFileTool(cwd));
        toolList.add(new EditFileTool(cwd));
        toolList.add(new BashTool(cwd));
        toolList.add(new GlobTool(cwd));
        toolList.add(new GrepTool(cwd));
        toolList.add(new ListDirectoryTool(cwd));

        // Web
        toolList.add(new WebFetchTool());
        toolList.add(new WebSearchTool());
        toolList.add(new PowerShellTool(cwd));

        // Planning & interaction
        toolList.add(new EnterPlanModeTool(ctx, planStore));
        toolList.add(new ExitPlanModeTool(ctx, planStore));
        toolList.add(new AskUserQuestionTool(ctx.agent()));
        toolList.add(new SendMessageTool(ctx.agent()));

        // Task management
        toolList.add(new TaskCreateTool(ctx.agent()));
        toolList.add(new TaskGetTool(ctx.agent()));
        toolList.add(new TaskListTool(ctx.agent()));
        toolList.add(new TaskUpdateTool(ctx.agent()));
        toolList.add(new TaskStopTool(ctx.agent()));
        toolList.add(new TaskOutputTool(taskManager));

        // Notebook, skills
        toolList.add(new NotebookEditTool(cwd));
        toolList.add(new SkillTool(skillRegistry));

        // Memory
        toolList.add(new MemoryWriteTool(memoryStore));
        toolList.add(new MemoryReadTool(memoryStore));

        // Synthetic output
        toolList.add(new SyntheticOutputTool());

        // Cron scheduling
        toolList.add(new CronCreateTool(cronScheduler));
        toolList.add(new CronDeleteTool(cronScheduler));
        toolList.add(new CronListTool(cronScheduler));

        // Remote trigger
        toolList.add(new RemoteTriggerTool());

        // Brief, config, todo
        toolList.add(new BriefTool(model));
        toolList.add(new ConfigTool());
        toolList.add(new TodoWriteTool());

        // Git worktrees
        toolList.add(new EnterWorktreeTool(ctx));
        toolList.add(new ExitWorktreeTool(ctx));

        // MCP proxy tools
        if (ctx.mcpManager() != null) {
            for (var mcpTool : ctx.mcpManager().allTools()) {
                toolList.add(new cz.krokviak.agents.cli.mcp.McpProxyTool(ctx.mcpManager(), mcpTool));
            }
        }

        // Dispatcher (needed by some tools below)
        ToolDispatcher dispatcher = new ToolDispatcher(toolList, hooks, ctx);

        // Tools that need the dispatcher or registry
        toolList.add(new AgentTool(ctx.agent()));
        toolList.add(new ToolSearchTool(dispatcher));
        toolList.add(new TeamCreateTool(teamManager));
        toolList.add(new TeamDeleteTool(teamManager));

        return new ToolRegistry(toolList, dispatcher);
    }
}
