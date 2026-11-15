package com.mealton.plan;

import com.mealton.recipe.Recipe;

/**
 * One meal on the calendar: a recipe, the tag it is planned for (Breakfast,
 * Dinner, Post-workout, or empty for no tag) and how many servings.
 */
public record PlannedMeal(String tag, Recipe recipe, int servings) {

	public PlannedMeal {
		tag = tag == null ? "" : tag.trim();
		if (recipe == null) {
			throw new IllegalArgumentException("Choose a recipe first.");
		}
		if (servings < 1 || servings > 20) {
			throw new IllegalArgumentException("Servings must be between 1 and 20.");
		}
	}

	public PlannedMeal withRecipe(Recipe other) {
		return new PlannedMeal(tag, other, servings);
	}

	public PlannedMeal withTag(String other) {
		return new PlannedMeal(other, recipe, servings);
	}

	public PlannedMeal withServings(int other) {
		return new PlannedMeal(tag, recipe, other);
	}
}