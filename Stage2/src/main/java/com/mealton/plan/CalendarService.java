package com.mealton.plan;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
 * Calendar weeks run Sunday to Saturday; the plan's Monday lands on the Monday
 * of that calendar week. The calendar lives in memory while the program runs;
 * it is saved with the rest of the user's data (see UserDataMapper).
 */
public class CalendarService {
	private final PlanService planner;
	private final RecipeBook book;
	private final RecipeAdvisor advisor;
	private final ProfileService profile;
	private final Map<LocalDate, List<PlannedMeal>> days = new TreeMap<>();
	private final Map<LocalDate, PrepEvent> prepDays = new TreeMap<>();
	private final Deque<Snapshot> history = new ArrayDeque<>();
	private final Deque<Snapshot> redoStack = new ArrayDeque<>();

	/** A batch-cooking session placed on a date. */
	public record PrepEvent(LocalDate date, LocalTime start, int minutes, int meals) {
		public PrepEvent {
			if (date == null || start == null) {
				throw new IllegalArgumentException("Choose a prep date and a start time.");
			}
			if (minutes < 1 || minutes > 1440) {
				throw new IllegalArgumentException("A prep session has to be between 1 minute and 24 hours.");
			}
		}

		public LocalTime end() {
			return start.plusMinutes(minutes);
		}
	}

	private record Snapshot(Map<LocalDate, List<PlannedMeal>> meals, Map<LocalDate, PrepEvent> prep) {
	}

	/**
	 * Usual time of day of a tag, used to keep a day in order (unknown tags go
	 * last).
	 */
	private static final List<String> TAG_ORDER = List.of("breakfast", "lunch", "snack", "post-workout", "dinner");

	public CalendarService(PlanService planner, RecipeBook book, RecipeAdvisor advisor, ProfileService profile) {
		this.planner = planner;
		this.book = book;
		this.advisor = advisor;
		this.profile = profile;
	}

	/**
	 * The Sunday that starts the week containing this date (weeks run Sunday to
	 * Saturday).
	 */
	public static LocalDate weekStart(LocalDate date) {
		if (date == null) {
			throw new IllegalArgumentException("Choose a date first.");
		}
		return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
	}

	// ---------------- undo

	private Snapshot snapshot() {
		Map<LocalDate, List<PlannedMeal>> copy = new TreeMap<>();
		for (Map.Entry<LocalDate, List<PlannedMeal>> e : days.entrySet()) {
			copy.put(e.getKey(), new ArrayList<>(e.getValue()));
		}
		return new Snapshot(copy, new TreeMap<>(prepDays));
	}

	private void remember() {
		redoStack.clear(); // a new change ends the chance to redo
		history.push(snapshot());
		while (history.size() > 50) {
			history.removeLast();
		}
	}

	public synchronized boolean canUndo() {
		return !history.isEmpty();
	}

	/** Puts the calendar back the way it was before the last change. */
	public synchronized boolean undo() {
		if (history.isEmpty()) {
			return false;
		}
		redoStack.push(snapshot());
		Snapshot s = history.pop();
		days.clear();
		days.putAll(s.meals());
		prepDays.clear();
		prepDays.putAll(s.prep());
		return true;
	}

	public synchronized boolean canRedo() {
		return !redoStack.isEmpty();
	}

	/** Puts back what the last Undo took away. */
	public synchronized boolean redo() {
		if (redoStack.isEmpty()) {
			return false;
		}
		history.push(snapshot());
		Snapshot s = redoStack.pop();
		days.clear();
		days.putAll(s.meals());
		prepDays.clear();
		prepDays.putAll(s.prep());
		return true;
	}

	// ---------------- prep days

	/** The prep session on a date, or null. */
	public synchronized PrepEvent prepOn(LocalDate date) {
		return prepDays.get(date);
	}

	public synchronized List<PrepEvent> prepEvents() {
		return new ArrayList<>(prepDays.values());
	}

