package com.mealton.nutrition;

import com.mealton.util.Formats;

/**
 * An amount of nutrition: calories plus protein, carbohydrates and fat in grams
 * (feature F04). It is a record, so it never changes; {@link #plus} and
 * {@link #times} return new values.
 *
 * @param calories kilocalories
 * @param protein  grams
 * @param carbs    grams
 * @param fat      grams
 */
public record Nutrients(double calories, double protein, double carbs, double fat) {

	public static final Nutrients ZERO = new Nutrients(0, 0, 0, 0);

	public Nutrients {
		check(calories, "Calories");
		check(protein, "Protein");
		check(carbs, "Carbs");
		check(fat, "Fat");
	}

	private static void check(double value, String what) {
		if (Double.isNaN(value) || Double.isInfinite(value) || value < 0) {
			throw new IllegalArgumentException(what + " must be a number that is 0 or more.");
		}
		if (value > 100000) {
			throw new IllegalArgumentException(what + " is unrealistically large.");
		}
	}

	public Nutrients plus(Nutrients other) {
		return new Nutrients(calories + other.calories, protein + other.protein, carbs + other.carbs, fat + other.fat);
	}

	public Nutrients times(double factor) {
		return new Nutrients(calories * factor, protein * factor, carbs * factor, fat * factor);
	}

	/** For example "520 kcal, protein 31 g, carbs 40 g, fat 12.5 g". */
	public String summary() {
		return Math.round(calories) + " kcal, protein " + grams(protein) + ", carbs " + grams(carbs) + ", fat "
				+ grams(fat);
	}

	private static String grams(double value) {
		return Formats.number(Math.round(value * 10.0) / 10.0) + " g";
	}
}