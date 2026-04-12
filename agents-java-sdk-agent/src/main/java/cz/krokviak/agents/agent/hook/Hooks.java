package cz.krokviak.agents.agent.hook;

import cz.krokviak.agents.api.hook.Hook;
import cz.krokviak.agents.api.hook.HookPhase;
import cz.krokviak.agents.api.hook.HookResult;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of {@link Hook}s indexed by {@link HookPhase}. Each phase carries
 * its own payload type (see {@code cz.krokviak.agents.api.hook.events.*});
 * registration is loose (hooks store their own phase), dispatch is typed.
 *
 * <p>Backwards-compatible with the existing PRE_TOOL/POST_TOOL hooks that
 * take {@link ToolUseEvent}: the overload {@link #dispatch(HookPhase, ToolUseEvent)}
 * is preserved so call-sites in the engine compile unchanged.
 */
public final class Hooks {
    private final Map<HookPhase, List<Hook<?>>> hooks = new EnumMap<>(HookPhase.class);

    /** Register a {@link cz.krokviak.agents.agent.hook.Hook} for PRE_TOOL/POST_TOOL. */
    public void register(cz.krokviak.agents.agent.hook.Hook hook) {
        hooks.computeIfAbsent(hook.phase(), _ -> new ArrayList<>()).add(hook);
    }

    /** Register a generic typed hook against a non-tool phase. */
    public <E> void register(HookPhase phase, Hook<E> hook) {
        hooks.computeIfAbsent(phase, _ -> new ArrayList<>()).add(hook);
    }

    /** Legacy tool-use dispatch — kept so existing engine code compiles. */
    public HookResult dispatch(HookPhase phase, ToolUseEvent event) {
        return dispatchTyped(phase, event);
    }

    /** Generic typed dispatch. Stops on first {@link HookResult.Block}. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <E> HookResult dispatchTyped(HookPhase phase, E event) {
        List<Hook<?>> phaseHooks = hooks.getOrDefault(phase, List.of());
        for (Hook<?> h : phaseHooks) {
            HookResult result = ((Hook) h).execute(event);
            if (result instanceof HookResult.Block) return result;
        }
        return new HookResult.Proceed();
    }

    public List<String> describe() {
        List<String> lines = new ArrayList<>();
        for (var entry : hooks.entrySet()) {
            for (Hook<?> hook : entry.getValue()) {
                lines.add(entry.getKey() + ": " + hook.getClass().getSimpleName());
            }
        }
        if (lines.isEmpty()) lines.add("(none)");
        return lines;
    }
}
