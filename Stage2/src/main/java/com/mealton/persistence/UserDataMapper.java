package com.mealton.persistence;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mealton.facade.MealtonFacade;
import com.mealton.nutrition.LoggedFood;
import com.mealton.nutrition.Nutrients;
import com.mealton.pantry.PantryItem;
import com.mealton.persistence.UserData.CalendarRow;
import com.mealton.persistence.UserData.DiaryRow;
import com.mealton.persistence.UserData.IngredientRow;
import com.mealton.persistence.UserData.LibraryRow;
import com.mealton.persistence.UserData.OwnRecipeRow;
import com.mealton.persistence.UserData.PantryRow;
import com.mealton.persistence.UserData.PlanRow;
import com.mealton.persistence.UserData.ProfileRow;
import com.mealton.persistence.UserData.ShoppingRow;
import com.mealton.plan.DayPlan;
import com.mealton.plan.MealEntry;
import com.mealton.profile.UserProfile;
import com.mealton.recipe.Recipe;
import com.mealton.recipe.RecipeIngredient;
import com.mealton.shopping.ShoppingItem;

/**
 * Copies a user's state out of a {@link MealtonFacade} into a {@link UserData}
 * and back again. It only uses the facade's public methods, so it works the
 * same for the GUI and the CLI.
 * <p>
 * Loading never crashes on one bad item: that item is skipped and described in
 * the returned warnings.
 */
public final class UserDataMapper {

	private UserDataMapper() {
		// utility class: nobody creates an instance
	}

	// ---------------- facade -> data ----------------

	public static UserData capture(MealtonFacade facade) {
		UserProfile p = facade.getProfile();
		ProfileRow profile = new ProfileRow(p.name(), p.diet().label(), new ArrayList<>(p.allergies()), p.calorieGoal(),
				p.proteinGoal(), p.carbsGoal(), p.fatGoal(), p.email(), p.birthDate(), p.sex(), p.weightKg(),
				p.heightCm(), p.activity().name(), p.goal().name(), new ArrayList<>(p.restrictions()), p.trackMacros());

		List<PantryRow> pantry = new ArrayList<>();
		for (PantryItem item : facade.getPantryItems()) {
			pantry.add(new PantryRow(item.getName(), item.getQuantity(), item.getUnit(), item.getCategory(),
					item.getExpiryDate() == null ? null : item.getExpiryDate().toString()));
		}

		List<ShoppingRow> shopping = new ArrayList<>();
		for (ShoppingItem item : facade.getShoppingItems()) {
			shopping.add(new ShoppingRow(item.getName(), item.getQuantity(), item.getUnit(), item.getCategory(),
					item.isChecked()));
		}

		List<LibraryRow> library = new ArrayList<>();
		List<OwnRecipeRow> own = new ArrayList<>();
		for (Recipe r : facade.getRecipes()) {
			if (r.isLibrary() && r.isDiscovered() && !r.isSaved()) {
				continue; // generated recipes you did not keep are simply generated again next time
			}
			if (r.isLibrary() && !r.isDiscovered()) {
				library.add(new LibraryRow(r.getTitle(), r.isSaved(), r.getRating(), r.getPhoto()));
			} else {
				List<IngredientRow> ingredients = new ArrayList<>();
				for (RecipeIngredient i : r.getIngredients()) {
					ingredients.add(new IngredientRow(i.name(), i.quantity(), i.unit()));
				}
				own.add(new OwnRecipeRow(r.getTitle(), r.getServings(), ingredients, new ArrayList<>(r.getSteps()),
						r.getRating(), r.getPrepMinutes(), r.getCookMinutes(), r.getSourceName(), r.getSourceUrl(),
						r.getPhoto(),
						r.getAiEstimate() == null ? null
								: new UserData.NutrientsRow(r.getAiEstimate().calories(), r.getAiEstimate().protein(),
										r.getAiEstimate().carbs(), r.getAiEstimate().fat()),
						new ArrayList<>(r.getAiTags()), new java.util.TreeMap<>(r.getAllStepIngredients()),
						r.getTotalOverride(), new java.util.TreeMap<>(r.getAllStepTimes()), r.getPublicRating(),
						r.getPublicRatingCount(), r.getFreshOverride()));
			}
		}

		List<PlanRow> plan = new ArrayList<>();
		for (DayPlan day : facade.getPlan().days()) {
			for (MealEntry entry : day.entries()) {
				plan.add(new PlanRow(day.day().getDisplayName(TextStyle.FULL, Locale.ENGLISH), entry.slot().label(),
						entry.recipe().getTitle()));
			}
		}

		List<CalendarRow> calendar = new ArrayList<>();
		for (LocalDate date : facade.getCalendarDates()) {
			for (MealEntry entry : facade.getCalendarMeals(date)) {
				calendar.add(new CalendarRow(date.toString(), entry.slot().label(), entry.recipe().getTitle()));
			}
		}

		List<DiaryRow> diary = new ArrayList<>();
		for (LoggedFood f : facade.getAllLoggedFood()) {
			Nutrients n = f.nutrients();
			diary.add(new DiaryRow(f.date().toString(), f.description(), n.calories(), n.protein(), n.carbs(), n.fat(),
					f.estimated(), f.meal(), f.time(), f.note(), f.source()));
		}

		return new UserData(UserData.CURRENT_VERSION, profile, pantry, shopping, library, own, plan, calendar, diary,
				new ArrayList<>(facade.getMealTags()));
	}

