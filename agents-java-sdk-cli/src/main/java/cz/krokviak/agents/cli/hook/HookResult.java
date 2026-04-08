package cz.krokviak.agents.cli.hook;

public sealed interface HookResult permits HookResult.Proceed, HookResult.Block {
    record Proceed() implements HookResult {}
    record Block(String reason) implements HookResult {}
}
