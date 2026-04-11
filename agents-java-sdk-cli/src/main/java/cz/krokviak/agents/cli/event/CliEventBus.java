package cz.krokviak.agents.cli.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Simple synchronous event bus for CLI lifecycle events.
 * Listeners are called in registration order on the emitting thread.
 */
public final class CliEventBus {
    private static final Logger log = LoggerFactory.getLogger(CliEventBus.class);

    private final List<Consumer<CliEvent>> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(Consumer<CliEvent> listener) {
        listeners.add(listener);
    }

    /**
     * Subscribe only to events of a specific type.
     */
    @SuppressWarnings("unchecked")
    public <T extends CliEvent> void on(Class<T> eventType, Consumer<T> handler) {
        listeners.add(event -> {
            if (eventType.isInstance(event)) handler.accept((T) event);
        });
    }

    public void emit(CliEvent event) {
        for (var listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                log.warn("Event listener failed for {}: {}", event.getClass().getSimpleName(), e.getMessage());
            }
        }
    }
}
