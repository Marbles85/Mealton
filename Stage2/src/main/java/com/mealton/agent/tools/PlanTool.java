package com.mealton.agent.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.mealton.agent.Tool;
import com.mealton.agent.ToolResult;
import com.mealton.llm.ToolSpec;
import com.mealton.plan.PlanService;
import com.mealton.recipe.RecipeIngredient;

/**
 * Lets the agent plan the week (feature F02): generate a plan from the saved
 * recipes, show it, swap single meals, check it against the profile and put the
 * missing ingredients on the shopping list.
 */
public class PlanTool implements Tool {
	private final PlanService plans;

	public PlanTool(PlanService plans) {
		this.plans = plans;
	}

	@Override
	public String name() {
		return "meal_plan";
	}

	@Override
	public ToolSpec schema() {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("action", "Required. One of: show, generate, set, clear, check, shopping, reset.");
		params.put("style", "For generate: 'variety' (default, spreads recipes over the week) or 'pantry' "
				+ "(plans recipes that fit the pantry best first).");
		params.put("meals", "For generate: which meals to plan: 'dinner', 'lunch,dinner' (default) or 'all'.");
		params.put("day", "For set and clear: monday ... sunday.");
		params.put("meal", "For set and clear: breakfast, lunch or dinner.");
		params.put("recipe_id", "For set: the id of a saved recipe (see the recipes tool, action 'list').");
		return new ToolSpec(name(),
				"The user's weekly meal plan, built from their saved recipes. 'generate' makes a new plan "
						+ "(skipping recipes that clash with the user's allergies or diet) and replaces the old "
						+ "one, 'show' displays it, 'set' puts one recipe into one meal, 'clear' empties one meal, "
						+ "'check' lists problems such as profile conflicts or too many repeats, 'shopping' adds "
						+ "the missing ingredients of every planned recipe to the shopping list and 'reset' deletes "
						+ "the plan. Ask before generating if a plan already exists, because generate replaces it.",
				params);
	}

	@Override
	public ToolResult execute(Map<String, String> args) {
		String action = args.getOrDefault("action", "").trim().toLowerCase();
		try {
			switch (action) {
			case "show":
				return ToolResult.ok(plans.current().describe());
			case "generate":
				return ToolResult.ok("New plan:\n" + plans.generate(args.get("style"), args.get("meals")).describe()
						+ problemsNote());
			case "set":
				return ToolResult.ok("Updated:\n"
						+ plans.set(args.get("day"), args.get("meal"), recipeId(args.get("recipe_id"))).describe()
						+ problemsNote());
			case "clear":
				return ToolResult.ok("Updated:\n" + plans.clearMeal(args.get("day"), args.get("meal")).describe());
			case "check": {
				List<String> problems = plans.validate();
				return ToolResult.ok(
						problems.isEmpty() ? "The plan looks fine." : "Problems:\n- " + String.join("\n- ", problems));
			}
			case "shopping": {
				List<RecipeIngredient> added = plans.addMissingToShoppingList();
				return ToolResult.ok(added.isEmpty()
						? "Nothing to add: the pantry and shopping list already cover the plan."
						: "Added to the shopping list: "
								+ added.stream().map(RecipeIngredient::toString).collect(Collectors.joining(", "))
								+ ".");
			}
			case "reset":
				plans.clearAll();
				return ToolResult.ok("The plan was deleted.");
			default:
				return ToolResult.error(
						"Unknown action '" + action + "'. Use show, generate, set, clear, check, shopping or reset.");
			}
		} catch (IllegalArgumentException e) {
			return ToolResult.error(e.getMessage());
		}
	}

	/**
	 * Problems of the current plan, appended to the answer so the agent can tell
	 * the user.
	 */
	private String problemsNote() {
		List<String> problems = plans.validate();
		return problems.isEmpty() ? "" : "\nProblems:\n- " + String.join("\n- ", problems);
	}

	private static long recipeId(String text) {
		try {
			return Long.parseLong(text == null ? "" : text.trim().replace("#", ""));
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(
					"Give 'recipe_id' as a whole number (use the recipes tool, action 'list').");
		}
	}
}