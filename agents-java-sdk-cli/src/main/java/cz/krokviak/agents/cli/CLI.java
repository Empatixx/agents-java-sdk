package cz.krokviak.agents.cli;

import cz.krokviak.agents.cli.agent.AgentRegistry;
import cz.krokviak.agents.cli.agent.TeamManager;
import cz.krokviak.agents.cli.command.Commands;
import cz.krokviak.agents.cli.command.builtin.*;
import cz.krokviak.agents.cli.engine.AgentRunner;
import cz.krokviak.agents.cli.engine.ToolDispatcher;
import cz.krokviak.agents.cli.context.ContextCompactor;
import cz.krokviak.agents.cli.hook.Hooks;
import cz.krokviak.agents.cli.hook.builtin.GuardrailHook;
import cz.krokviak.agents.cli.hook.builtin.PermissionHook;
import cz.krokviak.agents.cli.hook.builtin.PlanModeHook;
import cz.krokviak.agents.cli.mailbox.MailboxManager;
import cz.krokviak.agents.cli.memory.MemoryLoader;
import cz.krokviak.agents.cli.permission.PermissionManager;
import cz.krokviak.agents.cli.plugin.PluginContextImpl;
import cz.krokviak.agents.cli.plugin.Plugins;
import cz.krokviak.agents.cli.repl.Repl;
import cz.krokviak.agents.cli.task.TaskManager;
import cz.krokviak.agents.cli.render.AnsiRenderer;
import cz.krokviak.agents.cli.render.PlainRenderer;
import cz.krokviak.agents.cli.render.Renderer;
import cz.krokviak.agents.cli.tool.*;
import cz.krokviak.agents.model.AnthropicModel;
import cz.krokviak.agents.model.Model;
import cz.krokviak.agents.session.Session;
import cz.krokviak.agents.session.SQLiteSession;
import cz.krokviak.agents.tool.ExecutableTool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CLI {

    public static void main(String[] args) {
        CliConfig config = CliConfig.parse(args);
        Model model = new AnthropicModel(config.apiKey(), config.baseUrl(), config.model());
        Path cwd = config.workingDirectory();

        // Memory + prompt
        String projectInstructions = MemoryLoader.loadProjectInstructions(cwd);
        String systemPrompt = SystemPrompts.build(cwd, projectInstructions);

        // Permission
        PermissionManager.PermissionMode permMode = switch (config.permissionMode().toLowerCase()) {
            case "trust" -> PermissionManager.PermissionMode.TRUST;
            case "deny", "deny-all", "deny_all" -> PermissionManager.PermissionMode.DENY_ALL;
            default -> PermissionManager.PermissionMode.DEFAULT;
        };
        PermissionManager permissionManager = new PermissionManager(permMode, null);

        // Session
        Session session = null;
        if (config.sessionId() != null) {
            try {
                Path sessionDir = Path.of(System.getProperty("user.home"), ".claude-cli");
                Files.createDirectories(sessionDir);
                session = new SQLiteSession(sessionDir.resolve("sessions.db"));
            } catch (Exception e) {
                System.err.println("Warning: Failed to initialize session storage: " + e.getMessage());
            }
        }

        // Task manager
        TaskManager taskManager = new TaskManager();
        MailboxManager mailboxManager = new MailboxManager();
        AgentRegistry agentRegistry = new AgentRegistry();
        TeamManager teamManager = new TeamManager();

        // Context — choose renderer based on TTY detection
        boolean isTty = System.console() != null;
        Renderer output = isTty ? new AnsiRenderer() : new PlainRenderer();
        ContextCompactor compactor = new ContextCompactor(model);
        CliContext ctx = new CliContext(model, config.model(), config.apiKey(), config.baseUrl(),
            output, permissionManager, compactor, cwd, systemPrompt, session, config.sessionId(),
            taskManager, mailboxManager);

        // Hooks (plan mode → guardrail → permission, in order)
        Hooks hooks = new Hooks();
        hooks.register(new PlanModeHook());
        hooks.register(new GuardrailHook());
        hooks.register(new PermissionHook(permissionManager));

        // Tools — file system
        List<ExecutableTool> toolList = new ArrayList<>(List.of(
            new ReadFileTool(cwd), new WriteFileTool(cwd), new EditFileTool(cwd),
            new BashTool(cwd), new GlobTool(cwd), new GrepTool(cwd), new ListDirectoryTool(cwd)
        ));

        // Tools — web
        toolList.add(new WebFetchTool());
        toolList.add(new WebSearchTool());
        toolList.add(new PowerShellTool(cwd));

        // Tools — planning & interaction
        toolList.add(new EnterPlanModeTool(ctx));
        toolList.add(new ExitPlanModeTool(ctx));
        toolList.add(new AskUserQuestionTool(ctx));
        toolList.add(new SendMessageTool(mailboxManager));

        // Tools — task management
        toolList.add(new TaskCreateTool(taskManager));
        toolList.add(new TaskGetTool(taskManager));
        toolList.add(new TaskListTool(taskManager));
        toolList.add(new TaskUpdateTool(taskManager));
        toolList.add(new TaskStopTool(taskManager));
        toolList.add(new NotebookEditTool(cwd));

        // Tool dispatcher
        ToolDispatcher toolDispatcher = new ToolDispatcher(toolList, hooks, ctx);

        // Tools that need ToolDispatcher/TaskManager/AgentRegistry (added after dispatcher creation)
        toolList.add(new AgentTool(ctx, toolList, taskManager, agentRegistry));
        toolList.add(new ToolSearchTool(toolDispatcher));
        toolList.add(new TeamCreateTool(teamManager));
        toolList.add(new TeamDeleteTool(teamManager));

        // Commands (20 total)
        Commands commands = new Commands();
        commands.register(new ExitCommand());
        commands.register(new ClearCommand());
        commands.register(new CostCommand());
        commands.register(new CompactCommand());
        commands.register(new ModelCommand());
        commands.register(new ToolsCommand(toolDispatcher));
        commands.register(new PermissionsCommand());
        commands.register(new UndoCommand());
        commands.register(new SessionCommand());
        commands.register(new TasksCommand(taskManager));
        commands.register(new PlanCommand());
        commands.register(new DiffCommand());
        commands.register(new ContextCommand());
        commands.register(new DoctorCommand());
        commands.register(new MemoryCommand());
        commands.register(new HooksCommand(hooks));
        commands.register(new HelpCommand(commands));

        // Plugins
        Plugins.loadAll(new PluginContextImpl(commands, hooks, ctx));

        // Banner
        output.println("");
        output.println("\033[1mClaude Code CLI (Java)\033[0m — model: " + config.model());
        output.println("Working directory: " + cwd);
        if (permMode != PermissionManager.PermissionMode.TRUST) {
            output.println("Permission mode: " + config.permissionMode());
        }
        if (!projectInstructions.isBlank()) {
            output.println("\033[2mLoaded project instructions from CLAUDE.md\033[0m");
        }
        output.println("Type /help for commands, /exit to quit");

        // Run
        AgentRunner runner = new AgentRunner(ctx, toolDispatcher, config.maxTurns());
        new Repl(ctx, commands, runner).start();
    }
}
