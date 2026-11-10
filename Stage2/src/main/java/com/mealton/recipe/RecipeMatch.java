package com.mealton.recipe;

import java.util.List;

/**
 * How well one saved recipe fits what is in the pantry right now.
 *
 * @param recipe       the recipe that was checked
 * @param available    ingredients the pantry already covers
 * @param missing      what is still needed; each entry is the shortfall (for
 *                     example 100 g more flour)
 * @param expiringUsed how many of the available ingredients are use-soon pantry
 *                     items
 */
public record RecipeMatch(Recipe recipe, List<RecipeIngredient> available, List<RecipeIngredient> missing,
		int expiringUsed) {

	public RecipeMatch {
		available = List.copyOf(available);
		missing = List.copyOf(missing);
	}

	/** True when nothing is missing, so the recipe can be cooked right now. */
	public boolean isCookable() {
		return missing.isEmpty();
	}

	/** Share of the recipe's ingredients the pantry covers, from 0.0 to 1.0. */
	public double coverage() {
		int total = available.size() + missing.size();
		return total == 0 ? 0.0 : (double) available.size() / total;
	}
}