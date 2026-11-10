package com.mealton.util;

import java.util.Locale;
import java.util.Map;

/**
 * Unit conversion and serving scaling (feature F10). Supports weight (g, kg,
 * oz, lb) and volume (ml, l, tsp, tbsp, cup). You can convert within one kind
 * (cup -> ml) but not across kinds (cup -> g), because that would need to know
 * the ingredient's density.
 */
public final class UnitConverter {

	private enum Kind {
		WEIGHT, VOLUME
	}

	/** How much of the base unit (grams or millilitres) one of this unit is. */
	private record Unit(String name, Kind kind, double toBase) {
	}

	private static final Map<String, Unit> UNITS = Map.of("g", new Unit("g", Kind.WEIGHT, 1.0), "kg",
			new Unit("kg", Kind.WEIGHT, 1000.0), "oz", new Unit("oz", Kind.WEIGHT, 28.3495), "lb",
			new Unit("lb", Kind.WEIGHT, 453.592), "ml", new Unit("ml", Kind.VOLUME, 1.0), "l",
			new Unit("l", Kind.VOLUME, 1000.0), "tsp", new Unit("tsp", Kind.VOLUME, 4.92892159375), "tbsp",
			new Unit("tbsp", Kind.VOLUME, 14.78676478125), "cup", new Unit("cup", Kind.VOLUME, 236.5882365));

	/** Other ways people write the same unit. */
	private static final Map<String, String> ALIASES = Map.ofEntries(Map.entry("gram", "g"), Map.entry("grams", "g"),
			Map.entry("kilogram", "kg"), Map.entry("kilograms", "kg"), Map.entry("kgs", "kg"), Map.entry("ounce", "oz"),
			Map.entry("ounces", "oz"), Map.entry("pound", "lb"), Map.entry("pounds", "lb"), Map.entry("lbs", "lb"),
			Map.entry("milliliter", "ml"), Map.entry("milliliters", "ml"), Map.entry("millilitre", "ml"),
			Map.entry("millilitres", "ml"), Map.entry("liter", "l"), Map.entry("liters", "l"), Map.entry("litre", "l"),
			Map.entry("litres", "l"), Map.entry("teaspoon", "tsp"), Map.entry("teaspoons", "tsp"),
			Map.entry("tablespoon", "tbsp"), Map.entry("tablespoons", "tbsp"), Map.entry("cups", "cup"));

	private UnitConverter() {
	}

	/** True if this unit can be converted (case and plural forms are ignored). */
	public static boolean isKnown(String unit) {
		return find(unit) != null;
	}

	/** Converts an amount, e.g. convert(2, "cup", "ml") is about 473.18. */
	public static double convert(double amount, String from, String to) {
		if (amount < 0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
			throw new IllegalArgumentException("The amount must be zero or a positive number.");
		}
		Unit source = require(from);
		Unit target = require(to);
		if (source.kind() != target.kind()) {
			throw new IllegalArgumentException("Cannot convert " + source.name() + " to " + target.name()
					+ ": one is a " + source.kind().name().toLowerCase(Locale.ROOT) + " and the other is a "
					+ target.kind().name().toLowerCase(Locale.ROOT) + ".");
		}
		return amount * source.toBase() / target.toBase();
	}

	/** Scales an ingredient amount from one number of servings to another. */
	public static double scale(double amount, int fromServings, int toServings) {
		if (fromServings < 1 || toServings < 1) {
			throw new IllegalArgumentException("Servings must be at least 1.");
		}
		if (amount < 0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
			throw new IllegalArgumentException("The amount must be zero or a positive number.");
		}
		return amount * toServings / fromServings;
	}

	private static Unit require(String unit) {
		Unit found = find(unit);
		if (found == null) {
			throw new IllegalArgumentException(
					"Unknown unit '" + unit + "'. Supported: g, kg, oz, lb, ml, l, tsp, tbsp, cup.");
		}
		return found;
	}

	private static Unit find(String unit) {
		if (unit == null) {
			return null;
		}
		String key = unit.trim().toLowerCase(Locale.ROOT);
		return UNITS.get(ALIASES.getOrDefault(key, key));
	}
}