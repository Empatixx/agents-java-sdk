package cz.krokviak.agents.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cz.krokviak.agents.agent.spawn.AgentRegistry;
import cz.krokviak.agents.agent.spawn.TeamManager;
import cz.krokviak.agents.cli.command.Commands;
import cz.krokviak.agents.cli.command.builtin.*;
import cz.krokviak.agents.cli.cron.CronScheduler;
import cz.krokviak.agents.agent.engine.AgentRunner;
import cz.krokviak.agents.agent.engine.ToolDispatcher;
import cz.krokviak.agents.agent.context.ContextCompactor;
import cz.krokviak.agents.agent.hook.Hooks;
import cz.krokviak.agents.agent.hook.builtin.GuardrailHook;
import cz.krokviak.agents.agent.hook.builtin.PermissionHook;
import cz.krokviak.agents.agent.hook.builtin.PlanModeHook;
import cz.krokviak.agents.agent.mailbox.MailboxManager;
import cz.krokviak.agents.cli.memory.MemoryLoader;
import cz.krokviak.agents.cli.memory.MemoryStore;
import cz.krokviak.agents.agent.permission.PermissionManager;
import cz.krokviak.agents.cli.plugin.PluginContextImpl;
import cz.krokviak.agents.cli.plugin.Plugins;
import cz.krokviak.agents.cli.repl.Repl;
import cz.krokviak.agents.agent.task.TaskManager;
import cz.krokviak.agents.cli.render.PlainRenderer;
import cz.krokviak.agents.cli.render.Renderer;
import cz.krokviak.agents.cli.skill.SkillLoader;
import cz.krokviak.agents.cli.skill.SkillRegistry;
import cz.krokviak.agents.adapter.anthropic.AnthropicModel;
import cz.krokviak.agents.model.Model;
import cz.krokviak.agents.session.AdvancedSQLiteSession;
import cz.krokviak.agents.session.Session;
import cz.krokviak.agents.session.SessionMetadata;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

public class CLI {
    private static final Logger log = LoggerFactory.getLogger(CLI.class);

    public static void main(String[] args) {
        CliConfig config;
        try {
            config = CliConfig.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
            return;
        }
        Model model = switch (config.provider()) {
            case ANTHROPIC -> new AnthropicModel(config.apiKey(), config.baseUrl(), config.model());
            case OPENAI -> new cz.krokviak.agents.adapter.openai.OpenAIOfficalModel(config.apiKey(), config.baseUrl(), config.model());
        };
        Path cwd = config.workingDirectory();

        // Memory store — ~/.krok/projects/<cwd-hash>/memory/
        String cwdKey = cwd.toAbsolutePath().toString()
            .replaceAll("[^a-zA-Z0-9]", "_").replaceAll("_+", "_").replaceAll("^_|_$", "");
        Path memoryDir = Path.of(System.getProperty("user.home"), ".krok", "projects", cwdKey, "memory");
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

        // Session — always create, auto-generate ID if not provided
        AdvancedSQLiteSession advancedSession = null;
        String sessionId = config.sessionId();
        try {
            Path sessionDir = Path.of(System.getProperty("user.home"), ".krok-cli");
            Files.createDirectories(sessionDir);
            advancedSession = new AdvancedSQLiteSession(sessionDir.resolve("sessions.db"));
            if (sessionId == null) {
                sessionId = UUID.randomUUID().toString().substring(0, 8);
            }
            advancedSession.saveMetadata(new SessionMetadata(
                sessionId, null, Instant.now(), Instant.now(), 0, cwd.toAbsolutePath().toString()));
        } catch (Exception e) {
            log.warn( "Failed to init session storage", e);
        }
        // Wrap the persistent session with a BatchedSessionWriter so turn-loop
        // writes never block the main thread on disk I/O.
        Session session = advancedSession != null
            ? new cz.krokviak.agents.session.BatchedSessionWriter(advancedSession)
            : null;

        // Skills
        SkillRegistry skillRegistry = new SkillRegistry();
        SkillLoader.loadBuiltinSkills().forEach(skillRegistry::register);
        SkillLoader.loadUserSkills().forEach(skillRegistry::register);
        SkillLoader.loadProjectSkills(cwd).forEach(skillRegistry::register);

        // Task manager
        TaskManager taskManager = new TaskManager();
        MailboxManager mailboxManager = new MailboxManager();
        CronScheduler cronScheduler = new CronScheduler(entry ->
            log.info( "Triggered: " + entry.id()));
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
        } else {
            output = new PlainRenderer();
        }
        ContextCompactor compactor = new ContextCompactor(model);
        CliContext ctx = new CliContext(model, config.model(), config.apiKey(), config.baseUrl(),
            output, permissionManager, compactor, cwd, systemPrompt, session, sessionId,
            taskManager, mailboxManager);
        ctx.setAdvancedSession(advancedSession);

