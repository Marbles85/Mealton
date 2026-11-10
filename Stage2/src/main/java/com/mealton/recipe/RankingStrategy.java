package com.mealton.recipe;

/**
 * Strategy pattern: one way of putting recipe matches in order. The matcher
 * does not care which strategy it gets, so new orderings can be added without
 * changing it.
 */
public interface RankingStrategy {

	/** Short name shown to the user, for example "best match". */
	String name();

	/** Negative if {@code a} should be listed before {@code b}. */
	int compare(RecipeMatch a, RecipeMatch b);
}