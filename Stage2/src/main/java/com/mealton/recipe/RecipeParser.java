package com.mealton.recipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.mealton.util.UnitConverter;

/**
 * Turns plain text into recipe parts, so the command line and the AI agent can
 * both create recipes from one line of text.
 * <ul>
 * <li>Ingredients are separated by semicolons:
 * {@code "200 g flour; 2 eggs; 1/2 l milk"}</li>
 * <li>Steps are separated by the | character:
 * {@code "Mix everything|Fry in a pan"}</li>
 * </ul>
 */
public final class RecipeParser {

	private RecipeParser() {
	}

	/** Parses "200 g flour; 2 eggs; 1/2 l milk" into ingredients. */
	public static List<RecipeIngredient> parseIngredients(String text) {
		List<RecipeIngredient> result = new ArrayList<>();
		if (text == null || text.isBlank()) {
			return result;
		}
		for (String part : text.split(";")) {
			if (!part.isBlank()) {
				result.add(parseIngredient(part));
			}
		}
		return result;
	}

	/**
	 * Parses one ingredient such as "200 g flour" or "2 eggs": quantity, optional
	 * unit, then the name.
	 */
	public static RecipeIngredient parseIngredient(String line) {
		String[] words = line.trim().split("\\s+");
		double quantity = parseNumber(words[0], line);
		int nameStart = 1;
		String unit = "";
		if (words.length > 1 && UnitConverter.isKnown(words[1])) {
			unit = words[1];
			nameStart = 2; // if nothing follows the unit, the check below reports the missing name
		}
		if (nameStart >= words.length) {
			throw new IllegalArgumentException(
					"The ingredient '" + line.trim() + "' has no name. Write it like '200 g flour'.");
		}
		String name = String.join(" ", Arrays.copyOfRange(words, nameStart, words.length));
		return new RecipeIngredient(name, quantity, unit);
	}

	/** Splits "Mix everything|Fry in a pan" into separate steps. */
	public static List<String> parseSteps(String text) {
		List<String> steps = new ArrayList<>();
		if (text == null || text.isBlank()) {
			return steps;
		}
		for (String part : text.split("\\|")) {
			if (!part.isBlank()) {
				steps.add(part.trim());
			}
		}
		return steps;
	}

	private static double parseNumber(String word, String line) {
		try {
			if (word.contains("/")) {
				String[] parts = word.split("/");
				if (parts.length != 2) {
					throw new NumberFormatException();
				}
				return Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]);
			}
			return Double.parseDouble(word);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(
					"Each ingredient must start with a quantity, like '200 g flour'. Got '" + line.trim() + "'.");
		}
	}
}