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
import com.mealton.recipe.StarterRecipes;

/** The meal calendar: the weekly plan loaded onto real dates (F03). */
class CalendarServiceTest {

	// 2026-11-09 is a Monday
	private static final LocalDate MONDAY = LocalDate.of(2026, 11, 9);
	private static final LocalDate THURSDAY = LocalDate.of(2026, 11, 12);
	private static final LocalDate NEXT_MONDAY = LocalDate.of(2026, 11, 16);

	private MealtonFacade facade;

	@BeforeEach
	void setUp() {
		facade = new MealtonFacade();
		StarterRecipes.loadInto(facade);
	}

	@Test
	void weeksStartOnMonday() {
		assertEquals(MONDAY, CalendarService.weekStart(THURSDAY));
		assertEquals(MONDAY, CalendarService.weekStart(MONDAY));
		assertEquals(MONDAY, CalendarService.weekStart(LocalDate.of(2026, 11, 15)));
		assertThrows(IllegalArgumentException.class, () -> CalendarService.weekStart(null));
	}

	@Test
	void anEmptyPlanCannotBeLoaded() {
		assertThrows(IllegalArgumentException.class, () -> facade.loadPlanOnCalendar(MONDAY));
	}

	@Test
	void loadingPutsThePlanOnTheWholeWeekFromMonday() {
		facade.generatePlan("variety", "dinner");
		assertEquals(7, facade.loadPlanOnCalendar(THURSDAY)); // any date in the week works
		assertEquals(7, facade.getCalendarMealsInWeek(MONDAY));
		for (int i = 0; i < 7; i++) {
			List<MealEntry> meals = facade.getCalendarMeals(MONDAY.plusDays(i));
			assertEquals(1, meals.size());
			assertEquals(MealSlot.DINNER, meals.get(0).slot());
			assertEquals(facade.getPlan().days().get(i).get(MealSlot.DINNER).orElseThrow().recipe().getId(),
					meals.get(0).recipe().getId());
		}
		assertTrue(facade.getCalendarMeals(NEXT_MONDAY).isEmpty());
	}

	@Test
	void loadingAgainReplacesThatWeekButLeavesOtherWeeks() {
		facade.generatePlan("variety", "dinner");
		facade.loadPlanOnCalendar(MONDAY);
		facade.loadPlanOnCalendar(NEXT_MONDAY);
		assertEquals(7, facade.getCalendarMealsInWeek(MONDAY));
		assertEquals(7, facade.getCalendarMealsInWeek(NEXT_MONDAY));
		facade.generatePlan("variety", "lunch,dinner");
		facade.loadPlanOnCalendar(MONDAY);
		assertEquals(14, facade.getCalendarMealsInWeek(MONDAY));
		assertEquals(7, facade.getCalendarMealsInWeek(NEXT_MONDAY));
	}

	@Test
	void changingThePlanLaterDoesNotChangeWhatIsAlreadyOnTheCalendar() {
		facade.generatePlan("variety", "dinner");
		facade.loadPlanOnCalendar(MONDAY);
		facade.clearPlan();
		assertEquals(7, facade.getCalendarMealsInWeek(MONDAY));
	}

	@Test
	void aSingleMealCanBeSetAndCleared() {
		facade.setCalendarMeal(THURSDAY, "breakfast", 8);
		List<MealEntry> meals = facade.getCalendarMeals(THURSDAY);
		assertEquals(1, meals.size());
		assertEquals("Banana Oatmeal", meals.get(0).recipe().getTitle());
		facade.clearCalendarMeal(THURSDAY, "breakfast");
		assertTrue(facade.getCalendarMeals(THURSDAY).isEmpty());
	}

	@Test
	void mealsAreListedBreakfastFirst() {
		facade.setCalendarMeal(THURSDAY, "dinner", 1);
		facade.setCalendarMeal(THURSDAY, "breakfast", 8);
		facade.setCalendarMeal(THURSDAY, "lunch", 2);
		List<MealEntry> meals = facade.getCalendarMeals(THURSDAY);
		assertEquals(MealSlot.BREAKFAST, meals.get(0).slot());
		assertEquals(MealSlot.LUNCH, meals.get(1).slot());
		assertEquals(MealSlot.DINNER, meals.get(2).slot());
	}

	@Test
	void settingAnUnknownRecipeOrMealGivesAClearError() {
		assertThrows(IllegalArgumentException.class, () -> facade.setCalendarMeal(THURSDAY, "dinner", 999));
		assertThrows(IllegalArgumentException.class, () -> facade.setCalendarMeal(THURSDAY, "brunch", 1));
		assertThrows(IllegalArgumentException.class, () -> facade.setCalendarMeal(null, "dinner", 1));
	}

	@Test
	void clearingAnEmptyMealGivesAClearError() {
		assertThrows(IllegalArgumentException.class, () -> facade.clearCalendarMeal(THURSDAY, "dinner"));
	}

	@Test
	void clearingAWeekRemovesOnlyThatWeek() {
		facade.generatePlan("variety", "dinner");
		facade.loadPlanOnCalendar(MONDAY);
		facade.loadPlanOnCalendar(NEXT_MONDAY);
		assertEquals(7, facade.clearCalendarWeek(THURSDAY));
		assertEquals(0, facade.getCalendarMealsInWeek(MONDAY));
		assertEquals(7, facade.getCalendarMealsInWeek(NEXT_MONDAY));
	}

	@Test
	void warningsListDietAndAllergyClashesOnADate() {
		facade.setCalendarMeal(THURSDAY, "dinner", 3); // Chicken Fried Rice
		assertTrue(facade.getCalendarWarnings(THURSDAY).isEmpty());
		facade.setDiet("vegetarian");
		assertFalse(facade.getCalendarWarnings(THURSDAY).isEmpty());
	}

	@Test
	void shoppingForAWeekAddsWhatIsMissing() {
		facade.setCalendarMeal(THURSDAY, "dinner", 5); // Cheese Omelette
		assertFalse(facade.addCalendarWeekToShoppingList(MONDAY).isEmpty());
		assertFalse(facade.getShoppingItems().isEmpty());
	}

	@Test
	void shoppingForAnEmptyWeekGivesAClearError() {
		assertThrows(IllegalArgumentException.class, () -> facade.addCalendarWeekToShoppingList(MONDAY));
	}

	@Test
	void aDeletedRecipeDisappearsFromTheCalendar() {
		facade.setCalendarMeal(THURSDAY, "dinner", 5);
		facade.deleteRecipe(5);
		assertTrue(facade.getCalendarMeals(THURSDAY).isEmpty());
		assertEquals(0, facade.getCalendarMealsInWeek(THURSDAY));
	}
}