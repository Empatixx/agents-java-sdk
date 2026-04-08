package cz.krokviak.agents.cli.render;

public record Theme(
        String primary,
        String secondary,
        String success,
        String warning,
        String error,
        String muted,
        String accent,
        String reset,
        String bold,
        String dim
) {

    public static Theme dark() {
        return new Theme(
                "\033[36m",   // primary  — cyan
                "\033[35m",   // secondary — magenta
                "\033[32m",   // success  — green
                "\033[33m",   // warning  — yellow
                "\033[31m",   // error    — red
                "\033[2m",    // muted    — dim
                "\033[96m",   // accent   — bright cyan
                "\033[0m",    // reset
                "\033[1m",    // bold
                "\033[2m"     // dim
        );
    }

    public static Theme light() {
        return new Theme(
                "\033[34m",   // primary  — blue (readable on light bg)
                "\033[35m",   // secondary — magenta
                "\033[32m",   // success  — green
                "\033[33m",   // warning  — yellow (dark yellow)
                "\033[31m",   // error    — red
                "\033[2m",    // muted    — dim
                "\033[94m",   // accent   — bright blue
                "\033[0m",    // reset
                "\033[1m",    // bold
                "\033[2m"     // dim
        );
    }
}
