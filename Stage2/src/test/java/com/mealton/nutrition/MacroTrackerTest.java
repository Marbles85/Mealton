package com.mealton.nutrition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mealton.profile.ProfileService;

class MacroTrackerTest {

	private static final LocalDate DAY = LocalDate.of(2026, 11, 12);

	private ProfileService profile;
	private MacroTracker tracker;

	@BeforeEach
	void setUp() {
		profile = new ProfileService();
		tracker = new MacroTracker(profile::get);
	}

	@Test
	void loggingATableFoodIsNotAnEstimate() {
		LoggedFood entry = tracker.logFood(DAY, "egg", 2, "");
		assertEquals(1, entry.id());
		assertFalse(entry.estimated());
		assertEquals(143, entry.nutrients().calories(), 0.01);
	}

	@Test
	void customFoodIsMarkedAsEstimateWhenAsked() {
		LoggedFood entry = tracker.logCustom(DAY, "homemade lasagne", new Nutrients(650, 35, 60, 28), true);
		assertTrue(entry.estimated());
		assertTrue(entry.toString().contains("(estimate)"));
	}

	@Test
	void unknownFoodAsksForNumbers() {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> tracker.logFood(DAY, "pizza", 1, "g"));
		assertTrue(e.getMessage().contains("not in my food table"));
		assertTrue(tracker.entriesOn(DAY).isEmpty());
	}

	@Test
	void blankDescriptionOrNameIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> tracker.logFood(DAY, " ", 1, "g"));
		assertThrows(IllegalArgumentException.class, () -> tracker.logCustom(DAY, "", Nutrients.ZERO, true));
	}

	@Test
	void entriesAreKeptPerDay() {
		tracker.logFood(DAY, "egg", 1, "");
		tracker.logFood(DAY.plusDays(1), "egg", 1, "");
		assertEquals(1, tracker.entriesOn(DAY).size());
		assertEquals(1, tracker.entriesOn(DAY.plusDays(1)).size());
		assertEquals(0, tracker.entriesOn(DAY.minusDays(1)).size());
	}

	@Test
	void progressAddsUpTheDay() {
		tracker.logFood(DAY, "chicken", 200, "g");
		tracker.logCustom(DAY, "lasagne", new Nutrients(650, 35, 60, 28), true);
		DailyProgress progress = tracker.progress(DAY);
		assertEquals(2, progress.entryCount());
		assertEquals(980, progress.total().calories(), 0.01);
		assertEquals(97, progress.total().protein(), 0.01);
	}

	@Test
	void progressComparesWithTheCurrentProfileGoals() {
		tracker.logCustom(DAY, "lunch", new Nutrients(1200, 50, 100, 40), false);
		assertNull(tracker.progress(DAY).caloriesLeft());
		assertTrue(tracker.progress(DAY).summary().contains("no goal set"));

		profile.setCalorieGoal(2000);
		profile.setMacroGoals(100, 200, 30);
		DailyProgress progress = tracker.progress(DAY);
		assertEquals(800, progress.caloriesLeft().intValue());
		String text = progress.summary();
		assertTrue(text.contains("Calories: 1200 of 2000 kcal (800 left)"));
		assertTrue(text.contains("Protein: 50 of 100 g (50 left)"));
		assertTrue(text.contains("Fat: 40 of 30 g (10 over)"));
	}

	@Test
	void emptyDayHasNoTotals() {
		assertEquals("Nothing logged for " + DAY + " yet.", tracker.progress(DAY).summary());
		assertEquals(Nutrients.ZERO, tracker.progress(DAY).total());
	}

	@Test
	void deleteRemovesOnlyThatEntry() {
		LoggedFood first = tracker.logFood(DAY, "egg", 1, "");
		tracker.logFood(DAY, "banana", 1, "");
		assertEquals(first, tracker.delete(first.id()));
		assertEquals(1, tracker.entriesOn(DAY).size());
		assertThrows(IllegalArgumentException.class, () -> tracker.delete(first.id()));
		assertThrows(IllegalArgumentException.class, () -> tracker.delete(999));
	}

	@Test
	void idsAreNotReusedAfterADelete() {
		LoggedFood first = tracker.logFood(DAY, "egg", 1, "");
		tracker.delete(first.id());
		assertEquals(2, tracker.logFood(DAY, "egg", 1, "").id());
	}
}