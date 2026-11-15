package com.mealton.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Everyone has water, so a recipe never counts it as missing. */
class WaterAssumedTest {
	@Test
	void plainWaterIsNeverMissingButOtherWatersAre() {
		Recipe r = new Recipe("Rice", 2, List.of(new RecipeIngredient("rice", 1, "cup"),
				new RecipeIngredient("Warm Water", 2, "cup"), new RecipeIngredient("coconut water", 1, "cup")),
				List.of("Cook."));
		RecipeMatch m = new RecipeMatcher().matchOne(r, List.of(), LocalDate.of(2026, 11, 15));
		List<String> missing = m.missing().stream().map(RecipeIngredient::name).toList();
		assertEquals(List.of("rice", "coconut water"), missing);
		assertTrue(RecipeMatcher.isPlainWater("water"));
		assertFalse(RecipeMatcher.isPlainWater("rose water"));
	}
}