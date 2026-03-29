package cz.krokviak.agents.streaming;

import cz.krokviak.agents.runner.RunResult;

import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class EventStream<T> implements Iterable<StreamEvent<T>>, AutoCloseable {
    private final BlockingQueue<StreamEvent<T>> queue;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicReference<RunResult<T>> resultRef = new AtomicReference<>();

    public EventStream(BlockingQueue<StreamEvent<T>> queue) {
        this.queue = queue;
    }

    @Override
    public Iterator<StreamEvent<T>> iterator() {
        return new Iterator<>() {
            private StreamEvent<T> next = null;
            private boolean done = false;

            @Override
            public boolean hasNext() {
                if (done || cancelled.get()) return false;
                if (next != null) return true;
                try {
                    next = queue.take();
                    if (next instanceof StreamEvent.CompletedEvent<T> completed) {
                        resultRef.set(completed.result());
                        done = true;
                        return false;
                    }
                    return true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    done = true;
                    return false;
                }
            }

            @Override
            public StreamEvent<T> next() {
                if (!hasNext()) throw new java.util.NoSuchElementException();
                var event = next;
                next = null;
                return event;
            }
        };
    }

    @Override
    public void forEach(Consumer<? super StreamEvent<T>> handler) {
        for (StreamEvent<T> event : this) {
            handler.accept(event);
        }
    }

    public RunResult<T> result() {
        // Drain queue until complete
        while (resultRef.get() == null && !cancelled.get()) {
            try {
                StreamEvent<T> event = queue.take();
                if (event instanceof StreamEvent.CompletedEvent<T> completed) {
                    resultRef.set(completed.result());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return resultRef.get();
    }

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isComplete() {
        return resultRef.get() != null;
    }

    @Override
    public void close() {
        cancel();
    }
}
