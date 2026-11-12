package com.mealton.plan;

import java.util.List;

import com.mealton.recipe.Recipe;

/** The leaf of the plan: one recipe planned for one meal (feature F02). */
public final class MealEntry implements PlanComponent {
	private final MealSlot slot;
	private final Recipe recipe;

	public MealEntry(MealSlot slot, Recipe recipe) {
		if (slot == null || recipe == null) {
			throw new IllegalArgumentException("A meal needs a slot and a recipe.");
		}
		this.slot = slot;
		this.recipe = recipe;
	}

	public MealSlot slot() {
		return slot;
	}

	public Recipe recipe() {
		return recipe;
	}

	@Override
	public String describe() {
		return slot.label() + ": " + recipe.getTitle() + " (#" + recipe.getId() + ")";
	}

	@Override
	public int mealCount() {
		return 1;
	}

	@Override
	public List<Recipe> recipes() {
		return List.of(recipe);
	}
}