	/**
	 * Puts a prep session on the calendar (one Undo step). It replaces any session
	 * on that date.
	 */
	public synchronized void setPrep(PrepEvent event) {
		remember();
		prepDays.put(event.date(), event);
	}

	/**
	 * Moves or edits a prep session as one Undo step: the session that was on
	 * {@code oldDate} (if any) is taken off and {@code event} goes on its date.
	 */
	public synchronized void movePrep(LocalDate oldDate, PrepEvent event) {
		remember();
		if (oldDate != null) {
			prepDays.remove(oldDate);
		}
		prepDays.put(event.date(), event);
	}

	public synchronized boolean clearPrep(LocalDate date) {
		if (!prepDays.containsKey(date)) {
			return false;
		}
		remember();
		prepDays.remove(date);
		return true;
	}

	/** Loads a saved session without making an Undo step. */
	public synchronized void restorePrep(PrepEvent event) {
		prepDays.put(event.date(), event);
	}

	/** Forgets the undo steps (used after loading saved meals). */
	public synchronized void forgetHistory() {
		history.clear();
		redoStack.clear();
	}

	// ---------------- tagged meals (the calendar page)

	/**
	 * Every meal planned on a date, in the order shown. Deleted recipes are left
	 * out.
	 */
	public synchronized List<PlannedMeal> plannedOn(LocalDate date) {
		List<PlannedMeal> out = new ArrayList<>();
		for (PlannedMeal m : days.getOrDefault(requireDate(date), List.of())) {
			if (exists(m.recipe())) {
				out.add(m);
			}
		}
		return out;
	}

	private List<PlannedMeal> listFor(LocalDate date) {
		return days.computeIfAbsent(requireDate(date), d -> new ArrayList<>());
	}

	private void tidy(LocalDate date) {
		List<PlannedMeal> list = days.get(date);
		if (list != null && list.isEmpty()) {
			days.remove(date);
		}
	}

	private static String cleanTag(String tag) {
		String t = tag == null ? "" : tag.trim();
		if (t.length() > 20) {
			throw new IllegalArgumentException("A tag can be at most 20 characters.");
		}
		for (MealSlot slot : MealSlot.values()) {
			if (slot.label().equalsIgnoreCase(t)) {
				return slot.label();
			}
		}
		return t;
	}

	private static int rank(PlannedMeal m) {
		int k = TAG_ORDER.indexOf(m.tag().toLowerCase(Locale.ROOT));
		return k >= 0 ? k : TAG_ORDER.size();
	}

	private void sortDay(List<PlannedMeal> list) {
		list.sort((a, b) -> Integer.compare(rank(a), rank(b))); // stable: equal ranks keep their order
	}

	/**
	 * Adds a meal to a date and keeps the day in time order. @return where it ended
	 * up
	 */
	public synchronized int add(LocalDate date, String tag, long recipeId, int servings) {
		PlannedMeal meal = new PlannedMeal(cleanTag(tag), book.get(recipeId), servings);
		remember();
		List<PlannedMeal> list = listFor(date);
		list.add(meal);
		sortDay(list);
		return list.lastIndexOf(meal);
	}

	/** Puts another recipe into an existing meal (keeps its tag and servings). */
	public synchronized void replace(LocalDate date, int index, long recipeId) {
		List<PlannedMeal> list = visible(date, index);
		remember();
		PlannedMeal old = list.get(index);
		list.set(index, old.withRecipe(book.get(recipeId)));
	}

	public synchronized void remove(LocalDate date, int index) {
		visible(date, index);
		remember();
		days.get(date).remove(index);
		tidy(date);
	}

	public synchronized void setTag(LocalDate date, int index, String tag) {
		List<PlannedMeal> list = visible(date, index);
		String t = cleanTag(tag);
		remember();
		list.set(index, list.get(index).withTag(t));
		sortDay(list);
	}

