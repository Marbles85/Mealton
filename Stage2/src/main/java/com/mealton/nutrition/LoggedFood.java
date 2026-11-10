package com.mealton.nutrition;

import java.time.LocalDate;

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
 */
public record LoggedFood(long id, LocalDate date, String description, Nutrients nutrients, boolean estimated) {

	@Override
	public String toString() {
		return "#" + id + " " + description + ": " + nutrients.summary() + (estimated ? " (estimate)" : "");
	}
}