package com.mealton.nutrition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.mealton.recipe.Recipe;
import com.mealton.recipe.RecipeIngredient;

/**
 * Recipes from the web use many ingredient names; the food table should know
 * them.
 */
class WebIngredientsTest {
	@Test
	void everydayWebIngredientNamesAreKnown() {
		for (String name : List.of("fish sauce", "tamarind paste", "boneless chicken thighs", "lamb mince",
				"pork belly", "coconut milk", "red onions", "fresh ginger", "plain flour", "unsalted butter",
				"double cream", "spring onions", "bay leaves", "ground cumin", "chopped tomatoes", "soy sauce",
				"garlic cloves", "sesame oil", "white wine vinegar", "dark brown sugar", "chicken stock", "minced beef",
				"shrimp paste")) {
			assertTrue(FoodTable.knows(name), name);
		}
	}

	@Test
	void theLongestNameWins() {
		double stock = FoodTable.lookup("chicken stock", 100, "g").orElseThrow().calories();
		double chicken = FoodTable.lookup("chicken breast", 100, "g").orElseThrow().calories();
		assertTrue(stock < 20 && chicken > 150, "stock is not chicken");
		double pb = FoodTable.lookup("peanut butter", 100, "g").orElseThrow().calories();
		assertEquals(588, pb, 0.01);
	}

	@Test
	void cupsOfVegetablesAndFruitAreCounted() {
		double spinach = FoodTable.lookup("spinach", 1, "cup").orElseThrow().calories();
		assertEquals(8, spinach, 2); // about 30 g of raw spinach
		assertTrue(FoodTable.lookup("broccoli", 1, "cup").orElseThrow().calories() > 25);
		assertTrue(FoodTable.lookup("chopped onion", 0.5, "cup").isPresent());
		assertTrue(FoodTable.lookup("blueberries", 1, "cup").orElseThrow().calories() > 70);
	}

	@Test
	void sameFoodUnderDifferentNames() {
		assertTrue(FoodTable.sameFood("baby spinach", "spinach"));
		assertTrue(FoodTable.sameFood("fresh spinach leaves", "Spinach"));
		assertTrue(FoodTable.sameFood("red onions", "onion"));
		assertFalse(FoodTable.sameFood("chicken stock", "chicken breast"));
		assertFalse(FoodTable.sameFood("peanut butter", "butter"));
	}

	@Test
	void zeroFoodsNeedNoWeight() {
		assertEquals(0, FoodTable.lookup("a pinch of salt", 1, "").orElseThrow().calories(), 1e-9);
		assertEquals(0, FoodTable.lookup("water", 2, "cup").orElseThrow().calories(), 1e-9);
	}

	@Test
	void aTypicalWebRecipeIsFullyCounted() {
		Recipe r = new Recipe("Chicken Adobo", 4,
				List.of(new RecipeIngredient("chicken thighs", 800, "g"), new RecipeIngredient("soy sauce", 4, "tbsp"),
						new RecipeIngredient("white vinegar", 0.5, "cup"), new RecipeIngredient("garlic cloves", 6, ""),
						new RecipeIngredient("bay leaves", 3, ""), new RecipeIngredient("black pepper", 1, "tsp"),
						new RecipeIngredient("vegetable oil", 2, "tbsp"), new RecipeIngredient("salt", 1, "")),
				List.of("Cook it."));
		RecipeNutrition n = RecipeNutrition.of(r);
		assertTrue(n.isComplete(), n.notCounted().toString());
		assertTrue(n.perServing().calories() > 300 && n.perServing().calories() < 800, n.perServing().summary());
		assertFalse(n.counted().isEmpty());
	}
}