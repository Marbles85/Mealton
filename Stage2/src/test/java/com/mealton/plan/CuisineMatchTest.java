package com.mealton.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.mealton.recipe.Recipe;
import com.mealton.recipe.RecipeIngredient;

/**
 * The planner reads titles, tags and typical words when it matches a cuisine.
 */
class CuisineMatchTest {
	private static Recipe recipe(String title, String ingredient, String... tags) {
		Recipe r = new Recipe(title, 2, List.of(new RecipeIngredient(ingredient, 1, null)), List.of("Cook it."));
		r.setAiTags(List.of(tags));
		return r;
	}

	@Test
	void aTagOrTitleNamingTheCuisineIsAStrongMatch() {
		assertEquals(2,
				RangePlanner.cuisineStrength(recipe("Chicken Adobo", "chicken", "filipino"), Set.of("Filipino")));
		assertEquals(2, RangePlanner.cuisineStrength(recipe("Filipino Pork Stew", "pork"), Set.of("Filipino")));
	}

	@Test
	void typicalWordsAreAWeakMatchAndOtherFoodDoesNotMatch() {
		assertEquals(1, RangePlanner.cuisineStrength(recipe("Sinigang", "tamarind"), Set.of("Filipino")));
		assertEquals(1, RangePlanner.cuisineStrength(recipe("Beef Stew", "beef"), Set.of("comfort")));
		assertEquals(0, RangePlanner.cuisineStrength(recipe("Khmer Banh Xeo", "rice flour", "cambodian"),
				Set.of("Filipino", "comfort")));
		assertEquals(0, RangePlanner.cuisineStrength(recipe("Fruit Salad", "apple"), Set.of("Filipino", "comfort")));
	}
}