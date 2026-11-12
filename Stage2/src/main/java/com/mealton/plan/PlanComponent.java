package com.mealton.plan;

import java.util.List;

import com.mealton.recipe.Recipe;

/**
 * Composite pattern (Stage 1: plan structure). A single meal, a whole day and a
 * whole week are all "plan components", so code can ask any of them for its
 * text, its meal count or its recipes without caring how big it is.
 */
public interface PlanComponent {

	/** A readable description (several lines for a day or a week). */
	String describe();

	/** How many meals are planned inside this component. */
	int mealCount();

	/**
	 * Every recipe planned inside this component (a recipe planned twice appears
	 * twice).
	 */
	List<Recipe> recipes();
}