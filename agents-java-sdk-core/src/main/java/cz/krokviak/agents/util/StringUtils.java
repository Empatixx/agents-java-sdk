package cz.krokviak.agents.util;

/**
 * Small string helpers used across the SDK. Keeps formatting behaviour
 * consistent so call sites don't drift ("200 + ..." vs "200 no suffix").
 */
public final class StringUtils {

    private StringUtils() {}

    /**
     * Return {@code s} unchanged if it's no longer than {@code maxLen},
     * otherwise cut to {@code maxLen} and append {@code suffix}.
     *
     * @param s       input, may be {@code null}
     * @param maxLen  upper bound on returned length (exclusive of suffix)
     * @param suffix  appended when the string is cut; may be empty
     * @return truncated string, or {@code s} unchanged, or {@code null} if {@code s == null}
     */
    public static String truncate(String s, int maxLen, String suffix) {
        if (s == null) return null;
        if (maxLen < 0) throw new IllegalArgumentException("maxLen must be >= 0");
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + (suffix == null ? "" : suffix);
    }

    /** {@link #truncate(String, int, String)} with the conventional {@code "..."} suffix. */
    public static String truncate(String s, int maxLen) {
        return truncate(s, maxLen, "...");
    }
}
