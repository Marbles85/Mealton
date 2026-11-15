package com.mealton.agent.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mealton.agent.ToolResult;
import com.mealton.facade.MealtonFacade;
import com.mealton.recipe.StarterRecipes;

/**
 * The agent can see and change the calendar, prep days and the plan generator
 * through the same facade as the GUI.
 */
class CalendarPrepToolsTest {
	private MealtonFacade facade;
	private CalendarTool calendar;
	private PrepDayTool prep;
	private PlanMealsTool plan;
	private LocalDate day;

	@BeforeEach
	void setUp() {
		facade = new MealtonFacade();
		StarterRecipes.loadInto(facade);
		calendar = new CalendarTool(facade);
		prep = new PrepDayTool(facade);
		plan = new PlanMealsTool(facade);
		day = facade.today().plusDays(2);
	}

	private ToolResult run(com.mealton.agent.Tool t, String... kv) {
		java.util.Map<String, String> m = new java.util.HashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			m.put(kv[i], kv[i + 1]);
		}
		return t.execute(m);
	}

	@Test
	void theAgentCanAddViewMoveSwapAndUndoMeals() {
		assertTrue(run(calendar, "action", "add", "date", day.toString(), "recipe", "1", "tag", "Lunch").success());
		assertTrue(
				run(calendar, "action", "add", "date", day.toString(), "recipe", "2", "tag", "Dinner", "servings", "2")
						.success());
		assertEquals(2, facade.getPlannedMeals(day).size());
		String view = run(calendar, "action", "view", "from", day.toString(), "to", day.toString()).content();
		assertTrue(view.contains("[0] Lunch") && view.contains("[1] Dinner"));

		assertTrue(run(calendar, "action", "move", "date", day.toString(), "index", "0", "to_date",
				day.plusDays(1).toString()).success());
		assertEquals(1, facade.getPlannedMeals(day).size());
		assertEquals(1, facade.getPlannedMeals(day.plusDays(1)).size());
		assertTrue(run(calendar, "action", "swap", "date", day.toString(), "index", "0", "with_date",
				day.plusDays(1).toString(), "with_index", "0").success());
		assertTrue(run(calendar, "action", "set_servings", "date", day.toString(), "index", "0", "servings", "3")
				.success());
		assertEquals(3, facade.getPlannedMeals(day).get(0).servings());
		assertTrue(run(calendar, "action", "undo").success());
		assertEquals(1, facade.getPlannedMeals(day).get(0).servings(), "the swap carried the other meal's 1 serving");
	}

	@Test
	void badInputComesBackAsAnErrorNotACrash() {
		assertFalse(run(calendar, "action", "remove", "date", day.toString(), "index", "4").success());
		assertFalse(run(calendar, "action", "add", "date", day.toString(), "recipe", "no such recipe").success());
		assertFalse(run(calendar, "action", "view", "from", "not a date").success());
		assertFalse(run(calendar, "action", "rename_tag", "name", "Dinner", "new_name", "Supper").success(),
				"built-in tags stay");
		assertFalse(run(calendar, "action", "fly").success());
	}

	@Test
	void tagsCanBeAddedRenamedAndDeleted() {
		assertTrue(run(calendar, "action", "add_tag", "name", "Pre-game").success());
		assertTrue(facade.getMealTags().contains("Pre-game"));
		assertTrue(run(calendar, "action", "rename_tag", "name", "Pre-game", "new_name", "Snack").success());
		assertTrue(run(calendar, "action", "delete_tag", "name", "Snack").success());
		assertFalse(facade.getMealTags().contains("Snack"));
	}

	@Test
	void prepDaysCanBePreviewedSavedMovedAndDeleted() {
		run(calendar, "action", "add", "date", day.toString(), "recipe", "1", "tag", "Lunch");
		run(calendar, "action", "add", "date", day.toString(), "recipe", "2", "tag", "Dinner");
		LocalDate prepDay = day.minusDays(1);
		String preview = run(prep, "action", "preview", "prep_date", prepDay.toString(), "from", day.toString(), "to",
				day.toString(), "limit_minutes", "180").content();
		assertTrue(preview.contains("Preview only"));
		assertNull(facade.getPrepDay(prepDay), "a preview saves nothing");

		assertTrue(run(prep, "action", "save", "prep_date", prepDay.toString(), "from", day.toString(), "to",
				day.toString(), "limit_minutes", "180").success());
		assertNotNull(facade.getPrepDay(prepDay));
		LocalDate earlier = prepDay.minusDays(1);
		assertTrue(run(prep, "action", "save", "prep_date", earlier.toString(), "start_time", "9:00", "from",
				day.toString(), "to", day.toString(), "limit_minutes", "180", "replace_date", prepDay.toString())
				.success());
		assertNull(facade.getPrepDay(prepDay));
		assertNotNull(facade.getPrepDay(earlier));
		assertEquals(1, facade.getPrepDays().size());
		assertTrue(run(prep, "action", "delete", "date", earlier.toString()).success());
		assertTrue(facade.getPrepDays().isEmpty());
		assertFalse(run(prep, "action", "delete", "date", earlier.toString()).success());
		assertFalse(run(prep, "action", "preview", "prep_date", day.plusDays(3).toString(), "from", day.toString())
				.success(), "prep day after the first cooking day is refused");
	}

	@Test
	void theGeneratorDraftsFirstAndOnlyThenTouchesTheCalendar() {
		assertFalse(run(plan, "action", "apply").success(), "no draft yet");
		ToolResult made = run(plan, "action", "draft", "from", day.toString(), "to", day.plusDays(1).toString(),
				"meals", "Lunch,Dinner");
		assertTrue(made.success());
		assertTrue(facade.getPlannedMeals(day).isEmpty(), "a draft is not on the calendar");
		assertTrue(run(plan, "action", "swap", "date", day.toString(), "index", "0", "with_date",
				day.plusDays(1).toString(), "with_index", "1").success());
		assertTrue(run(plan, "action", "reroll", "date", day.toString(), "index", "0").success());
		assertTrue(run(plan, "action", "apply").success());
		assertEquals(2, facade.getPlannedMeals(day).size());
		assertFalse(run(plan, "action", "apply").success(), "the draft is used up");
	}

	@Test
	void theOverviewMentionsEverythingTheUserHas() {
		run(calendar, "action", "add", "date", facade.today().toString(), "recipe", "1", "tag", "Lunch");
		String text = new AppOverviewTool(facade).execute(Map.of()).content();
		assertTrue(
				text.contains("Pantry") && text.contains("Shopping list") && text.contains("Meal plan, next 7 days"));
		assertTrue(text.contains("Lunch"));
	}
}