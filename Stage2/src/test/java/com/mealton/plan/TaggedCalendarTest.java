package com.mealton.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mealton.facade.MealtonFacade;
import com.mealton.persistence.UserData;
import com.mealton.persistence.UserDataMapper;
import com.mealton.recipe.StarterRecipes;

/**
 * The calendar page's meals: tags, order, moving, swapping, undo and saving.
 */
class TaggedCalendarTest {
	private static final LocalDate D1 = LocalDate.of(2026, 11, 17);
	private static final LocalDate D2 = LocalDate.of(2026, 11, 18);

	private MealtonFacade facade;

	@BeforeEach
	void setUp() {
		facade = new MealtonFacade();
		StarterRecipes.loadInto(facade);
	}

	private List<String> tags(LocalDate d) {
		return facade.getPlannedMeals(d).stream().map(PlannedMeal::tag).toList();
	}

	@Test
	void mealsStayInTimeOrderAndCustomTagsWork() {
		facade.addPlannedMeal(D1, "dinner", 1, 1);
		facade.addPlannedMeal(D1, "Post-workout", 2, 1);
		facade.addPlannedMeal(D1, "BREAKFAST", 8, 2);
		facade.addPlannedMeal(D1, "", 3, 1);
		assertEquals(List.of("Breakfast", "Post-workout", "Dinner", ""), tags(D1));
		assertEquals(2, facade.getPlannedMeals(D1).get(0).servings());
	}

	@Test
	void twoMealsCanShareATag() {
		facade.addPlannedMeal(D1, "Dinner", 1, 1);
		facade.addPlannedMeal(D1, "Dinner", 2, 1);
		assertEquals(2, facade.getPlannedMeals(D1).size());
		assertEquals(1, facade.getCalendarMeals(D1).size()); // the older view shows one per slot
	}

	@Test
	void aTagCanBeChangedAndTooLongIsRejected() {
		facade.addPlannedMeal(D1, "Dinner", 1, 1);
		facade.setPlannedMealTag(D1, 0, "Late snack");
		assertEquals("Late snack", facade.getPlannedMeals(D1).get(0).tag());
		assertThrows(IllegalArgumentException.class, () -> facade.setPlannedMealTag(D1, 0, "x".repeat(21)));
	}

	@Test
	void movingKeepsTheTagAndCanSlotBetweenMeals() {
		facade.addPlannedMeal(D1, "Breakfast", 8, 1);
		facade.addPlannedMeal(D1, "Dinner", 1, 1);
		facade.addPlannedMeal(D2, "Lunch", 2, 1);
		facade.movePlannedMeal(D2, 0, D1, 1); // between breakfast and dinner
		assertEquals(List.of("Breakfast", "Lunch", "Dinner"), tags(D1));
		assertTrue(facade.getPlannedMeals(D2).isEmpty());
		assertTrue(facade.getCalendarDates().contains(D1));
		assertFalse(facade.getCalendarDates().contains(D2));
	}

	@Test
	void swappingExchangesRecipesButNotTags() {
		facade.addPlannedMeal(D1, "Breakfast", 8, 1);
		facade.addPlannedMeal(D2, "Dinner", 1, 1);
		long a = facade.getPlannedMeals(D1).get(0).recipe().getId();
		long b = facade.getPlannedMeals(D2).get(0).recipe().getId();
		facade.swapPlannedMeals(D1, 0, D2, 0);
		assertEquals(b, facade.getPlannedMeals(D1).get(0).recipe().getId());
		assertEquals("Breakfast", facade.getPlannedMeals(D1).get(0).tag());
		assertEquals(a, facade.getPlannedMeals(D2).get(0).recipe().getId());
	}

	@Test
	void everyChangeCanBeUndone() {
		facade.addPlannedMeal(D1, "Dinner", 1, 1);
		facade.addPlannedMeal(D1, "Lunch", 2, 1);
		facade.removePlannedMeal(D1, 0);
		assertEquals(1, facade.getPlannedMeals(D1).size());
		assertTrue(facade.undoCalendar());
		assertEquals(2, facade.getPlannedMeals(D1).size());
		assertTrue(facade.undoCalendar());
		assertTrue(facade.undoCalendar());
		assertTrue(facade.getPlannedMeals(D1).isEmpty());
		assertFalse(facade.undoCalendar());
	}

	@Test
	void badIndexAndRecipeGiveClearErrors() {
		assertThrows(IllegalArgumentException.class, () -> facade.removePlannedMeal(D1, 0));
		assertThrows(IllegalArgumentException.class, () -> facade.addPlannedMeal(D1, "Dinner", 999, 1));
		assertThrows(IllegalArgumentException.class, () -> facade.addPlannedMeal(D1, "Dinner", 1, 0));
	}

	@Test
	void tagsOrderAndServingsSurviveSaveAndLoad() {
		facade.addPlannedMeal(D1, "Post-workout", 2, 3);
		facade.addPlannedMeal(D1, "Dinner", 1, 1);
		facade.addPlannedMeal(D1, "Dinner", 5, 2);
		UserData data = UserDataMapper.capture(facade);
		MealtonFacade again = new MealtonFacade();
		StarterRecipes.loadInto(again);
		UserDataMapper.restore(again, data);
		assertEquals(tags(D1), again.getPlannedMeals(D1).stream().map(PlannedMeal::tag).toList());
		assertEquals(3, again.getPlannedMeals(D1).get(0).servings());
		assertFalse(again.canUndoCalendar());
	}
}