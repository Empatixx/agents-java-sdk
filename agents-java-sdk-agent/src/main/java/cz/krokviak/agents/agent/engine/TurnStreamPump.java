package cz.krokviak.agents.agent.engine;

import cz.krokviak.agents.api.event.AgentEvent;
import cz.krokviak.agents.api.event.EventBus;
import cz.krokviak.agents.model.ModelResponseStream;

import java.util.function.Consumer;

/**
 * Pumps a {@link ModelResponseStream} into a {@link StreamCollector} while
 * emitting the shared text / thinking / done events on the bus. Tool-call
 * deltas are delegated to the caller via {@code toolCallHandler} — the main
 * agent runner feeds them into a {@link StreamingToolExecutor} for eager
 * parallel execution, a sub-agent collects them for serial dispatch.
 *
 * <p>Both {@code AgentRunner} and {@code AgentSpawner} used to carry this
 * loop verbatim. Consolidating it here means the text / thinking signalling
 * (including the thinking→text transition that emits {@code ThinkingDone})
 * has one implementation instead of two-that-must-stay-in-sync.
 */
public final class TurnStreamPump {

    /** Called once per {@link ModelResponseStream.Event.ToolCallDelta}. */
    public interface ToolCallDeltaHandler {
        void accept(String toolCallId, String name, String argumentsDelta);
    }

    /**
     * Iterate {@code stream} until it completes. Events are emitted on
     * {@code bus}, text + done are recorded on {@code collector}, tool
     * deltas are handed off to {@code toolDelta}. A "spinner stop on first
     * event" signal is forwarded via {@code onFirstEvent} (runs exactly once).
     *
     * @param stream        model stream to drain; closed by the caller
     * @param bus           event bus to emit {@code ResponseDelta}, {@code ThinkingDelta}, {@code ThinkingDone}
     * @param collector     where to accumulate text + Done payload
     * @param toolDelta     callback for tool-call deltas (may be no-op for sub-agents without eager exec)
     * @param onFirstEvent  hook fired exactly once on the first event seen (typical use: emit SpinnerStop)
     */
    public static void pump(
            ModelResponseStream stream,
            EventBus bus,
            StreamCollector collector,
            ToolCallDeltaHandler toolDelta,
            Runnable onFirstEvent) {
        boolean firstEvent = true;
        boolean thinkingActive = false;
        for (ModelResponseStream.Event event : stream) {
            if (firstEvent) {
                if (onFirstEvent != null) onFirstEvent.run();
                firstEvent = false;
            }
            switch (event) {
                case ModelResponseStream.Event.TextDelta td -> {
                    if (thinkingActive) {
                        bus.emit(new AgentEvent.ThinkingDone());
                        thinkingActive = false;
                    }
                    bus.emit(new AgentEvent.ResponseDelta(td.delta()));
                    collector.onTextDelta(td.delta());
                }
                case ModelResponseStream.Event.ThinkingDelta thd -> {
                    thinkingActive = true;
                    bus.emit(new AgentEvent.ThinkingDelta(thd.delta()));
                }
                case ModelResponseStream.Event.ToolCallDelta tcd -> {
                    collector.onToolCallDelta(tcd.toolCallId(), tcd.name(), tcd.argumentsDelta());
                    if (toolDelta != null) toolDelta.accept(tcd.toolCallId(), tcd.name(), tcd.argumentsDelta());
                }
                case ModelResponseStream.Event.Done done -> {
                    if (thinkingActive) {
                        bus.emit(new AgentEvent.ThinkingDone());
                        thinkingActive = false;
                    }
                    collector.onDone(done.fullResponse());
                }
            }
        }
    }

    /** Convenience: no extra tool-delta behaviour beyond collecting. */
    public static void pump(ModelResponseStream stream, EventBus bus, StreamCollector collector) {
        pump(stream, bus, collector, null, null);
    }

    /** Convenience: no-op first-event hook. */
    public static void pump(ModelResponseStream stream, EventBus bus, StreamCollector collector,
                            Consumer<ModelResponseStream.Event.ToolCallDelta> toolDelta) {
        pump(stream, bus, collector,
            (id, name, delta) -> toolDelta.accept(new ModelResponseStream.Event.ToolCallDelta(id, name, delta)),
            null);
    }

    private TurnStreamPump() {}
}
