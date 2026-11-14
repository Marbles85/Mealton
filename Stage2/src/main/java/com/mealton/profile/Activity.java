package com.mealton.profile;

/** How active the user is on a normal day (used for the calorie suggestion). */
public enum Activity {
	SITTING("Mostly sitting", 1.2), LIGHT("Lightly active", 1.375), ACTIVE("Active", 1.55);

	private final String label;
	private final double factor;

	Activity(String label, double factor) {
		this.label = label;
		this.factor = factor;
	}

	/** The name shown to the user. */
	public String label() {
		return label;
	}

	/** The number BMR is multiplied by to estimate daily calories. */
	public double factor() {
		return factor;
	}

	/**
	 * Reads an activity level from saved text; unknown or empty text gives LIGHT.
	 */
	public static Activity fromText(String text) {
		if (text != null) {
			for (Activity a : values()) {
				if (a.name().equalsIgnoreCase(text.trim()) || a.label.equalsIgnoreCase(text.trim())) {
					return a;
				}
			}
		}
		return LIGHT;
	}
}