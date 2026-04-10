package cz.krokviak.agents.cli;

import cz.krokviak.agents.cli.agent.AgentRegistry;
import cz.krokviak.agents.cli.agent.TeamManager;
import cz.krokviak.agents.cli.command.Commands;
import cz.krokviak.agents.cli.command.builtin.*;
import cz.krokviak.agents.cli.cron.CronScheduler;
import cz.krokviak.agents.cli.engine.AgentRunner;
import cz.krokviak.agents.cli.engine.ToolDispatcher;
import cz.krokviak.agents.cli.context.ContextCompactor;
import cz.krokviak.agents.cli.hook.Hooks;
import cz.krokviak.agents.cli.hook.builtin.GuardrailHook;
import cz.krokviak.agents.cli.hook.builtin.PermissionHook;
import cz.krokviak.agents.cli.hook.builtin.PlanModeHook;
import cz.krokviak.agents.cli.mailbox.MailboxManager;
import cz.krokviak.agents.cli.memory.MemoryLoader;
import cz.krokviak.agents.cli.memory.MemoryStore;
import cz.krokviak.agents.cli.permission.PermissionManager;
import cz.krokviak.agents.cli.plugin.PluginContextImpl;
import cz.krokviak.agents.cli.plugin.Plugins;
import cz.krokviak.agents.cli.repl.Repl;
import cz.krokviak.agents.cli.task.TaskManager;
import cz.krokviak.agents.cli.render.PlainRenderer;
import cz.krokviak.agents.cli.render.Renderer;
import cz.krokviak.agents.cli.skill.SkillLoader;
import cz.krokviak.agents.cli.skill.SkillRegistry;
import cz.krokviak.agents.cli.tool.*;
import cz.krokviak.agents.cli.tool.MemoryWriteTool;
import cz.krokviak.agents.cli.tool.MemoryReadTool;
import cz.krokviak.agents.model.AnthropicModel;
import cz.krokviak.agents.model.Model;
import cz.krokviak.agents.model.OpenAIChatCompletionsModel;
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
        Model model = switch (config.provider()) {
            case ANTHROPIC -> new AnthropicModel(config.apiKey(), config.baseUrl(), config.model());
            case OPENAI -> new cz.krokviak.agents.model.OpenAIOfficalModel(config.apiKey(), config.baseUrl(), config.model());
        };
        Path cwd = config.workingDirectory();

        // Memory store — ~/.claude/projects/<cwd-hash>/memory/
        String cwdKey = cwd.toAbsolutePath().toString()
            .replaceAll("[^a-zA-Z0-9]", "_").replaceAll("_+", "_").replaceAll("^_|_$", "");
        Path memoryDir = Path.of(System.getProperty("user.home"), ".claude", "projects", cwdKey, "memory");
        MemoryStore memoryStore = new MemoryStore(memoryDir);

        // Memory + prompt
        String projectInstructions = MemoryLoader.loadProjectInstructions(cwd, memoryStore);
        String systemPrompt = SystemPrompts.build(cwd, projectInstructions);

        // Permission
        PermissionManager.PermissionMode permMode = switch (config.permissionMode().toLowerCase()) {
            case "trust" -> PermissionManager.PermissionMode.TRUST;
            case "deny", "deny-all", "deny_all" -> PermissionManager.PermissionMode.DENY_ALL;
            default -> PermissionManager.PermissionMode.DEFAULT;
        };
        PermissionManager permissionManager = new PermissionManager(permMode);

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

        // Skills
        SkillRegistry skillRegistry = new SkillRegistry();
        SkillLoader.loadBuiltinSkills().forEach(skillRegistry::register);
        SkillLoader.loadUserSkills().forEach(skillRegistry::register);
        SkillLoader.loadProjectSkills(cwd).forEach(skillRegistry::register);

        // Task manager
        TaskManager taskManager = new TaskManager();
        MailboxManager mailboxManager = new MailboxManager();
        CronScheduler cronScheduler = new CronScheduler(entry ->
            System.err.println("[cron] Triggered: " + entry.id() + " — " + entry.prompt()));
        AgentRegistry agentRegistry = new AgentRegistry();
        TeamManager teamManager = new TeamManager();

        // Context — choose renderer. TamboUI for interactive terminal, PlainRenderer for piped.
        Renderer output;
        cz.krokviak.agents.cli.render.tui.CliApp cliApp = null;
        cz.krokviak.agents.cli.render.tui.CliController ctrl = null;
        if (System.console() != null) {
            ctrl = new cz.krokviak.agents.cli.render.tui.CliController();
            var tuiRenderer = new cz.krokviak.agents.cli.render.tui.TuiRenderer(ctrl);
            cliApp = new cz.krokviak.agents.cli.render.tui.CliApp(ctrl, tuiRenderer);
            output = tuiRenderer;
            permissionManager.setTuiRenderer(tuiRenderer);
        } else {
            output = new PlainRenderer();
        }
        ContextCompactor compactor = new ContextCompactor(model);
        CliContext ctx = new CliContext(model, config.model(), config.apiKey(), config.baseUrl(),
            output, permissionManager, compactor, cwd, systemPrompt, session, config.sessionId(),
            taskManager, mailboxManager);

        // Store TuiRenderer on context for ExitPlanModeTool
        if (output instanceof cz.krokviak.agents.cli.render.tui.TuiRenderer tr) {
            ctx.setTuiRenderer(tr);
        }

        // Sync plan mode state to CliController for TUI display
        if (ctrl != null) {
            final var fCtrl = ctrl;
            ctx.onPlanModeChange(() -> fCtrl.setPlanMode(ctx.isPlanMode()));
        }

        // Hooks (plan mode → guardrail → permission, in order)
        Hooks hooks = new Hooks();
        var planStore = new cz.krokviak.agents.cli.plan.PlanStore();
        ctx.setPlanStore(planStore);
        hooks.register(new PlanModeHook(planStore));
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
        toolList.add(new EnterPlanModeTool(ctx, planStore));
        toolList.add(new ExitPlanModeTool(ctx, planStore));
        toolList.add(new AskUserQuestionTool(ctx));
        toolList.add(new SendMessageTool(mailboxManager));

        // Tools — task management
        toolList.add(new TaskCreateTool(taskManager));
        toolList.add(new TaskGetTool(taskManager));
        toolList.add(new TaskListTool(taskManager));
        toolList.add(new TaskUpdateTool(taskManager));
        toolList.add(new TaskStopTool(taskManager));
        toolList.add(new NotebookEditTool(cwd));
        toolList.add(new SkillTool(skillRegistry));

        // Tools — memory
        toolList.add(new MemoryWriteTool(memoryStore));
        toolList.add(new MemoryReadTool(memoryStore));

        // Tools — task output & synthetic output
        toolList.add(new TaskOutputTool(taskManager));
        toolList.add(new SyntheticOutputTool());

        // Tools — cron scheduling
        toolList.add(new CronCreateTool(cronScheduler));
        toolList.add(new CronDeleteTool(cronScheduler));
        toolList.add(new CronListTool(cronScheduler));

        // Tools — remote trigger
        toolList.add(new RemoteTriggerTool());

        // Tools — brief, config, todo
        toolList.add(new BriefTool(model));
        toolList.add(new ConfigTool());
        toolList.add(new TodoWriteTool());

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
        commands.register(new PlanCommand(planStore));
        commands.register(new DiffCommand());
        commands.register(new ContextCommand());
        commands.register(new DoctorCommand());
        commands.register(new MemoryCommand());
        commands.register(new HooksCommand(hooks));
        commands.register(new HelpCommand(commands));

        // Plugins
        Plugins.loadAll(new PluginContextImpl(commands, hooks, ctx));

        // Populate command trie for autocomplete
        if (ctrl != null) {
            for (var cmd : commands.all()) {
                ctrl.commandTrie().insert(cmd.name(), cmd.description());
            }
        }

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

        // Wire plan mode toggle (Tab key in TUI)
        if (cliApp != null) {
            final var fCtrl = ctrl;
            final var fCtx = ctx;
            final var fPlanStore = planStore;
            cliApp.setPlanModeToggle(() -> {
                boolean entering = !fCtx.isPlanMode();
                fCtx.setPlanMode(entering);
                fCtrl.setPlanMode(entering);
                if (entering) {
                    try {
                        String slug = fPlanStore.createPlan();
                        fCtrl.setPlanSlug(slug);
                    } catch (Exception ignored) {}
                } else {
                }
            });
        }

        // Run
        AgentRunner runner = new AgentRunner(ctx, toolDispatcher, config.maxTurns());
        new Repl(ctx, commands, runner, cliApp).start();
    }
}
