package com.mealton.agent.tools;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import com.mealton.agent.Tool;
import com.mealton.agent.ToolResult;
import com.mealton.facade.MealtonFacade;
import com.mealton.llm.ToolSpec;
import com.mealton.plan.CalendarService.PrepEvent;
import com.mealton.plan.PlannedMeal;
import com.mealton.recipe.Recipe;

/**
 * Lets the agent look at and change the meal-plan calendar, using the same
 * facade methods as the Meal plan page (so every change is one Undo step).
 */
public class CalendarTool implements Tool {
	private static final int MAX_DAYS = 31;
	private final MealtonFacade facade;

	public CalendarTool(MealtonFacade facade) {
		this.facade = facade;
	}

	@Override
	public String name() {
		return "calendar";
	}

	@Override
	public ToolSpec schema() {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("action", "One of: view (default), add, remove, move, swap, replace, set_tag, set_servings, "
				+ "something_else, undo, redo, tags, add_tag, rename_tag, delete_tag.");
		params.put("from", "For view: first date (2026-11-16, today, tomorrow or a weekday). Defaults to today.");
		params.put("to", "For view: last date (at most 31 days after 'from'). Defaults to 6 days after 'from'.");
		params.put("date",
				"The day of the meal to change (for add, remove, move, swap, replace, set_tag, set_servings, something_else).");
		params.put("index", "Which meal on that day, counting from 0 as shown by 'view' (all actions except add).");
		params.put("recipe", "For add and replace: a recipe id or title.");
		params.put("tag",
				"For add and set_tag: Breakfast, Lunch, Dinner, or one of the user's own tags (empty = none).");
		params.put("servings", "For add and set_servings: a whole number (default 1).");
		params.put("to_date", "For move: the day to move the meal to.");
		params.put("position", "For move: optional place on that day, from 0. Omit to put it in time order.");
		params.put("with_date", "For swap: the day of the other meal.");
		params.put("with_index", "For swap: the index of the other meal.");
		params.put("name", "For add_tag, rename_tag and delete_tag: the tag name.");
		params.put("new_name", "For rename_tag: the new name.");
		return new ToolSpec(name(),
				"The user's meal-plan calendar. 'view' shows a range of days with each meal's index, tag and servings, "
						+ "prep days and warnings. The other actions change it, always through the same rules as the Meal plan "
						+ "page: 'add' a meal, 'remove', 'move' to another day or place, 'swap' two meals, 'replace' the recipe, "
						+ "'set_tag', 'set_servings', 'something_else' (let Mealton pick another recipe, like the page does), "
						+ "'undo' and 'redo' the last calendar change, and the tags: 'tags' lists them, 'add_tag', 'rename_tag', "
						+ "'delete_tag' (Breakfast, Lunch and Dinner cannot be changed). Past days cannot be edited. "
						+ "Always 'view' first so you use the right index, and tell the user what you changed.",
				params);
	}

