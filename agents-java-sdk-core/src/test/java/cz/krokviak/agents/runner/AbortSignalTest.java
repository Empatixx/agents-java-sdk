package cz.krokviak.agents.runner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AbortSignalTest {

    @Test
    void freshSignalIsNotAborted() {
        var s = new AbortSignal();
        assertFalse(s.isAborted());
        assertDoesNotThrow(s::throwIfAborted);
    }

    @Test
    void abortSetsFlagAndThrowIfAbortedFails() {
        var s = new AbortSignal();
        s.abort();
        assertTrue(s.isAborted());
        assertThrows(AbortException.class, s::throwIfAborted);
    }

    @Test
    void abortIsIdempotent() {
        var s = new AbortSignal();
        s.abort();
        s.abort();
        assertTrue(s.isAborted());
    }

    @Test
    void resetClearsFlag() {
        var s = new AbortSignal();
        s.abort();
        s.reset();
        assertFalse(s.isAborted());
        assertDoesNotThrow(s::throwIfAborted);
    }

    @Test
    void abortExceptionHasSensibleMessage() {
        assertEquals("aborted", new AbortException().getMessage());
        assertEquals("custom", new AbortException("custom").getMessage());
    }
}
