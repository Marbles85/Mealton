package com.mealton.nutrition;

import java.util.ArrayList;
import java.util.List;

import com.mealton.recipe.Recipe;
import com.mealton.recipe.RecipeIngredient;

/**
 * An estimate of what a recipe contains (feature F11), worked out from the
 * built-in {@link FoodTable}. Ingredients the table does not know (or cannot
 * weigh) are listed in {@code notCounted} and add nothing, so the figures are a
 * lower bound and are always shown as an estimate.
 *
 * @param total       the whole recipe
 * @param perServing  the whole recipe divided by its servings
 * @param servings    how many servings the recipe makes
 * @param counted     names of the ingredients that were added up
 * @param notCounted  names of the ingredients that could not be added up
 * @param aiEstimated true when the figures are Claude's estimate for the whole
 *                    recipe because the table could not cover it
 */
public record RecipeNutrition(Nutrients total, Nutrients perServing, int servings, List<String> counted,
		List<String> notCounted, boolean aiEstimated) {

	public RecipeNutrition {
		counted = List.copyOf(counted);
		notCounted = List.copyOf(notCounted);
	}

	/** Adds up the ingredients of a recipe. */
	public static RecipeNutrition of(Recipe recipe) {
		Nutrients total = Nutrients.ZERO;
		List<String> counted = new ArrayList<>();
		List<String> notCounted = new ArrayList<>();
		for (RecipeIngredient ingredient : recipe.getIngredients()) {
			try {
				var found = FoodTable.lookup(ingredient.name(), ingredient.quantity(), ingredient.unit());
				if (found.isPresent()) {
					total = total.plus(found.get());
					counted.add(ingredient.name());
				} else {
					notCounted.add(ingredient.name());
				}
			} catch (IllegalArgumentException cannotWeigh) {
				notCounted.add(ingredient.name());
			}
		}
		boolean ai = false;
		if (!notCounted.isEmpty() && recipe.getAiEstimate() != null) {
			// the built-in table could not cover everything, so use Claude's estimate for
			// the whole recipe
			total = recipe.getAiEstimate();
			counted.addAll(notCounted);
			notCounted.clear();
			ai = true;
		}
		return new RecipeNutrition(total, total.times(1.0 / recipe.getServings()), recipe.getServings(), counted,
				notCounted, ai);
	}

	/** Share of the ingredients that were counted, from 0.0 to 1.0. */
	public double coverage() {
		int all = counted.size() + notCounted.size();
		return all == 0 ? 0.0 : (double) counted.size() / all;
	}

	/** True when every ingredient was counted. */
	public boolean isComplete() {
		return notCounted.isEmpty();
	}

	/**
	 * One line for screens, for example "Per serving (estimate): 420 kcal, protein
	 * 21 g, carbs 40 g, fat 18 g".
	 */
	public String line() {
		if (counted.isEmpty()) {
			return "No nutrition estimate: none of the ingredients are in the food table.";
		}
		return "Per serving (estimate" + (aiEstimated ? ", by Claude" : "") + "): " + perServing.summary()
				+ (notCounted.isEmpty() ? "" : "  (not counted: " + String.join(", ", notCounted) + ")");
	}
}