	@Override
	public ToolResult execute(Map<String, String> args) {
		String action = args.getOrDefault("action", "view").trim().toLowerCase();
		try {
			LocalDate today = facade.today();
			switch (action) {
			case "view":
			case "":
				return view(args, today);
			case "add": {
				LocalDate d = date(args, "date", today);
				Recipe r = RecipeLookup.find(facade, args.get("recipe"));
				int at = facade.addPlannedMeal(d, args.getOrDefault("tag", ""), r.getId(),
						RecipeLookup.whole(args.get("servings"), "servings", 1));
				return ToolResult
						.ok("Added " + r.getTitle() + " to " + AgentDates.show(d) + " (meal index " + at + ").");
			}
			case "remove": {
				LocalDate d = date(args, "date", today);
				int i = index(args, "index");
				String name = facade.getPlannedMeals(d).get(checked(d, i)).recipe().getTitle();
				facade.removePlannedMeal(d, i);
				return ToolResult.ok("Removed " + name + " from " + AgentDates.show(d) + ". 'undo' brings it back.");
			}
			case "move": {
				LocalDate d = date(args, "date", today);
				LocalDate to = date(args, "to_date", today);
				int i = index(args, "index");
				String name = facade.getPlannedMeals(d).get(checked(d, i)).recipe().getTitle();
				facade.movePlannedMeal(d, i, to, RecipeLookup.whole(args.get("position"), "position", -1));
				return ToolResult.ok("Moved " + name + " to " + AgentDates.show(to) + ".");
			}
			case "swap": {
				LocalDate a = date(args, "date", today);
				LocalDate b = date(args, "with_date", today);
				int ai = index(args, "index");
				int bi = index(args, "with_index");
				String an = facade.getPlannedMeals(a).get(checked(a, ai)).recipe().getTitle();
				String bn = facade.getPlannedMeals(b).get(checked(b, bi)).recipe().getTitle();
				facade.swapPlannedMeals(a, ai, b, bi);
				return ToolResult.ok("Swapped " + an + " (" + AgentDates.show(a) + ") with " + bn + " ("
						+ AgentDates.show(b) + ").");
			}
			case "replace": {
				LocalDate d = date(args, "date", today);
				int i = index(args, "index");
				checked(d, i);
				Recipe r = RecipeLookup.find(facade, args.get("recipe"));
				facade.replacePlannedMeal(d, i, r.getId());
				return ToolResult.ok("That meal on " + AgentDates.show(d) + " is now " + r.getTitle() + ".");
			}
			case "set_tag": {
				LocalDate d = date(args, "date", today);
				int i = index(args, "index");
				checked(d, i);
				facade.setPlannedMealTag(d, i, args.getOrDefault("tag", ""));
				return ToolResult.ok("Tag updated.");
			}
			case "set_servings": {
				LocalDate d = date(args, "date", today);
				int i = index(args, "index");
				checked(d, i);
				facade.setPlannedMealServings(d, i, RecipeLookup.whole(args.get("servings"), "servings", 1));
				return ToolResult.ok("Servings updated.");
			}
			case "something_else": {
				LocalDate d = date(args, "date", today);
				int i = index(args, "index");
				String old = facade.getPlannedMeals(d).get(checked(d, i)).recipe().getTitle();
				Recipe now = facade.swapForSuggestion(d, i);
				return ToolResult.ok("Swapped " + old + " for " + now.getTitle() + " on " + AgentDates.show(d) + ".");
			}
			case "undo":
				return ToolResult.ok(facade.undoCalendar() ? "Undid the last calendar change." : "Nothing to undo.");
			case "redo":
				return ToolResult.ok(facade.redoCalendar() ? "Redid it." : "Nothing to redo.");
			case "tags":
				return ToolResult.ok("Meal tags: " + String.join(", ", facade.getMealTags()) + ".");
			case "add_tag":
				return ToolResult.ok("Tag ready: " + facade.addMealTag(args.get("name")) + ".");
			case "rename_tag": {
				int n = facade.renameMealTag(args.getOrDefault("name", ""), args.getOrDefault("new_name", ""));
				return ToolResult.ok("Renamed; " + n + " meal(s) follow the new name.");
			}
			case "delete_tag": {
				int n = facade.deleteMealTag(args.getOrDefault("name", ""));
				return ToolResult.ok("Deleted the tag; " + n + " meal(s) are now untagged. 'undo' brings them back.");
			}
			default:
				return ToolResult.error("Unknown action '" + action + "'.");
			}
		} catch (IllegalArgumentException | IndexOutOfBoundsException e) {
			return ToolResult.error(e.getMessage() == null ? "There is no meal at that index." : e.getMessage());
		}
	}

	private LocalDate date(Map<String, String> args, String key, LocalDate today) {
		if (args.get(key) == null || args.get(key).isBlank()) {
			throw new IllegalArgumentException("Give '" + key + "'.");
		}
		return AgentDates.parse(args.get(key), today, today);
	}

	private int index(Map<String, String> args, String key) {
		String t = args.get(key);
		if (t == null || t.isBlank()) {
			throw new IllegalArgumentException("Give '" + key + "' (the meal's index from 'view').");
		}
		return RecipeLookup.whole(t, key, 0);
	}

	/** Makes sure the index points at a meal; returns it. */
	private int checked(LocalDate d, int i) {
		if (i < 0 || i >= facade.getPlannedMeals(d).size()) {
			throw new IllegalArgumentException(
					"There is no meal " + i + " on " + AgentDates.show(d) + ". Use 'view' to see the indexes.");
		}
		return i;
	}

	private ToolResult view(Map<String, String> args, LocalDate today) {
		LocalDate from = AgentDates.parse(args.get("from"), today, today);
		LocalDate to = AgentDates.parse(args.get("to"), today, from.plusDays(6));
		if (to.isBefore(from)) {
			return ToolResult.error("'to' has to be on or after 'from'.");
		}
		if (from.plusDays(MAX_DAYS).isBefore(to)) {
			return ToolResult.error("Ask for at most " + MAX_DAYS + " days at a time.");
		}
		StringBuilder sb = new StringBuilder("Meal plan calendar " + from + " to " + to + " (today is " + today + "):");
		int meals = 0;
		for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
			sb.append("\n").append(AgentDates.show(d))
					.append(d.equals(today) ? " (today)" : d.isBefore(today) ? " (past)" : "").append(":");
			PrepEvent prep = facade.getPrepDay(d);
			if (prep != null) {
				sb.append("\n  PREP DAY ").append(prep.start()).append("-").append(prep.end()).append(", ")
						.append(prep.meals()).append(" portions");
			}
			var day = facade.getPlannedMeals(d);
			if (day.isEmpty() && prep == null) {
				sb.append(" nothing planned");
			}
			for (int i = 0; i < day.size(); i++) {
				PlannedMeal m = day.get(i);
				meals++;
				sb.append("\n  [").append(i).append("] ").append(m.tag().isEmpty() ? "(no tag)" : m.tag()).append(": ")
						.append(m.recipe().getTitle()).append(", ").append(m.servings())
						.append(m.servings() == 1 ? " serving" : " servings");
			}
			for (String w : facade.getCalendarWarnings(d)) {
				sb.append("\n  WARNING: ").append(w);
			}
		}
		sb.append("\nTotal planned meals in this range: ").append(meals).append(".");
		return ToolResult.ok(sb.toString());
	}
}