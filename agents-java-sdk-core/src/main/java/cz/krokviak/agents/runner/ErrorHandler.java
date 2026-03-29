package cz.krokviak.agents.runner;

import cz.krokviak.agents.context.RunContext;

@FunctionalInterface
public interface ErrorHandler<T> {
    Object handle(RunContext<T> ctx, Exception error);
}
