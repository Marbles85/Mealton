package com.mealton.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import com.mealton.facade.MealtonFacade;
import com.mealton.pantry.PantryItem;
import com.mealton.recipe.StarterRecipes;

/**
 * The prep-day grocery list counts pantry food in a different (but compatible)
 * unit.
 */
class PrepPantryUnitsTest {
	private static final LocalDate PREP = LocalDate.of(2026, 11, 22);
	private static final LocalDate A = LocalDate.of(2026, 11, 23);

	@Test
	void pantryInKilogramsCoversARecipeInGrams() {
		MealtonFacade facade = new MealtonFacade();
		StarterRecipes.loadInto(facade);
		for (int i = 0; i < 7; i++) {
			facade.addPlannedMeal(A.plusDays(i), "Dinner", 4 + i % 3, 1);
		}
		var options = new PrepScheduler.Options(PREP, LocalTime.of(10, 0), A, A.plusDays(6), 600);
		var row = facade.buildPrepSchedule(options).ingredients().stream()
				.filter(r -> r.unit().equalsIgnoreCase("g") && r.shortBy() > 0).findFirst().orElseThrow();
		facade.addPantryItem(new PantryItem(row.name(), 50, "kg", "Uncategorized", null));
		var after = facade.buildPrepSchedule(options).ingredients().stream()
				.filter(r -> r.name().equalsIgnoreCase(row.name()) && r.unit().equalsIgnoreCase("g")).findFirst()
				.orElseThrow();
		assertEquals(0, after.shortBy(), 1e-9);
		assertTrue(after.have() >= row.need());
	}
}