package cz.krokviak.agents.cli.hook;

import cz.krokviak.agents.api.hook.HookPhase;
import cz.krokviak.agents.api.hook.HookResult;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class Hooks {
    private final Map<HookPhase, List<Hook>> hooks = new EnumMap<>(HookPhase.class);

    public void register(Hook hook) {
        hooks.computeIfAbsent(hook.phase(), _ -> new ArrayList<>()).add(hook);
    }

    public HookResult dispatch(HookPhase phase, ToolUseEvent event) {
        List<Hook> phaseHooks = hooks.getOrDefault(phase, List.of());
        for (Hook hook : phaseHooks) {
            HookResult result = hook.execute(event);
            if (result instanceof HookResult.Block) {
                return result;
            }
        }
        return new HookResult.Proceed();
    }

    public List<String> describe() {
        List<String> lines = new ArrayList<>();
        for (var entry : hooks.entrySet()) {
            for (Hook hook : entry.getValue()) {
                lines.add(entry.getKey() + ": " + hook.getClass().getSimpleName());
            }
        }
        if (lines.isEmpty()) lines.add("(none)");
        return lines;
    }
}
