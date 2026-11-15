package com.mealton.recipe;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.mealton.pantry.PantryItem;

/**
 * Spinach in the pantry (100 g) is enough for a recipe that asks for 1 cup of
 * baby spinach.
 */
class PantrySpinachTest {
	@Test
	void aDifferentNameAndUnitStillCountsAsHavingIt() {
		Recipe r = new Recipe("Green Eggs", 2, List.of(new RecipeIngredient("baby spinach leaves", 1, "cup")),
				List.of("Mix."));
		PantryItem spinach = new PantryItem("Spinach", 100, "g", "Produce", null);
		RecipeMatch m = new RecipeMatcher().matchOne(r, List.of(spinach), java.time.LocalDate.now());
		assertTrue(m.missing().isEmpty(), m.missing().toString());
	}
}