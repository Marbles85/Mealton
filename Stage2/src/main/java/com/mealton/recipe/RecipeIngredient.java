package com.mealton.recipe;

import com.mealton.util.Formats;
import com.mealton.util.UnitConverter;

/**
 * One line of a recipe, for example "200 g flour". A record is a small
 * read-only data class: the name, quantity and unit never change after
 * creation.
 */
public record RecipeIngredient(String name, double quantity, String unit) {

	public RecipeIngredient {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Ingredient name must not be empty.");
		}
		if (Double.isNaN(quantity) || Double.isInfinite(quantity) || quantity <= 0) {
			throw new IllegalArgumentException("Quantity must be a positive number.");
		}
		name = name.trim();
		unit = unit == null ? "" : unit.trim();
	}

	/**
	 * The same ingredient, with the amount changed from one number of servings to
	 * another.
	 */
	public RecipeIngredient scaled(int fromServings, int toServings) {
		return new RecipeIngredient(name, UnitConverter.scale(quantity, fromServings, toServings), unit);
	}

	@Override
	public String toString() {
		return Formats.quantityWithUnit(quantity, unit) + " " + name;
	}
}