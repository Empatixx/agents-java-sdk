package cz.krokviak.agents.examples.streaming;

import cz.krokviak.agents.def.Agent;
import cz.krokviak.agents.adapter.openai.OpenAIResponsesModel;
import cz.krokviak.agents.runner.Runner;
import cz.krokviak.agents.streaming.StreamEvent;

public class StreamingExample {

    public static void main(String[] args) {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null) {
            System.err.println("Set OPENAI_API_KEY environment variable");
            return;
        }

        var runner = Runner.of(new OpenAIResponsesModel(apiKey));

        Agent<Void> agent = Agent.<Void>builder("Storyteller")
            .instructions("You are a creative storyteller. Tell engaging short stories.")
            .build();

        System.out.println("Streaming response:");
        try (var stream = runner.runStreamed(agent, "Tell me a very short story about a robot")) {
            for (StreamEvent<Void> event : stream) {
                switch (event) {
                    case StreamEvent.TextDeltaEvent<Void> e -> System.out.print(e.delta());
                    case StreamEvent.ToolCallEvent<Void> e -> System.out.print("\n[Calling: " + e.toolName() + "]");
                    case StreamEvent.AgentUpdatedEvent<Void> e -> System.out.print("\n[Agent: " + e.agent().name() + "]");
                    default -> {}
                }
            }
            System.out.println("\n\nTokens: " + stream.result().usage().totalTokens());
        }
    }
}
