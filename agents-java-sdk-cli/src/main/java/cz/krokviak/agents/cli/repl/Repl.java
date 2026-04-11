package cz.krokviak.agents.cli.repl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;
import cz.krokviak.agents.cli.command.Commands;
import cz.krokviak.agents.cli.engine.AgentRunner;
import cz.krokviak.agents.cli.render.tui.CliApp;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Repl {
    private static final Logger log = LoggerFactory.getLogger(Repl.class);
    private final CliContext ctx;
    private final Commands commands;
    private final AgentRunner runner;
    private final CliApp cliApp; // null when piped/non-TTY
    private volatile boolean runnerActive;

    public Repl(CliContext ctx, Commands commands, AgentRunner runner, CliApp cliApp) {
        this.ctx = ctx;
        this.commands = commands;
        this.runner = runner;
        this.cliApp = cliApp;
    }

    public void start() {
        loadSessionHistory();
        startNotificationWatcher();

        if (cliApp != null) {
            Thread.ofVirtual().name("cli-app").start(() -> {
                try { cliApp.run(); } catch (Exception e) {
                    log.warn( "CliApp error", e);
                }
            });
            try { cliApp.awaitReady(); } catch (InterruptedException ignored) {}
            startLoop(cliApp::readLine);
        } else {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                startLoop(reader::readLine);
            } catch (Exception e) {
                ctx.output().printError("Fatal: " + e.getMessage());
            }
        }
    }

    private void startNotificationWatcher() {
        if (ctx.taskManager() == null) return;
        Thread.ofVirtual().name("notification-watcher").start(() -> {
            while (true) {
                try {
                    Thread.sleep(500);
                    if (runnerActive) continue; // runner handles its own notifications
                    var notifications = ctx.taskManager().drainNotifications();
                    for (var n : notifications) {
                        String icon = switch (n.status()) {
                            case COMPLETED -> "\u2713";
                            case FAILED -> "\u2717";
                            case KILLED -> "\u2298";
                            default -> "\u25cf";
                        };
                        ctx.output().println("");
                        ctx.output().println("  " + icon + " Agent " + n.description() + " " +
                            n.status().name().toLowerCase());
                        if (n.summary() != null && !n.summary().isBlank()) {
                            String summary = n.summary().length() > 150
                                ? n.summary().substring(0, 150) + "..." : n.summary();
                            ctx.output().println("    " + summary);
                        }
                    }
                } catch (InterruptedException e) {
                    break;
                } catch (Exception ignored) {}
            }
        });
    }

    @FunctionalInterface
    private interface InputReader {
        String readLine() throws Exception;
    }

    private void startLoop(InputReader reader) {
        while (true) {
            printPrompt();
            try {
                String line = reader.readLine();
                if (line == null) break;
                String input = line.trim();
                if (input.isEmpty()) continue;
                if (input.startsWith("/")) {
                    dispatchCommand(input);
                } else {
                    runPrompt(input);
                    // Process any queued prompts that arrived while runner was active
                    while (true) {
                        String queued = consumeQueuedPrompt();
                        if (queued == null) break;
                        runPrompt(queued);
                    }
                }
            } catch (Exception e) {
                ctx.output().printError("Fatal: " + e.getMessage());
                break;
            }
        }
    }

    private void printPrompt() {
        if (ctx.costTracker().totalInputTokens() > 0) {
            ctx.output().printPromptWithCost(ctx.costTracker().format());
        } else {
            ctx.output().printPrompt();
        }
    }

    private void dispatchCommand(String input) {
        String[] parts = input.substring(1).split("\\s+", 2);
        String name = parts[0];
        String args = parts.length > 1 ? parts[1] : null;
        Command cmd = commands.find(name);
        if (cmd == null) { ctx.output().printError("Unknown command: /" + name + " (type /help)"); return; }
        cmd.execute(args, ctx);
    }

    private void runPrompt(String input) {
        try {
            runnerActive = true;
            if (cliApp != null) cliApp.setRunnerBusy(true);
            runner.run(input);
        } catch (Exception e) {
            ctx.output().printError(e.getMessage());
        } finally {
            runnerActive = false;
            if (cliApp != null) cliApp.setRunnerBusy(false);
        }
    }

    private String consumeQueuedPrompt() {
        if (cliApp == null) return null;
        // Also drain inputQueue in case something slipped through
        String fromQueue = cliApp.drainQueued();
        // Get from controller's queued prompt (set by TUI during runner activity)
        String fromCtrl = null;
        if (ctx.promptRenderer() != null) {
            fromCtrl = ctx.promptRenderer().consumeQueuedPrompt();
        }
        // Concatenate both sources
        if (fromQueue != null && fromCtrl != null) return fromCtrl + "\n" + fromQueue;
        if (fromCtrl != null) return fromCtrl;
        return fromQueue;
    }

    private void loadSessionHistory() {
        if (ctx.session() != null && ctx.sessionId() != null) {
            var loaded = ctx.session().getHistory(ctx.sessionId());
            if (!loaded.isEmpty()) {
                ctx.history().addAll(loaded);
                ctx.output().println("\033[2mLoaded " + loaded.size() + " messages from session: " + ctx.sessionId() + "\033[0m");
            }
        }
    }
}
