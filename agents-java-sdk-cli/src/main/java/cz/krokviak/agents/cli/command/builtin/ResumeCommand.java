package cz.krokviak.agents.cli.command.builtin;

import cz.krokviak.agents.api.dto.SessionInfo;
import cz.krokviak.agents.cli.CliContext;
import cz.krokviak.agents.cli.command.Command;
import cz.krokviak.agents.cli.render.PromptRenderer;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ResumeCommand implements Command {
    @Override public String name() { return "resume"; }
    @Override public List<String> aliases() { return List.of("r"); }
    @Override public String description() { return "List and resume a previous session"; }

    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("d MMM HH:mm").withZone(ZoneId.systemDefault());

    @Override
    public void execute(String args, CliContext ctx) {
        String currentId = ctx.agent().currentSessionId();
        List<SessionInfo> sessions = ctx.agent().listSessions().stream()
            .filter(s -> !s.sessionId().equals(currentId))
            .toList();

        if (sessions.isEmpty()) {
            ctx.output().println("No other sessions found.");
            return;
        }

        if (args != null && !args.isBlank()) {
            SessionInfo selected = matchSession(args.trim(), sessions);
            if (selected == null) {
                ctx.output().printError("No session matching '" + args.trim() + "'.");
                return;
            }
            resumeSession(ctx, selected);
            return;
        }

        PromptRenderer renderer = ctx.promptRenderer();
        int limit = Math.min(sessions.size(), 8);
        List<SessionInfo> visible = sessions.subList(0, limit);

        String[] options = new String[visible.size() + 1];
        options[0] = "Back";
        for (int i = 0; i < visible.size(); i++) {
            options[i + 1] = formatSessionLine(visible.get(i));
        }

        if (renderer != null) {
            int selected = renderer.promptSelection("Resume session", options);
            if (selected <= 0 || selected > visible.size()) {
                ctx.output().println("Cancelled.");
                return;
            }
            resumeSession(ctx, visible.get(selected - 1));
        } else {
            ctx.output().println("Recent sessions:");
            for (int i = 0; i < visible.size(); i++) {
                ctx.output().println("  " + (i + 1) + ". " + formatSessionLine(visible.get(i)));
            }
            ctx.output().println("Use /resume <number> to resume");
        }
    }

    private String formatSessionLine(SessionInfo s) {
        String title = s.title() != null ? truncate(s.title(), 50) : "(untitled)";
        String date = s.lastActivityAt() != null ? DATE_FMT.format(s.lastActivityAt()) : "?";
        String ago = formatTimeAgo(s.lastActivityAt());
        return String.format("%s  %s (%s)  %d msgs", title, date, ago, s.messageCount());
    }

    private void resumeSession(CliContext ctx, SessionInfo meta) {
        ctx.agent().loadSession(meta.sessionId()).join();
        var loaded = ctx.agent().history().items();

        String title = meta.title() != null ? truncate(meta.title(), 60) : "(untitled)";
        ctx.output().println("");
        ctx.output().println("Resumed: " + title);
        ctx.output().println("  " + loaded.size() + " messages loaded | " + formatTimeAgo(meta.lastActivityAt()));

        for (int i = loaded.size() - 1; i >= 0; i--) {
            if (loaded.get(i) instanceof cz.krokviak.agents.runner.InputItem.UserMessage msg) {
                String content = msg.content();
                if (content.length() > 80) content = content.substring(0, 80) + "...";
                ctx.output().println("  Last: \"" + content + "\"");
                break;
            }
        }
        ctx.output().println("");
    }

    private SessionInfo matchSession(String arg, List<SessionInfo> sessions) {
        try {
            int idx = Integer.parseInt(arg) - 1;
            if (idx >= 0 && idx < sessions.size()) return sessions.get(idx);
        } catch (NumberFormatException ignored) {}
        for (var s : sessions) {
            if (s.sessionId().startsWith(arg)) return s;
        }
        return null;
    }

    private static String formatTimeAgo(Instant when) {
        if (when == null) return "?";
        Duration d = Duration.between(when, Instant.now());
        if (d.toMinutes() < 1) return "just now";
        if (d.toMinutes() < 60) return d.toMinutes() + "m ago";
        if (d.toHours() < 24) return d.toHours() + "h ago";
        return d.toDays() + "d ago";
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
