package com.mealton.persistence;

import java.util.List;

/**
 * Everything saved for one user, in a form that is easy to write as JSON (plain
 * records, dates as text). Recipes in the plan and calendar are saved by title,
 * so they still match after the recipes are loaded again.
 */
public record UserData(int version, ProfileRow profile, List<PantryRow> pantry, List<ShoppingRow> shopping,
		List<LibraryRow> libraryRecipes, List<OwnRecipeRow> ownRecipes, List<PlanRow> plan, List<CalendarRow> calendar,
		List<DiaryRow> diary) {

	public static final int CURRENT_VERSION = 1;

	/**
	 * The profile. Files saved before the Profile page was rebuilt only have the
	 * first seven values; the rest then load as "not set" (restrictions as null,
	 * trackMacros as true through {@link #tracksMacros()}).
	 */
	public record ProfileRow(String name, String diet, List<String> allergies, int calories, int protein, int carbs,
			int fat, String email, String birthDate, String sex, double weightKg, int heightCm, String activity,
			String goal, List<String> restrictions, Boolean trackMacros) {

		/** The old seven-value form. */
		public ProfileRow(String name, String diet, List<String> allergies, int calories, int protein, int carbs,
				int fat) {
			this(name, diet, allergies, calories, protein, carbs, fat, "", "", "", 0, 0, null, null, null, null);
		}

		/** Macro tracking is on unless the file says it was switched off. */
		public boolean tracksMacros() {
			return trackMacros == null || trackMacros;
		}
	}

	public record PantryRow(String name, double quantity, String unit, String category, String expires) {
	}

	public record ShoppingRow(String name, double quantity, String unit, String category, boolean checked) {
	}

	/**
	 * A recipe that comes with the app: only what the user changed (saved heart,
	 * stars).
	 */
	public record LibraryRow(String title, boolean saved, int rating, String photo) {
	}

	public record IngredientRow(String name, double quantity, String unit) {
	}

	public record OwnRecipeRow(String title, int servings, List<IngredientRow> ingredients, List<String> steps,
			int rating, int prepMinutes, int cookMinutes, String sourceName, String sourceUrl, String photo,
			NutrientsRow aiEstimate, List<String> aiTags, java.util.Map<Integer, List<String>> stepUses,
			int totalMinutes, java.util.Map<Integer, Integer> stepTimes, double publicRating, int publicRatingCount) {
	}

	/** Claude's nutrition estimate for a whole recipe. */
	public record NutrientsRow(double calories, double protein, double carbs, double fat) {
	}

	public record PlanRow(String day, String meal, String recipe) {
	}

	public record CalendarRow(String date, String meal, String recipe) {
	}

	public record DiaryRow(String date, String description, double calories, double protein, double carbs, double fat,
			boolean estimated) {
	}
}