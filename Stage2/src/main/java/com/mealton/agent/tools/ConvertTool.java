package com.mealton.agent.tools;

import java.util.LinkedHashMap;
import java.util.Map;

import com.mealton.agent.Tool;
import com.mealton.agent.ToolResult;
import com.mealton.llm.ToolSpec;
import com.mealton.util.Formats;
import com.mealton.util.UnitConverter;

/**
 * Lets the agent convert cooking units and scale ingredient amounts (feature
 * F10). Actions: "convert" (for example 2 cup to ml) and "scale" (change the
 * number of servings). The maths is done by {@link UnitConverter}, so the LLM
 * never has to calculate by itself.
 */
public class ConvertTool implements Tool {

	@Override
	public String name() {
		return "convert";
	}

	@Override
	public ToolSpec schema() {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("action", "Required. One of: convert, scale.");
		params.put("amount", "Required. The number to convert or scale, for example '2' or '0.5'.");
		params.put("from", "For convert: the unit to convert from (g, kg, oz, lb, ml, l, tsp, tbsp, cup).");
		params.put("to", "For convert: the unit to convert to. Must be the same kind as 'from'.");
		params.put("from_servings", "For scale: how many servings the amount is written for.");
		params.put("to_servings", "For scale: how many servings the user wants.");
		return new ToolSpec(name(),
				"Converts cooking units (weight to weight, volume to volume) and scales an ingredient "
						+ "amount to a different number of servings. Always use this instead of calculating yourself.",
				params);
	}

	@Override
	public ToolResult execute(Map<String, String> args) {
		String action = args.getOrDefault("action", "").trim().toLowerCase();
		try {
			double amount = Formats.parseQuantityOrDefault(args.get("amount"), Double.NaN);
			if (Double.isNaN(amount)) {
				return ToolResult.error("Give an 'amount' as a number, for example 2.");
			}
			switch (action) {
			case "convert": {
				String from = args.get("from");
				String to = args.get("to");
				if (from == null || from.isBlank() || to == null || to.isBlank()) {
					return ToolResult.error("Give both 'from' and 'to' units, for example from=cup, to=ml.");
				}
				double result = UnitConverter.convert(amount, from, to);
				return ToolResult.ok(Formats.number(amount) + " " + from.trim() + " = " + Formats.number(round(result))
						+ " " + to.trim());
			}
			case "scale": {
				int fromServings = parseServings(args.get("from_servings"));
				int toServings = parseServings(args.get("to_servings"));
				double result = UnitConverter.scale(amount, fromServings, toServings);
				return ToolResult.ok(Formats.number(amount) + " for " + fromServings + " servings becomes "
						+ Formats.number(round(result)) + " for " + toServings + " servings");
			}
			default:
				return ToolResult.error("Unknown action '" + action + "'. Use convert or scale.");
			}
		} catch (IllegalArgumentException e) {
			return ToolResult.error(e.getMessage());
		}
	}

	private static int parseServings(String text) {
		try {
			return Integer.parseInt(text == null ? "" : text.trim());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Servings must be a whole number, for example 4.");
		}
	}

	private static double round(double value) {
		return Math.round(value * 100.0) / 100.0;
	}
}