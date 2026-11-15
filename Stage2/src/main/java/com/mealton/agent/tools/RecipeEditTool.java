package com.mealton.agent.tools;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mealton.agent.Tool;
import com.mealton.agent.ToolResult;
import com.mealton.facade.MealtonFacade;
import com.mealton.llm.ToolSpec;
import com.mealton.plan.PlannedMeal;
import com.mealton.recipe.Recipe;
import com.mealton.recipe.RecipeIngredient;

/**
 * Lets the agent rewrite recipes when the user agrees: swap an ingredient (in
 * the ingredient list and in the steps), add or remove ingredients, change
 * steps, title and servings, delete a recipe, or put it in or out of My
 * recipes. Ready-made library recipes cannot be edited, so the change is made
 * on a new recipe of the user's own ("... (my version)"), and the calendar can
 * be pointed at it. Changes to the user's own recipes are made in place.
 */
public class RecipeEditTool implements Tool {
	private final MealtonFacade facade;

	public RecipeEditTool(MealtonFacade facade) {
		this.facade = facade;
	}

	@Override
	public String name() {
		return "recipe_edit";
	}

	@Override
	public ToolSpec schema() {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("action",
				"Required. One of: show, substitute, add_ingredient, remove_ingredient, set_step, add_step, "
						+ "remove_step, set_details, delete, save, unsave.");
		params.put("recipe", "A recipe id or title.");
		params.put("ingredient",
				"For substitute and remove_ingredient: the ingredient to change (a name, or its number from 'show').");
		params.put("with", "For substitute: the new ingredient name.");
		params.put("quantity",
				"For substitute (optional), add_ingredient: the amount. Substitute keeps the old amount if omitted.");
		params.put("unit",
				"For substitute (optional) and add_ingredient: the unit, such as g, ml, tbsp, or empty for pieces.");
		params.put("name", "For add_ingredient: the ingredient name.");
		params.put("step", "For set_step and remove_step: the step number from 'show', starting at 1.");
		params.put("text", "For set_step and add_step: the step text.");
		params.put("title", "For set_details: a new title.");
		params.put("servings", "For set_details: a new number of servings (this does not scale the ingredients).");
		params.put("use_on_calendar",
				"For any change to a ready-made recipe: 'true' to also put the new copy in place of the "
						+ "old one on the calendar from today on. Default false.");
		return new ToolSpec(name(),
				"Edits recipes. 'show' prints a recipe with numbered ingredients and steps. 'substitute' swaps one ingredient for "
						+ "another everywhere in the recipe (the ingredient list and the steps), 'add_ingredient'/'remove_ingredient', "
						+ "'set_step'/'add_step'/'remove_step', 'set_details' (title, servings), 'delete', 'save'/'unsave' (My recipes). "
						+ "Use it when the user has agreed to a change, and tell them exactly what changed. Ready-made recipes are "
						+ "copied into the user's own recipes first; say so and give the new recipe's name.",
				params);
	}