        // MCP servers
        var mcpManager = new cz.krokviak.agents.cli.mcp.McpManager();
        mcpManager.initialize(cwd);
        ctx.setMcpManager(mcpManager);
        if (mcpManager.hasServers()) {
            output.println("  MCP: " + mcpManager.allTools().size() + " tools from "
                + mcpManager.servers().size() + " server(s)");
        }

        // Wire event bus → renderer
        new cz.krokviak.agents.cli.event.RenderEventListener(output).register(ctx.eventBus());

        // Install UI-agnostic agent facade (Phase 1: delegates to existing managers via CliContext)
        var agentService = new cz.krokviak.agents.agent.service.AgentServiceImpl(ctx);
        ctx.setAgent(agentService);
        permissionManager.setAgentService(agentService);
        agentService.setAgentRegistry(agentRegistry);
        agentService.setTeamManager(teamManager);
        var spawner = new cz.krokviak.agents.agent.spawn.AgentSpawner(ctx, agentRegistry, taskManager);
        agentService.setSpawner(spawner);

        // Load output styles from ~/.claude/output-styles/ + .krok/output-styles/
        ctx.setOutputStyles(new cz.krokviak.agents.cli.style.OutputStyleRegistry(
            cz.krokviak.agents.cli.style.OutputStyleLoader.load(cwd)));

        // Install tips scheduler — one hint per spinner cycle, least-recently-shown.
        var tipRegistry = new cz.krokviak.agents.cli.tips.TipRegistry(
            cz.krokviak.agents.cli.tips.TipLoader.loadAll());
        new cz.krokviak.agents.cli.tips.TipScheduler(output, tipRegistry)
            .install(agentService.events());

        // Store TuiRenderer on context for ExitPlanModeTool
        if (output instanceof cz.krokviak.agents.cli.render.tui.TuiRenderer tr) {
            ctx.setPromptRenderer(tr);
            // Install the blocking-prompt bridge so PermissionRequested/QuestionRequested
            // events drive TUI dialogs on virtual threads and resolve the agent-side futures.
            new cz.krokviak.agents.cli.service.PermissionDialogBridge(agentService, tr).install();
        }

        // Sync plan mode state to CliController for TUI display
        if (ctrl != null) {
            final var fCtrl = ctrl;
            ctx.onPlanModeChange(() -> fCtrl.setPlanMode(ctx.isPlanMode()));
        }

        // Hooks (plan mode → guardrail → permission, in order)
        Hooks hooks = new Hooks();
        ctx.setHooks(hooks);
        var planStore = new cz.krokviak.agents.agent.plan.PlanStore();
        ctx.setPlanStore(planStore);
        hooks.register(new PlanModeHook(planStore));
        hooks.register(new GuardrailHook());
        hooks.register(new PermissionHook(permissionManager));

        // Tools & dispatcher
        ToolRegistry toolRegistry = ToolRegistry.create(ctx, cwd, model,
            taskManager, mailboxManager, memoryStore, skillRegistry,
            cronScheduler, agentRegistry, teamManager, planStore, hooks);
        ToolDispatcher toolDispatcher = toolRegistry.dispatcher();
        agentService.setToolDispatcher(toolDispatcher);

