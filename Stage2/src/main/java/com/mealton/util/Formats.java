package com.mealton.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.Locale;

import com.mealton.pantry.PantryItem;

/**
 * Text formatting and input parsing shared by the CLI and the GUI. No screen
 * code in here.
 */
public final class Formats {

	private Formats() {
		// utility class: nobody creates an instance
	}

	/**
	 * 200.0 becomes "200", 2.5 becomes "2.5", 0.333 becomes "0.33". Always uses a
	 * dot.
	 */
	public static String number(double value) {
		return new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(value);
	}

	/** "200 g", or just "12" when there is no unit. */
	public static String quantityWithUnit(double quantity, String unit) {
		return unit == null || unit.isBlank() ? number(quantity) : number(quantity) + " " + unit;
	}

	/**
	 * Parses a quantity the user typed. Throws IllegalArgumentException with a
	 * friendly message.
	 */
	public static double parseQuantity(String text) {
		if (text == null || text.isBlank()) {
			throw new IllegalArgumentException("Please enter a quantity.");
		}
		try {
			return Double.parseDouble(text.trim());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Quantity must be a number, got '" + text.trim() + "'.");
		}
	}

	/** Same as parseQuantity, but a blank field means the default value. */
	public static double parseQuantityOrDefault(String text, double defaultValue) {
		return text == null || text.isBlank() ? defaultValue : parseQuantity(text);
	}

	/** Readable expiry status, for example "expires tomorrow (2026-11-02)". */
	public static String expiryText(PantryItem item, LocalDate today) {
		if (item.getExpiryDate() == null) {
			return "(no expiry date)";
		}
		int d = item.daysLeft(today);
		if (d < 0) {
			return "EXPIRED " + item.getExpiryDate() + " (" + (-d) + " day(s) ago)";
		}
		if (d == 0) {
			return "expires TODAY (" + item.getExpiryDate() + ")";
		}
		if (d == 1) {
			return "expires tomorrow (" + item.getExpiryDate() + ")";
		}
		return "expires " + item.getExpiryDate() + " (in " + d + " days)";
	}
}