	@Override
	public ToolResult execute(Map<String, String> args) {
		String action = args.getOrDefault("action", "").trim().toLowerCase();
		try {
			Recipe r = RecipeLookup.find(facade, args.get("recipe"));
			switch (action) {
			case "show":
				return ToolResult.ok(describe(r));
			case "substitute":
				return substitute(r, args);
			case "add_ingredient": {
				List<RecipeIngredient> list = new ArrayList<>(r.getIngredients());
				list.add(parse(args.get("quantity"), args.get("unit"), args.get("name")));
				return save(r, r.getTitle(), r.getServings(), list, r.getSteps(), args,
						"Added " + list.get(list.size() - 1) + ".");
			}
			case "remove_ingredient": {
				List<RecipeIngredient> list = new ArrayList<>(r.getIngredients());
				RecipeIngredient gone = list.remove(ingredientIndex(r, args.get("ingredient")));
				if (list.isEmpty()) {
					return ToolResult.error("A recipe needs at least one ingredient.");
				}
				return save(r, r.getTitle(), r.getServings(), list, r.getSteps(), args, "Removed " + gone + ".");
			}
			case "set_step": {
				List<String> steps = new ArrayList<>(r.getSteps());
				int i = stepIndex(steps, args.get("step"));
				steps.set(i, text(args));
				return save(r, r.getTitle(), r.getServings(), r.getIngredients(), steps, args,
						"Step " + (i + 1) + " updated.");
			}
			case "add_step": {
				List<String> steps = new ArrayList<>(r.getSteps());
				steps.add(text(args));
				return save(r, r.getTitle(), r.getServings(), r.getIngredients(), steps, args,
						"Added step " + steps.size() + ".");
			}
			case "remove_step": {
				List<String> steps = new ArrayList<>(r.getSteps());
				int i = stepIndex(steps, args.get("step"));
				steps.remove(i);
				return save(r, r.getTitle(), r.getServings(), r.getIngredients(), steps, args,
						"Removed step " + (i + 1) + ".");
			}
			case "set_details": {
				String title = args.get("title") == null || args.get("title").isBlank() ? r.getTitle()
						: args.get("title").trim();
				int servings = RecipeLookup.whole(args.get("servings"), "servings", r.getServings());
				return save(r, title, servings, r.getIngredients(), r.getSteps(), args, "Details updated.");
			}
			case "delete":
				if (r.isLibrary() && !r.isInMyRecipes()) {
					return ToolResult.error("'" + r.getTitle() + "' is a ready-made recipe and cannot be deleted.");
				}
				facade.deleteRecipe(r.getId());
				return ToolResult.ok("Deleted '" + r.getTitle() + "'.");
			case "save":
				facade.saveToMyRecipes(r.getId());
				return ToolResult.ok("Saved '" + r.getTitle() + "' to My recipes.");
			case "unsave":
				facade.removeFromMyRecipes(r.getId());
				return ToolResult.ok("Took '" + r.getTitle() + "' out of My recipes.");
			default:
				return ToolResult.error("Unknown action '" + action + "'.");
			}
		} catch (IllegalArgumentException | IndexOutOfBoundsException e) {
			return ToolResult
					.error(e.getMessage() == null ? "That number does not exist in the recipe." : e.getMessage());
		}
	}

	// ------------------------------------------------------------------ substitute

	private ToolResult substitute(Recipe r, Map<String, String> args) {
		String with = args.get("with");
		if (with == null || with.isBlank()) {
			return ToolResult.error("Say what to use instead ('with').");
		}
		List<RecipeIngredient> list = new ArrayList<>(r.getIngredients());
		int i = ingredientIndex(r, args.get("ingredient"));
		RecipeIngredient old = list.get(i);
		double quantity = old.quantity();
		String unit = old.unit();
		if (args.get("quantity") != null && !args.get("quantity").isBlank()) {
			quantity = com.mealton.util.Formats.parseQuantity(args.get("quantity"));
			unit = args.getOrDefault("unit", "");
		} else if (args.get("unit") != null) {
			unit = args.get("unit");
		}
		RecipeIngredient now = new RecipeIngredient(with.trim(), quantity, unit);
		list.set(i, now);
		List<String> steps = new ArrayList<>();
		Pattern p = Pattern.compile("(?i)\\b" + Pattern.quote(old.name()) + "\\b");
		int stepsChanged = 0;
		for (String s : r.getSteps()) {
			String changed = p.matcher(s).replaceAll(Matcher.quoteReplacement(with.trim().toLowerCase(Locale.ROOT)));
			if (!changed.equals(s)) {
				stepsChanged++;
			}
			steps.add(changed);
		}
		return save(r, r.getTitle(), r.getServings(), list, steps, args, "Swapped " + old + " for " + now
				+ (stepsChanged == 0 ? "" : " and updated " + stepsChanged + " step(s) that mentioned it") + ".");
	}

	// ------------------------------------------------------------------ saving

	/**
	 * Saves the change on the user's own recipe, or on a new own copy when the
	 * recipe is ready-made.
	 */
	private ToolResult save(Recipe r, String title, int servings, List<RecipeIngredient> ingredients,
			List<String> steps, Map<String, String> args, String what) {
		if ((!r.isLibrary() && !r.isDiscovered()) || r.isInMyRecipes()) { // yours, or saved: edited in place
			Recipe edited = r.copyWith(title, servings, ingredients, steps).markMadeByMealton();
			Recipe saved = facade.updateRecipe(r.getId(), edited);
			return ToolResult.ok(what + " Updated your recipe '" + saved.getTitle() + "' (#" + saved.getId() + ").\n"
					+ describe(saved));
		}
		String copyTitle = title.equals(r.getTitle()) ? uniqueTitle(r.getTitle() + " (my version)") : title;
		Recipe copy = facade.saveRecipe(r.copyWith(copyTitle, servings, ingredients, steps).markMadeByMealton());
		String note = "";
		if ("true".equalsIgnoreCase(args.getOrDefault("use_on_calendar", "false"))) {
			note = " Put it on the calendar in place of the original on " + swapOnCalendar(r.getId(), copy.getId())
					+ " meal(s).";
		}
		return ToolResult.ok(what + " '" + r.getTitle()
				+ "' is a ready-made recipe, so the change went into a new recipe of your own: '" + copy.getTitle()
				+ "' (#" + copy.getId() + ")." + note + "\n" + describe(copy));
	}

