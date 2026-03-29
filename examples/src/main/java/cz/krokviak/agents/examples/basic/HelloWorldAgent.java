package cz.krokviak.agents.examples.basic;

import cz.krokviak.agents.agent.Agent;
import cz.krokviak.agents.model.ModelRegistry;
import cz.krokviak.agents.model.OpenAIResponsesModel;
import cz.krokviak.agents.runner.Runner;

public class HelloWorldAgent {

    public static void main(String[] args) {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null) {
            System.err.println("Set OPENAI_API_KEY environment variable");
            return;
        }

        ModelRegistry.setDefault(new OpenAIResponsesModel(apiKey));

        Agent<Void> agent = Agent.<Void>builder("Greeter")
            .instructions("You are a friendly greeter. Keep responses short and cheerful.")
            .model("gpt-4.1")
            .build();

        var result = Runner.run(agent, "Hello! What's your name?");
        System.out.println("Agent: " + result.finalOutput());
        System.out.println("Tokens used: " + result.usage().totalTokens());
    }
}
