package cz.krokviak.agents.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StringUtilsTest {

    @Test
    void shortStringReturnedUnchanged() {
        assertEquals("hello", StringUtils.truncate("hello", 10));
        assertEquals("hello", StringUtils.truncate("hello", 5));
    }

    @Test
    void longStringTruncatedWithDefaultSuffix() {
        assertEquals("hello...", StringUtils.truncate("hello world", 5));
    }

    @Test
    void customSuffix() {
        assertEquals("hello[cut]", StringUtils.truncate("hello world", 5, "[cut]"));
    }

    @Test
    void emptySuffix() {
        assertEquals("hello", StringUtils.truncate("hello world", 5, ""));
    }

    @Test
    void nullInput() {
        assertNull(StringUtils.truncate(null, 10));
    }

    @Test
    void nullSuffixTreatedAsEmpty() {
        assertEquals("hello", StringUtils.truncate("hello world", 5, null));
    }

    @Test
    void negativeMaxLenThrows() {
        assertThrows(IllegalArgumentException.class, () -> StringUtils.truncate("x", -1));
    }
}