	/**
	 * Gives every meal tagged {@code from} the tag {@code to} (an empty string
	 * removes the tag). One Undo step; nothing is recorded when no meal has it.
	 *
	 * @return how many meals changed
	 */
	public synchronized int retag(String from, String to) {
		String target = cleanTag(to);
		int count = 0;
		for (List<PlannedMeal> list : days.values()) {
			for (PlannedMeal m : list) {
				if (m.tag().equalsIgnoreCase(from)) {
					count++;
				}
			}
		}
		if (count == 0) {
			return 0;
		}
		remember();
		for (List<PlannedMeal> list : days.values()) {
			for (int i = 0; i < list.size(); i++) {
				if (list.get(i).tag().equalsIgnoreCase(from)) {
					list.set(i, list.get(i).withTag(target));
				}
			}
			sortDay(list);
		}
		return count;
	}

	public synchronized void setServings(LocalDate date, int index, int servings) {
		List<PlannedMeal> list = visible(date, index);
		PlannedMeal changed = list.get(index).withServings(servings);
		remember();
		list.set(index, changed);
	}

	/**
	 * Moves a meal to a place in another (or the same) day. Meals keep their tag.
	 *
	 * @param toIndex the position it should take, or -1 to add it to the day in
	 *                time order
	 */
	public synchronized void move(LocalDate from, int fromIndex, LocalDate to, int toIndex) {
		List<PlannedMeal> source = visible(from, fromIndex);
		requireDate(to);
		remember();
		PlannedMeal moving = source.remove(fromIndex);
		List<PlannedMeal> target = listFor(to);
		if (toIndex < 0) {
			target.add(moving);
			sortDay(target);
		} else {
			int at = Math.min(toIndex, target.size());
			if (from.equals(to) && fromIndex < toIndex) {
				at = Math.max(0, at - 1);
			}
			target.add(Math.min(at, target.size()), moving);
		}
		tidy(from);
	}

	/** Swaps the recipes (and servings) of two meals; each keeps its own tag. */
	public synchronized void swap(LocalDate a, int ai, LocalDate b, int bi) {
		List<PlannedMeal> la = visible(a, ai);
		List<PlannedMeal> lb = visible(b, bi);
		if (la == lb && ai == bi) {
			return;
		}
		remember();
		PlannedMeal ma = la.get(ai);
		PlannedMeal mb = lb.get(bi);
		la.set(ai, new PlannedMeal(ma.tag(), mb.recipe(), mb.servings()));
		lb.set(bi, new PlannedMeal(mb.tag(), ma.recipe(), ma.servings()));
	}

	/**
	 * The raw list of a date after checking that the index points at a real meal.
	 */
	private List<PlannedMeal> visible(LocalDate date, int index) {
		List<PlannedMeal> list = days.get(requireDate(date));
		if (list == null || index < 0 || index >= list.size()) {
			throw new IllegalArgumentException("There is no meal there any more.");
		}
		return list;
	}

	/**
	 * Puts a drafted plan on the calendar as one undoable step. On each date, the
	 * meals with the same tags as the draft are replaced; meals with other tags
	 * stay.
	 *
	 * @return how many meals were added
	 */
	public synchronized int applyDraft(Map<LocalDate, List<PlannedMeal>> draft) {
		remember();
		int added = 0;
		for (Map.Entry<LocalDate, List<PlannedMeal>> e : draft.entrySet()) {
			List<PlannedMeal> list = listFor(e.getKey());
			java.util.Set<String> tags = new java.util.HashSet<>();
			for (PlannedMeal m : e.getValue()) {
				tags.add(m.tag());
			}
			list.removeIf(m -> tags.contains(m.tag()));
			list.addAll(e.getValue());
			sortDay(list);
			added += e.getValue().size();
			tidy(e.getKey());
		}
		return added;
	}

	/** Every meal on the calendar, for the "what recipes are planned" checks. */
	public synchronized List<Recipe> recipesOn(LocalDate date) {
		List<Recipe> out = new ArrayList<>();
		for (PlannedMeal m : plannedOn(date)) {
			out.add(m.recipe());
		}
		return out;
	}

