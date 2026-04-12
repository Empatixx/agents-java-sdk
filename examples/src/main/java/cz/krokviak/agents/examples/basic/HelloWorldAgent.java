package cz.krokviak.agents.examples.basic;

import cz.krokviak.agents.def.Agent;
import cz.krokviak.agents.adapter.openai.OpenAIResponsesModel;
import cz.krokviak.agents.runner.Runner;

public class HelloWorldAgent {

    public static void main(String[] args) {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null) {
            System.err.println("Set OPENAI_API_KEY environment variable");
            return;
        }

        var runner = Runner.of(new OpenAIResponsesModel(apiKey));

        Agent<Void> agent = Agent.<Void>builder("Greeter")
            .instructions("You are a friendly greeter. Keep responses short and cheerful.")
            .model("gpt-4.1")
            .build();

        var result = runner.run(agent, "Hello! What's your name?");
        System.out.println("Agent: " + result.finalOutput());
        System.out.println("Tokens used: " + result.usage().totalTokens());
    }
}
