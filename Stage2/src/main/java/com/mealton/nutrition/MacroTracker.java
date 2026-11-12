package com.mealton.nutrition;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.mealton.profile.UserProfile;
import com.mealton.util.Formats;

/**
 * The food diary (feature F04): log what you ate, see the day's totals and
 * compare them with the daily goals from the profile. Food with a known name
 * comes from the built-in {@link FoodTable}; anything else is logged with
 * numbers typed by the user or estimated by the AI, and is marked as an
 * estimate.
 */
public class MacroTracker {
	private final Supplier<UserProfile> profile;
	private final List<LoggedFood> entries = new ArrayList<>();
	private long nextId = 1;

	/**
	 * @param profile gives the current profile whenever goals are needed, so goal
	 *                changes show immediately
	 */
	public MacroTracker(Supplier<UserProfile> profile) {
		this.profile = profile;
	}

	/**
	 * Logs an amount of a food from the built-in table, for example ("egg", 2, "").
	 *
	 * @throws IllegalArgumentException if the food is not in the table (give the
	 *                                  numbers with {@link #logCustom}) or the unit
	 *                                  cannot be used for it
	 */
	public synchronized LoggedFood logFood(LocalDate date, String name, double quantity, String unit) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Give the name of the food.");
		}
		Nutrients nutrients = FoodTable.lookup(name, quantity, unit)
				.orElseThrow(() -> new IllegalArgumentException(
						"'" + name.trim() + "' is not in my food table. Log it with your own numbers "
								+ "(calories, protein, carbs, fat)."));
		return add(date, Formats.quantityWithUnit(quantity, unit) + " " + name.trim(), nutrients, false);
	}

	/** Logs a food with numbers given by the user or estimated by the AI. */
	public synchronized LoggedFood logCustom(LocalDate date, String description, Nutrients nutrients,
			boolean estimated) {
		if (description == null || description.isBlank()) {
			throw new IllegalArgumentException("Describe what was eaten, for example 'homemade lasagne'.");
		}
		return add(date, description.trim(), nutrients, estimated);
	}

	private LoggedFood add(LocalDate date, String description, Nutrients nutrients, boolean estimated) {
		if (date == null) {
			throw new IllegalArgumentException("A date is needed.");
		}
		LoggedFood entry = new LoggedFood(nextId++, date, description, nutrients, estimated);
		entries.add(entry);
		return entry;
	}

	/** Everything logged on a day, oldest first. */
	public synchronized List<LoggedFood> entriesOn(LocalDate date) {
		List<LoggedFood> result = new ArrayList<>();
		for (LoggedFood entry : entries) {
			if (entry.date().equals(date)) {
				result.add(entry);
			}
		}
		return result;
	}

	/** Every entry on every day, oldest first (used when saving). */
	public synchronized List<LoggedFood> allEntries() {
		return new ArrayList<>(entries);
	}

	/** Removes a logged entry, for example one that was logged by mistake. */
	public synchronized LoggedFood delete(long id) {
		for (int i = 0; i < entries.size(); i++) {
			if (entries.get(i).id() == id) {
				return entries.remove(i);
			}
		}
		throw new IllegalArgumentException("There is no logged food with id " + id + ".");
	}

	/** The day's totals compared with the profile's goals. */
	public synchronized DailyProgress progress(LocalDate date) {
		List<LoggedFood> day = entriesOn(date);
		Nutrients total = Nutrients.ZERO;
		for (LoggedFood entry : day) {
			total = total.plus(entry.nutrients());
		}
		return new DailyProgress(date, total, day.size(), profile.get());
	}
}