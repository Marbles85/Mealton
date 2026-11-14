package com.mealton.profile;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Who the user is and what they need from their food (Stage 1: UserProfile,
 * feature F01). It is a record, so it never changes: every "with..." method
 * returns a new, checked copy. A goal of 0 means "not set".
 *
 * @param name         optional display name
 * @param diet         the main eating style (derived from {@code restrictions}
 *                     when several are chosen)
 * @param allergies    foods to avoid, lower case (for example "peanut")
 * @param calorieGoal  daily calories, 0 or 500 to 10000
 * @param proteinGoal  daily protein in grams, 0 to 1000
 * @param carbsGoal    daily carbohydrates in grams, 0 to 1000
 * @param fatGoal      daily fat in grams, 0 to 1000
 * @param email        optional contact email
 * @param birthDate    date of birth as yyyy-MM-dd, or "" when not set
 * @param sex          "female", "male" or "" when not set
 * @param weightKg     body weight, 0 (not set) or 20 to 400
 * @param heightCm     height, 0 (not set) or 100 to 250
 * @param activity     how active the user is
 * @param goal         what the user wants their weight to do
 * @param restrictions every dietary restriction chosen (see
 *                     {@link #RESTRICTIONS})
 * @param trackMacros  false hides the macro tracker everywhere
 */
public record UserProfile(String name, Diet diet, Set<String> allergies, int calorieGoal, int proteinGoal,
		int carbsGoal, int fatGoal, String email, String birthDate, String sex, double weightKg, int heightCm,
		Activity activity, Goal goal, Set<String> restrictions, boolean trackMacros) {

	/** The dietary restrictions a user can choose, in display order. */
	public static final List<String> RESTRICTIONS = List.of("vegetarian", "vegan", "pescatarian", "gluten-free",
			"dairy-free", "halal", "high-protein", "low-carb", "keto");
	/** Main diets, most restrictive first (decides {@link #diet()}). */
	private static final List<Diet> MAIN_DIETS = List.of(Diet.VEGAN, Diet.VEGETARIAN, Diet.PESCATARIAN,
			Diet.GLUTEN_FREE, Diet.KETO);
	private static final DateTimeFormatter SHOWN = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
	private static final List<DateTimeFormatter> ACCEPTED = List.of(DateTimeFormatter.ISO_LOCAL_DATE,
			DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.ENGLISH),
			DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.ENGLISH),
			DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH),
			DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH),
			DateTimeFormatter.ofPattern("M/d/uuuu", Locale.ENGLISH));

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

		email = email == null ? "" : email.trim();
		if (email.length() > 80) {
			throw new IllegalArgumentException("The email is too long.");
		}
		birthDate = normalizeBirthDate(birthDate);
		sex = sex == null ? "" : sex.trim().toLowerCase(Locale.ROOT);
		if (!sex.isEmpty() && !sex.equals("female") && !sex.equals("male")) {
			throw new IllegalArgumentException("Sex must be female or male.");
		}
		if (weightKg != 0 && (Double.isNaN(weightKg) || weightKg < 20 || weightKg > 400)) {
			throw new IllegalArgumentException("Enter a weight between 20 and 400 kg.");
		}
		if (heightCm != 0 && (heightCm < 100 || heightCm > 250)) {
			throw new IllegalArgumentException("Enter a height between 100 and 250 cm.");
		}
		activity = activity == null ? Activity.LIGHT : activity;
		goal = goal == null ? Goal.MAINTAIN : goal;

		TreeSet<String> chosen = new TreeSet<>();
		if (restrictions != null) {
			for (String r : restrictions) {
				String key = r == null ? "" : r.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
				if (key.isEmpty()) {
					continue;
				}
				if (!RESTRICTIONS.contains(key)) {
					throw new IllegalArgumentException("Unknown dietary restriction '" + r.trim() + "'. Choose from: "
							+ String.join(", ", RESTRICTIONS) + ".");
				}
				chosen.add(key);
			}
		}
		if (diet != Diet.ANYTHING) {
			chosen.add(diet.label());
		}
		diet = Diet.ANYTHING;
		for (Diet main : MAIN_DIETS) {
			if (chosen.contains(main.label())) {
				diet = main;
				break;
			}
		}
		restrictions = Collections.unmodifiableSet(chosen);
	}

	/** The original seven-value form: the new fields get their defaults. */
	public UserProfile(String name, Diet diet, Set<String> allergies, int calorieGoal, int proteinGoal, int carbsGoal,
			int fatGoal) {
		this(name, diet, allergies, calorieGoal, proteinGoal, carbsGoal, fatGoal, "", "", "", 0, 0, Activity.LIGHT,
				Goal.MAINTAIN, Set.of(), true);
	}

	private static void checkGrams(int grams, String what) {
		if (grams < 0 || grams > 1000) {
			throw new IllegalArgumentException("The " + what + " goal must be between 0 and 1000 grams.");
		}
	}

	/**
	 * Reads a date such as "Mar 4, 2005" or "2005-03-04"; throws if it is not one.
	 */
	public static LocalDate parseDate(String text) {
		String t = text.trim();
		for (DateTimeFormatter f : ACCEPTED) {
			try {
				return LocalDate.parse(t, f);
			} catch (DateTimeParseException e) {
				// try the next format
			}
		}
		throw new IllegalArgumentException("Enter your date of birth like Mar 4, 2005.");
	}

	private static String normalizeBirthDate(String text) {
		if (text == null || text.isBlank()) {
			return "";
		}
		LocalDate date = parseDate(text);
		try {
			int age = Period.between(date, LocalDate.now()).getYears();
			if (date.isAfter(LocalDate.now()) || age < 13 || age > 120) {
				throw new IllegalArgumentException("Enter a date of birth that makes you between 13 and 120.");
			}
		} catch (DateTimeException e) {
			throw new IllegalArgumentException("Enter your date of birth like Mar 4, 2005.");
		}
		return date.toString();
	}

	/** A profile with nothing set. */
	public static UserProfile empty() {
		return new UserProfile("", Diet.ANYTHING, Set.of(), 0, 0, 0, 0);
	}

	/** The date of birth as shown to the user ("Mar 4, 2005"), or "". */
	public String birthDateText() {
		return birthDate.isEmpty() ? "" : LocalDate.parse(birthDate).format(SHOWN);
	}

	/** Age in whole years on this day, or null when no date of birth is set. */
	public Integer age(LocalDate today) {
		return birthDate.isEmpty() ? null : Period.between(LocalDate.parse(birthDate), today).getYears();
	}

	private UserProfile copy(String newName, Diet newDiet, Set<String> newAllergies, int calories, int protein,
			int carbs, int fat, Set<String> newRestrictions) {
		return new UserProfile(newName, newDiet, newAllergies, calories, protein, carbs, fat, email, birthDate, sex,
				weightKg, heightCm, activity, goal, newRestrictions, trackMacros);
	}

	public UserProfile withName(String newName) {
		return copy(newName, diet, allergies, calorieGoal, proteinGoal, carbsGoal, fatGoal, restrictions);
	}

	/**
	 * Makes this the one main diet: other main diets are dropped, extras such as
	 * dairy-free stay.
	 */
	public UserProfile withDiet(Diet newDiet) {
		Set<String> kept = new TreeSet<>(restrictions);
		for (Diet main : MAIN_DIETS) {
			kept.remove(main.label());
		}
		return copy(name, newDiet, allergies, calorieGoal, proteinGoal, carbsGoal, fatGoal, kept);
	}

	/** Replaces every dietary restriction (several can be chosen at once). */
	public UserProfile withRestrictions(Set<String> newRestrictions) {
		return copy(name, Diet.ANYTHING, allergies, calorieGoal, proteinGoal, carbsGoal, fatGoal, newRestrictions);
	}

	public UserProfile withAllergy(String allergy) {
		if (allergy == null || allergy.isBlank()) {
			throw new IllegalArgumentException("Give the name of the food to avoid, for example 'peanut'.");
		}
		Set<String> more = new TreeSet<>(allergies);
		more.add(allergy);
		return copy(name, diet, more, calorieGoal, proteinGoal, carbsGoal, fatGoal, restrictions);
	}

	public UserProfile withoutAllergy(String allergy) {
		String wanted = allergy == null ? "" : allergy.trim().toLowerCase(Locale.ROOT);
		if (!allergies.contains(wanted)) {
			throw new IllegalArgumentException("'" + wanted + "' is not on your allergy list.");
		}
		Set<String> fewer = new TreeSet<>(allergies);
		fewer.remove(wanted);
		return copy(name, diet, fewer, calorieGoal, proteinGoal, carbsGoal, fatGoal, restrictions);
	}

	public UserProfile withCalorieGoal(int calories) {
		return copy(name, diet, allergies, calories, proteinGoal, carbsGoal, fatGoal, restrictions);
	}

	public UserProfile withMacroGoals(int protein, int carbs, int fat) {
		return copy(name, diet, allergies, calorieGoal, protein, carbs, fat, restrictions);
	}

	public UserProfile withEmail(String newEmail) {
		return new UserProfile(name, diet, allergies, calorieGoal, proteinGoal, carbsGoal, fatGoal, newEmail, birthDate,
				sex, weightKg, heightCm, activity, goal, restrictions, trackMacros);
	}

	/** Sets the date of birth from text such as "Mar 4, 2005"; "" clears it. */
	public UserProfile withBirthDate(String text) {
		return new UserProfile(name, diet, allergies, calorieGoal, proteinGoal, carbsGoal, fatGoal, email, text, sex,
				weightKg, heightCm, activity, goal, restrictions, trackMacros);
	}

	public UserProfile withSex(String newSex) {
		return new UserProfile(name, diet, allergies, calorieGoal, proteinGoal, carbsGoal, fatGoal, email, birthDate,
				newSex, weightKg, heightCm, activity, goal, restrictions, trackMacros);
	}

	/** Sets weight and height (0 means not set). */
	public UserProfile withBody(double newWeightKg, int newHeightCm) {
		return new UserProfile(name, diet, allergies, calorieGoal, proteinGoal, carbsGoal, fatGoal, email, birthDate,
				sex, newWeightKg, newHeightCm, activity, goal, restrictions, trackMacros);
	}

	public UserProfile withActivity(Activity newActivity) {
		return new UserProfile(name, diet, allergies, calorieGoal, proteinGoal, carbsGoal, fatGoal, email, birthDate,
				sex, weightKg, heightCm, newActivity, goal, restrictions, trackMacros);
	}

	public UserProfile withGoal(Goal newGoal) {
		return new UserProfile(name, diet, allergies, calorieGoal, proteinGoal, carbsGoal, fatGoal, email, birthDate,
				sex, weightKg, heightCm, activity, newGoal, restrictions, trackMacros);
	}

	public UserProfile withTrackMacros(boolean track) {
		return new UserProfile(name, diet, allergies, calorieGoal, proteinGoal, carbsGoal, fatGoal, email, birthDate,
				sex, weightKg, heightCm, activity, goal, restrictions, track);
	}

	/**
	 * A short readable description, used by the CLI and shown to the AI as what it
	 * knows about the user.
	 */
	public String summary() {
		StringBuilder sb = new StringBuilder();
		sb.append("Name: ").append(name.isEmpty() ? "(not set)" : name);
		sb.append("; Diet: ").append(restrictions.isEmpty() ? diet.label() : String.join(", ", restrictions));
		sb.append("; Allergies: ").append(allergies.isEmpty() ? "none" : String.join(", ", allergies));
		if (weightKg > 0 || heightCm > 0) {
			sb.append("; Body: ").append(weightKg > 0 ? weightKg + " kg" : "weight not set");
			sb.append(", ").append(heightCm > 0 ? heightCm + " cm" : "height not set");
			sb.append("; Activity: ").append(activity.label()).append("; Goal: ").append(goal.label());
		}
		if (!trackMacros) {
			return sb.append("; Macro tracking: off").toString();
		}
		sb.append("; Daily goals: ");
		sb.append(calorieGoal == 0 ? "calories not set" : calorieGoal + " kcal");
		sb.append(", protein ").append(proteinGoal == 0 ? "not set" : proteinGoal + " g");
		sb.append(", carbs ").append(carbsGoal == 0 ? "not set" : carbsGoal + " g");
		sb.append(", fat ").append(fatGoal == 0 ? "not set" : fatGoal + " g");
		return sb.toString();
	}
}