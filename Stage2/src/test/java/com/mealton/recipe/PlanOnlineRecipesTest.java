package com.mealton.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mealton.facade.MealtonFacade;

/** The meal planner can pull extra recipes from the free recipe services. */
class PlanOnlineRecipesTest {
	private MealtonFacade facade;

	@BeforeEach
	void setUp() {
		facade = new MealtonFacade();
		facade.setSpoonacularKey("");
		StarterRecipes.loadInto(facade);
	}

	private static RecipeSource source(IOException failure) {
		return new RecipeSource() {
			@Override
			public String name() {
				return "Test service";
			}

			@Override
			public List<RecipeImporter.Imported> find(int count, String hint, java.util.Set<String> knownTitles,
					java.util.function.IntConsumer progress) throws IOException {
				if (failure != null) {
					throw failure;
				}
				return List.of(new RecipeImporter.Imported(new Recipe("Online " + hint + " bowl", 2,
						List.of(new RecipeIngredient("rice", 100, "g")), List.of("Cook")), null));
			}
		};
	}

	@Test
	void eachHintBringsRecipesAndNothingIsStoredYet() {
		facade.setRecipeSource(source(null));
		int before = facade.getRecipes().size();
		List<Recipe> found = facade.findRecipesForPlan(List.of("thai", "breakfast"), 5);
		assertEquals(2, found.size());
		assertEquals(before, facade.getRecipes().size());
		assertEquals(2, facade.swapInDiscoveredRecipes(found));
		assertTrue(facade.getRecipes().stream().anyMatch(r -> r.getTitle().equals("Online thai bowl")));
	}

	@Test
	void aFailingServiceJustMeansNoExtraRecipes() {
		facade.setRecipeSource(source(new IOException("the site answered with error 402")));
		assertTrue(facade.findRecipesForPlan(List.of("thai"), 5).isEmpty());
	}
}