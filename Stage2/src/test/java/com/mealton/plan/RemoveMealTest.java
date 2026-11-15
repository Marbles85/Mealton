package com.mealton.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Removing a meal from the preview and refilling the empty slot on regenerate.
 */
class RemoveMealTest {
	private MealtonFacade facade;
	private LocalDate today;

	@BeforeEach
	void setUp() {
		facade = new MealtonFacade();
		StarterRecipes.loadInto(facade);
		today = facade.today();
	}

	private Draft draft() {
		return facade.draftPlan(new Options(today, today.plusDays(1), List.of("Breakfast", "Lunch", "Dinner"), null,
				false, null, Set.of(), true, true));
	}

	@Test
	void removedMealLeavesAGapThatRegenerateFills() {
		Draft d = draft();
		assertEquals(0, facade.missingDraftMeals(d));
		int lunch = indexOf(d, today, "Lunch");
		facade.removeDraftMeal(d, today, lunch);
		assertEquals(5, d.mealCount());
		assertEquals(1, facade.missingDraftMeals(d));
		// keep everything that is left: only the gap is filled
		java.util.Set<String> kept = new java.util.HashSet<>();
		for (LocalDate day : d.meals().keySet()) {
			for (int i = 0; i < d.meals().get(day).size(); i++) {
				kept.add(RangePlanner.key(day, i));
			}
		}
		var before = d.meals().get(today).stream().map(m -> m.recipe().getId()).toList();
		facade.regenerateDraft(d, kept);
		assertEquals(6, d.mealCount());
		assertEquals(0, facade.missingDraftMeals(d));
		List<String> tags = d.meals().get(today).stream().map(m -> m.tag()).toList();
		assertEquals(List.of("Breakfast", "Lunch", "Dinner"), tags);
		assertTrue(before.contains(d.meals().get(today).get(0).recipe().getId()));
	}

	@Test
	void aDoubledMealStaysUntilItIsRemoved() {
		Draft d = draft();
		facade.copyDraftMeal(d, today, indexOf(d, today, "Lunch"), today, -1, false);
		assertEquals(7, d.mealCount());
		facade.removeDraftMeal(d, today, indexOf(d, today, "Lunch"));
		assertEquals(6, d.mealCount());
		assertEquals(0, facade.missingDraftMeals(d));
	}

	private static int indexOf(Draft d, LocalDate day, String tag) {
		List<PlannedMeal> list = d.meals().get(day);
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i).tag().equals(tag)) {
				return i;
			}
		}
		throw new IllegalStateException(tag);
	}
}