	private String uniqueTitle(String base) {
		String t = base;
		int n = 2;
		boolean clash = true;
		while (clash) {
			clash = false;
			for (Recipe x : facade.getRecipes()) {
				if (x.getTitle().equalsIgnoreCase(t)) {
					clash = true;
					t = base + " " + n++;
					break;
				}
			}
		}
		return t;
	}

	private int swapOnCalendar(long oldId, long newId) {
		int n = 0;
		LocalDate today = facade.today();
		for (int d = 0; d < 120; d++) {
			LocalDate date = today.plusDays(d);
			List<PlannedMeal> day = facade.getPlannedMeals(date);
			for (int i = 0; i < day.size(); i++) {
				if (day.get(i).recipe().getId() == oldId) {
					facade.replacePlannedMeal(date, i, newId);
					n++;
				}
			}
		}
		return n;
	}

	// ------------------------------------------------------------------ helpers

	private static RecipeIngredient parse(String quantity, String unit, String name) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Give the ingredient 'name'.");
		}
		double q = quantity == null || quantity.isBlank() ? 1 : com.mealton.util.Formats.parseQuantity(quantity);
		return new RecipeIngredient(name.trim(), q, unit == null ? "" : unit.trim());
	}

	private static String text(Map<String, String> args) {
		String t = args.get("text");
		if (t == null || t.isBlank()) {
			throw new IllegalArgumentException("Give the step 'text'.");
		}
		return t.trim();
	}

	private static int stepIndex(List<String> steps, String number) {
		int n = RecipeLookup.whole(number, "step", -1);
		if (n < 1 || n > steps.size()) {
			throw new IllegalArgumentException("Step has to be between 1 and " + steps.size() + ".");
		}
		return n - 1;
	}

	/**
	 * An ingredient by its number from 'show' (1, 2, ...) or by (part of) its name.
	 */
	private static int ingredientIndex(Recipe r, String which) {
		if (which == null || which.isBlank()) {
			throw new IllegalArgumentException("Say which 'ingredient' (its name or number from 'show').");
		}
		List<RecipeIngredient> list = r.getIngredients();
		try {
			int n = Integer.parseInt(which.trim());
			if (n >= 1 && n <= list.size()) {
				return n - 1;
			}
		} catch (NumberFormatException e) {
			// a name
		}
		String w = which.trim().toLowerCase(Locale.ROOT);
		int found = -1;
		for (int i = 0; i < list.size(); i++) {
			String name = list.get(i).name().toLowerCase(Locale.ROOT);
			if (name.equals(w)) {
				return i;
			}
			if (name.contains(w) || w.contains(name)) {
				if (found >= 0) {
					throw new IllegalArgumentException(
							"More than one ingredient matches '" + which + "'. Use its number from 'show'.");
				}
				found = i;
			}
		}
		if (found < 0) {
			throw new IllegalArgumentException("There is no ingredient '" + which + "' in '" + r.getTitle() + "'.");
		}
		return found;
	}

	private static String describe(Recipe r) {
		StringBuilder sb = new StringBuilder("#" + r.getId() + " " + r.getTitle() + " (" + r.getServings() + " servings"
				+ (r.isLibrary() ? ", ready-made" : "") + ")\nIngredients:");
		int n = 1;
		for (RecipeIngredient i : r.getIngredients()) {
			sb.append("\n ").append(n++).append(". ").append(i);
		}
		sb.append("\nSteps:");
		n = 1;
		for (String s : r.getSteps()) {
			sb.append("\n ").append(n++).append(". ").append(s);
		}
		return sb.toString();
	}
}