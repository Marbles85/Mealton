package com.mealton.profile;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Who the user is and what they need from their food (Stage 1: UserProfile,
 * feature F01). It is a record, so it never changes: every "with..." method
 * returns a new, checked copy. A goal of 0 means "not set".
 *
 * @param name        optional display name
 * @param diet        eating style
 * @param allergies   foods to avoid, lower case (for example "peanut")
 * @param calorieGoal daily calories, 0 or 500 to 10000
 * @param proteinGoal daily protein in grams, 0 to 1000
 * @param carbsGoal   daily carbohydrates in grams, 0 to 1000
 * @param fatGoal     daily fat in grams, 0 to 1000
 */
public record UserProfile(String name, Diet diet, Set<String> allergies, int calorieGoal, int proteinGoal,
		int carbsGoal, int fatGoal) {

	public UserProfile {
		name = name == null ? "" : name.trim();
		diet = diet == null ? Diet.ANYTHING : diet;
		TreeSet<String> cleaned = new TreeSet<>();
		if (allergies != null) {
			for (String allergy : allergies) {
				if (allergy != null && !allergy.isBlank()) {
					cleaned.add(allergy.trim().toLowerCase(Locale.ROOT));
				}
			}
		}
		allergies = Collections.unmodifiableSet(cleaned);
		if (calorieGoal != 0 && (calorieGoal < 500 || calorieGoal > 10000)) {
			throw new IllegalArgumentException("The calorie goal must be between 500 and 10000 (or 0 for none).");
		}
		checkGrams(proteinGoal, "protein");
		checkGrams(carbsGoal, "carbohydrate");
		checkGrams(fatGoal, "fat");
	}

	private static void checkGrams(int grams, String what) {
		if (grams < 0 || grams > 1000) {
			throw new IllegalArgumentException("The " + what + " goal must be between 0 and 1000 grams.");
		}
	}

	/** A profile with nothing set. */
	public static UserProfile empty() {
		return new UserProfile("", Diet.ANYTHING, Set.of(), 0, 0, 0, 0);
	}

	public UserProfile withName(String newName) {
		return new UserProfile(newName, diet, allergies, calorieGoal, proteinGoal, carbsGoal, fatGoal);
	}

	public UserProfile withDiet(Diet newDiet) {
		return new UserProfile(name, newDiet, allergies, calorieGoal, proteinGoal, carbsGoal, fatGoal);
	}

	public UserProfile withAllergy(String allergy) {
		if (allergy == null || allergy.isBlank()) {
			throw new IllegalArgumentException("Give the name of the food to avoid, for example 'peanut'.");
		}
		Set<String> more = new TreeSet<>(allergies);
		more.add(allergy);
		return new UserProfile(name, diet, more, calorieGoal, proteinGoal, carbsGoal, fatGoal);
	}

	public UserProfile withoutAllergy(String allergy) {
		String wanted = allergy == null ? "" : allergy.trim().toLowerCase(Locale.ROOT);
		if (!allergies.contains(wanted)) {
			throw new IllegalArgumentException("'" + wanted + "' is not on your allergy list.");
		}
		Set<String> fewer = new TreeSet<>(allergies);
		fewer.remove(wanted);
		return new UserProfile(name, diet, fewer, calorieGoal, proteinGoal, carbsGoal, fatGoal);
	}

	public UserProfile withCalorieGoal(int calories) {
		return new UserProfile(name, diet, allergies, calories, proteinGoal, carbsGoal, fatGoal);
	}

	public UserProfile withMacroGoals(int protein, int carbs, int fat) {
		return new UserProfile(name, diet, allergies, calorieGoal, protein, carbs, fat);
	}

	/**
	 * A short readable description, used by the CLI and shown to the AI as what it
	 * knows about the user.
	 */
	public String summary() {
		StringBuilder sb = new StringBuilder();
		sb.append("Name: ").append(name.isEmpty() ? "(not set)" : name);
		sb.append("; Diet: ").append(diet.label());
		sb.append("; Allergies: ").append(allergies.isEmpty() ? "none" : String.join(", ", allergies));
		sb.append("; Daily goals: ");
		sb.append(calorieGoal == 0 ? "calories not set" : calorieGoal + " kcal");
		sb.append(", protein ").append(proteinGoal == 0 ? "not set" : proteinGoal + " g");
		sb.append(", carbs ").append(carbsGoal == 0 ? "not set" : carbsGoal + " g");
		sb.append(", fat ").append(fatGoal == 0 ? "not set" : fatGoal + " g");
		return sb.toString();
	}
}