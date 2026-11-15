package com.mealton.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.mealton.facade.MealtonFacade;

/** A recipe you saved can be edited, wherever it came from. */
class EditSavedRecipeTest {
	private static Recipe starter(MealtonFacade facade) {
		StarterRecipes.loadInto(facade);
		return facade.getRecipes().stream().filter(Recipe::isLibrary).findFirst().orElseThrow();
	}

	@Test
	void anUnsavedReadyMadeRecipeStillCannotBeEdited() {
		MealtonFacade facade = new MealtonFacade();
		Recipe lib = starter(facade);
		Recipe edit = lib.copyWith("Changed", lib.getServings(), lib.getIngredients(), lib.getSteps());
		assertThrows(IllegalArgumentException.class, () -> facade.updateRecipe(lib.getId(), edit));
	}

	@Test
	void aSavedReadyMadeRecipeCanBeEditedAndBecomesYours() {
		MealtonFacade facade = new MealtonFacade();
		Recipe lib = starter(facade);
		long id = lib.getId();
		facade.saveToMyRecipes(id);
		Recipe edit = lib.copyWith("My own take", lib.getServings(), lib.getIngredients(), lib.getSteps());
		Recipe now = facade.updateRecipe(id, edit);
		assertEquals("My own take", now.getTitle());
		assertEquals(id, now.getId());
		assertFalse(now.isLibrary());
		assertTrue(now.isInMyRecipes());
	}
}