        // Commands (20 total)
        Commands commands = new Commands();
        commands.register(new ExitCommand());
        commands.register(new ClearCommand());
        commands.register(new CostCommand());
        commands.register(new CompactCommand());
        commands.register(new ModelCommand());
        commands.register(new ToolsCommand());
        commands.register(new OutputStyleCommand());
        commands.register(new ThinkingCommand());
        commands.register(new PermissionsCommand());
        commands.register(new UndoCommand());
        commands.register(new SessionCommand());
        commands.register(new ResumeCommand());
        commands.register(new TasksCommand());
        commands.register(new PlanCommand(planStore));
        commands.register(new DiffCommand());
        commands.register(new ContextCommand());
        commands.register(new DoctorCommand());
        commands.register(new MemoryCommand());
        commands.register(new HooksCommand(hooks));
        // Marketplace
        var marketplaceManager = new cz.krokviak.agents.cli.plugin.marketplace.MarketplaceManager();
        commands.register(new MarketplaceCommand(marketplaceManager));
        commands.register(new PluginCommand(marketplaceManager));
        commands.register(new PluginsCommand());
        commands.register(new HelpCommand(commands));

        // Reconcile marketplaces + load plugins (local + marketplace-installed)
        marketplaceManager.reconcile();
        var pluginCtx = new PluginContextImpl(commands, hooks, skillRegistry, ctx);
        Plugins.loadAll(pluginCtx, cwd);
        // Also load marketplace-installed plugins
        for (var pluginPath : marketplaceManager.enabledPluginPaths()) {
            try {
                var manifest = pluginPath.resolve("plugin.json");
                if (!java.nio.file.Files.isRegularFile(manifest)) continue;
                var plugin = cz.krokviak.agents.cli.plugin.PluginLoader.loadPlugin(pluginPath, manifest);
                plugin.commands().forEach(pluginCtx::addCommand);
                plugin.skills().forEach(pluginCtx::addSkill);
                plugin.hooks().forEach(pluginCtx::addHook);
                output.println("  Loaded marketplace plugin: " + plugin.name());
            } catch (Exception e) {
                log.warn( "Failed to load marketplace plugin from " + pluginPath, e);
            }
        }

        // Populate command trie for autocomplete
        if (ctrl != null) {
            for (var cmd : commands.all()) {
                ctrl.commandTrie().insert(cmd.name(), cmd.description());
            }
        }

        // Banner
        output.println("");
        output.println("\033[1mKrok AI\033[0m — model: " + config.model());
        output.println("Working directory: " + cwd);
        output.println("\033[2mSession: " + sessionId + "\033[0m");
        if (permMode != PermissionManager.PermissionMode.TRUST) {
            output.println("Permission mode: " + config.permissionMode());
        }
        if (!projectInstructions.isBlank()) {
            output.println("\033[2mLoaded project instructions from AGENTS.md\033[0m");
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
        agentService.setRunner(runner);

        // SESSION_START hook fires once the agent + registry + hooks are fully wired.
        hooks.dispatchTyped(cz.krokviak.agents.api.hook.HookPhase.SESSION_START,
            new cz.krokviak.agents.api.hook.events.SessionEvent(sessionId, ctx.history().size()));

        // SESSION_END on JVM shutdown — flushes plugin state, drains background schedulers.
        final String finalSessionId = sessionId;
        final var finalCtx = ctx;
        final var finalHooks = hooks;
        final var finalSpawner = spawner;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                finalHooks.dispatchTyped(cz.krokviak.agents.api.hook.HookPhase.SESSION_END,
                    new cz.krokviak.agents.api.hook.events.SessionEvent(finalSessionId, finalCtx.history().size()));
            } catch (Exception ignored) {}
            try { finalSpawner.shutdown(); } catch (Exception ignored) {}
        }, "session-end-hook"));

        new Repl(ctx, commands, cliApp).start();
    }
}
