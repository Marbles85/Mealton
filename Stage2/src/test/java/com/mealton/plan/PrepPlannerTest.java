package com.mealton.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mealton.plan.PrepPlanner.PrepItem;
import com.mealton.plan.PrepPlanner.PrepPlan;
import com.mealton.recipe.Recipe;
import com.mealton.recipe.RecipeIngredient;

/** The prep-day planner (F05). */
class PrepPlannerTest {

	private static Recipe recipe(long id, String title, int servings, RecipeIngredient... ingredients) {
		Recipe r = new Recipe(title, servings, List.of(ingredients), null);
		r.setId(id);
		return r;
	}

	private static PrepItem find(PrepPlan plan, String name) {
		return plan.ingredients().stream().filter(i -> i.name().equalsIgnoreCase(name)).findFirst().orElseThrow();
	}

	@Test
	void anEmptyPlanCannotBePrepared() {
		assertThrows(IllegalArgumentException.class, () -> PrepPlanner.build(new WeekPlan()));
	}

	@Test
	void sameIngredientAndUnitFromTwoRecipesIsAddedUp() {
		Recipe soup = recipe(1, "Soup", 2, new RecipeIngredient("Onion", 1, ""),
				new RecipeIngredient("flour", 100, "g"));
		Recipe stew = recipe(2, "Stew", 2, new RecipeIngredient("onion", 2, ""));
		WeekPlan plan = new WeekPlan();
		plan.day(DayOfWeek.MONDAY).set(MealSlot.DINNER, soup);
		plan.day(DayOfWeek.TUESDAY).set(MealSlot.DINNER, stew);

		PrepPlan prep = PrepPlanner.build(plan);
		assertEquals(3, find(prep, "onion").quantity(), 0.0001);
		assertEquals(List.of("Soup", "Stew"), find(prep, "onion").usedIn());
		assertEquals(100, find(prep, "flour").quantity(), 0.0001);
		assertEquals(2, prep.meals());
		assertEquals(2, prep.recipes());
	}

	@Test
	void differentUnitsStayOnSeparateLines() {
		Recipe a = recipe(1, "A", 1, new RecipeIngredient("tomato", 2, ""));
		Recipe b = recipe(2, "B", 1, new RecipeIngredient("tomato", 400, "g"));
		WeekPlan plan = new WeekPlan();
		plan.day(DayOfWeek.MONDAY).set(MealSlot.DINNER, a);
		plan.day(DayOfWeek.TUESDAY).set(MealSlot.DINNER, b);
		long tomatoLines = PrepPlanner.build(plan).ingredients().stream().filter(i -> i.name().equals("tomato"))
				.count();
		assertEquals(2, tomatoLines);
	}

	@Test
	void aRecipePlannedTwiceCountsTwiceAndSuggestsBatchCooking() {
		Recipe soup = recipe(1, "Soup", 4, new RecipeIngredient("lentils", 250, "g"));
		WeekPlan plan = new WeekPlan();
		plan.day(DayOfWeek.MONDAY).set(MealSlot.DINNER, soup);
		plan.day(DayOfWeek.THURSDAY).set(MealSlot.DINNER, soup);

		PrepPlan prep = PrepPlanner.build(plan);
		assertEquals(500, find(prep, "lentils").quantity(), 0.0001);
		assertEquals(1, prep.cookAhead().size());
		assertTrue(prep.cookAhead().get(0).contains("Soup is planned 2 times"));
		assertTrue(prep.cookAhead().get(0).contains("8 servings"));
	}

	@Test
	void sharedIngredientsAreFlaggedOnlyWhenSeveralRecipesUseThem() {
		Recipe a = recipe(1, "A", 1, new RecipeIngredient("onion", 1, ""), new RecipeIngredient("rice", 100, "g"));
		Recipe b = recipe(2, "B", 1, new RecipeIngredient("onion", 1, ""));
		WeekPlan plan = new WeekPlan();
		plan.day(DayOfWeek.MONDAY).set(MealSlot.DINNER, a);
		plan.day(DayOfWeek.TUESDAY).set(MealSlot.DINNER, b);

		PrepPlan prep = PrepPlanner.build(plan);
		assertEquals(1, prep.sharedPrep().size());
		assertTrue(prep.sharedPrep().get(0).contains("onion"));
		assertTrue(prep.cookAhead().isEmpty());
	}

	@Test
	void theTextMentionsEveryPart() {
		Recipe a = recipe(1, "A", 2, new RecipeIngredient("onion", 1, ""));
		Recipe b = recipe(2, "B", 2, new RecipeIngredient("onion", 1, ""));
		WeekPlan plan = new WeekPlan();
		plan.day(DayOfWeek.MONDAY).set(MealSlot.DINNER, a);
		plan.day(DayOfWeek.TUESDAY).set(MealSlot.DINNER, b);
		plan.day(DayOfWeek.WEDNESDAY).set(MealSlot.DINNER, a);

		String text = PrepPlanner.build(plan).describe();
		assertTrue(text.contains("Everything the week needs"));
		assertTrue(text.contains("Shared by several recipes"));
		assertTrue(text.contains("Cook ahead"));
		assertFalse(text.isBlank());
	}
}