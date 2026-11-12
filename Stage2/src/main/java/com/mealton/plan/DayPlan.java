package com.mealton.plan;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mealton.recipe.Recipe;

/** Composite: the meals planned for one day (feature F02). */
public final class DayPlan implements PlanComponent {
	private final DayOfWeek day;
	private final Map<MealSlot, MealEntry> meals = new EnumMap<>(MealSlot.class);

	public DayPlan(DayOfWeek day) {
		this.day = day;
	}

	public DayOfWeek day() {
		return day;
	}

	/** Plans a recipe for a meal, replacing whatever was planned there. */
	public void set(MealSlot slot, Recipe recipe) {
		meals.put(slot, new MealEntry(slot, recipe));
	}

	/** @return true if a meal was removed */
	public boolean remove(MealSlot slot) {
		return meals.remove(slot) != null;
	}

	public Optional<MealEntry> get(MealSlot slot) {
		return Optional.ofNullable(meals.get(slot));
	}

	/** The planned meals in breakfast, lunch, dinner order. */
	public List<MealEntry> entries() {
		return new ArrayList<>(meals.values());
	}

	@Override
	public String describe() {
		StringBuilder sb = new StringBuilder(dayName());
		if (meals.isEmpty()) {
			sb.append("\n  (nothing planned)");
		}
		for (MealEntry entry : meals.values()) {
			sb.append("\n  ").append(entry.describe());
		}
		return sb.toString();
	}

	String dayName() {
		String name = day.name();
		return name.charAt(0) + name.substring(1).toLowerCase();
	}

	@Override
	public int mealCount() {
		return meals.size();
	}

	@Override
	public List<Recipe> recipes() {
		List<Recipe> recipes = new ArrayList<>();
		for (MealEntry entry : meals.values()) {
			recipes.addAll(entry.recipes());
		}
		return recipes;
	}
}