package com.mealton.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mealton.facade.MealtonFacade;
import com.mealton.persistence.UserData;
import com.mealton.persistence.UserDataMapper;
import com.mealton.recipe.Recipe;
import com.mealton.recipe.StarterRecipes;

class PrepCalendarTest {
	private static final LocalDate D = LocalDate.of(2026, 11, 22);
	private MealtonFacade facade;

	@BeforeEach
	void setUp() {
		facade = new MealtonFacade();
		StarterRecipes.loadInto(facade);
		facade.addPlannedMeal(D.plusDays(1), "Lunch", 1, 1);
		facade.addPlannedMeal(D.plusDays(1), "Dinner", 2, 1);
	}

	private PrepScheduler.Schedule schedule() {
		return facade.buildPrepSchedule(
				new PrepScheduler.Options(D, LocalTime.of(10, 0), D.plusDays(1), D.plusDays(1), 180));
	}

	@Test
	void aPrepDayCanBeAddedRemovedAndUndone() {
		facade.savePrepDay(schedule());
		assertNotNull(facade.getPrepDay(D));
		assertEquals(LocalTime.of(10, 0), facade.getPrepDay(D).start());
		assertTrue(facade.removePrepDay(D));
		assertNull(facade.getPrepDay(D));
		assertFalse(facade.removePrepDay(D));
		assertTrue(facade.undoCalendar());
		assertNotNull(facade.getPrepDay(D));
		assertTrue(facade.undoCalendar());
		assertNull(facade.getPrepDay(D));
	}

	@Test
	void editingAPrepDayMovesItInsteadOfAddingASecondOne() {
		facade.savePrepDay(schedule());
		LocalDate other = D.minusDays(1);
		var moved = facade.buildPrepSchedule(
				new PrepScheduler.Options(other, LocalTime.of(9, 0), D.plusDays(1), D.plusDays(1), 180));
		facade.movePrepDay(D, moved);
		assertNull(facade.getPrepDay(D), "the old date is free again");
		assertNotNull(facade.getPrepDay(other));
		assertEquals(LocalTime.of(9, 0), facade.getPrepDay(other).start());
		assertEquals(1, facade.getPrepDays().size());
		assertTrue(facade.undoCalendar(), "one Undo puts it back where it was");
		assertNotNull(facade.getPrepDay(D));
		assertNull(facade.getPrepDay(other));
	}

	@Test
	void aPrepDayIsSavedAndLoadedAgain() {
		facade.savePrepDay(schedule());
		UserData data = UserDataMapper.capture(facade);
		assertEquals(1, data.prepDays().size());
		MealtonFacade other = new MealtonFacade();
		StarterRecipes.loadInto(other);
		assertTrue(UserDataMapper.restore(other, data).isEmpty());
		assertEquals(facade.getPrepDay(D), other.getPrepDay(D));
		assertFalse(other.canUndoCalendar());
	}

	@Test
	void oldFilesWithoutPrepDaysStillLoad() {
		UserData data = UserDataMapper.capture(facade);
		UserData old = new UserData(data.version(), data.profile(), data.pantry(), data.shopping(),
				data.libraryRecipes(), data.ownRecipes(), data.plan(), data.calendar(), data.diary(), data.mealTags(),
				data.categories());
		assertNull(old.prepDays());
		MealtonFacade other = new MealtonFacade();
		StarterRecipes.loadInto(other);
		assertTrue(UserDataMapper.restore(other, old).isEmpty());
	}

	@Test
	void swapForSuggestionPicksADifferentRecipeAndCanBeUndone() {
		Recipe before = facade.getPlannedMeals(D.plusDays(1)).get(0).recipe();
		Recipe now = facade.swapForSuggestion(D.plusDays(1), 0);
		assertNotEquals(before.getId(), now.getId());
		assertEquals(now.getId(), facade.getPlannedMeals(D.plusDays(1)).get(0).recipe().getId());
		assertEquals("Lunch", facade.getPlannedMeals(D.plusDays(1)).get(0).tag());
		assertTrue(facade.undoCalendar());
		assertEquals(before.getId(), facade.getPlannedMeals(D.plusDays(1)).get(0).recipe().getId());
		assertThrows(IllegalArgumentException.class, () -> facade.swapForSuggestion(D.plusDays(1), 9));
	}

	@Test
	void anUndoCanBeRedoneUntilANewChangeIsMade() {
		Recipe before = facade.getPlannedMeals(D.plusDays(1)).get(0).recipe();
		facade.swapForSuggestion(D.plusDays(1), 0);
		long swapped = facade.getPlannedMeals(D.plusDays(1)).get(0).recipe().getId();
		assertFalse(facade.canRedoCalendar());
		assertTrue(facade.undoCalendar());
		assertEquals(before.getId(), facade.getPlannedMeals(D.plusDays(1)).get(0).recipe().getId());
		assertTrue(facade.canRedoCalendar());
		assertTrue(facade.redoCalendar());
		assertEquals(swapped, facade.getPlannedMeals(D.plusDays(1)).get(0).recipe().getId());
		assertTrue(facade.undoCalendar());
		facade.addPlannedMeal(D, "Lunch", 3, 1); // a new change
		assertFalse(facade.canRedoCalendar());
		assertFalse(facade.redoCalendar());
	}

	@Test
	void tagsCanBeRenamedAndDeleted() {
		facade.addMealTag("Pre-game");
		facade.addPlannedMeal(D, "Pre-game", 1, 1);
		assertEquals(1, facade.renameMealTag("Pre-game", "Pre-match"));
		assertEquals("Pre-match", facade.getPlannedMeals(D).get(0).tag());
		assertTrue(facade.getMealTags().contains("Pre-match"));
		assertFalse(facade.getMealTags().contains("Pre-game"));
		assertThrows(IllegalArgumentException.class, () -> facade.renameMealTag("Lunch", "Brunch"));
		assertThrows(IllegalArgumentException.class, () -> facade.renameMealTag("Pre-match", "dinner"));
		assertThrows(IllegalArgumentException.class, () -> facade.renameMealTag("Pre-match", "  "));
		assertEquals(1, facade.deleteMealTag("Pre-match"));
		assertEquals("", facade.getPlannedMeals(D).get(0).tag());
		assertFalse(facade.getMealTags().contains("Pre-match"));
		assertThrows(IllegalArgumentException.class, () -> facade.deleteMealTag("Dinner"));
		assertTrue(facade.undoCalendar()); // the meals get their tag back
		assertEquals("Pre-match", facade.getPlannedMeals(D).get(0).tag());
	}
}