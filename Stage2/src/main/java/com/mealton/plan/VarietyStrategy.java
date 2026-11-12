package com.mealton.plan;

import java.time.DayOfWeek;
import java.util.Comparator;
import java.util.List;

import com.mealton.recipe.Recipe;

/**
 * Spreads the saved recipes over the week: always takes the recipe that has
 * been used the fewest times so far. Among equals, the better rated recipe
 * wins, then the older one.
 */
public final class VarietyStrategy implements PlanStrategy {

	@Override
	public String name() {
		return "variety";
	}

	@Override
	public Recipe choose(List<Recipe> candidates, WeekPlan sofar, DayOfWeek day, MealSlot slot) {
		return candidates.stream().min(Comparator.<Recipe>comparingInt(r -> sofar.timesUsed(r.getId()))
				.thenComparing(Comparator.comparingInt(Recipe::getRating).reversed()).thenComparingLong(Recipe::getId))
				.orElseThrow();
	}
}