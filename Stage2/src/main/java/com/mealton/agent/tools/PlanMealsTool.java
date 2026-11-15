package com.mealton.agent.tools;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mealton.agent.Tool;
import com.mealton.agent.ToolResult;
import com.mealton.facade.MealtonFacade;
import com.mealton.llm.ToolSpec;
import com.mealton.plan.PlannedMeal;
import com.mealton.plan.RangePlanner;
import com.mealton.plan.RangePlanner.Draft;
import com.mealton.profile.Diet;

/**
 * The "Plan meals" generator for the agent: make a draft for any dates, look at
 * it, change it, and only then apply it to the calendar. The draft is made by
 * Mealton's own planner (profile, allergies, pantry first), not invented by the
 * LLM, and nothing reaches the calendar until 'apply'.
 */
public class PlanMealsTool implements Tool {
	private final MealtonFacade facade;
	private Draft draft;

	public PlanMealsTool(MealtonFacade facade) {
		this.facade = facade;
	}

	@Override
	public String name() {
		return "plan_meals";
	}

	@Override
	public ToolSpec schema() {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("action", "Required. One of: draft, show, reroll, swap, apply, add_to_shopping_list.");
		params.put("from", "For draft: first date (2026-11-16, today, tomorrow, weekday). Defaults to today.");
		params.put("to", "For draft: last date (up to 28 days). Defaults to 6 days after 'from'.");
		params.put("meals", "For draft: comma separated, from Breakfast, Lunch, Dinner. Defaults to all three.");
		params.put("diet",
				"For draft: optional diet for this plan only (vegetarian, vegan, ...). Defaults to the profile.");
		params.put("calories", "For draft: optional daily calories for this plan only. Defaults to the profile.");
		params.put("cuisines", "For draft: optional, comma separated, for example 'Italian, Mexican'.");
		params.put("date", "For reroll and swap: the day of the meal in the draft.");
		params.put("index", "For reroll and swap: the meal's index on that day from 0, as shown by show/draft.");
		params.put("with_date", "For swap: the day of the other meal.");
		params.put("with_index", "For swap: the other meal's index.");
		return new ToolSpec(name(),
				"The Plan meals generator. 'draft' makes a plan for a range of dates from the user's profile, allergies "
						+ "and pantry and keeps it as a draft; 'show' prints it; 'reroll' replaces one meal with another recipe; "
						+ "'swap' exchanges two meals; 'apply' puts the draft on the calendar (one Undo step, it replaces meals "
						+ "with the same tags on those days); 'add_to_shopping_list' adds what the draft's recipes are missing. "
						+ "Show the draft to the user and ask before you 'apply'.",
				params);
	}

	@Override
	public ToolResult execute(Map<String, String> args) {
		String action = args.getOrDefault("action", "").trim().toLowerCase();
		try {
			LocalDate today = facade.today();
			switch (action) {
			case "draft": {
				LocalDate from = AgentDates.parse(args.get("from"), today, today);
				LocalDate to = AgentDates.parse(args.get("to"), today, from.plusDays(6));
				List<String> tags = new ArrayList<>();
				String meals = args.get("meals");
				for (String t : (meals == null || meals.isBlank() ? "Breakfast,Lunch,Dinner" : meals).split(",")) {
					String n = t.trim();
					for (String known : RangePlanner.TAGS) {
						if (known.equalsIgnoreCase(n)) {
							tags.add(known);
						}
					}
				}
				if (tags.isEmpty()) {
					return ToolResult.error("meals has to include Breakfast, Lunch or Dinner.");
				}
				Diet diet = args.get("diet") == null || args.get("diet").isBlank() ? null
						: Diet.fromText(args.get("diet"));
				Integer calories = args.get("calories") == null || args.get("calories").isBlank() ? null
						: Integer.valueOf(RecipeLookup.whole(args.get("calories"), "calories", 0));
				Set<String> cuisines = new java.util.LinkedHashSet<>();
				if (args.get("cuisines") != null) {
					for (String c : args.get("cuisines").split(",")) {
						if (!c.isBlank()) {
							cuisines.add(c.trim());
						}
					}
				}
				draft = facade.draftPlan(
						new RangePlanner.Options(from, to, tags, diet, false, calories, cuisines, true, true));
				return ToolResult.ok("Draft made (nothing is on the calendar yet).\n" + describe());
			}
			case "show":
				return need() ? ToolResult.error("There is no draft yet. Use 'draft' first.")
						: ToolResult.ok(describe());
			case "reroll": {
				if (need()) {
					return ToolResult.error("There is no draft yet. Use 'draft' first.");
				}
				facade.rerollDraftMeal(draft, AgentDates.parse(args.get("date"), today, today),
						RecipeLookup.whole(args.get("index"), "index", 0));
				return ToolResult.ok("Replaced that meal.\n" + describe());
			}
			case "swap": {
				if (need()) {
					return ToolResult.error("There is no draft yet. Use 'draft' first.");
				}
				facade.swapDraftMeals(draft, AgentDates.parse(args.get("date"), today, today),
						RecipeLookup.whole(args.get("index"), "index", 0),
						AgentDates.parse(args.get("with_date"), today, today),
						RecipeLookup.whole(args.get("with_index"), "with_index", 0));
				return ToolResult.ok("Swapped.\n" + describe());
			}
			case "apply": {
				if (need()) {
					return ToolResult.error("There is no draft yet. Use 'draft' first.");
				}
				int n = facade.applyDraft(draft);
				draft = null;
				return ToolResult.ok("Added " + n + " meals to the calendar. 'undo' (calendar tool) takes them back.");
			}
			case "add_to_shopping_list": {
				if (need()) {
					return ToolResult.error("There is no draft yet. Use 'draft' first.");
				}
				int n = facade.addDraftToShoppingList(draft);
				return ToolResult.ok(n == 0 ? "Nothing to add: the pantry and list already cover it."
						: "Added " + n + " ingredients to the shopping list.");
			}
			default:
				return ToolResult.error("Unknown action '" + action
						+ "'. Use draft, show, reroll, swap, apply or add_to_shopping_list.");
			}
		} catch (IllegalArgumentException | IndexOutOfBoundsException e) {
			return ToolResult.error(e.getMessage() == null ? "There is no meal at that index." : e.getMessage());
		}
	}

	private boolean need() {
		return draft == null;
	}

	private String describe() {
		StringBuilder sb = new StringBuilder("Draft " + draft.options().start() + " to " + draft.options().end() + ": "
				+ draft.mealCount() + " meals, about " + draft.avgKcal() + " kcal a day.");
		for (var e : draft.meals().entrySet()) {
			sb.append("\n").append(AgentDates.show(e.getKey())).append(":");
			List<PlannedMeal> day = e.getValue();
			for (int i = 0; i < day.size(); i++) {
				sb.append("\n  [").append(i).append("] ").append(day.get(i).tag()).append(": ")
						.append(day.get(i).recipe().getTitle());
			}
		}
		return sb.toString();
	}
}