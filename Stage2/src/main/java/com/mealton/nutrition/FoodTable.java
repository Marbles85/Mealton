package com.mealton.nutrition;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.mealton.util.UnitConverter;

/**
 * A small built-in table of common foods (feature F04). Values are typical
 * figures per 100 g, so they are good estimates, not lab results. Volume units
 * (cup, tbsp...) only work for foods with a known density, and counts ("2
 * eggs") only work for foods with a known piece weight. Foods that are not in
 * the table can still be logged with numbers typed by the user or estimated by
 * the AI.
 */
public final class FoodTable {

	/**
	 * @param per100g nutrition per 100 g; @param gramsPerPiece 0 if unknown; @param
	 *                gramsPerMl 0 if unknown
	 */
	private record Food(Nutrients per100g, double gramsPerPiece, double gramsPerMl) {
	}

	private static final Map<String, Food> FOODS = new LinkedHashMap<>();
	private static final Map<String, String> ALIASES = new LinkedHashMap<>();

	private static void food(String name, double kcal, double p, double c, double f, double piece, double density) {
		FOODS.put(name, new Food(new Nutrients(kcal, p, c, f), piece, density));
	}

	static {
		// meat, fish, eggs, dairy
		food("chicken breast", 165, 31, 0, 3.6, 0, 0);
		food("beef", 250, 26, 0, 15, 0, 0);
		food("pork", 242, 27, 0, 14, 0, 0);
		food("turkey", 135, 30, 0, 1, 0, 0);
		food("ham", 145, 21, 1.5, 6, 0, 0);
		food("bacon", 541, 37, 1.4, 42, 0, 0);
		food("salmon", 208, 20, 0, 13, 0, 0);
		food("tuna", 132, 28, 0, 1, 0, 0);
		food("shrimp", 99, 24, 0.2, 0.3, 0, 0);
		food("egg", 143, 12.6, 0.7, 9.5, 50, 0);
		food("milk", 61, 3.2, 4.8, 3.3, 0, 1.03);
		food("yogurt", 61, 3.5, 4.7, 3.3, 0, 1.03);
		food("cheese", 402, 25, 1.3, 33, 0, 0);
		food("butter", 717, 0.9, 0.1, 81, 0, 0.96);
		food("tofu", 76, 8, 1.9, 4.8, 0, 0);
		// grains and bread
		food("rice", 365, 7, 80, 0.7, 0, 0.77);
		food("cooked rice", 130, 2.7, 28, 0.3, 0, 0.67);
		food("pasta", 371, 13, 75, 1.5, 0, 0);
		food("cooked pasta", 158, 5.8, 31, 0.9, 0, 0);
		food("bread", 265, 9, 49, 3.2, 30, 0);
		food("tortilla", 312, 8, 52, 8, 45, 0);
		food("oats", 389, 17, 66, 7, 0, 0.38);
		food("cereal", 379, 7, 84, 2, 0, 0);
		food("flour", 364, 10, 76, 1, 0, 0.53);
		food("sugar", 387, 0, 100, 0, 0, 0.85);
		// vegetables and fruit
		food("potato", 77, 2, 17, 0.1, 170, 0);
		food("sweet potato", 86, 1.6, 20, 0.1, 130, 0);
		food("tomato", 18, 0.9, 3.9, 0.2, 120, 0);
		food("onion", 40, 1.1, 9.3, 0.1, 110, 0);
		food("carrot", 41, 0.9, 10, 0.2, 60, 0);
		food("broccoli", 34, 2.8, 7, 0.4, 0, 0);
		food("spinach", 23, 2.9, 3.6, 0.4, 0, 0);
		food("lettuce", 15, 1.4, 2.9, 0.2, 0, 0);
		food("cucumber", 15, 0.7, 3.6, 0.1, 0, 0);
		food("bell pepper", 31, 1, 6, 0.3, 120, 0);
		food("mushroom", 22, 3.1, 3.3, 0.3, 0, 0);
		food("garlic", 149, 6.4, 33, 0.5, 3, 0);
		food("corn", 86, 3.3, 19, 1.4, 0, 0);
		food("peas", 81, 5, 14, 0.4, 0, 0);
		food("apple", 52, 0.3, 14, 0.2, 180, 0);
		food("banana", 89, 1.1, 23, 0.3, 118, 0);
		food("orange", 47, 0.9, 12, 0.1, 130, 0);
		food("strawberry", 32, 0.7, 7.7, 0.3, 0, 0);
		food("avocado", 160, 2, 9, 15, 150, 0);
		// beans, nuts, fats, extras
		food("black beans", 132, 8.9, 24, 0.5, 0, 0);
		food("chickpeas", 164, 8.9, 27, 2.6, 0, 0);
		food("lentils", 116, 9, 20, 0.4, 0, 0);
		food("peanut butter", 588, 25, 20, 50, 0, 1.08);
		food("almonds", 579, 21, 22, 50, 0, 0.6);
		food("olive oil", 884, 0, 0, 100, 0, 0.92);
		food("vegetable oil", 884, 0, 0, 100, 0, 0.92);
		food("honey", 304, 0.3, 82, 0, 0, 1.42);
		food("chocolate", 546, 5, 60, 31, 0, 0);
		// drinks
		food("orange juice", 45, 0.7, 10, 0.2, 0, 1.04);
		food("water", 0, 0, 0, 0, 0, 1);
		food("black coffee", 1, 0.1, 0, 0, 0, 1);

		ALIASES.put("chicken", "chicken breast");
		ALIASES.put("ground beef", "beef");
		ALIASES.put("steak", "beef");
		ALIASES.put("yoghurt", "yogurt");
		ALIASES.put("coffee", "black coffee");
		ALIASES.put("peppers", "bell pepper");
		ALIASES.put("pepper", "bell pepper");
		ALIASES.put("potatoe", "potato");
	}

