package com.mealton.nutrition;

import java.time.LocalDate;
import java.util.List;

/**
 * One thing the user ate (feature F04).
 *
 * @param id          unique number, used to delete the entry
 * @param date        the day it was eaten
 * @param description for example "2 egg" or "homemade lasagne"
 * @param nutrients   what it adds up to
 * @param estimated   true when the numbers are an estimate (by the AI or typed
 *                    by the user), false when they came from the built-in food
 *                    table
 * @param meal        "Breakfast", "Lunch", "Dinner", "Snack", "Post-workout" or
 *                    "" when not said
 * @param time        the time it was logged as "HH:mm", or ""
 * @param note        what was different ("only used one egg"), or ""
 * @param source      "recipe", "pantry" or "custom" (or "" for old entries)
 */
public record LoggedFood(long id, LocalDate date, String description, Nutrients nutrients, boolean estimated,
		String meal, String time, String note, String source) {

	/** The meal types offered when logging, in order. */
	public static final List<String> MEALS = List.of("Breakfast", "Lunch", "Dinner", "Snack", "Post-workout");

	public LoggedFood {
		meal = meal == null ? "" : meal.trim();
		time = time == null ? "" : time.trim();
		note = note == null ? "" : note.trim();
		source = source == null ? "" : source.trim();
	}

	/** The form used before meals, times and notes existed. */
	public LoggedFood(long id, LocalDate date, String description, Nutrients nutrients, boolean estimated) {
		this(id, date, description, nutrients, estimated, "", "", "", "");
	}

	@Override
	public String toString() {
		return "#" + id + " " + (meal.isEmpty() ? "" : meal + ": ") + description + ": " + nutrients.summary()
				+ (estimated ? " (estimate)" : "");
	}
}