	// ---------------- the older, slot-based calls (CLI, weekly plan)

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
		remember();
		LocalDate monday = weekStart(anyDateInWeek);
		int loaded = 0;
		List<DayPlan> planDays = plan.days();
		for (int i = 0; i < planDays.size(); i++) {
			// the plan runs Monday..Sunday; the calendar week starts on Sunday, so Monday
			// is the 2nd column
			LocalDate date = monday.plusDays(1 + i % 7).minusDays(i % 7 == 6 ? 7 : 0);
			days.remove(date);
			for (MealEntry entry : planDays.get(i).entries()) {
				listFor(date).add(new PlannedMeal(entry.slot().label(), entry.recipe(), 1));
				loaded++;
			}
		}
		return loaded;
	}

	/**
	 * The meals planned on one date under Breakfast, Lunch or Dinner, breakfast
	 * first (meals with other tags are on {@link #plannedOn}).
	 */
	public synchronized List<MealEntry> mealsOn(LocalDate date) {
		List<MealEntry> entries = new ArrayList<>();
		for (MealSlot slot : MealSlot.values()) {
			for (PlannedMeal m : plannedOn(date)) {
				if (m.tag().equals(slot.label())) {
					entries.add(new MealEntry(slot, m.recipe()));
					break;
				}
			}
		}
		return entries;
	}

	/** Every date that has at least one meal, in date order (used when saving). */
	public synchronized List<LocalDate> plannedDates() {
		List<LocalDate> dates = new ArrayList<>();
		for (LocalDate d : days.keySet()) {
			if (!plannedOn(d).isEmpty()) {
				dates.add(d);
			}
		}
		return dates;
	}

	public synchronized int mealCountOn(LocalDate date) {
		return plannedOn(date).size();
	}

	/**
	 * Puts a recipe into the Breakfast, Lunch or Dinner meal on a date, replacing
	 * what was there.
	 */
	public synchronized void set(LocalDate date, String slotText, long recipeId) {
		MealSlot slot = MealSlot.fromText(slotText);
		Recipe recipe = book.get(recipeId);
		requireDate(date);
		remember();
		List<PlannedMeal> list = listFor(date);
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i).tag().equals(slot.label())) {
				list.set(i, list.get(i).withRecipe(recipe));
				return;
			}
		}
		list.add(new PlannedMeal(slot.label(), recipe, 1));
		sortDay(list);
	}

	/** Empties one meal on a date. */
	public synchronized void clear(LocalDate date, String slotText) {
		MealSlot slot = MealSlot.fromText(slotText);
		List<PlannedMeal> list = days.get(requireDate(date));
		int at = -1;
		if (list != null) {
			for (int i = 0; i < list.size(); i++) {
				if (list.get(i).tag().equals(slot.label())) {
					at = i;
					break;
				}
			}
		}
		if (at < 0) {
			throw new IllegalArgumentException(
					"Nothing is planned for " + date + " " + slot.label().toLowerCase(Locale.ROOT) + ".");
		}
		remember();
		list.remove(at);
		tidy(date);
	}

	/**
	 * Empties the whole week (Sunday to Saturday) that contains this date. @return
	 * how many meals were removed
	 */
	public synchronized int clearWeek(LocalDate anyDateInWeek) {
		LocalDate monday = weekStart(anyDateInWeek);
		int removed = 0;
		remember();
		for (int i = 0; i < 7; i++) {
			removed += mealCountOn(monday.plusDays(i));
			days.remove(monday.plusDays(i));
		}
		return removed;
	}

	public synchronized void clearAll() {
		days.clear();
		history.clear();
	}

	/** Meals in the week (Sunday to Saturday) that contains this date. */
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
		for (PlannedMeal meal : plannedOn(date)) {
			for (String warning : ProfileCheck.conflicts(meal.recipe(), profile.get())) {
				warnings.add((meal.tag().isEmpty() ? "Meal" : meal.tag()) + " (" + meal.recipe().getTitle() + "): "
						+ warning);
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
			for (PlannedMeal meal : plannedOn(monday.plusDays(i))) {
				distinct.putIfAbsent(meal.recipe().getId(), meal.recipe());
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