package com.mealton.plan;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.mealton.recipe.Recipe;
import com.mealton.recipe.RecipeIngredient;

/** Desserts are never planned as breakfast, lunch or dinner. */
class MealFitTest {
	private static Recipe recipe(String title, String... tags) {
		Recipe r = new Recipe(title, 2, List.of(new RecipeIngredient("egg", 1, null)), List.of("Cook it."));
		r.setAiTags(List.of(tags));
		return r;
	}

	@Test
	void dessertsAreRecognised() {
		assertTrue(RangePlanner.looksLikeDessert(recipe("Vanilla Ice Cream")));
		assertTrue(RangePlanner.looksLikeDessert(recipe("Pumpkin Pecan Streusel Cake")));
		assertTrue(RangePlanner.looksLikeDessert(recipe("Avocado Chocolate Bits Frozen Yogurt")));
		assertTrue(RangePlanner.looksLikeDessert(recipe("Apple Pie")));
		assertTrue(RangePlanner.looksLikeDessert(recipe("Something Nice", "dessert")));
	}

	@Test
	void dinnersAndBreakfastsAreNotDesserts() {
		assertFalse(RangePlanner.looksLikeDessert(recipe("Chicken Pot Pie")));
		assertFalse(RangePlanner.looksLikeDessert(recipe("Crab Cakes")));
		assertFalse(RangePlanner.looksLikeDessert(recipe("Fluffy Pancakes")));
		assertFalse(RangePlanner.looksLikeDessert(recipe("Banana Oatmeal")));
		assertFalse(RangePlanner.looksLikeDessert(recipe("Chicken Mole")));
		assertFalse(RangePlanner.looksLikeDessert(recipe("Sweet Potato Soup")));
		assertFalse(RangePlanner.looksLikeDessert(recipe("Spaghetti Squash Boats", "italian")));
	}
}