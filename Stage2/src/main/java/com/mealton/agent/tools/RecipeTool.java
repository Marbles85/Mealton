package com.mealton.agent.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mealton.agent.Tool;
import com.mealton.agent.ToolResult;
import com.mealton.llm.ToolSpec;
import com.mealton.recipe.Recipe;
import com.mealton.recipe.RecipeBook;
import com.mealton.recipe.RecipeIngredient;
import com.mealton.recipe.RecipeParser;

/**
 * Lets the agent work with the user's saved recipes (feature F13): list them,
 * show one, save a new one, rate one, scale one to a different number of
 * servings, or list the top rated. There is deliberately no delete action, so
 * the AI cannot remove recipes on its own.
 */
public class RecipeTool implements Tool {
	private final RecipeBook book;

	public RecipeTool(RecipeBook book) {
		this.book = book;
	}

	@Override
	public String name() {
		return "recipes";
	}

	@Override
	public ToolSpec schema() {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("action", "Required. One of: list, top, show, save, rate, scale.");
		params.put("id", "For show, rate and scale: the id of a saved recipe (see 'list').");
		params.put("title", "For save: the recipe title.");
		params.put("servings", "For save: how many servings the recipe makes, a whole number.");
		params.put("ingredients", "For save: ingredients separated by semicolons, each as 'quantity [unit] name', "
				+ "for example '200 g flour; 2 eggs; 0.5 l milk'.");
		params.put("steps", "For save: optional cooking steps separated by the | character.");
		params.put("stars", "For rate: a whole number from 1 to 5.");
		params.put("to_servings", "For scale: the new number of servings.");
		return new ToolSpec(name(),
				"Works with the user's saved recipes. 'list' shows all of them, 'top' the best rated, "
						+ "'show' one recipe in full, 'save' stores a new recipe, 'rate' gives 1 to 5 stars, "
						+ "and 'scale' shows a recipe for a different number of servings (without changing it).",
				params);
	}

	@Override
	public ToolResult execute(Map<String, String> args) {
		String action = args.getOrDefault("action", "").trim().toLowerCase();
		try {
			switch (action) {
			case "list":
				return ToolResult.ok(summary("Saved recipes", book.getAll()));
			case "top":
				return ToolResult.ok(summary("Top rated recipes", book.getTopRated()));
			case "show":
				return ToolResult.ok(describe(book.get(id(args))));
			case "save":
				return save(args);
			case "rate":
				Recipe rated = book.rate(id(args), whole(args.get("stars"), "stars"));
				return ToolResult.ok("Rated '" + rated.getTitle() + "' " + rated.getRating() + " out of 5 stars.");
			case "scale":
				Recipe scaled = book.scaled(id(args), whole(args.get("to_servings"), "to_servings"));
				return ToolResult.ok(describe(scaled) + "\n(This is a scaled copy. The saved recipe is unchanged.)");
			default:
				return ToolResult.error("Unknown action '" + action + "'. Use list, top, show, save, rate or scale.");
			}
		} catch (IllegalArgumentException e) {
			return ToolResult.error(e.getMessage());
		}
	}

	private ToolResult save(Map<String, String> args) {
		List<RecipeIngredient> ingredients = RecipeParser.parseIngredients(args.get("ingredients"));
		Recipe recipe = new Recipe(args.get("title"), whole(args.get("servings"), "servings"), ingredients,
				RecipeParser.parseSteps(args.get("steps")));
		Recipe saved = book.save(recipe);
		return ToolResult.ok("Saved recipe #" + saved.getId() + ": " + saved);
	}

	private static String summary(String title, List<Recipe> recipes) {
		if (recipes.isEmpty()) {
			return title + ": none yet.";
		}
		StringBuilder sb = new StringBuilder(title + " (" + recipes.size() + "):");
		for (Recipe r : recipes) {
			sb.append("\n- #").append(r.getId()).append(" ").append(r)
					.append(r.isRated() ? ", " + r.getRating() + "/5 stars" : ", not rated");
		}
		return sb.toString();
	}

	private static String describe(Recipe r) {
		StringBuilder sb = new StringBuilder(r.getTitle() + " (" + r.getServings() + " servings, "
				+ (r.isRated() ? r.getRating() + "/5 stars" : "not rated") + ")\nIngredients:");
		for (RecipeIngredient ingredient : r.getIngredients()) {
			sb.append("\n- ").append(ingredient);
		}
		if (!r.getSteps().isEmpty()) {
			sb.append("\nSteps:");
			int number = 1;
			for (String step : r.getSteps()) {
				sb.append("\n").append(number++).append(". ").append(step);
			}
		}
		return sb.toString();
	}

	private static long id(Map<String, String> args) {
		String text = args.get("id");
		try {
			return Long.parseLong(text == null ? "" : text.trim().replace("#", ""));
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(
					"Give the recipe 'id' as a whole number (use action 'list' to see ids).");
		}
	}

	private static int whole(String text, String field) {
		try {
			return Integer.parseInt(text == null ? "" : text.trim());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("'" + field + "' must be a whole number.");
		}
	}
}