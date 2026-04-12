package cz.krokviak.agents.examples.tools;

import cz.krokviak.agents.def.Agent;
import cz.krokviak.agents.adapter.openai.OpenAIResponsesModel;
import cz.krokviak.agents.runner.Runner;
import cz.krokviak.agents.tool.*;

import java.util.List;

public class FunctionToolExample {

    static class WeatherTools {
        @FunctionTool(name = "get_weather", description = "Get current weather for a city")
        public ToolOutput getWeather(@Param("city name") String city) {
            // Simulated weather data
            return ToolOutput.text("It's 72°F and sunny in " + city);
        }
    }

    public static void main(String[] args) {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null) {
            System.err.println("Set OPENAI_API_KEY environment variable");
            return;
        }

        var runner = Runner.of(new OpenAIResponsesModel(apiKey));

        List<Tool> tools = Tools.fromClass(new WeatherTools());

        // Lambda-based tool
        Tool timeTool = Tools.function("get_time")
            .description("Get current time in a timezone")
            .param("timezone", String.class, "The timezone (e.g., UTC, EST)")
            .handler((toolArgs, ctx) -> ToolOutput.text("Current time in " + toolArgs.get("timezone", String.class) + ": 14:30"))
            .build();

        tools.add(timeTool);

        Agent<Void> agent = Agent.<Void>builder("WeatherBot")
            .instructions("You help users check weather and time. Use the available tools.")
            .tools(tools)
            .build();

        var result = runner.run(agent, "What's the weather in Prague and what time is it in UTC?");
        System.out.println("Agent: " + result.finalOutput());
    }
}
