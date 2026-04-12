package cz.krokviak.agents.examples.guardrails;

import cz.krokviak.agents.def.Agent;
import cz.krokviak.agents.exception.InputGuardrailTrippedException;
import cz.krokviak.agents.guardrail.GuardrailResult;
import cz.krokviak.agents.guardrail.InputGuardrail;
import cz.krokviak.agents.guardrail.OutputGuardrail;
import cz.krokviak.agents.adapter.openai.OpenAIResponsesModel;
import cz.krokviak.agents.runner.Runner;

import java.util.List;

public class GuardrailExample {

    public static void main(String[] args) {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null) {
            System.err.println("Set OPENAI_API_KEY environment variable");
            return;
        }

        var runner = Runner.of(new OpenAIResponsesModel(apiKey));

        // Input guardrail: block PII
        InputGuardrail<Void> piiFilter = InputGuardrail.of("pii_filter", (ctx, input) -> {
            String text = input.text().toLowerCase();
            if (text.contains("ssn") || text.contains("social security")) {
                return GuardrailResult.tripwire("PII detected: social security number reference");
            }
            return GuardrailResult.pass();
        });

        // Output guardrail: check length
        OutputGuardrail<Void> lengthCheck = OutputGuardrail.of("length_check", (ctx, output) -> {
            if (output.length() > 5000) {
                return GuardrailResult.tripwire("Response too long");
            }
            return GuardrailResult.pass();
        });

        Agent<Void> agent = Agent.<Void>builder("SafeAgent")
            .instructions("You are a helpful assistant.")
            .inputGuardrails(List.of(piiFilter))
            .outputGuardrails(List.of(lengthCheck))
            .build();

        try {
            var result = runner.run(agent, "What is my SSN?");
            System.out.println("Response: " + result.finalOutput());
        } catch (InputGuardrailTrippedException e) {
            System.out.println("Blocked: " + e.reason());
        }
    }
}
