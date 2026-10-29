package com.mealton.util;

import java.util.List;

/**
 * Suggested categories for pantry and shopping items. AI-suggested categories
 * come later.
 */
public final class Categories {

	public static final List<String> DEFAULT = List.of("Produce", "Dairy", "Meat & Fish", "Grains & Bread", "Frozen",
			"Snacks", "Pantry staples", "Other");

	private Categories() {
		// utility class: nobody creates an instance
	}
}