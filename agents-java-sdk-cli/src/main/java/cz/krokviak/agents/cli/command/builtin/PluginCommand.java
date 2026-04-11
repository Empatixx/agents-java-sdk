package cz.krokviak.agents.cli.command.builtin;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;
import cz.krokviak.agents.cli.render.PromptRenderer;
import cz.krokviak.agents.cli.plugin.marketplace.MarketplaceManager;

public class PluginCommand implements Command {
    private final MarketplaceManager marketplace;

    public PluginCommand(MarketplaceManager marketplace) {
        this.marketplace = marketplace;
    }

    @Override public String name() { return "plugin"; }
    @Override public String description() { return "Manage plugins"; }

    @Override
    public void execute(String args, CliContext ctx) {
        // Inline: /plugin install github:owner/repo
        if (args != null && !args.isBlank()) {
            handleInline(args.trim(), ctx);
            return;
        }

        PromptRenderer renderer = ctx.promptRenderer();
        if (renderer == null) { ctx.output().printError("Requires interactive terminal."); return; }

        int action = renderer.promptSelection("Plugins", new String[]{
            "Back",
            "Install from Git",
            "Install from marketplace",
            "Manage installed",
            "Update all"
        });

        switch (action) {
            case 1 -> installFromGit(ctx, renderer);
            case 2 -> installFromMarketplace(ctx, renderer);
            case 3 -> manageInstalled(ctx, renderer);
            case 4 -> updateAll(ctx);
            default -> {}
        }
    }

    private void installFromGit(CliContext ctx, PromptRenderer renderer) {
        String source = renderer.promptTextInput("Install from Git", "github:owner/repo");
        if (source.isEmpty()) return;

        ctx.output().startSpinner("Installing...");
        try {
            var result = marketplace.installFromGit(source);
            ctx.output().stopSpinner();
            ctx.output().println("Installed: " + result.id() + " v" + result.version());
            ctx.output().println("Restart to load.");
        } catch (Exception e) {
            ctx.output().stopSpinner();
            ctx.output().printError("Install failed: " + e.getMessage());
        }
    }

    private void installFromMarketplace(CliContext ctx, PromptRenderer renderer) {
        var available = marketplace.browsePlugins();
        if (available.isEmpty()) {
            ctx.output().println("No plugins available. Use /marketplace to add a source first.");
            return;
        }

        String[] options = new String[available.size() + 1];
        options[0] = "Back";
        for (int i = 0; i < available.size(); i++) {
            var p = available.get(i);
            String desc = p.description() != null ? p.description() : "";
            options[i + 1] = p.id() + "\n" + desc;
        }

        int selected = renderer.promptSelection("Install plugin", options);
        if (selected <= 0 || selected > available.size()) return;

        var plugin = available.get(selected - 1);
        int confirm = renderer.promptSelection("Install " + plugin.id() + "?", new String[]{"No", "Yes"});
        if (confirm != 1) return;

        ctx.output().startSpinner("Installing " + plugin.id() + "...");
        try {
            var result = marketplace.installPlugin(plugin.id());
            ctx.output().stopSpinner();
            ctx.output().println("Installed: " + result.id() + " v" + result.version());
            ctx.output().println("Restart to load.");
        } catch (Exception e) {
            ctx.output().stopSpinner();
            ctx.output().printError(e.getMessage());
        }
    }

    private void manageInstalled(CliContext ctx, PromptRenderer renderer) {
        var installed = marketplace.listInstalled();
        if (installed.isEmpty()) {
            ctx.output().println("No plugins installed.");
            return;
        }

        String[] options = new String[installed.size() + 1];
        options[0] = "Back";
        for (int i = 0; i < installed.size(); i++) {
            var p = installed.get(i);
            String status = p.enabled() ? "\u2713" : "\u2717";
            options[i + 1] = status + " " + p.id() + " v" + p.version();
        }

        int selected = renderer.promptSelection("Installed plugins", options);
        if (selected <= 0 || selected > installed.size()) return;

        var plugin = installed.get(selected - 1);
        String toggleLabel = plugin.enabled() ? "Disable" : "Enable";

        int action = renderer.promptSelection(plugin.id(), new String[]{
            "Back", toggleLabel, "Update", "Remove"
        });

        switch (action) {
            case 1 -> {
                try {
                    marketplace.setEnabled(plugin.id(), !plugin.enabled());
                    ctx.output().println(plugin.id() + " " + (!plugin.enabled() ? "enabled" : "disabled") + ". Restart to apply.");
                } catch (Exception e) { ctx.output().printError(e.getMessage()); }
            }
            case 2 -> {
                if ("git".equals(plugin.marketplace()) && plugin.installPath() != null) {
                    ctx.output().startSpinner("Updating...");
                    try {
                        cz.krokviak.agents.cli.plugin.marketplace.GitOps.pull(
                            java.nio.file.Path.of(plugin.installPath()));
                        ctx.output().stopSpinner();
                        ctx.output().println("Updated. Restart to apply.");
                    } catch (Exception e) {
                        ctx.output().stopSpinner();
                        ctx.output().printError(e.getMessage());
                    }
                } else {
                    ctx.output().println("Only git-installed plugins can be updated.");
                }
            }
            case 3 -> {
                int confirm = renderer.promptSelection("Remove " + plugin.id() + "?", new String[]{"No", "Yes"});
                if (confirm == 1) {
                    try {
                        marketplace.removePlugin(plugin.id());
                        ctx.output().println("Removed.");
                    } catch (Exception e) { ctx.output().printError(e.getMessage()); }
                }
            }
        }
    }

    private void updateAll(CliContext ctx) {
        ctx.output().startSpinner("Updating...");
        try {
            marketplace.reconcile();
            int count = 0;
            for (var p : marketplace.listInstalled()) {
                if ("git".equals(p.marketplace()) && p.installPath() != null) {
                    try {
                        cz.krokviak.agents.cli.plugin.marketplace.GitOps.pull(
                            java.nio.file.Path.of(p.installPath()));
                        count++;
                    } catch (Exception ignored) {}
                }
            }
            ctx.output().stopSpinner();
            ctx.output().println("Updated " + count + " plugin(s). Restart to apply.");
        } catch (Exception e) {
            ctx.output().stopSpinner();
            ctx.output().printError(e.getMessage());
        }
    }

    private void handleInline(String args, CliContext ctx) {
        String[] parts = args.split("\\s+", 2);
        String sub = parts[0];
        String rest = parts.length > 1 ? parts[1].trim() : null;

        if ("install".equals(sub) || "i".equals(sub)) {
            if (rest == null) { ctx.output().printError("Usage: /plugin install github:owner/repo"); return; }
            ctx.output().startSpinner("Installing...");
            try {
                MarketplaceManager.InstalledPlugin result;
                if (rest.contains("/") || rest.startsWith("git:") || rest.startsWith("github:")) {
                    result = marketplace.installFromGit(rest);
                } else {
                    result = marketplace.installPlugin(rest);
                }
                ctx.output().stopSpinner();
                ctx.output().println("Installed: " + result.id() + " v" + result.version() + ". Restart to load.");
            } catch (Exception e) {
                ctx.output().stopSpinner();
                ctx.output().printError(e.getMessage());
            }
        } else {
            ctx.output().printError("Unknown: " + sub + ". Type /plugin for menu or /plugin install github:owner/repo");
        }
    }
}
