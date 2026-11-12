package com.mealton.agent.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mealton.agent.ToolResult;
import com.mealton.nutrition.MacroTracker;
import com.mealton.profile.ProfileService;

class NutritionToolTest {

	private static final LocalDate DAY = LocalDate.of(2026, 11, 12);

	private MacroTracker tracker;
	private NutritionTool tool;

	@BeforeEach
	void setUp() {
		ProfileService profile = new ProfileService();
		profile.setCalorieGoal(2000);
		tracker = new MacroTracker(profile::get);
		tool = new NutritionTool(tracker, () -> DAY);
	}

	/** What a tool call returned: whether it failed, and its text. */
	private record Out(boolean isError, String content) {
	}

	private Out run(String... keyValues) {
		Map<String, String> args = new HashMap<>();
		for (int i = 0; i < keyValues.length; i += 2) {
			args.put(keyValues[i], keyValues[i + 1]);
		}
		ToolResult result = tool.execute(args);
		return new Out(!result.success(), result.content());
	}

	@Test
	void logUsesTheTableAndReportsProgress() {
		Out result = run("action", "log", "food", "eggs", "quantity", "3");
		assertFalse(result.isError());
		assertTrue(result.content().contains("3 eggs"));
		assertTrue(result.content().contains("Calories: 215 of 2000 kcal (1786 left)"));
		assertEquals(1, tracker.entriesOn(DAY).size());
	}

	@Test
	void quantityDefaultsToOne() {
		assertFalse(run("action", "log", "food", "banana").isError());
		assertEquals(105, tracker.entriesOn(DAY).get(0).nutrients().calories(), 1.0);
	}

	@Test
	void unknownFoodIsAnErrorSoTheAgentCanEstimateInstead() {
		Out unknown = run("action", "log", "food", "pizza", "quantity", "1", "unit", "slice");
		assertTrue(unknown.isError());
		assertTrue(unknown.content().contains("not in my food table"));
		assertTrue(tracker.entriesOn(DAY).isEmpty());

		Out estimate = run("action", "log_estimate", "food", "pizza slice", "calories", "285", "protein", "12", "carbs",
				"36", "fat", "10");
		assertFalse(estimate.isError());
		assertTrue(estimate.content().contains("(estimate)"));
		assertTrue(tracker.entriesOn(DAY).get(0).estimated());
	}

	@Test
	void estimateNeedsAllFourNumbers() {
		Out missing = run("action", "log_estimate", "food", "soup", "calories", "100");
		assertTrue(missing.isError());
		assertTrue(missing.content().contains("protein"));
		Out notANumber = run("action", "log_estimate", "food", "soup", "calories", "abc", "protein", "1", "carbs", "1",
				"fat", "1");
		assertTrue(notANumber.isError());
		assertTrue(tracker.entriesOn(DAY).isEmpty());
	}

	@Test
	void badAmountsAndUnitsAreErrorsNotCrashes() {
		assertTrue(run("action", "log", "food", "bread", "quantity", "-1").isError());
		assertTrue(run("action", "log", "food", "cheese", "quantity", "1", "unit", "cup").isError());
		assertTrue(run("action", "log", "food", "egg", "quantity", "lots").isError());
	}

	@Test
	void listProgressAndDelete() {
		assertEquals("Nothing has been logged today.", run("action", "list").content());
		run("action", "log", "food", "egg", "quantity", "2");
		assertTrue(run("action", "list").content().contains("#1 2 egg"));
		assertTrue(run("action", "progress").content().contains("of 2000 kcal"));
		assertFalse(run("action", "delete", "id", "1").isError());
		assertTrue(run("action", "delete", "id", "1").isError());
		assertTrue(run("action", "delete", "id", "x").isError());
	}

	@Test
	void unknownActionIsAnError() {
		assertTrue(run("action", "fly").isError());
		assertTrue(run().isError());
	}
}