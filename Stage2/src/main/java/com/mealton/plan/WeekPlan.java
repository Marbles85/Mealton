package com.mealton.plan;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mealton.recipe.Recipe;

/**
 * Composite: a Monday-to-Sunday meal plan made of {@link DayPlan}s (feature
 * F02).
 */
public final class WeekPlan implements PlanComponent {
	private final List<DayPlan> days = new ArrayList<>();

	public WeekPlan() {
		for (DayOfWeek day : DayOfWeek.values()) {
			days.add(new DayPlan(day));
		}
	}

	public DayPlan day(DayOfWeek day) {
		return days.get(day.ordinal());
	}

	public List<DayPlan> days() {
		return new ArrayList<>(days);
	}

	/** How many times a recipe appears in the week. */
	public int timesUsed(long recipeId) {
		int count = 0;
		for (Recipe recipe : recipes()) {
			if (recipe.getId() == recipeId) {
				count++;
			}
		}
		return count;
	}

	/** Each recipe of the week once, in the order it first appears. */
	public List<Recipe> distinctRecipes() {
		Map<Long, Recipe> distinct = new LinkedHashMap<>();
		for (Recipe recipe : recipes()) {
			distinct.putIfAbsent(recipe.getId(), recipe);
		}
		return new ArrayList<>(distinct.values());
	}

	@Override
	public String describe() {
		if (mealCount() == 0) {
			return "No meals are planned yet.";
		}
		StringBuilder sb = new StringBuilder();
		for (DayPlan day : days) {
			if (sb.length() > 0) {
				sb.append("\n");
			}
			sb.append(day.describe());
		}
		return sb.toString();
	}

	@Override
	public int mealCount() {
		int count = 0;
		for (DayPlan day : days) {
			count += day.mealCount();
		}
		return count;
	}

	@Override
	public List<Recipe> recipes() {
		List<Recipe> recipes = new ArrayList<>();
		for (DayPlan day : days) {
			recipes.addAll(day.recipes());
		}
		return recipes;
	}
}