package com.mealton.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mealton.facade.MealtonFacade;
import com.mealton.recipe.StarterRecipes;

class PrepSchedulerTest {
	private static final LocalDate PREP = LocalDate.of(2026, 11, 22);
	private static final LocalDate A = LocalDate.of(2026, 11, 23);
	private static final LocalDate B = LocalDate.of(2026, 11, 29);

	private MealtonFacade facade;

	@BeforeEach
	void setUp() {
		facade = new MealtonFacade();
		StarterRecipes.loadInto(facade);
		for (int i = 0; i < 7; i++) {
			facade.addPlannedMeal(A.plusDays(i), "Lunch", 1 + i % 3, 1);
			facade.addPlannedMeal(A.plusDays(i), "Dinner", 4 + i % 3, 1);
		}
	}

	private PrepScheduler.Options options(int limit) {
		return new PrepScheduler.Options(PREP, LocalTime.of(10, 0), A, B, limit);
	}

	@Test
	void validatesDates() {
		assertEquals("Pick both dates.", PrepScheduler.error(PREP, null, B));
		assertTrue(PrepScheduler.error(PREP, B, A).startsWith("The end date"));
		assertTrue(PrepScheduler.error(PREP, A, A.plusDays(14)).startsWith("Prep covers"));
		assertTrue(PrepScheduler.error(A.plusDays(1), A, B).startsWith("Your prep day"));
		assertEquals("", PrepScheduler.error(PREP, A, B));
	}

	@Test
	void emptyRangeIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> facade.buildPrepSchedule(
				new PrepScheduler.Options(PREP, LocalTime.of(10, 0), A.plusDays(30), A.plusDays(32), 120)));
	}

	@Test
	void lanesNeverOverlapAndTasksStartAfterZero() {
		var s = facade.buildPrepSchedule(options(600));
		for (String lane : PrepScheduler.LANES) {
			var t = s.tasks().stream().filter(x -> x.lane().equals(lane))
					.sorted(java.util.Comparator.comparingInt(PrepScheduler.Task::startMinute)).toList();
			for (int i = 1; i < t.size(); i++) {
				assertTrue(t.get(i).startMinute() >= t.get(i - 1).endMinute(), lane + " overlaps");
			}
		}
		assertTrue(s.tasks().stream().allMatch(x -> x.startMinute() >= 0 && x.minutes() > 0));
		assertTrue(s.leftOut().isEmpty());
		assertTrue(s.fits());
	}

	@Test
	void aShortLimitLeavesRecipesOutButKeepsAtLeastOne() {
		var full = facade.buildPrepSchedule(options(600));
		var tight = facade.buildPrepSchedule(options(30));
		assertFalse(tight.leftOut().isEmpty());
		assertTrue(tight.totalMinutes() <= full.totalMinutes());
		assertTrue(tight.meals().stream().anyMatch(m -> !m.fresh()));
		assertEquals(tight.leftOut().size(), tight.meals().stream().filter(m -> m.fresh()).count());
	}

	@Test
	void laterDaysAreFrozen() {
		var s = facade.buildPrepSchedule(options(600));
		assertTrue(s.meals().stream().mapToInt(m -> m.frozen()).sum() > 0);
	}

	@Test
	void shortIngredientsGoOnTheShoppingListOnce() {
		var s = facade.buildPrepSchedule(options(600));
		var ids = facade.addPrepShortToShoppingList(s);
		assertEquals(s.shortRows().size(), ids.size());
		assertTrue(facade.addPrepShortToShoppingList(s).isEmpty());
		assertEquals(ids.size(), facade.deleteShoppingItems(ids));
	}
}