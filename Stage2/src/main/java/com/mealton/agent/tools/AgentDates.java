package com.mealton.agent.tools;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

/**
 * Reads the dates the LLM sends: 2026-11-16, today, tomorrow, or a weekday name
 * (the next one).
 */
final class AgentDates {

	private AgentDates() {
	}

	static LocalDate parse(String text, LocalDate today, LocalDate whenEmpty) {
		if (text == null || text.isBlank()) {
			return whenEmpty;
		}
		String t = text.trim().toLowerCase(Locale.ROOT);
		if (t.equals("today")) {
			return today;
		}
		if (t.equals("tomorrow")) {
			return today.plusDays(1);
		}
		for (DayOfWeek d : DayOfWeek.values()) {
			String full = d.getDisplayName(TextStyle.FULL, Locale.ENGLISH).toLowerCase(Locale.ROOT);
			if (t.equals(full) || t.equals(full.substring(0, 3))) {
				return today.with(TemporalAdjusters.nextOrSame(d));
			}
		}
		try {
			return LocalDate.parse(t);
		} catch (java.time.format.DateTimeParseException e) {
			throw new IllegalArgumentException(
					"'" + text + "' is not a date. Use 2026-11-16, today, tomorrow or a weekday.");
		}
	}

	static String show(LocalDate d) {
		return d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + d;
	}
}