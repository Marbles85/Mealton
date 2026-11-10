package com.mealton.agent.tools;

import java.util.LinkedHashMap;
import java.util.Map;

import com.mealton.agent.Tool;
import com.mealton.agent.ToolResult;
import com.mealton.llm.ToolSpec;
import com.mealton.profile.Diet;
import com.mealton.profile.ProfileService;
import com.mealton.profile.UserProfile;

/**
 * Lets the agent read and update the user's profile (feature F01): diet,
 * allergies and daily goals. The agent should only change the profile when the
 * user clearly says so (for example "I'm vegan now").
 */
public class ProfileTool implements Tool {
	private final ProfileService profile;

	public ProfileTool(ProfileService profile) {
		this.profile = profile;
	}

	@Override
	public String name() {
		return "profile";
	}

	@Override
	public ToolSpec schema() {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("action", "Required. One of: show, set_name, set_diet, add_allergy, remove_allergy, "
				+ "set_calories, set_macros.");
		params.put("value", "For set_name, set_diet, add_allergy, remove_allergy and set_calories: the new value. "
				+ "Diets: " + Diet.options() + ".");
		params.put("protein", "For set_macros: daily protein goal in grams.");
		params.put("carbs", "For set_macros: daily carbohydrate goal in grams.");
		params.put("fat", "For set_macros: daily fat goal in grams.");
		return new ToolSpec(name(),
				"Shows or changes the user's profile: name, diet, allergies and daily nutrition goals. "
						+ "Only change it when the user clearly asks or states a new fact about themselves.",
				params);
	}

	@Override
	public ToolResult execute(Map<String, String> args) {
		String action = args.getOrDefault("action", "").trim().toLowerCase();
		String value = args.get("value");
		try {
			switch (action) {
			case "show":
				return ToolResult.ok(profile.get().summary());
			case "set_name":
				return updated(profile.setName(value));
			case "set_diet":
				return updated(profile.setDiet(value));
			case "add_allergy":
				return updated(profile.addAllergy(value));
			case "remove_allergy":
				return updated(profile.removeAllergy(value));
			case "set_calories":
				return updated(profile.setCalorieGoal(whole(value, "value")));
			case "set_macros":
				return updated(profile.setMacroGoals(whole(args.get("protein"), "protein"),
						whole(args.get("carbs"), "carbs"), whole(args.get("fat"), "fat")));
			default:
				return ToolResult.error("Unknown action '" + action + "'. Use show, set_name, set_diet, "
						+ "add_allergy, remove_allergy, set_calories or set_macros.");
			}
		} catch (IllegalArgumentException e) {
			return ToolResult.error(e.getMessage());
		}
	}

	private static ToolResult updated(UserProfile profile) {
		return ToolResult.ok("Profile updated. " + profile.summary());
	}

	private static int whole(String text, String field) {
		try {
			return Integer.parseInt(text == null ? "" : text.trim());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("'" + field + "' must be a whole number.");
		}
	}
}