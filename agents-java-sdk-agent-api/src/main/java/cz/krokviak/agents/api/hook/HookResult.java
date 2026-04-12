package cz.krokviak.agents.api.hook;

public sealed interface HookResult permits HookResult.Proceed, HookResult.Block {
    record Proceed() implements HookResult {}
    record Block(String reason) implements HookResult {}
}
