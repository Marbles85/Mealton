package com.mealton.plan;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mealton.profile.ProfileService;
import com.mealton.recipe.ProfileCheck;
import com.mealton.recipe.Recipe;
import com.mealton.recipe.RecipeAdvisor;
import com.mealton.recipe.RecipeBook;
import com.mealton.recipe.RecipeIngredient;
import com.mealton.recipe.RecipeMatch;

/**
 * Builds and edits the weekly meal plan (feature F02) from the user's saved
 * recipes. Generating skips recipes that clash with the profile (allergies,
 * diet). Editing is free, but {@link #validate()} reports problems such as a
 * planned recipe that conflicts with the profile.
 */
public class PlanService {
	private static final int TOO_MANY_REPEATS = 3;

	private final RecipeBook book;
	private final RecipeAdvisor advisor;
	private final ProfileService profile;
	private WeekPlan plan = new WeekPlan();

	public PlanService(RecipeBook book, RecipeAdvisor advisor, ProfileService profile) {
		this.book = book;
		this.advisor = advisor;
		this.profile = profile;
	}

	/**
	 * Replaces the current plan with a new one.
	 *
	 * @param strategyName "variety" (default) or "pantry"
	 * @param slotsText    "dinner", "lunch,dinner", "all"; blank means lunch and
	 *                     dinner
	 */
	public synchronized WeekPlan generate(String strategyName, String slotsText) {
		List<MealSlot> slots = MealSlot.listFromText(slotsText);
		List<Recipe> all = book.getAll();
		if (all.isEmpty()) {
			throw new IllegalArgumentException("There are no saved recipes to plan with. Save some recipes first.");
		}
		List<Recipe> allowed = new ArrayList<>();
		for (Recipe recipe : all) {
			if (ProfileCheck.conflicts(recipe, profile.get()).isEmpty()) {
				allowed.add(recipe);
			}
		}
		if (allowed.isEmpty()) {
			throw new IllegalArgumentException("Every saved recipe conflicts with your allergies or diet, "
					+ "so nothing can be planned. Save more recipes or change your profile.");
		}
		PlanStrategy strategy = strategyFor(strategyName, allowed);
		WeekPlan fresh = new WeekPlan();
		for (DayOfWeek day : DayOfWeek.values()) {
			for (MealSlot slot : slots) {
				List<Recipe> candidates = new ArrayList<>(allowed);
				// avoid the same recipe twice in one day, unless it is the only choice
				List<Recipe> notToday = new ArrayList<>(candidates);
				notToday.removeIf(r -> fresh.day(day).recipes().stream().anyMatch(t -> t.getId() == r.getId()));
				if (!notToday.isEmpty()) {
					candidates = notToday;
				}
				fresh.day(day).set(slot, strategy.choose(candidates, fresh, day, slot));
			}
		}
		plan = fresh;
		return plan;
	}

	private PlanStrategy strategyFor(String name, List<Recipe> allowed) {
		String n = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
		switch (n) {
		case "":
		case "variety":
			return new VarietyStrategy();
		case "pantry":
			List<Recipe> ranked = new ArrayList<>();
			for (RecipeMatch match : advisor.suggest("best")) {
				ranked.add(match.recipe());
			}
			return new PantryFirstStrategy(ranked);
		default:
			throw new IllegalArgumentException("Unknown plan style '" + name + "'. Use 'variety' or 'pantry'.");
		}
	}

	/** The current plan (empty if none was made yet). */
	public synchronized WeekPlan current() {
		return plan;
	}

	/** Puts a recipe into one meal, replacing what was there. */
	public synchronized WeekPlan set(String dayText, String slotText, long recipeId) {
		DayOfWeek day = parseDay(dayText);
		MealSlot slot = MealSlot.fromText(slotText);
		Recipe recipe = book.get(recipeId);
		plan.day(day).set(slot, recipe);
		return plan;
	}

	/** Empties one meal. */
	public synchronized WeekPlan clearMeal(String dayText, String slotText) {
		DayOfWeek day = parseDay(dayText);
		MealSlot slot = MealSlot.fromText(slotText);
		if (!plan.day(day).remove(slot)) {
			throw new IllegalArgumentException(
					"Nothing is planned for " + dayText.trim() + " " + slot.label().toLowerCase(Locale.ROOT) + ".");
		}
		return plan;
	}

	/** Throws the whole plan away. */
	public synchronized void clearAll() {
		plan = new WeekPlan();
	}

	/** Problems with the current plan, or an empty list when it looks fine. */
	public synchronized List<String> validate() {
		List<String> problems = new ArrayList<>();
		if (plan.mealCount() == 0) {
			problems.add("The plan is empty.");
			return problems;
		}
		for (DayPlan day : plan.days()) {
			for (MealEntry entry : day.entries()) {
				for (String warning : ProfileCheck.conflicts(entry.recipe(), profile.get())) {
					problems.add(day.dayName() + " " + entry.slot().label().toLowerCase(Locale.ROOT) + " ("
							+ entry.recipe().getTitle() + "): " + warning);
				}
			}
		}
		for (Recipe recipe : plan.distinctRecipes()) {
			int times = plan.timesUsed(recipe.getId());
			if (times > TOO_MANY_REPEATS) {
				problems.add(recipe.getTitle() + " is planned " + times + " times this week.");
			}
		}
		return problems;
	}

	/**
	 * Puts what the planned recipes are missing on the shopping list. Each recipe
	 * is checked against the pantry and the list on its own, so an ingredient
	 * shared by two recipes is added once at the larger amount, not summed.
	 *
	 * @return what was added
	 */
	public synchronized List<RecipeIngredient> addMissingToShoppingList() {
		List<RecipeIngredient> added = new ArrayList<>();
		for (Recipe recipe : plan.distinctRecipes()) {
			added.addAll(advisor.addMissingToShoppingList(recipe.getId()));
		}
		return added;
	}

	/** Reads "monday", "Mon" and so on. */
	static DayOfWeek parseDay(String text) {
		String t = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
		for (DayOfWeek day : DayOfWeek.values()) {
			String name = day.name().toLowerCase(Locale.ROOT);
			if (t.length() >= 3 && name.startsWith(t)) {
				return day;
			}
		}
		throw new IllegalArgumentException("Unknown day '" + text + "'. Use monday ... sunday.");
	}
}