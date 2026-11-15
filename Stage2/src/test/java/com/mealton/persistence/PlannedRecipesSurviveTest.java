package com.mealton.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mealton.facade.MealtonFacade;
import com.mealton.plan.PlannedMeal;
import com.mealton.plan.RangePlanner;
import com.mealton.plan.RangePlanner.Options;
import com.mealton.recipe.Recipe;
import com.mealton.recipe.RecipeIngredient;
import com.mealton.recipe.StarterRecipes;

/**
 * A web recipe you put on the calendar is still there after logging out and
 * back in.
 */
class PlannedRecipesSurviveTest {
	private static MealtonFacade facadeWithOnlineRecipe(String title) {
		MealtonFacade facade = new MealtonFacade();
		StarterRecipes.loadInto(facade);
		Recipe online = new Recipe(title, 2, List.of(new RecipeIngredient("rice", 200, "g")), List.of("Cook it."));
		assertEquals(1, facade.swapInDiscoveredRecipes(List.of(online)));
		return facade;
	}

	@Test
	void aPlannedOnlineRecipeComesBackWithoutWarnings() {
		MealtonFacade facade = facadeWithOnlineRecipe("Test Noodle Dish");
		Recipe online = facade.getRecipes().stream().filter(r -> r.getTitle().equals("Test Noodle Dish")).findFirst()
				.orElseThrow();
		LocalDate day = facade.today().plusDays(1);
		facade.addPlannedMeal(day, "Dinner", online.getId(), 1);

		MealtonFacade other = new MealtonFacade();
		StarterRecipes.loadInto(other);
		List<String> warnings = UserDataMapper.restore(other, UserDataMapper.capture(facade));
		assertTrue(warnings.isEmpty(), warnings.toString());
		assertEquals(1, other.getPlannedMeals(day).size());
		assertEquals("Test Noodle Dish", other.getPlannedMeals(day).get(0).recipe().getTitle());
	}

	@Test
	void aRefreshNeverDropsARecipeThatIsOnTheCalendar() {
		MealtonFacade facade = facadeWithOnlineRecipe("Test Calendar Dish");
		Recipe online = facade.getRecipes().stream().filter(r -> r.getTitle().equals("Test Calendar Dish")).findFirst()
				.orElseThrow();
		facade.addPlannedMeal(facade.today().plusDays(2), "Lunch", online.getId(), 1);
		assertTrue(facade.recipeTitlesInUse().contains("Test Calendar Dish"));
	}

	@Test
	void copyingAMealKeepsTheOriginalAndAddsAnother() {
		MealtonFacade facade = new MealtonFacade();
		StarterRecipes.loadInto(facade);
		LocalDate day = facade.today().plusDays(1);
		RangePlanner.Draft draft = facade.draftPlan(new Options(day, day.plusDays(2), List.of("Dinner"), null, false,
				null, java.util.Set.of(), true, true));
		int before = draft.mealCount();
		PlannedMeal first = draft.meals().get(day).get(0);
		PlannedMeal copy = facade.copyDraftMeal(draft, day, 0, day.plusDays(2), -1, false);
		assertEquals(before + 1, draft.mealCount());
		assertEquals(first.recipe().getId(), copy.recipe().getId());
		assertEquals(first.recipe().getId(), draft.meals().get(day).get(0).recipe().getId(), "the original stays");

		// dropping a copy on a meal puts the recipe in that place
		facade.copyDraftMeal(draft, day, 0, day.plusDays(1), 0, true);
		assertEquals(first.recipe().getId(), draft.meals().get(day.plusDays(1)).get(0).recipe().getId());
		assertEquals(before + 1, draft.mealCount());
	}
}