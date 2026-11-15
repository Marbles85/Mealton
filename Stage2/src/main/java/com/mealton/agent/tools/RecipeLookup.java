package com.mealton.agent.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mealton.facade.MealtonFacade;
import com.mealton.recipe.Recipe;

/**
 * Finds a recipe from what the LLM sends: an id number or (part of) a title.
 */
final class RecipeLookup {

	private RecipeLookup() {
	}

	static Recipe find(MealtonFacade facade, String text) {
		if (text == null || text.isBlank()) {
			throw new IllegalArgumentException("Say which recipe (its id or its title).");
		}
		String t = text.trim();
		try {
			return facade.getRecipe(Long.parseLong(t.replace("#", "")));
		} catch (NumberFormatException e) {
			// not an id: look at the titles
		}
		String wanted = t.toLowerCase(Locale.ROOT);
		List<Recipe> contains = new ArrayList<>();
		for (Recipe r : facade.getRecipes()) {
			String title = r.getTitle().toLowerCase(Locale.ROOT);
			if (title.equals(wanted)) {
				return r;
			}
			if (title.contains(wanted)) {
				contains.add(r);
			}
		}
		if (contains.size() == 1) {
			return contains.get(0);
		}
		if (contains.isEmpty()) {
			throw new IllegalArgumentException("No recipe matches '" + text + "'.");
		}
		StringBuilder sb = new StringBuilder("More than one recipe matches '" + text + "', use the id:");
		for (int i = 0; i < contains.size() && i < 6; i++) {
			sb.append(" #").append(contains.get(i).getId()).append(" ").append(contains.get(i).getTitle()).append(";");
		}
		throw new IllegalArgumentException(sb.toString());
	}

	static int whole(String text, String what, int fallback) {
		if (text == null || text.isBlank()) {
			return fallback;
		}
		try {
			return Integer.parseInt(text.trim());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(what + " has to be a whole number.");
		}
	}
}