	private FoodTable() {
	}

	/**
	 * True if the food is in the table (plurals and a few common alternative names
	 * are understood).
	 */
	public static boolean knows(String name) {
		return find(name) != null;
	}

	/** The table's food names, for the AI and for help text. */
	public static java.util.Set<String> names() {
		return java.util.Collections.unmodifiableSet(FOODS.keySet());
	}

	/**
	 * Nutrition for an amount of a food.
	 *
	 * @return empty if the food is not in the table
	 * @throws IllegalArgumentException if the food is known but the unit cannot be
	 *                                  used for it
	 */
	public static Optional<Nutrients> lookup(String name, double quantity, String unit) {
		if (Double.isNaN(quantity) || Double.isInfinite(quantity) || quantity <= 0) {
			throw new IllegalArgumentException("The amount must be more than 0.");
		}
		String key = key(name);
		Food food = find(name);
		if (food == null) {
			return Optional.empty();
		}
		double grams = toGrams(key, food, quantity, unit == null ? "" : unit.trim().toLowerCase(Locale.ROOT));
		return Optional.of(food.per100g().times(grams / 100.0));
	}

	private static double toGrams(String key, Food food, double quantity, String unit) {
		if (unit.isEmpty() || unit.equals("piece") || unit.equals("pieces") || unit.equals("slice")
				|| unit.equals("slices")) {
			if (food.gramsPerPiece() <= 0) {
				throw new IllegalArgumentException(
						"I can't count '" + key + "' by the piece. " + "Use a weight such as grams.");
			}
			return quantity * food.gramsPerPiece();
		}
		if (!UnitConverter.isKnown(unit)) {
			throw new IllegalArgumentException(
					"I don't know the unit '" + unit + "'. Use g, kg, oz, lb, " + "ml, l, tsp, tbsp or cup.");
		}
		try {
			return UnitConverter.convert(quantity, unit, "g");
		} catch (IllegalArgumentException volume) {
			// a volume unit: only possible when the food has a known density
			if (food.gramsPerMl() <= 0) {
				throw new IllegalArgumentException(
						"I can't turn " + unit + " of '" + key + "' into grams. Use a weight such as grams.");
			}
			return UnitConverter.convert(quantity, unit, "ml") * food.gramsPerMl();
		}
	}

	private static Food find(String name) {
		return FOODS.get(key(name));
	}

	/**
	 * The table name for what the user typed: lower case, plural removed,
	 * alternative names mapped.
	 */
	private static String key(String name) {
		String n = name == null ? "" : name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
		for (String candidate : new String[] { n, singular(n) }) {
			if (FOODS.containsKey(candidate)) {
				return candidate;
			}
			if (ALIASES.containsKey(candidate)) {
				return ALIASES.get(candidate);
			}
		}
		return n;
	}

	private static String singular(String n) {
		if (n.endsWith("ies") && n.length() > 4) {
			return n.substring(0, n.length() - 3) + "y";
		}
		if (n.endsWith("oes") && n.length() > 4) {
			return n.substring(0, n.length() - 2);
		}
		if (n.endsWith("s") && !n.endsWith("ss") && n.length() > 3) {
			return n.substring(0, n.length() - 1);
		}
		return n;
	}
}