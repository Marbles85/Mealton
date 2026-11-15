package com.mealton.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mealton.facade.MealtonFacade;
import com.mealton.plan.RangePlanner.Draft;
import com.mealton.plan.RangePlanner.Options;
import com.mealton.recipe.StarterRecipes;

/**
 * Plan meals: a draft for any dates that only reaches the calendar when
 * applied.
 */
class RangePlannerTest {
	private MealtonFacade facade;
	private LocalDate today;

	@BeforeEach
	void setUp() {
		facade = new MealtonFacade();
		StarterRecipes.loadInto(facade);
		today = facade.today();
	}

	private Options options(LocalDate start, LocalDate end, List<String> tags) {
		return new Options(start, end, tags, null, false, null, Set.of(), true, true);
	}

	@Test
	void rangeErrorsUseTheMockupWording() {
		assertEquals("Pick both dates.", RangePlanner.rangeError(null, today));
		assertEquals("The end date has to be on or after the start date.",
				RangePlanner.rangeError(today, today.minusDays(1)));
		assertTrue(RangePlanner.rangeError(today, today.plusDays(28)).startsWith("Mealton can plan up to 4 weeks"));
		assertEquals("", RangePlanner.rangeError(today, today.plusDays(27)));
		assertEquals(7, RangePlanner.days(today, today.plusDays(6)));
	}

	@Test
	void aDraftHasOneMealPerTagPerDayAndDoesNotTouchTheCalendar() {
		Draft d = facade.draftPlan(options(today, today.plusDays(6), RangePlanner.TAGS));
		assertEquals(21, d.mealCount());
		assertEquals(7, d.meals().size());
		assertTrue(facade.getPlannedMeals(today).isEmpty());
		assertFalse(d.steps().isEmpty());
	}

	@Test
	void pastDaysAreSkipped() {
		LocalDate start = today.minusDays(3);
		assertEquals(3, RangePlanner.pastDays(start, today.plusDays(3), today));
		Draft d = facade.draftPlan(options(start, today.plusDays(3), List.of("Dinner")));
		assertEquals(4, d.meals().size());
		assertTrue(d.meals().keySet().stream().noneMatch(x -> x.isBefore(today)));
		assertThrows(IllegalArgumentException.class,
				() -> facade.draftPlan(options(today.minusDays(5), today.minusDays(2), List.of("Dinner"))));
	}

	@Test
	void recipesThatClashWithTheProfileAreNeverPlanned() {
		facade.setDiet("vegetarian");
		Draft d = facade.draftPlan(options(today, today.plusDays(13), RangePlanner.TAGS));
		for (List<PlannedMeal> day : d.meals().values()) {
			for (PlannedMeal m : day) {
				assertTrue(com.mealton.recipe.ProfileCheck.conflicts(m.recipe(), facade.getProfile()).isEmpty());
			}
		}
	}

	@Test
	void applyingPutsTheDraftOnTheCalendarAsOneUndoableStep() {
		Draft d = facade.draftPlan(options(today, today.plusDays(2), List.of("Lunch", "Dinner")));
		assertEquals(6, facade.applyDraft(d));
		assertEquals(2, facade.getPlannedMeals(today).size());
		assertEquals("Lunch", facade.getPlannedMeals(today).get(0).tag());
		assertTrue(facade.undoCalendar());
		assertTrue(facade.getPlannedMeals(today).isEmpty());
	}

	@Test
	void applyingKeepsMealsWithOtherTags() {
		facade.addPlannedMeal(today, "Post-workout", 1, 1);
		Draft d = facade.draftPlan(options(today, today, List.of("Dinner")));
		facade.applyDraft(d);
		assertEquals(2, facade.getPlannedMeals(today).size());
	}

	@Test
	void aKeptMealSurvivesRegeneratingTheRest() {
		Draft d = facade.draftPlan(options(today, today.plusDays(3), List.of("Dinner")));
		PlannedMeal first = d.meals().get(today).get(0);
		facade.regenerateDraft(d, Set.of(RangePlanner.key(today, 0)));
		assertEquals(first.recipe().getId(), d.meals().get(today).get(0).recipe().getId());
		assertEquals(4, d.mealCount());
	}

	@Test
	void rerollingGivesADifferentRecipe() {
		Draft d = facade.draftPlan(options(today, today, List.of("Dinner")));
		long before = d.meals().get(today).get(0).recipe().getId();
		facade.rerollDraftMeal(d, today, 0);
		assertNotEquals(before, d.meals().get(today).get(0).recipe().getId());
		assertThrows(IllegalArgumentException.class, () -> facade.rerollDraftMeal(d, today, 5));
	}

	@Test
	void shoppingForADraftAddsWhatIsMissing() {
		Draft d = facade.draftPlan(options(today, today.plusDays(2), RangePlanner.TAGS));
		assertTrue(d.toBuy() > 0);
		assertTrue(facade.addDraftToShoppingList(d) > 0);
		assertFalse(facade.getShoppingItems().isEmpty());
	}

	@Test
	void atLeastOneMealMustBePicked() {
		assertThrows(IllegalArgumentException.class, () -> facade.draftPlan(options(today, today, List.of())));
	}

	@Test
	void draftMealsCanBeSwappedAndMovedFreely() {
		Draft d = facade.draftPlan(options(today, today.plusDays(2), RangePlanner.TAGS));
		LocalDate a = today;
		LocalDate b = today.plusDays(1);
		var ra = d.meals().get(a).get(0).recipe();
		var rb = d.meals().get(b).get(2).recipe();
		facade.swapDraftMeals(d, a, 0, b, 2);
		assertEquals(rb.getId(), d.meals().get(a).get(0).recipe().getId());
		assertEquals(ra.getId(), d.meals().get(b).get(2).recipe().getId());
		assertEquals("Breakfast", d.meals().get(a).get(0).tag(), "each place keeps its tag");
		assertEquals("Dinner", d.meals().get(b).get(2).tag());

		int before = d.mealCount();
		var moving = d.meals().get(a).get(1); // lunch
		facade.moveDraftMeal(d, a, 1, b, 0);
		assertEquals(before, d.mealCount());
		assertEquals(2, d.meals().get(a).size());
		assertEquals(4, d.meals().get(b).size());
		assertEquals(moving.recipe().getId(), d.meals().get(b).get(0).recipe().getId());
		assertEquals("Lunch", d.meals().get(b).get(0).tag(), "the meal keeps its tag");

		facade.moveDraftMeal(d, b, 0, a, -1); // back, in time order
		assertEquals(3, d.meals().get(a).size());
		assertEquals(List.of("Breakfast", "Lunch", "Dinner"), d.meals().get(a).stream().map(m -> m.tag()).toList());

		// within one day: drop the breakfast after the dinner
		facade.moveDraftMeal(d, a, 0, a, 3);
		assertEquals("Dinner", d.meals().get(a).get(1).tag());
		assertEquals("Breakfast", d.meals().get(a).get(2).tag());
		assertEquals(3, d.meals().get(a).size());

		assertThrows(IllegalArgumentException.class, () -> facade.moveDraftMeal(d, a, 0, today.plusDays(9), -1));
		assertThrows(IllegalArgumentException.class, () -> facade.swapDraftMeals(d, a, 9, b, 0));
		assertTrue(facade.getPlannedMeals(today).isEmpty(), "the calendar is untouched until Apply");
	}
}