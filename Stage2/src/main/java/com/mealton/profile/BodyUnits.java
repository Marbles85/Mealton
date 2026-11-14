package com.mealton.profile;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads body measurements typed in any common unit and returns kilograms or
 * centimetres, which is what the profile stores.
 * <ul>
 * <li>Weight: "62", "62 kg", "135 lb", "135 lbs", "135 pounds".</li>
 * <li>Height: "165", "165 cm", "1.65 m", "5'6\"", "5' 6", "5 ft 6 in", "5ft",
 * "66 in". A bare number under 10 is read as feet (so "5.5" is 5.5 ft).</li>
 * </ul>
 */
public final class BodyUnits {
	private static final double KG_PER_LB = 0.45359237;
	private static final double CM_PER_IN = 2.54;
	private static final String NUM = "(\\d+(?:[.,]\\d+)?)";
	private static final Pattern WEIGHT = Pattern
			.compile("^" + NUM + "\\s*(kg|kgs|kilos?|kilograms?|lb|lbs|pounds?)?$");
	private static final Pattern FEET_INCHES = Pattern
			.compile("^(\\d+(?:[.,]\\d+)?)\\s*(?:'|\u2019|ft|feet|foot)\\s*(?:" + NUM
					+ "\\s*(?:\"|\u201D|''|in|inch|inches)?)?$");
	private static final Pattern SIMPLE = Pattern.compile("^" + NUM + "\\s*(cm|m|in|inch|inches)?$");
	private static final Pattern METRIC_ONLY = Pattern.compile("^\\s*[\\d.,]+\\s*(kg|kgs|cm)?\\s*$",
			Pattern.CASE_INSENSITIVE);

	private BodyUnits() {
	}

	/** Weight in kilograms (to 0.1), or an IllegalArgumentException. */
	public static double parseWeightKg(String text) {
		Matcher m = WEIGHT.matcher(clean(text));
		if (!m.matches()) {
			throw new IllegalArgumentException("Enter a weight like 62 kg or 135 lb.");
		}
		double value = number(m.group(1));
		String unit = m.group(2) == null ? "kg" : m.group(2);
		double kg = unit.startsWith("k") ? value : value * KG_PER_LB;
		return Math.round(kg * 10) / 10.0;
	}

	/** Height in centimetres, or an IllegalArgumentException. */
	public static int parseHeightCm(String text) {
		String t = clean(text);
		Matcher fi = FEET_INCHES.matcher(t);
		if (fi.matches()) {
			double inches = number(fi.group(1)) * 12 + (fi.group(2) == null ? 0 : number(fi.group(2)));
			return (int) Math.round(inches * CM_PER_IN);
		}
		Matcher s = SIMPLE.matcher(t);
		if (s.matches()) {
			double value = number(s.group(1));
			String unit = s.group(2);
			if (unit == null) {
				return (int) Math.round(value < 10 ? value * 12 * CM_PER_IN : value);
			}
			switch (unit) {
			case "cm":
				return (int) Math.round(value);
			case "m":
				return (int) Math.round(value * 100);
			default:
				return (int) Math.round(value * CM_PER_IN);
			}
		}
		throw new IllegalArgumentException("Enter a height like 165 cm or 5'6\".");
	}

	/** True when the text is plain kg / cm (no conversion worth showing). */
	public static boolean isMetric(String text) {
		return text == null || METRIC_ONLY.matcher(text).matches();
	}

	private static String clean(String text) {
		return text == null ? "" : text.trim().toLowerCase(Locale.ROOT).replace("\u2032", "'").replace("\u2033", "\"");
	}

	private static double number(String s) {
		return Double.parseDouble(s.replace(',', '.'));
	}
}