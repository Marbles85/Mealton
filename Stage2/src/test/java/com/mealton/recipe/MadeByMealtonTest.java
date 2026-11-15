package com.mealton.recipe;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The "Mealton generated" filter: starter recipes, Claude's recipes and ones
 * Mealton edited.
 */
class MadeByMealtonTest {
	private static Recipe plain() {
		return new Recipe("Soup", 2, List.of(new RecipeIngredient("water", 1, "cup")), List.of("Heat."));
	}

	@Test
	void starterRecipesCount() {
		Recipe r = plain();
		r.setLibrary(true);
		assertTrue(r.isMadeByMealton());
	}

	@Test
	void foundOnlineRecipesDoNotButClaudeWrittenOnesDo() {
		Recipe web = plain();
		web.setLibrary(true);
		web.setDiscovered(true);
		web.setSource("food.example.com", "https://food.example.com/soup");
		assertFalse(web.isMadeByMealton());
		Recipe written = plain();
		written.setLibrary(true);
		written.setDiscovered(true);
		written.setSource(RecipeDiscovery.CREATOR, "");
		assertTrue(written.isMadeByMealton());
	}

	@Test
	void anEditedRecipeKeepsTheMarkAndItIsNotAFilterWord() {
		Recipe mine = plain();
		assertFalse(mine.isMadeByMealton());
		Recipe edited = mine.copyWith("Soup (my version)", 2, mine.getIngredients(), mine.getSteps())
				.markMadeByMealton();
		assertTrue(edited.isMadeByMealton());
		assertTrue(edited.copyWith("Soup 2", 2, edited.getIngredients(), edited.getSteps()).isMadeByMealton());
	}
}