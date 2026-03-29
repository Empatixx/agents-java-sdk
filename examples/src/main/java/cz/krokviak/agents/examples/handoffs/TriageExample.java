package cz.krokviak.agents.examples.handoffs;

import cz.krokviak.agents.agent.Agent;
import cz.krokviak.agents.handoff.Handoff;
import cz.krokviak.agents.model.ModelRegistry;
import cz.krokviak.agents.model.OpenAIResponsesModel;
import cz.krokviak.agents.runner.Runner;

import java.util.List;

public class TriageExample {

    public static void main(String[] args) {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null) {
            System.err.println("Set OPENAI_API_KEY environment variable");
            return;
        }

        ModelRegistry.setDefault(new OpenAIResponsesModel(apiKey));

        // Specialist agents
        Agent<Void> billingAgent = Agent.<Void>builder("BillingSpecialist")
            .instructions("You are a billing specialist. Help with invoices, payments, and refunds.")
            .build();

        Agent<Void> techAgent = Agent.<Void>builder("TechSupport")
            .instructions("You are tech support. Help with technical issues, bugs, and troubleshooting.")
            .build();

        // Triage agent with handoffs
        Agent<Void> triageAgent = Agent.<Void>builder("Triage")
            .instructions("""
                You are a triage agent. Based on the user's issue:
                - Transfer to BillingSpecialist for billing/payment issues
                - Transfer to TechSupport for technical issues
                - Handle general questions yourself
                """)
            .handoffs(List.of(
                Handoff.<Void>to(billingAgent)
                    .description("Transfer to billing specialist for payment and invoice issues")
                    .build(),
                Handoff.<Void>to(techAgent)
                    .description("Transfer to tech support for technical problems")
                    .build()
            ))
            .build();

        var result = Runner.run(triageAgent, "I'm having trouble with my invoice from last month");
        System.out.println("Final agent: " + result.lastAgent().name());
        System.out.println("Response: " + result.finalOutput());
    }
}
