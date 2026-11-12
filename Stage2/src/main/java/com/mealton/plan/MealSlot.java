package com.mealton.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The meals of a day that can be planned (feature F02). */
public enum MealSlot {
	BREAKFAST("Breakfast"), LUNCH("Lunch"), DINNER("Dinner");

	private final String label;

	MealSlot(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

	/**
	 * Reads "dinner", "Lunch" and so on. @throws IllegalArgumentException for
	 * anything else
	 */
	public static MealSlot fromText(String text) {
		String t = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
		for (MealSlot slot : values()) {
			if (slot.name().toLowerCase(Locale.ROOT).equals(t)) {
				return slot;
			}
		}
		throw new IllegalArgumentException("Unknown meal '" + text + "'. Choose breakfast, lunch or dinner.");
	}

	/** Reads "dinner", "lunch,dinner" or "all". Blank means lunch and dinner. */
	public static List<MealSlot> listFromText(String text) {
		String t = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
		if (t.isEmpty()) {
			return List.of(LUNCH, DINNER);
		}
		if (t.equals("all")) {
			return List.of(values());
		}
		List<MealSlot> slots = new ArrayList<>();
		for (String part : t.split("[,\\s]+")) {
			MealSlot slot = fromText(part);
			if (!slots.contains(slot)) {
				slots.add(slot);
			}
		}
		slots.sort(null); // breakfast, lunch, dinner order
		return slots;
	}
}