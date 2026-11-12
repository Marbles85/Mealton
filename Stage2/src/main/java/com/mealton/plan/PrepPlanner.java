package com.mealton.plan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.mealton.recipe.Recipe;
import com.mealton.recipe.RecipeIngredient;

/**
 * The prep-day planner (feature F05): turns a weekly plan into one combined
 * list for a cooking-prep session. It adds up every ingredient the week needs,
 * points out ingredients that several recipes share (prep them once), and
 * suggests batch cooking for recipes that appear more than once. Plain code, no
 * AI needed; the facade can ask the AI to turn the result into a short
 * schedule.
 */
public final class PrepPlanner {

	/**
	 * One ingredient for the whole week. {@code usedIn} lists the recipe titles
	 * that need it.
	 */
	public record PrepItem(String name, String unit, double quantity, List<String> usedIn) {
		public PrepItem {
			usedIn = List.copyOf(usedIn);
		}

		public RecipeIngredient asIngredient() {
			return new RecipeIngredient(name, quantity, unit);
		}
	}

	/**
	 * The result.
	 *
	 * @param ingredients everything the week needs, combined (the same ingredient
	 *                    in the same unit is added up)
	 * @param sharedPrep  sentences about ingredients used by two or more recipes
	 * @param cookAhead   sentences about recipes planned more than once
	 * @param meals       how many meals the plan has
	 * @param recipes     how many different recipes it uses
	 */
	public record PrepPlan(List<PrepItem> ingredients, List<String> sharedPrep, List<String> cookAhead, int meals,
			int recipes) {
		public PrepPlan {
			ingredients = List.copyOf(ingredients);
			sharedPrep = List.copyOf(sharedPrep);
			cookAhead = List.copyOf(cookAhead);
		}

		/** The plan as readable text. */
		public String describe() {
			StringBuilder sb = new StringBuilder("Prep day: " + meals + " meal(s) from " + recipes + " recipe(s).");
			sb.append("\nEverything the week needs:");
			for (PrepItem item : ingredients) {
				sb.append("\n  - ").append(item.asIngredient()).append("  (").append(String.join(", ", item.usedIn()))
						.append(")");
			}
			if (!sharedPrep.isEmpty()) {
				sb.append("\nShared by several recipes (buy, measure or chop these together):");
				sharedPrep.forEach(s -> sb.append("\n  - ").append(s));
			}
			if (!cookAhead.isEmpty()) {
				sb.append("\nCook ahead:");
				cookAhead.forEach(s -> sb.append("\n  - ").append(s));
			}
			return sb.toString();
		}
	}

	private PrepPlanner() {
	}

	/** Builds the prep plan for a weekly plan. */
	public static PrepPlan build(WeekPlan plan) {
		if (plan.mealCount() == 0) {
			throw new IllegalArgumentException("The plan is empty. Generate a plan first, then make the prep list.");
		}
		Map<String, double[]> totals = new LinkedHashMap<>();
		Map<String, String[]> names = new LinkedHashMap<>(); // key -> {name, unit}
		Map<String, Set<String>> usedIn = new LinkedHashMap<>();
		for (Recipe recipe : plan.recipes()) { // one entry per planned meal, so repeats count twice
			for (RecipeIngredient ingredient : recipe.getIngredients()) {
				String key = ingredient.name().trim().toLowerCase(Locale.ROOT) + "|"
						+ ingredient.unit().trim().toLowerCase(Locale.ROOT);
				totals.computeIfAbsent(key, k -> new double[1])[0] += ingredient.quantity();
				names.putIfAbsent(key, new String[] { ingredient.name().trim(), ingredient.unit().trim() });
				usedIn.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(recipe.getTitle());
			}
		}
		List<PrepItem> items = new ArrayList<>();
		for (Map.Entry<String, double[]> e : totals.entrySet()) {
			String[] nameAndUnit = names.get(e.getKey());
			items.add(new PrepItem(nameAndUnit[0], nameAndUnit[1], e.getValue()[0],
					new ArrayList<>(usedIn.get(e.getKey()))));
		}
		items.sort(
				Comparator.comparing((PrepItem i) -> i.name().toLowerCase(Locale.ROOT)).thenComparing(PrepItem::unit));

		List<String> shared = new ArrayList<>();
		for (PrepItem item : items) {
			if (item.usedIn().size() >= 2) {
				shared.add(item.asIngredient() + " in total, used in " + String.join(", ", item.usedIn()) + ".");
			}
		}
		List<String> ahead = new ArrayList<>();
		for (Recipe recipe : plan.distinctRecipes()) {
			int times = plan.timesUsed(recipe.getId());
			if (times >= 2) {
				ahead.add(recipe.getTitle() + " is planned " + times + " times: cook it once for "
						+ times * recipe.getServings() + " servings and reheat it.");
			}
		}
		return new PrepPlan(items, shared, ahead, plan.mealCount(), plan.distinctRecipes().size());
	}
}