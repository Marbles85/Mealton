package com.mealton.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.mealton.facade.MealtonFacade;

class StarterRecipesTest {

	@Test
	void thereAreTwelveStarterRecipesWithUniqueTitles() {
		List<Recipe> recipes = StarterRecipes.create();
		assertEquals(12, recipes.size());
		Set<String> titles = new HashSet<>();
		for (Recipe r : recipes) {
			assertTrue(titles.add(r.getTitle().toLowerCase()), "duplicate title " + r.getTitle());
		}
	}

	@Test
	void everyStarterRecipeHasIngredientsAndStepsAndIsALibraryRecipe() {
		for (Recipe r : StarterRecipes.create()) {
			assertFalse(r.getIngredients().isEmpty(), r.getTitle());
			assertFalse(r.getSteps().isEmpty(), r.getTitle());
			assertTrue(r.isLibrary(), r.getTitle());
			assertFalse(r.isInMyRecipes(), r.getTitle());
		}
	}

	@Test
	void loadingTwiceDoesNotAddDuplicates() {
		MealtonFacade facade = new MealtonFacade();
		assertEquals(12, StarterRecipes.loadInto(facade));
		assertEquals(0, StarterRecipes.loadInto(facade));
		assertEquals(12, facade.getRecipes().size());
	}

	@Test
	void starterRecipesStayOutOfMyRecipesUntilSaved() {
		MealtonFacade facade = new MealtonFacade();
		StarterRecipes.loadInto(facade);
		assertTrue(facade.getMyRecipes().isEmpty());
		long id = facade.getRecipes().get(0).getId();
		facade.saveToMyRecipes(id);
		assertEquals(1, facade.getMyRecipes().size());
	}
}