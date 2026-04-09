package cz.krokviak.agents.cli.repl;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;
import cz.krokviak.agents.cli.command.Commands;
import cz.krokviak.agents.cli.engine.AgentRunner;
import cz.krokviak.agents.cli.render.tui.CliApp;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Repl {
    private final CliContext ctx;
    private final Commands commands;
    private final AgentRunner runner;
    private final CliApp cliApp; // null when piped/non-TTY

    public Repl(CliContext ctx, Commands commands, AgentRunner runner, CliApp cliApp) {
        this.ctx = ctx;
        this.commands = commands;
        this.runner = runner;
        this.cliApp = cliApp;
    }

    public void start() {
        loadSessionHistory();

        if (cliApp != null) {
            Thread.ofVirtual().name("cli-app").start(() -> {
                try { cliApp.run(); } catch (Exception e) {
                    System.err.println("CliApp error: " + e.getMessage());
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
                    try { runner.run(input); }
                    catch (Exception e) { ctx.output().printError(e.getMessage()); }
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
