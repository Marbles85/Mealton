package com.mealton.nutrition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.mealton.recipe.Recipe;
import com.mealton.recipe.RecipeIngredient;
import com.mealton.recipe.StarterRecipes;

/** Nutrition per recipe (F11). */
class RecipeNutritionTest {

	private static Recipe recipe(int servings, RecipeIngredient... ingredients) {
		return new Recipe("Test", servings, List.of(ingredients), null);
	}

	@Test
	void addsUpTheIngredientsAndDividesByServings() {
		// 100 g chicken breast = 165 kcal, 31 g protein
		RecipeNutrition n = RecipeNutrition.of(recipe(2, new RecipeIngredient("chicken", 200, "g")));
		assertEquals(330, n.total().calories(), 0.01);
		assertEquals(165, n.perServing().calories(), 0.01);
		assertEquals(31, n.perServing().protein(), 0.01);
		assertEquals(2, n.servings());
		assertTrue(n.isComplete());
		assertEquals(1.0, n.coverage(), 0.0001);
	}

	@Test
	void unknownIngredientsAreListedAndAddNothing() {
		RecipeNutrition n = RecipeNutrition.of(
				recipe(1, new RecipeIngredient("chicken", 100, "g"), new RecipeIngredient("dragonfruit", 100, "g")));
		assertEquals(165, n.total().calories(), 0.01);
		assertEquals(List.of("dragonfruit"), n.notCounted());
		assertFalse(n.isComplete());
		assertEquals(0.5, n.coverage(), 0.0001);
		assertTrue(n.line().contains("not counted: dragonfruit"));
	}

	@Test
	void anIngredientThatCannotBeWeighedIsNotCounted() {
		// garlic has a piece weight but no density, so tablespoons cannot be turned
		// into grams
		RecipeNutrition n = RecipeNutrition.of(recipe(1, new RecipeIngredient("garlic", 2, "tbsp")));
		assertEquals(List.of("garlic"), n.notCounted());
		assertTrue(n.line().startsWith("No nutrition estimate"));
	}

	@Test
	void aRecipeWithNothingKnownHasNoEstimate() {
		RecipeNutrition n = RecipeNutrition.of(recipe(1, new RecipeIngredient("dragonfruit", 1, "")));
		assertEquals(0.0, n.coverage(), 0.0001);
		assertTrue(n.line().startsWith("No nutrition estimate"));
	}

	@Test
	void servingsChangeThePerServingFigureButNotTheTotal() {
		RecipeIngredient oil = new RecipeIngredient("olive oil", 100, "g");
		RecipeNutrition two = RecipeNutrition.of(recipe(2, oil));
		RecipeNutrition four = RecipeNutrition.of(recipe(4, oil));
		assertEquals(two.total().calories(), four.total().calories(), 0.01);
		assertEquals(two.perServing().calories(), four.perServing().calories() * 2, 0.01);
	}

	@Test
	void everyStarterRecipeCanBeFullyEstimated() {
		for (Recipe r : StarterRecipes.create()) {
			RecipeNutrition n = RecipeNutrition.of(r);
			assertTrue(n.isComplete(), r.getTitle() + " has uncounted: " + n.notCounted());
			assertTrue(n.perServing().calories() > 0, r.getTitle());
		}
	}

	@Test
	void theLineIsLabelledAsAnEstimate() {
		RecipeNutrition n = RecipeNutrition.of(recipe(1, new RecipeIngredient("egg", 2, "")));
		assertTrue(n.line().startsWith("Per serving (estimate):"));
	}
}