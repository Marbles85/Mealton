package com.mealton.agent.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.mealton.agent.Tool;
import com.mealton.agent.ToolResult;
import com.mealton.llm.ToolSpec;
import com.mealton.recipe.RecipeAdvisor;
import com.mealton.recipe.RecipeIngredient;
import com.mealton.recipe.RecipeMatch;

/**
 * Lets the agent answer "what can I cook with what I have?" (F07) and "put what
 * I am missing on my shopping list" (F08). It compares the user's saved recipes
 * with their real pantry.
 */
public class MealIdeasTool implements Tool {
	private final RecipeAdvisor advisor;

	public MealIdeasTool(RecipeAdvisor advisor) {
		this.advisor = advisor;
	}

	@Override
	public String name() {
		return "meal_ideas";
	}

	@Override
	public ToolSpec schema() {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("action", "Required. One of: suggest, check, add_missing.");
		params.put("priority", "For suggest: 'best' (closest to cookable first, the default) "
				+ "or 'expiring' (use up food that is about to spoil first).");
		params.put("id", "For check and add_missing: the id of a saved recipe (see the recipes tool, action 'list').");
		return new ToolSpec(name(),
				"Compares the user's saved recipes with what is in their pantry right now. "
						+ "'suggest' ranks all saved recipes and shows what is available and missing for each, "
						+ "'check' does the same for one recipe, and 'add_missing' puts the missing ingredients "
						+ "of one recipe on the shopping list. Expired food does not count as available.",
				params);
	}

	@Override
	public ToolResult execute(Map<String, String> args) {
		String action = args.getOrDefault("action", "").trim().toLowerCase();
		try {
			switch (action) {
			case "suggest":
				return suggest(args.get("priority"));
			case "check":
				return ToolResult.ok(describe(advisor.check(id(args))));
			case "add_missing":
				return addMissing(id(args));
			default:
				return ToolResult.error("Unknown action '" + action + "'. Use suggest, check or add_missing.");
			}
		} catch (IllegalArgumentException e) {
			return ToolResult.error(e.getMessage());
		}
	}

	private ToolResult suggest(String priority) {
		List<RecipeMatch> matches = advisor.suggest(priority);
		if (matches.isEmpty()) {
			return ToolResult
					.ok("There are no saved recipes to compare with the pantry. You can suggest dishes yourself "
							+ "from the pantry contents and offer to save them with the recipes tool.");
		}
		StringBuilder sb = new StringBuilder("Saved recipes ranked against the pantry (" + matches.size() + "):");
		for (RecipeMatch m : matches) {
			sb.append("\n").append(describe(m));
		}
		return ToolResult.ok(sb.toString());
	}

	private ToolResult addMissing(long id) {
		String title = advisor.check(id).recipe().getTitle();
		List<RecipeIngredient> added = advisor.addMissingToShoppingList(id);
		if (added.isEmpty()) {
			return ToolResult.ok("Nothing to add for '" + title + "': the pantry and shopping list already cover it.");
		}
		return ToolResult.ok("Added to the shopping list for '" + title + "': " + join(added) + ".");
	}

	private static String describe(RecipeMatch m) {
		StringBuilder sb = new StringBuilder("- #" + m.recipe().getId() + " " + m.recipe().getTitle());
		if (m.isCookable()) {
			sb.append(": can be cooked now");
		} else {
			sb.append(": ").append(m.available().size()).append(" of ")
					.append(m.available().size() + m.missing().size()).append(" ingredients available");
		}
		if (m.expiringUsed() > 0) {
			sb.append(", uses ").append(m.expiringUsed()).append(" ingredient(s) that will spoil soon");
		}
		if (!m.available().isEmpty()) {
			sb.append("\n    have: ").append(join(m.available()));
		}
		if (!m.missing().isEmpty()) {
			sb.append("\n    missing: ").append(join(m.missing()));
		}
		return sb.toString();
	}

	private static String join(List<RecipeIngredient> ingredients) {
		return ingredients.stream().map(RecipeIngredient::toString).collect(Collectors.joining(", "));
	}

	private static long id(Map<String, String> args) {
		String text = args.get("id");
		try {
			return Long.parseLong(text == null ? "" : text.trim().replace("#", ""));
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(
					"Give the recipe 'id' as a whole number (use the recipes tool, action 'list').");
		}
	}
}