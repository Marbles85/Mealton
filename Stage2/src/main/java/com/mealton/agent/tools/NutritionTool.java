package com.mealton.agent.tools;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.mealton.agent.Tool;
import com.mealton.agent.ToolResult;
import com.mealton.llm.ToolSpec;
import com.mealton.nutrition.LoggedFood;
import com.mealton.nutrition.MacroTracker;
import com.mealton.nutrition.Nutrients;
import com.mealton.util.Formats;

/**
 * Lets the agent keep the food diary (feature F04). Foods in the built-in table
 * are logged exactly; for any other food the agent estimates the numbers itself
 * (action log_estimate), and the entry is marked as an estimate.
 */
public class NutritionTool implements Tool {
	private final MacroTracker tracker;
	private final Supplier<LocalDate> today;

	public NutritionTool(MacroTracker tracker, Supplier<LocalDate> today) {
		this.tracker = tracker;
		this.today = today;
	}

	@Override
	public String name() {
		return "nutrition";
	}

	@Override
	public ToolSpec schema() {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("action", "Required. One of: log, log_estimate, progress, list, delete.");
		params.put("food", "For log: the food name, for example 'egg' or 'chicken breast'. "
				+ "For log_estimate: a description such as 'homemade lasagne, 1 portion'.");
		params.put("quantity", "For log: the amount (default 1).");
		params.put("unit", "For log: g, kg, oz, lb, ml, l, tsp, tbsp, cup, or empty for pieces (2 eggs).");
		params.put("calories", "For log_estimate: your best estimate of the calories (kcal) for the whole portion.");
		params.put("protein", "For log_estimate: estimated grams of protein for the whole portion.");
		params.put("carbs", "For log_estimate: estimated grams of carbohydrate for the whole portion.");
		params.put("fat", "For log_estimate: estimated grams of fat for the whole portion.");
		params.put("id", "For delete: the id of a logged food (see list).");
		return new ToolSpec(name(),
				"The user's food diary for today. 'log' records an amount of a common food using exact table "
						+ "values; if the food is not in the table it returns an error, and you should then use "
						+ "'log_estimate' with your own best estimate of the whole portion (it is marked as an "
						+ "estimate). 'progress' compares today's totals with the user's daily goals, 'list' shows "
						+ "today's entries and 'delete' removes one. Only log what the user says they ate.",
				params);
	}

	@Override
	public ToolResult execute(Map<String, String> args) {
		String action = args.getOrDefault("action", "").trim().toLowerCase();
		try {
			switch (action) {
			case "log": {
				double quantity = args.get("quantity") == null || args.get("quantity").isBlank() ? 1
						: Formats.parseQuantity(args.get("quantity"));
				LoggedFood entry = tracker.logFood(today.get(), args.get("food"), quantity, args.get("unit"));
				return ToolResult.ok("Logged " + entry + "\n" + tracker.progress(today.get()).summary());
			}
			case "log_estimate": {
				Nutrients estimate = new Nutrients(number(args, "calories"), number(args, "protein"),
						number(args, "carbs"), number(args, "fat"));
				LoggedFood entry = tracker.logCustom(today.get(), args.get("food"), estimate, true);
				return ToolResult.ok("Logged " + entry + "\n" + tracker.progress(today.get()).summary());
			}
			case "progress":
				return ToolResult.ok(tracker.progress(today.get()).summary());
			case "list": {
				List<LoggedFood> entries = tracker.entriesOn(today.get());
				if (entries.isEmpty()) {
					return ToolResult.ok("Nothing has been logged today.");
				}
				StringBuilder sb = new StringBuilder("Logged today:");
				entries.forEach(e -> sb.append("\n- ").append(e));
				return ToolResult.ok(sb.toString());
			}
			case "delete": {
				LoggedFood removed = tracker.delete(wholeId(args.get("id")));
				return ToolResult.ok("Deleted " + removed);
			}
			default:
				return ToolResult
						.error("Unknown action '" + action + "'. Use log, log_estimate, progress, list or delete.");
			}
		} catch (IllegalArgumentException e) {
			return ToolResult.error(e.getMessage());
		}
	}

	private static double number(Map<String, String> args, String field) {
		String text = args.get(field);
		if (text == null || text.isBlank()) {
			throw new IllegalArgumentException("log_estimate needs '" + field + "' (a number).");
		}
		try {
			return Double.parseDouble(text.trim());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("'" + field + "' must be a number, got '" + text + "'.");
		}
	}

	private static long wholeId(String text) {
		try {
			return Long.parseLong(text == null ? "" : text.trim().replace("#", ""));
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Give the 'id' of a logged food as a whole number (see list).");
		}
	}
}