package cz.krokviak.agents.session;

import cz.krokviak.agents.runner.InputItem;
import cz.krokviak.agents.runner.RunItem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class BatchedSessionWriterTest {

    private static class FakeSession implements Session {
        final Map<String, List<RunItem>> writes = new ConcurrentHashMap<>();
        final AtomicInteger saveCalls = new AtomicInteger();
        @Override public List<InputItem> getHistory(String id) { return List.of(); }
        @Override public void save(String id, List<RunItem> items) {
            saveCalls.incrementAndGet();
            writes.computeIfAbsent(id, _ -> Collections.synchronizedList(new ArrayList<>())).addAll(items);
        }
    }

    @Test
    void writesAreBufferedAndEventuallyFlushed() {
        var fake = new FakeSession();
        try (var w = new BatchedSessionWriter(fake, /*intervalMs*/ 50, /*threshold*/ 10)) {
            w.save("s1", List.of(new RunItem.UserInput("hi")));
            w.save("s1", List.of(new RunItem.UserInput("there")));
            // Force flush explicitly instead of waiting the interval.
            w.flush();
            assertEquals(2, fake.writes.get("s1").size());
        }
    }

    @Test
    void shutdownFlushesPending() {
        var fake = new FakeSession();
        var w = new BatchedSessionWriter(fake, 10_000, 1000); // huge interval so worker won't auto-flush
        w.save("s1", List.of(new RunItem.UserInput("one")));
        w.save("s1", List.of(new RunItem.UserInput("two")));
        w.shutdown();
        assertEquals(2, fake.writes.get("s1").size());
    }

    @Test
    void emptyOrNullSaveIsNoop() {
        var fake = new FakeSession();
        try (var w = new BatchedSessionWriter(fake)) {
            w.save("s1", List.of());
            w.save("s1", null);
            w.flush();
        }
        assertEquals(0, fake.saveCalls.get());
    }

    @Test
    void readsBypassQueue() {
        var fake = new FakeSession() {
            @Override public List<InputItem> getHistory(String id) {
                return List.of(new InputItem.UserMessage("from-db"));
            }
        };
        try (var w = new BatchedSessionWriter(fake)) {
            var h = w.getHistory("s1");
            assertEquals(1, h.size());
        }
    }

    @Test
    void thresholdFlushIsNonBlockingOnQueueSide() {
        var fake = new FakeSession();
        try (var w = new BatchedSessionWriter(fake, 5_000, 5)) {
            assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), () -> {
                for (int i = 0; i < 100; i++) {
                    w.save("s1", List.of(new RunItem.UserInput("msg-" + i)));
                }
            });
            w.flush();
            assertEquals(100, fake.writes.get("s1").size());
        }
    }

    @Test
    void savesAfterShutdownStillPersistSynchronously() {
        var fake = new FakeSession();
        var w = new BatchedSessionWriter(fake);
        w.shutdown();
        w.save("s1", List.of(new RunItem.UserInput("late")));
        assertEquals(1, fake.writes.get("s1").size());
    }
}
