package cz.krokviak.agents.cli.cost;

import java.util.Map;

public class CostTracker {

    private static final Map<String, ModelPricing> PRICING = Map.of(
        "claude-sonnet-4-20250514", new ModelPricing(3.0, 15.0),
        "claude-opus-4-20250514", new ModelPricing(15.0, 75.0),
        "claude-haiku-4-20250514", new ModelPricing(0.80, 4.0)
    );

    private int totalInputTokens;
    private int totalOutputTokens;
    private double totalCostUsd;
    private String lastModel;

    public void record(String model, int inputTokens, int outputTokens) {
        this.lastModel = model;
        this.totalInputTokens += inputTokens;
        this.totalOutputTokens += outputTokens;

        ModelPricing pricing = findPricing(model);
        if (pricing != null) {
            totalCostUsd += (inputTokens / 1_000_000.0) * pricing.inputPerMillion()
                         + (outputTokens / 1_000_000.0) * pricing.outputPerMillion();
        }
    }

    private ModelPricing findPricing(String model) {
        ModelPricing exact = PRICING.get(model);
        if (exact != null) return exact;
        // Fuzzy match by prefix
        for (var entry : PRICING.entrySet()) {
            if (model.startsWith(entry.getKey().substring(0, Math.min(12, entry.getKey().length())))) {
                return entry.getValue();
            }
        }
        return null;
    }

    public int totalInputTokens() { return totalInputTokens; }
    public int totalOutputTokens() { return totalOutputTokens; }
    public double totalCostUsd() { return totalCostUsd; }

    public String format() {
        return String.format("[tokens: %,d in, %,d out | cost: $%.4f]",
            totalInputTokens, totalOutputTokens, totalCostUsd);
    }
}
