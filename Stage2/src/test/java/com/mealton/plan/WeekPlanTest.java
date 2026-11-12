package com.mealton.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mealton.recipe.Recipe;
import com.mealton.recipe.RecipeParser;

class WeekPlanTest {

	private WeekPlan week;
	private Recipe pancakes;
	private Recipe soup;

	private static Recipe recipe(long id, String title) {
		Recipe r = new Recipe(title, 2, RecipeParser.parseIngredients("1 onion"), null);
		r.setId(id);
		return r;
	}

	@BeforeEach
	void setUp() {
		week = new WeekPlan();
		pancakes = recipe(1, "Pancakes");
		soup = recipe(2, "Soup");
	}

	@Test
	void newWeekIsEmptyWithSevenDays() {
		assertEquals(7, week.days().size());
		assertEquals(0, week.mealCount());
		assertEquals("No meals are planned yet.", week.describe());
		assertEquals(DayOfWeek.MONDAY, week.days().get(0).day());
		assertEquals(DayOfWeek.SUNDAY, week.days().get(6).day());
	}

	@Test
	void compositeCountsMealsAcrossDays() {
		week.day(DayOfWeek.MONDAY).set(MealSlot.LUNCH, soup);
		week.day(DayOfWeek.MONDAY).set(MealSlot.DINNER, pancakes);
		week.day(DayOfWeek.FRIDAY).set(MealSlot.DINNER, pancakes);
		assertEquals(2, week.day(DayOfWeek.MONDAY).mealCount());
		assertEquals(3, week.mealCount());
		assertEquals(3, week.recipes().size());
	}

	@Test
	void settingAMealAgainReplacesIt() {
		DayPlan monday = week.day(DayOfWeek.MONDAY);
		monday.set(MealSlot.DINNER, pancakes);
		monday.set(MealSlot.DINNER, soup);
		assertEquals(1, monday.mealCount());
		assertEquals("Soup", monday.get(MealSlot.DINNER).orElseThrow().recipe().getTitle());
	}

	@Test
	void removeSaysWhetherAnythingWasRemoved() {
		DayPlan monday = week.day(DayOfWeek.MONDAY);
		monday.set(MealSlot.DINNER, pancakes);
		assertTrue(monday.remove(MealSlot.DINNER));
		assertFalse(monday.remove(MealSlot.DINNER));
		assertTrue(monday.get(MealSlot.DINNER).isEmpty());
	}

	@Test
	void timesUsedAndDistinctRecipes() {
		week.day(DayOfWeek.MONDAY).set(MealSlot.DINNER, pancakes);
		week.day(DayOfWeek.TUESDAY).set(MealSlot.DINNER, pancakes);
		week.day(DayOfWeek.WEDNESDAY).set(MealSlot.DINNER, soup);
		assertEquals(2, week.timesUsed(1));
		assertEquals(1, week.timesUsed(2));
		assertEquals(0, week.timesUsed(99));
		assertEquals(List.of(1L, 2L), week.distinctRecipes().stream().map(Recipe::getId).toList());
	}

	@Test
	void describeListsDaysAndMealsInOrder() {
		week.day(DayOfWeek.MONDAY).set(MealSlot.DINNER, pancakes);
		week.day(DayOfWeek.MONDAY).set(MealSlot.BREAKFAST, soup);
		String text = week.describe();
		assertTrue(text.startsWith("Monday\n  Breakfast: Soup (#2)\n  Dinner: Pancakes (#1)"));
		assertTrue(text.contains("Tuesday\n  (nothing planned)"));
	}

	@Test
	void mealEntryIsALeafOfTheSameInterface() {
		PlanComponent leaf = new MealEntry(MealSlot.DINNER, pancakes);
		assertEquals(1, leaf.mealCount());
		assertEquals("Dinner: Pancakes (#1)", leaf.describe());
		assertThrows(IllegalArgumentException.class, () -> new MealEntry(null, pancakes));
		assertThrows(IllegalArgumentException.class, () -> new MealEntry(MealSlot.DINNER, null));
	}

	@Test
	void mealSlotsAreReadFromText() {
		assertEquals(List.of(MealSlot.LUNCH, MealSlot.DINNER), MealSlot.listFromText(""));
		assertEquals(List.of(MealSlot.BREAKFAST, MealSlot.LUNCH, MealSlot.DINNER), MealSlot.listFromText("all"));
		assertEquals(List.of(MealSlot.LUNCH, MealSlot.DINNER), MealSlot.listFromText("dinner, Lunch"));
		assertEquals(List.of(MealSlot.DINNER), MealSlot.listFromText("dinner,dinner"));
		assertThrows(IllegalArgumentException.class, () -> MealSlot.fromText("brunch"));
	}
}