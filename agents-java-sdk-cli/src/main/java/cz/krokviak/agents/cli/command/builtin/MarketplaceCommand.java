package cz.krokviak.agents.cli.command.builtin;

import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;
import cz.krokviak.agents.cli.render.PromptRenderer;
import cz.krokviak.agents.cli.plugin.marketplace.MarketplaceManager;

import java.util.ArrayList;
import java.util.List;

public class MarketplaceCommand implements Command {
    private final MarketplaceManager manager;

    public MarketplaceCommand(MarketplaceManager manager) {
        this.manager = manager;
    }

    @Override public String name() { return "marketplace"; }
    @Override public List<String> aliases() { return List.of("mkt"); }
    @Override public String description() { return "Manage plugin marketplaces"; }

    @Override
    public void execute(String args, CliContext ctx) {
        // Inline: /marketplace add github:owner/repo
        if (args != null && !args.isBlank()) {
            handleInline(args.trim(), ctx);
            return;
        }

        PromptRenderer renderer = ctx.promptRenderer();
        if (renderer == null) { ctx.output().printError("Requires interactive terminal."); return; }

        int action = renderer.promptSelection("Marketplace", new String[]{
            "Back",
            "Add marketplace",
            "Browse plugins",
            "Manage registered",
            "Update all"
        });

        switch (action) {
            case 1 -> addFlow(ctx, renderer);
            case 2 -> browseFlow(ctx, renderer);
            case 3 -> manageFlow(ctx, renderer);
            case 4 -> updateAll(ctx);
            default -> {}
        }
    }

    private void browseFlow(CliContext ctx, PromptRenderer renderer) {
        var available = manager.browsePlugins();
        if (available.isEmpty()) {
            ctx.output().println("No plugins available.");
            ctx.output().println("Add a marketplace: /marketplace add github:owner/repo");
            return;
        }

        String[] options = new String[available.size() + 1];
        options[0] = "Back";
        for (int i = 0; i < available.size(); i++) {
            var p = available.get(i);
            String desc = p.description() != null ? p.description() : "";
            options[i + 1] = p.id() + "\n" + desc;
        }

        int selected = renderer.promptSelection("Available plugins", options);
        if (selected <= 0 || selected > available.size()) return;

        var plugin = available.get(selected - 1);
        int confirm = renderer.promptSelection("Install " + plugin.id() + "?", new String[]{"No", "Yes"});
        if (confirm != 1) return;

        ctx.output().startSpinner("Installing...");
        try {
            var result = manager.installPlugin(plugin.id());
            ctx.output().stopSpinner();
            ctx.output().println("Installed: " + result.id() + " v" + result.version());
            ctx.output().println("Restart to load.");
        } catch (Exception e) {
            ctx.output().stopSpinner();
            ctx.output().printError(e.getMessage());
        }
    }

    private void manageFlow(CliContext ctx, PromptRenderer renderer) {
        var mkts = manager.listMarketplaces();
        if (mkts.isEmpty()) {
            ctx.output().println("No marketplaces registered.");
            ctx.output().println("Add one: /marketplace add github:owner/repo");
            return;
        }

        var names = new ArrayList<>(mkts.keySet());
        String[] options = new String[names.size() + 1];
        options[0] = "Back";
        for (int i = 0; i < names.size(); i++) {
            options[i + 1] = names.get(i);
        }

        int selected = renderer.promptSelection("Registered marketplaces", options);
        if (selected <= 0 || selected > names.size()) return;

        String name = names.get(selected - 1);
        int action = renderer.promptSelection(name, new String[]{"Back", "Update", "Remove"});

        switch (action) {
            case 1 -> {
                ctx.output().startSpinner("Updating...");
                try { manager.reconcile(); ctx.output().stopSpinner(); ctx.output().println("Updated."); }
                catch (Exception e) { ctx.output().stopSpinner(); ctx.output().printError(e.getMessage()); }
            }
            case 2 -> {
                int confirm = renderer.promptSelection("Remove " + name + "?", new String[]{"No", "Yes"});
                if (confirm == 1) {
                    try { manager.removeMarketplace(name); ctx.output().println("Removed."); }
                    catch (Exception e) { ctx.output().printError(e.getMessage()); }
                }
            }
        }
    }

    private void addFlow(CliContext ctx, PromptRenderer renderer) {
        String source = renderer.promptTextInput("Add marketplace", "github:owner/repo");
        if (source.isEmpty()) return;

        ctx.output().startSpinner("Adding marketplace...");
        try {
            String name = manager.addMarketplace(source);
            ctx.output().stopSpinner();
            ctx.output().println("Added: " + name);
        } catch (Exception e) {
            ctx.output().stopSpinner();
            ctx.output().printError(e.getMessage());
        }
    }

    private void updateAll(CliContext ctx) {
        ctx.output().startSpinner("Updating all marketplaces...");
        try {
            manager.reconcile();
            ctx.output().stopSpinner();
            ctx.output().println("All marketplaces updated.");
        } catch (Exception e) {
            ctx.output().stopSpinner();
            ctx.output().printError(e.getMessage());
        }
    }

    private void handleInline(String args, CliContext ctx) {
        String[] parts = args.split("\\s+", 2);
        String sub = parts[0];
        String rest = parts.length > 1 ? parts[1].trim() : null;

        switch (sub) {
            case "add" -> {
                if (rest == null) { ctx.output().printError("Usage: /marketplace add github:owner/repo"); return; }
                ctx.output().startSpinner("Adding...");
                try {
                    String name = manager.addMarketplace(rest);
                    ctx.output().stopSpinner();
                    ctx.output().println("Added: " + name);
                } catch (Exception e) {
                    ctx.output().stopSpinner();
                    ctx.output().printError(e.getMessage());
                }
            }
            case "remove", "rm" -> {
                if (rest == null) { ctx.output().printError("Usage: /marketplace remove <name>"); return; }
                try { manager.removeMarketplace(rest); ctx.output().println("Removed."); }
                catch (Exception e) { ctx.output().printError(e.getMessage()); }
            }
            default -> ctx.output().printError("Unknown: " + sub + ". Type /marketplace for menu.");
        }
    }
}
