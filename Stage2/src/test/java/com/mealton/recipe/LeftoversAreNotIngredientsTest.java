package com.mealton.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import com.mealton.diary.MealLogService;
import com.mealton.facade.MealtonFacade;
import com.mealton.pantry.PantryItem;
import com.mealton.plan.PrepScheduler;

/**
 * A saved leftover meal in the pantry never counts as an ingredient someone
 * has.
 */
class LeftoversAreNotIngredientsTest {
	@Test
	void aLeftoverNamedAfterADishDoesNotCoverItsWords() {
		MealtonFacade facade = new MealtonFacade();
		StarterRecipes.loadInto(facade);
		LocalDate a = LocalDate.of(2026, 11, 23);
		for (int i = 0; i < 7; i++) {
			facade.addPlannedMeal(a.plusDays(i), "Dinner", 4 + i % 3, 1);
		}
		var options = new PrepScheduler.Options(LocalDate.of(2026, 11, 22), LocalTime.of(10, 0), a, a.plusDays(6), 600);
		var before = facade.buildPrepSchedule(options).ingredients().stream().filter(r -> r.shortBy() > 0).toList();
		assertTrue(!before.isEmpty());
		var first = before.get(0);
		facade.addPantryItem(new PantryItem(first.name() + " stuffed peppers", 50, first.unit(),
				MealLogService.LEFTOVER_CATEGORY, facade.today().plusDays(3)));
		var after = facade.buildPrepSchedule(options).ingredients().stream()
				.filter(r -> r.name().equals(first.name()) && r.unit().equals(first.unit())).findFirst().orElseThrow();
		assertEquals(first.shortBy(), after.shortBy(), 1e-9);
	}
}