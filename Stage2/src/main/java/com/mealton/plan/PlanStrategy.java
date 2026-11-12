package com.mealton.plan;

import java.time.DayOfWeek;
import java.util.List;

import com.mealton.recipe.Recipe;

/**
 * Strategy pattern (Stage 1: plan generation). Decides which recipe goes into
 * one meal of the week, so "how to plan" can be swapped without changing the
 * code that fills the week.
 */
public interface PlanStrategy {

	/** The name used in commands, for example "variety". */
	String name();

	/**
	 * Picks a recipe for one meal.
	 *
	 * @param candidates the recipes allowed for this meal (never empty)
	 * @param sofar      what is already planned, so the strategy can avoid
	 *                   repeating itself
	 */
	Recipe choose(List<Recipe> candidates, WeekPlan sofar, DayOfWeek day, MealSlot slot);
}