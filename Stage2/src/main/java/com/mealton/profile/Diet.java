package com.mealton.profile;

import java.util.Locale;

/** The eating style a user follows (feature F01). */
public enum Diet {
	ANYTHING("anything"), VEGETARIAN("vegetarian"), VEGAN("vegan"), PESCATARIAN("pescatarian"),
	GLUTEN_FREE("gluten-free"), KETO("keto");

	private final String label;

	Diet(String label) {
		this.label = label;
	}

	/** The name shown to the user, for example "gluten-free". */
	public String label() {
		return label;
	}

	/** Reads a diet from text such as "Vegan", "gluten free" or "GLUTEN_FREE". */
	public static Diet fromText(String text) {
		if (text == null || text.isBlank()) {
			throw new IllegalArgumentException("Give a diet: " + options() + ".");
		}
		String wanted = text.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
		for (Diet diet : values()) {
			if (diet.label.equals(wanted)) {
				return diet;
			}
		}
		throw new IllegalArgumentException("Unknown diet '" + text.trim() + "'. Choose one of: " + options() + ".");
	}

	/** All diet names in one string, for help and error messages. */
	public static String options() {
		StringBuilder sb = new StringBuilder();
		for (Diet diet : values()) {
			if (sb.length() > 0) {
				sb.append(", ");
			}
			sb.append(diet.label);
		}
		return sb.toString();
	}
}