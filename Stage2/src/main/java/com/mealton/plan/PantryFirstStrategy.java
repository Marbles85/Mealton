package com.mealton.plan;

import java.time.DayOfWeek;
import java.util.Comparator;
import java.util.List;

import com.mealton.recipe.Recipe;

/**
 * Plans the recipes that fit the pantry best first. It is given the recipes
 * already ranked against the pantry (best fit first); a recipe is only repeated
 * once every other recipe has been used as often.
 */
public final class PantryFirstStrategy implements PlanStrategy {
	private final List<Long> rankedIds;

	/** @param ranked recipes with the best pantry match first */
	public PantryFirstStrategy(List<Recipe> ranked) {
		this.rankedIds = ranked.stream().map(Recipe::getId).toList();
	}

	@Override
	public String name() {
		return "pantry";
	}

	@Override
	public Recipe choose(List<Recipe> candidates, WeekPlan sofar, DayOfWeek day, MealSlot slot) {
		return candidates.stream().min(
				Comparator.<Recipe>comparingInt(r -> sofar.timesUsed(r.getId())).thenComparingInt(r -> rank(r.getId())))
				.orElseThrow();
	}

	private int rank(long id) {
		int index = rankedIds.indexOf(id);
		return index < 0 ? Integer.MAX_VALUE : index;
	}
}