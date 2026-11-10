package com.mealton.nutrition;

import java.time.LocalDate;

import com.mealton.profile.UserProfile;
import com.mealton.util.Formats;

/**
 * How one day's eating compares with the daily goals in the user's profile
 * (feature F04). A goal of 0 in the profile means "not set", and that line only
 * shows the total.
 *
 * @param date       the day
 * @param total      everything eaten that day
 * @param entryCount how many things were logged
 * @param goals      the profile the totals are compared with
 */
public record DailyProgress(LocalDate date, Nutrients total, int entryCount, UserProfile goals) {

	/**
	 * Calories left for the day; negative when over; null if there is no calorie
	 * goal.
	 */
	public Integer caloriesLeft() {
		return goals.calorieGoal() == 0 ? null : (int) Math.round(goals.calorieGoal() - total.calories());
	}

	/**
	 * One line per nutrient, for example "Calories: 1450 of 2000 kcal (550 left)".
	 */
	public String summary() {
		if (entryCount == 0) {
			return "Nothing logged for " + date + " yet.";
		}
		return "Eaten on " + date + " (" + entryCount + " item(s)):\n"
				+ line("Calories", total.calories(), goals.calorieGoal(), "kcal") + "\n"
				+ line("Protein", total.protein(), goals.proteinGoal(), "g") + "\n"
				+ line("Carbs", total.carbs(), goals.carbsGoal(), "g") + "\n"
				+ line("Fat", total.fat(), goals.fatGoal(), "g");
	}

	private static String line(String label, double eaten, int goal, String unit) {
		String text = "  " + label + ": " + Formats.number(Math.round(eaten * 10.0) / 10.0);
		if (goal == 0) {
			return text + " " + unit + " (no goal set)";
		}
		long difference = Math.round(goal - eaten);
		return text + " of " + goal + " " + unit
				+ (difference >= 0 ? " (" + difference + " left)" : " (" + (-difference) + " over)");
	}
}