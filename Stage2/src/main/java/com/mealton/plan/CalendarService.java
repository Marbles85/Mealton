package com.mealton.plan;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.mealton.profile.ProfileService;
import com.mealton.recipe.ProfileCheck;
import com.mealton.recipe.Recipe;
import com.mealton.recipe.RecipeAdvisor;
import com.mealton.recipe.RecipeBook;
import com.mealton.recipe.RecipeIngredient;

/**
 * The meal calendar (feature F03): meals on real dates. The weekly plan is
 * built on its own ({@link PlanService}) and is then <b>loaded</b> onto a week
 * of the calendar. After that, single meals can be changed, cleared or shopped
 * for without touching the plan.
 * <p>
 * Weeks always run Monday to Sunday, so the plan's Monday lands on a Monday of
 * the calendar. The calendar lives in memory while the program runs; it is
 * saved with the rest of the user's data (see UserDataMapper).
 */
public class CalendarService {
	private final PlanService planner;
	private final RecipeBook book;
	private final RecipeAdvisor advisor;
	private final ProfileService profile;
	private final Map<LocalDate, Map<MealSlot, Recipe>> days = new TreeMap<>();

	public CalendarService(PlanService planner, RecipeBook book, RecipeAdvisor advisor, ProfileService profile) {
		this.planner = planner;
		this.book = book;
		this.advisor = advisor;
		this.profile = profile;
	}

	/** The Monday of the week that contains this date. */
	public static LocalDate weekStart(LocalDate date) {
		if (date == null) {
			throw new IllegalArgumentException("Choose a date first.");
		}
		return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
	}

	/**
	 * Loads the current weekly plan onto the week that contains
	 * {@code anyDateInWeek}. Whatever was on those seven dates before is replaced.
	 *
	 * @return how many meals were loaded
	 */
	public synchronized int loadPlan(LocalDate anyDateInWeek) {
		WeekPlan plan = planner.current();
		if (plan.mealCount() == 0) {
			throw new IllegalArgumentException(
					"The plan is empty. Generate a plan first, then load it onto the calendar.");
		}
		LocalDate monday = weekStart(anyDateInWeek);
		int loaded = 0;
		List<DayPlan> planDays = plan.days();
		for (int i = 0; i < planDays.size(); i++) {
			LocalDate date = monday.plusDays(i);
			days.remove(date);
			for (MealEntry entry : planDays.get(i).entries()) {
				put(date, entry.slot(), entry.recipe());
				loaded++;
			}
		}
		return loaded;
	}

	/**
	 * The meals planned on one date, breakfast first. Recipes that were deleted
	 * meanwhile are left out.
	 */
	public synchronized List<MealEntry> mealsOn(LocalDate date) {
		List<MealEntry> entries = new ArrayList<>();
		Map<MealSlot, Recipe> day = days.get(requireDate(date));
		if (day == null) {
			return entries;
		}
		for (Map.Entry<MealSlot, Recipe> e : day.entrySet()) {
			if (exists(e.getValue())) {
				entries.add(new MealEntry(e.getKey(), e.getValue()));
			}
		}
		return entries;
	}

	/** Every date that has at least one meal, in date order (used when saving). */
	public synchronized List<LocalDate> plannedDates() {
		List<LocalDate> dates = new ArrayList<>();
		for (Map.Entry<LocalDate, Map<MealSlot, Recipe>> entry : days.entrySet()) {
			if (!entry.getValue().isEmpty()) {
				dates.add(entry.getKey());
			}
		}
		return dates;
	}

	public synchronized int mealCountOn(LocalDate date) {
		return mealsOn(date).size();
	}

	/** Puts a recipe into one meal on a date, replacing what was there. */
	public synchronized void set(LocalDate date, String slotText, long recipeId) {
		MealSlot slot = MealSlot.fromText(slotText);
		put(requireDate(date), slot, book.get(recipeId));
	}

	/** Empties one meal on a date. */
	public synchronized void clear(LocalDate date, String slotText) {
		MealSlot slot = MealSlot.fromText(slotText);
		Map<MealSlot, Recipe> day = days.get(requireDate(date));
		if (day == null || day.remove(slot) == null) {
			throw new IllegalArgumentException(
					"Nothing is planned for " + date + " " + slot.label().toLowerCase(Locale.ROOT) + ".");
		}
		if (day.isEmpty()) {
			days.remove(date);
		}
	}

	/**
	 * Empties the whole week (Monday to Sunday) that contains this date. @return
	 * how many meals were removed
	 */
	public synchronized int clearWeek(LocalDate anyDateInWeek) {
		LocalDate monday = weekStart(anyDateInWeek);
		int removed = 0;
		for (int i = 0; i < 7; i++) {
			removed += mealCountOn(monday.plusDays(i));
			days.remove(monday.plusDays(i));
		}
		return removed;
	}

	public synchronized void clearAll() {
		days.clear();
	}

	/** Meals in the week (Monday to Sunday) that contains this date. */
	public synchronized int mealsInWeek(LocalDate anyDateInWeek) {
		LocalDate monday = weekStart(anyDateInWeek);
		int count = 0;
		for (int i = 0; i < 7; i++) {
			count += mealCountOn(monday.plusDays(i));
		}
		return count;
	}

	/** Allergy and diet warnings for the meals on one date. */
	public synchronized List<String> warningsOn(LocalDate date) {
		List<String> warnings = new ArrayList<>();
		for (MealEntry entry : mealsOn(date)) {
			for (String warning : ProfileCheck.conflicts(entry.recipe(), profile.get())) {
				warnings.add(entry.slot().label() + " (" + entry.recipe().getTitle() + "): " + warning);
			}
		}
		return warnings;
	}

	/**
	 * Puts what the week's recipes are missing on the shopping list. Each recipe is
	 * checked against the pantry and the list on its own, so a shared ingredient is
	 * added once at the larger amount.
	 *
	 * @return what was added
	 */
	public synchronized List<RecipeIngredient> addWeekToShoppingList(LocalDate anyDateInWeek) {
		LocalDate monday = weekStart(anyDateInWeek);
		Map<Long, Recipe> distinct = new LinkedHashMap<>();
		for (int i = 0; i < 7; i++) {
			for (MealEntry entry : mealsOn(monday.plusDays(i))) {
				distinct.putIfAbsent(entry.recipe().getId(), entry.recipe());
			}
		}
		if (distinct.isEmpty()) {
			throw new IllegalArgumentException("There are no meals on the calendar that week.");
		}
		List<RecipeIngredient> added = new ArrayList<>();
		for (Recipe recipe : distinct.values()) {
			added.addAll(advisor.addMissingToShoppingList(recipe.getId()));
		}
		return added;
	}

	private void put(LocalDate date, MealSlot slot, Recipe recipe) {
		days.computeIfAbsent(date, d -> new EnumMap<>(MealSlot.class)).put(slot, recipe);
	}

	private static LocalDate requireDate(LocalDate date) {
		if (date == null) {
			throw new IllegalArgumentException("Choose a date first.");
		}
		return date;
	}

	private boolean exists(Recipe recipe) {
		try {
			book.get(recipe.getId());
			return true;
		} catch (IllegalArgumentException gone) {
			return false;
		}
	}
}