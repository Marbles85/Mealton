package com.mealton.profile;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

/**
 * Suggests daily calorie and macro targets from the profile (Mifflin-St Jeor).
 * <p>
 * BMR = 10 x weight(kg) + 6.25 x height(cm) - 5 x age + 5 (male) or -161
 * (female); when the sex is not set the average, -78, is used. Calories = BMR x
 * activity factor + the goal's adjustment, rounded to 5 and never below
 * {@link #MIN_CALORIES}. Macros: 27% protein, 29% fat, the rest carbohydrate.
 */
public final class TargetCalculator {
	/** Mealton never suggests (or plans) less than this per day. */
	public static final int MIN_CALORIES = 1200;
	private static final double PROTEIN_SHARE = 0.27;
	private static final double FAT_SHARE = 0.29;

	/**
	 * One suggestion.
	 *
	 * @param calories    the suggested daily calories (at least 1200)
	 * @param rawCalories what the maths gave before the 1200 minimum was applied
	 * @param capped      true when rawCalories was below the minimum
	 * @param bmr         basal metabolic rate, rounded
	 * @param factor      the activity factor used
	 * @param adjustment  calories added or taken for the goal
	 * @param maintenance BMR times the factor, rounded
	 */
	public record Suggestion(int calories, int rawCalories, boolean capped, int bmr, double factor, int adjustment,
			int maintenance, int protein, int carbs, int fat, Goal goal, boolean highProtein) {

		/** "Mifflin-St Jeor . BMR 1,465 x 1.375 - 500", as shown on the green card. */
		public String formulaText() {
			String adj = adjustment == 0 ? "" : (adjustment > 0 ? " + " : " − ") + Math.abs(adjustment);
			return "Mifflin–St Jeor · BMR " + fmt(bmr) + " × " + factor + adj;
		}

		/** The plain-language "Why this number" text. */
		public String explanation() {
			String goalWord = adjustment < 0 ? "lose weight steadily"
					: adjustment > 0 ? "gain gradually" : "stay where you are";
			return "Your body uses about " + fmt(maintenance) + " kcal a day at your activity level. To " + goalWord
					+ ", aim for " + fmt(calories) + " kcal"
					+ (highProtein ? ", with extra protein to match your high-protein preference." : ".");
		}
	}

	private TargetCalculator() {
	}

	/**
	 * A suggestion for this profile, or empty when weight, height or date of birth
	 * are missing.
	 */
	public static Optional<Suggestion> suggest(UserProfile p) {
		return suggest(p, LocalDate.now());
	}

	/** Same as {@link #suggest(UserProfile)} with a given "today" (for tests). */
	public static Optional<Suggestion> suggest(UserProfile p, LocalDate today) {
		Integer age = p.age(today);
		if (p.weightKg() <= 0 || p.heightCm() <= 0 || age == null) {
			return Optional.empty();
		}
		int sexTerm = "male".equals(p.sex()) ? 5 : "female".equals(p.sex()) ? -161 : -78;
		double bmrExact = 10 * p.weightKg() + 6.25 * p.heightCm() - 5 * age + sexTerm;
		int bmr = (int) Math.round(bmrExact);
		double factor = p.activity().factor();
		int maintenance = (int) Math.round(bmrExact * factor);
		int adjustment = p.goal().adjustment();
		int raw = (int) (Math.round((bmrExact * factor + adjustment) / 5.0) * 5);
		boolean capped = raw < MIN_CALORIES;
		int calories = capped ? MIN_CALORIES : raw;
		int protein = (int) Math.round(calories * PROTEIN_SHARE / 4);
		int fat = (int) Math.round(calories * FAT_SHARE / 9);
		int carbs = (int) Math.round((calories - protein * 4.0 - fat * 9.0) / 4);
		boolean highProtein = p.restrictions().contains("high-protein");
		return Optional.of(new Suggestion(calories, raw, capped, bmr, factor, adjustment, maintenance, protein, carbs,
				fat, p.goal(), highProtein));
	}

	/** Calories that the three macro numbers add up to (4 / 4 / 9 per gram). */
	public static int macroCalories(int protein, int carbs, int fat) {
		return protein * 4 + carbs * 4 + fat * 9;
	}

	/** 1465 -> "1,465". */
	public static String fmt(int n) {
		return NumberFormat.getIntegerInstance(Locale.US).format(n);
	}
}