	// ---------------- data -> facade ----------------

	/**
	 * Puts saved data into a facade that already has the ready-made library recipes
	 * loaded.
	 *
	 * @return one warning per item that could not be restored (empty when
	 *         everything came back)
	 */
	public static List<String> restore(MealtonFacade facade, UserData data) {
		List<String> warnings = new ArrayList<>();
		if (data == null) {
			return warnings;
		}

		// recipes first: the plan and calendar refer to them
		for (LibraryRow row : orEmpty(data.libraryRecipes())) {
			attempt(warnings, "recipe '" + row.title() + "'", () -> {
				Recipe r = findByTitle(facade, row.title());
				if (r == null) {
					throw new IllegalArgumentException("it no longer comes with the app");
				}
				if (row.saved()) {
					facade.saveToMyRecipes(r.getId());
				}
				if (row.rating() > 0) {
					facade.rateRecipe(r.getId(), row.rating());
				}
				r.setPhoto(row.photo());
			});
		}
		for (OwnRecipeRow row : orEmpty(data.ownRecipes())) {
			attempt(warnings, "recipe '" + row.title() + "'", () -> {
				List<RecipeIngredient> ingredients = new ArrayList<>();
				for (IngredientRow i : orEmpty(row.ingredients())) {
					ingredients.add(new RecipeIngredient(i.name(), i.quantity(), i.unit()));
				}
				Recipe made = new Recipe(row.title(), row.servings(), ingredients, row.steps());
				made.setTimes(row.prepMinutes(), row.cookMinutes());
				made.setSource(row.sourceName(), row.sourceUrl());
				made.setPhoto(row.photo());
				if (row.aiEstimate() != null) {
					var e = row.aiEstimate();
					made.setAiEstimate(
							new com.mealton.nutrition.Nutrients(e.calories(), e.protein(), e.carbs(), e.fat()));
				}
				made.setAiTags(row.aiTags());
				if (row.stepUses() != null) {
					row.stepUses().forEach((step, names) -> {
						try {
							made.setStepIngredients(step, names);
						} catch (IllegalArgumentException outOfDate) {
							// a step that no longer exists: ignore
						}
					});
				}
				made.setTotalOverride(row.totalMinutes());
				try {
					made.setFreshOverride(row.freshDays());
				} catch (IllegalArgumentException outOfRange) {
					// keep the suggestion
				}
				if (row.publicRating() > 0) {
					made.setPublicRating(row.publicRating(), row.publicRatingCount());
				}
				if (row.stepTimes() != null) {
					row.stepTimes().forEach((step, minutes) -> {
						try {
							made.setStepTime(step, minutes);
						} catch (IllegalArgumentException outOfDate) {
							// a step that no longer exists: ignore
						}
					});
				}
				Recipe saved = facade.saveRecipe(made);
				if (row.rating() > 0) {
					facade.rateRecipe(saved.getId(), row.rating());
				}
			});
		}

		ProfileRow p = data.profile();
		if (p != null) {
			attempt(warnings, "profile", () -> {
				facade.setProfileName(p.name());
				facade.setDiet(p.diet());
				if (p.restrictions() != null) {
					facade.setRestrictions(new java.util.TreeSet<>(p.restrictions()));
				}
				for (String allergy : orEmpty(p.allergies())) {
					facade.addAllergy(allergy);
				}
				facade.setCalorieGoal(p.calories());
				facade.setMacroGoals(p.protein(), p.carbs(), p.fat());
				// each of these is checked on its own so one bad value cannot drop the rest
				attempt(warnings, "profile email", () -> facade.setProfileEmail(p.email()));
				attempt(warnings, "date of birth", () -> facade.setBirthDate(p.birthDate()));
				attempt(warnings, "sex", () -> facade.setSex(p.sex()));
				attempt(warnings, "weight and height", () -> facade.setBody(p.weightKg(), p.heightCm()));
				facade.setActivity(com.mealton.profile.Activity.fromText(p.activity()));
				facade.setGoal(com.mealton.profile.Goal.fromText(p.goal()));
				facade.setTrackMacros(p.tracksMacros());
			});
		}

		for (PantryRow row : orEmpty(data.pantry())) {
			attempt(warnings, "pantry item '" + row.name() + "'",
					() -> facade.addPantryItem(new PantryItem(row.name(), row.quantity(), row.unit(), row.category(),
							row.expires() == null ? null : LocalDate.parse(row.expires()))));
		}

		for (ShoppingRow row : orEmpty(data.shopping())) {
			attempt(warnings, "shopping item '" + row.name() + "'", () -> {
				ShoppingItem item = facade
						.addShoppingItem(new ShoppingItem(row.name(), row.quantity(), row.unit(), row.category()));
				if (row.checked()) {
					facade.toggleShoppingItem(item.getId());
				}
			});
		}
		facade.forgetShoppingHistory(); // loading must not look like something the user can undo

		for (PlanRow row : orEmpty(data.plan())) {
			attempt(warnings, "planned meal " + row.day() + " " + row.meal(), () -> {
				facade.setPlannedMeal(row.day(), row.meal(), idOf(facade, row.recipe()));
			});
		}

		for (CalendarRow row : orEmpty(data.calendar())) {
			attempt(warnings, "calendar meal " + row.date() + " " + row.meal(), () -> {
				facade.setCalendarMeal(LocalDate.parse(row.date()), row.meal(), idOf(facade, row.recipe()));
			});
		}

		for (String tag : orEmpty(data.mealTags())) {
			attempt(warnings, "meal tag '" + tag + "'", () -> facade.addMealTag(tag));
		}

		for (DiaryRow row : orEmpty(data.diary())) {
			attempt(warnings, "food diary entry '" + row.description() + "'",
					() -> facade.restoreLoggedEntry(new LoggedFood(0, LocalDate.parse(row.date()), row.description(),
							new Nutrients(row.calories(), row.protein(), row.carbs(), row.fat()), row.estimated(),
							row.meal(), row.time(), row.note(), row.source())));
		}
		return warnings;
	}

	private static long idOf(MealtonFacade facade, String title) {
		Recipe r = findByTitle(facade, title);
		if (r == null) {
			throw new IllegalArgumentException("the recipe '" + title + "' no longer exists");
		}
		return r.getId();
	}

	private static Recipe findByTitle(MealtonFacade facade, String title) {
		if (title == null) {
			return null;
		}
		for (Recipe r : facade.getRecipes()) {
			if (r.getTitle().equalsIgnoreCase(title.trim())) {
				return r;
			}
		}
		return null;
	}

	private static <T> List<T> orEmpty(List<T> list) {
		return list == null ? List.of() : list;
	}

	private static void attempt(List<String> warnings, String what, Runnable action) {
		try {
			action.run();
		} catch (RuntimeException e) { // IllegalArgumentException, DateTimeParseException, ...
			warnings.add("Could not restore " + what + ": " + e.getMessage());
		}
	}
}