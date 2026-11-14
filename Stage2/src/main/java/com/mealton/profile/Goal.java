package com.mealton.profile;

/** What the user wants their weight to do (used for the calorie suggestion). */
public enum Goal {
	LOSE_FAST("Lose 1 kg / week", -1000), LOSE("Lose 0.5 kg / week", -500), MAINTAIN("Maintain", 0), GAIN("Gain", 300);

	private final String label;
	private final int adjustment;

	Goal(String label, int adjustment) {
		this.label = label;
		this.adjustment = adjustment;
	}

	/** The name shown to the user. */
	public String label() {
		return label;
	}

	/** Calories added to (or taken from) what the body uses each day. */
	public int adjustment() {
		return adjustment;
	}

	/** Reads a goal from saved text; unknown or empty text gives MAINTAIN. */
	public static Goal fromText(String text) {
		if (text != null) {
			for (Goal g : values()) {
				if (g.name().equalsIgnoreCase(text.trim()) || g.label.equalsIgnoreCase(text.trim())) {
					return g;
				}
			}
		}
		return MAINTAIN;
	}
}