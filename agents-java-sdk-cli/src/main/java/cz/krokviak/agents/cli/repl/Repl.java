package cz.krokviak.agents.cli.repl;

import cz.krokviak.agents.api.event.AgentEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;
import cz.krokviak.agents.cli.command.Commands;
import cz.krokviak.agents.agent.engine.AgentRunner;
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
        // No-op: TaskManager now pushes AgentEvent.TaskNotification onto the event bus
        // as soon as they arrive; RenderEventListener handles rendering. Kept as a
        // method for bootstrap symmetry in case an alternative frontend wants to
        // attach lifecycle hooks here.
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
                    input = handlePasteAndImages(input);
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
        var costs = ctx.agent().costs();
        if (costs.inputTokens() > 0) {
            ctx.output().printPromptWithCost(costs.formatted());
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

    private final cz.krokviak.agents.cli.paste.PasteHandler pasteHandler = new cz.krokviak.agents.cli.paste.PasteHandler();
    private cz.krokviak.agents.cli.paste.ImageHandler imageHandler;
    private int imageCounter = 0;

    private cz.krokviak.agents.cli.paste.ImageHandler getImageHandler() {
        if (imageHandler == null) {
            String sid = ctx.agent().currentSessionId();
            imageHandler = new cz.krokviak.agents.cli.paste.ImageHandler(sid != null ? sid : "default");
        }
        return imageHandler;
    }

    private String handlePasteAndImages(String input) {
        var imgHandler = getImageHandler();

        // 1. Check if input is an image file path
        if (imgHandler.isImagePath(input)) {
            try {
                var img = imgHandler.processImagePath(input);
                ctx.agent().appendHistoryItem(img);
                imageCounter++;
                ctx.eventBus().emit(new cz.krokviak.agents.api.event.AgentEvent.ImageAttached(
                    img.filePath(), imageCounter));
                return "[Image #" + imageCounter + "] attached. " +
                    (img.description() != null ? img.description() : "Please analyze it.");
            } catch (Exception e) {
                log.warn("Failed to process image path", e);
            }
        }

        // 2. Check clipboard for image (trigger words)
        if (input.length() <= 5 && (input.equalsIgnoreCase("img") || input.equalsIgnoreCase("paste") || input.equalsIgnoreCase("image"))) {
            try {
                var clipImg = imgHandler.tryClipboardImage();
                if (clipImg != null) {
                    ctx.agent().appendHistoryItem(clipImg);
                    imageCounter++;
                    ctx.eventBus().emit(new cz.krokviak.agents.api.event.AgentEvent.ImageAttached(
                        clipImg.filePath(), imageCounter));
                    return "[Image #" + imageCounter + "] attached from clipboard. Please analyze it.";
                }
            } catch (Exception e) {
                log.debug("No clipboard image", e);
            }
        }

        // 3. Large paste detection
        if (pasteHandler.isPaste(input)) {
            var result = pasteHandler.savePaste(input);
            if (result != null) {
                ctx.agent().appendHistoryItem(new cz.krokviak.agents.runner.InputItem.SystemMessage(
                    "<pasted-content path=\"" + result.filePath().toAbsolutePath() + "\">\n"
                    + result.content() + "\n</pasted-content>"));
                ctx.output().println("  Pasted " + result.lineCount() + " lines saved");
                return result.reference();
            }
        }

        return input;
    }

    private void loadSessionHistory() {
        String sid = ctx.agent().currentSessionId();
        if (sid == null) return;
        try {
            ctx.agent().loadSession(sid).join();
            int n = ctx.agent().history().size();
            if (n > 0) {
                ctx.output().println("\033[2mLoaded " + n + " messages from session: " + sid + "\033[0m");
            }
        } catch (Exception ignored) {
            // session may not exist yet
        }
    }
}
