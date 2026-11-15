package com.mealton.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.mealton.facade.MealtonFacade;

/** The built-in recipes wait until the internet recipes have loaded. */
class StarterHoldTest {
	@Test
	void allRecipesAreEmptyUntilReleased() {
		MealtonFacade facade = new MealtonFacade();
		int starters = StarterRecipes.loadInto(facade);
		assertTrue(starters > 0);
		facade.holdStarterRecipes();
		assertTrue(facade.isHoldingStarterRecipes());
		assertEquals(0, facade.getRecipes().size());
		assertEquals(starters, facade.getRecipesForSaving().size(), "nothing is lost when saving");
		facade.releaseStarterRecipes();
		assertEquals(starters, facade.getRecipes().size());
	}

	@Test
	void foundRecipesShowWhileTheStartersWait() {
		MealtonFacade facade = new MealtonFacade();
		StarterRecipes.loadInto(facade);
		facade.holdStarterRecipes();
		Recipe web = new Recipe("Web soup", 2, java.util.List.of(new RecipeIngredient("water", 1, "cup")),
				java.util.List.of("Heat."));
		facade.swapInDiscoveredRecipes(java.util.List.of(web));
		assertEquals(1, facade.getRecipes().size());
		assertEquals("Web soup", facade.getRecipes().get(0).getTitle());
	}
}