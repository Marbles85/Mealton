package com.mealton.plan;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.mealton.nutrition.RecipeNutrition;
import com.mealton.pantry.PantryInventory;
import com.mealton.pantry.PantryItem;
import com.mealton.profile.Diet;
import com.mealton.profile.UserProfile;
import com.mealton.recipe.ProfileCheck;
import com.mealton.recipe.Recipe;
import com.mealton.recipe.RecipeAdvisor;
import com.mealton.recipe.RecipeBook;
import com.mealton.recipe.RecipeIngredient;

/**
 * Drafts meals for any range of dates (feature F02). The draft is only a
 * preview: nothing reaches the calendar until it is applied. Recipes that clash
 * with the profile are never used; the rest are scored by how often they were
 * already used in the draft, how close they are to the calorie target, the
 * cuisines asked for, the pantry and what was eaten last week.
 */
public final class RangePlanner {

	public static final int MAX_DAYS = 28;
	public static final List<String> TAGS = List.of("Breakfast", "Lunch", "Dinner");
	public static final List<String> SUGGESTED_CUISINES = List.of("Mediterranean", "Asian", "Mexican", "Italian",
			"Middle Eastern", "Comfort");

	/** What the user asked for. {@code diet} is null for "from profile". */
	public record Options(LocalDate start, LocalDate end, List<String> tags, Diet diet, boolean highProtein,
			Integer calories, Set<String> cuisines, boolean pantryFirst, boolean avoidRecent) {
	}

	/** One line of "What Mealton did". */
	public record Step(boolean ok, String title, String detail, String tool) {
	}

	/**
	 * A draft plan: meals per date (in time order) plus what was done to make it.
	 */
	public static final class Draft {
		private final Options options;
		private final Map<LocalDate, List<PlannedMeal>> meals = new LinkedHashMap<>();
		private final List<Step> steps = new ArrayList<>();
		private int expiringUsed;
		private int expiringTotal;
		private int toBuy;

		Draft(Options options) {
			this.options = options;
		}

		public Options options() {
			return options;
		}

		public Map<LocalDate, List<PlannedMeal>> meals() {
			return meals;
		}

		public List<Step> steps() {
			return steps;
		}

		public int mealCount() {
			return meals.values().stream().mapToInt(List::size).sum();
		}

		public int expiringUsed() {
			return expiringUsed;
		}

		public int expiringTotal() {
			return expiringTotal;
		}

		public int toBuy() {
			return toBuy;
		}

		public List<Recipe> distinctRecipes() {
			Map<Long, Recipe> out = new LinkedHashMap<>();
			for (List<PlannedMeal> day : meals.values()) {
				for (PlannedMeal m : day) {
					out.putIfAbsent(m.recipe().getId(), m.recipe());
				}
			}
			return new ArrayList<>(out.values());
		}

		/** Average calories per planned day, or 0 when nothing has nutrition data. */
		public int avgKcal() {
			double sum = 0;
			int days = 0;
			for (List<PlannedMeal> day : meals.values()) {
				double d = 0;
				for (PlannedMeal m : day) {
					RecipeNutrition n = RecipeNutrition.of(m.recipe());
					if (!n.counted().isEmpty()) {
						d += n.perServing().calories() * m.servings();
					}
				}
				if (d > 0) {
					sum += d;
					days++;
				}
			}
			return days == 0 ? 0 : (int) Math.round(sum / days);
		}
	}

	private final RecipeBook book;
	private final RecipeAdvisor advisor;
	private final com.mealton.profile.ProfileService profile;
	private final PantryInventory pantry;
	private final CalendarService calendar;
	private final Random random;

	public RangePlanner(RecipeBook book, RecipeAdvisor advisor, com.mealton.profile.ProfileService profile,
			PantryInventory pantry, CalendarService calendar, Random random) {
		this.book = book;
		this.advisor = advisor;
		this.profile = profile;
		this.pantry = pantry;
		this.calendar = calendar;
		this.random = random;
	}

	// ---------------- checking the dates

	/**
	 * The number of days from start to end, both included (0 when a date is
	 * missing).
	 */
	public static int days(LocalDate start, LocalDate end) {
		if (start == null || end == null) {
			return 0;
		}
		return (int) ChronoUnit.DAYS.between(start, end) + 1;
	}

	/** The message to show for a bad range, or an empty text when it is fine. */
	public static String rangeError(LocalDate start, LocalDate end) {
		if (start == null || end == null) {
			return "Pick both dates.";
		}
		int n = days(start, end);
		if (n < 1) {
			return "The end date has to be on or after the start date.";
		}
		if (n > MAX_DAYS) {
			return "Mealton can plan up to 4 weeks at a time — shorten the range.";
		}
		return "";
	}

	/** How many days of the range are before today (those are left alone). */
	public static int pastDays(LocalDate start, LocalDate end, LocalDate today) {
		if (rangeError(start, end).length() > 0 || !start.isBefore(today)) {
			return 0;
		}
		return (int) Math.min(days(start, end), ChronoUnit.DAYS.between(start, today));
	}

	// ---------------- drafting

	public Draft generate(Options o) {
		String error = rangeError(o.start(), o.end());
		if (!error.isEmpty()) {
			throw new IllegalArgumentException(error);
		}
		if (o.tags() == null || o.tags().isEmpty()) {
			throw new IllegalArgumentException("Pick at least one meal to plan (Breakfast, Lunch or Dinner).");
		}
		LocalDate today = pantry.today();
		if (o.end().isBefore(today)) {
			throw new IllegalArgumentException("Every one of those days has already passed.");
		}
		UserProfile used = effectiveProfile(o);
		List<Recipe> allowed = allowed(used);
		if (allowed.isEmpty()) {
			throw new IllegalArgumentException(
					"Every saved recipe conflicts with your allergies or diet, so nothing can be planned. "
							+ "Save more recipes or change your profile.");
		}
		Draft draft = new Draft(o);
		Set<Long> recent = o.avoidRecent() ? recentRecipes(o.start()) : Set.of();
		Map<Long, Integer> usedCount = new HashMap<>();
		LocalDate from = o.start().isBefore(today) ? today : o.start();
		for (LocalDate d = from; !d.isAfter(o.end()); d = d.plusDays(1)) {
			List<PlannedMeal> day = new ArrayList<>();
			for (String tag : orderedTags(o.tags())) {
				Recipe r = pick(allowed, tag, o, day, usedCount, recent, null);
				usedCount.merge(r.getId(), 1, Integer::sum);
				day.add(new PlannedMeal(tag, r, 1));
			}
			draft.meals.put(d, day);
		}
		describe(draft, used, allowed.size(), pastDays(o.start(), o.end(), today));
		return draft;
	}

	/** Another recipe for one meal of the draft (the one it had is avoided). */
	public void reroll(Draft draft, LocalDate date, int index) {
		List<PlannedMeal> day = draft.meals.get(date);
		if (day == null || index < 0 || index >= day.size()) {
			throw new IllegalArgumentException("There is no meal there any more.");
		}
		Options o = draft.options();
		PlannedMeal old = day.get(index);
		List<PlannedMeal> others = new ArrayList<>(day);
		others.remove(index);
		Recipe r = pick(allowed(effectiveProfile(o)), old.tag(), o, others, counts(draft, old), recentFor(o),
				old.recipe());
		day.set(index, new PlannedMeal(old.tag(), r, old.servings()));
		refreshNumbers(draft);
	}

	/**
	 * A different recipe for a meal that is already on the calendar, chosen the way
	 * the generator chooses (profile, allergies, pantry first, same tag). Nothing
	 * is changed.
	 *
	 * @throws IllegalArgumentException if there is no meal there or nothing else
	 *                                  fits
	 */
	public Recipe suggestFor(LocalDate date, int index) {
		List<PlannedMeal> day = calendar.plannedOn(date);
		if (index < 0 || index >= day.size()) {
			throw new IllegalArgumentException("There is no meal there any more.");
		}
		PlannedMeal old = day.get(index);
		Options o = new Options(date, date, List.of(old.tag()), null, false, null, Set.of(), true, false);
		List<PlannedMeal> others = new ArrayList<>(day);
		others.remove(index);
		return pick(allowed(effectiveProfile(o)), old.tag(), o, others, new HashMap<>(), Set.of(), old.recipe());
	}

	/**
	 * Makes new choices for every meal that is not kept. @param kept keys from
	 * {@link #key}
	 */
	public void regenerateRest(Draft draft, Set<String> kept) {
		Options o = draft.options();
		List<Recipe> allowed = allowed(effectiveProfile(o));
		Set<Long> recent = recentFor(o);
		Map<Long, Integer> usedCount = new HashMap<>();
		for (Map.Entry<LocalDate, List<PlannedMeal>> e : draft.meals.entrySet()) {
			for (int i = 0; i < e.getValue().size(); i++) {
				if (kept.contains(key(e.getKey(), i))) {
					usedCount.merge(e.getValue().get(i).recipe().getId(), 1, Integer::sum);
				}
			}
		}
		for (Map.Entry<LocalDate, List<PlannedMeal>> e : draft.meals.entrySet()) {
			List<PlannedMeal> day = e.getValue();
			for (int i = 0; i < day.size(); i++) {
				if (kept.contains(key(e.getKey(), i))) {
					continue;
				}
				PlannedMeal old = day.get(i);
				List<PlannedMeal> others = new ArrayList<>(day);
				others.remove(i);
				Recipe r = pick(allowed, old.tag(), o, others, usedCount, recent, old.recipe());
				usedCount.merge(r.getId(), 1, Integer::sum);
				day.set(i, new PlannedMeal(old.tag(), r, old.servings()));
			}
		}
		refreshNumbers(draft);
	}

	/**
	 * Moves a meal of the draft to another (or the same) day. The meal keeps its
	 * tag, recipe and servings.
	 *
	 * @param toIndex the position to take, or -1 to join the day in time order
	 * @throws IllegalArgumentException if either day is outside the drafted dates
	 *                                  or the meal is gone
	 */
	public void moveMeal(Draft draft, LocalDate from, int fromIndex, LocalDate to, int toIndex) {
		List<PlannedMeal> source = draft.meals.get(from);
		List<PlannedMeal> target = draft.meals.get(to);
		if (source == null || fromIndex < 0 || fromIndex >= source.size()) {
			throw new IllegalArgumentException("There is no meal there any more.");
		}
		if (target == null) {
			throw new IllegalArgumentException("Meals can only go on days inside the dates you chose.");
		}
		PlannedMeal moving = source.remove(fromIndex);
		if (toIndex < 0) {
			target.add(moving);
			target.sort((a, b) -> Integer.compare(order(a), order(b))); // stable
		} else {
			int at = Math.min(toIndex, target.size() + (source == target ? 1 : 0));
			if (source == target && fromIndex < toIndex) {
				at = Math.max(0, at - 1);
			}
			target.add(Math.min(at, target.size()), moving);
		}
		refreshNumbers(draft);
	}

	/**
	 * Swaps the recipes (and servings) of two drafted meals; each place keeps its
	 * own tag.
	 */
	public void swapMeals(Draft draft, LocalDate a, int ai, LocalDate b, int bi) {
		List<PlannedMeal> la = draft.meals.get(a);
		List<PlannedMeal> lb = draft.meals.get(b);
		if (la == null || lb == null || ai < 0 || bi < 0 || ai >= la.size() || bi >= lb.size()) {
			throw new IllegalArgumentException("There is no meal there any more.");
		}
		if (la == lb && ai == bi) {
			return;
		}
		PlannedMeal ma = la.get(ai);
		PlannedMeal mb = lb.get(bi);
		la.set(ai, new PlannedMeal(ma.tag(), mb.recipe(), mb.servings()));
		lb.set(bi, new PlannedMeal(mb.tag(), ma.recipe(), ma.servings()));
		refreshNumbers(draft);
	}

	private static final List<String> TIME_ORDER = List.of("breakfast", "lunch", "snack", "post-workout", "dinner");

	private static int order(PlannedMeal m) {
		int k = TIME_ORDER.indexOf(m.tag().toLowerCase(Locale.ROOT));
		return k >= 0 ? k : TIME_ORDER.size();
	}

	/** The key that names one meal of a draft (used for the "kept" set). */
	public static String key(LocalDate date, int index) {
		return date + "#" + index;
	}

	/**
	 * Puts the draft on the calendar as one undoable step. @return how many meals
	 * were added
	 */
	public int apply(Draft draft) {
		return calendar.applyDraft(draft.meals());
	}

	/**
	 * Puts what the draft's recipes are missing on the shopping list. @return how
	 * many ingredients were added
	 */
	public int addMissingToShoppingList(Draft draft) {
		int n = 0;
		for (Recipe r : draft.distinctRecipes()) {
			n += advisor.addMissingToShoppingList(r.getId()).size();
		}
		return n;
	}

	// ---------------- internals

	private UserProfile effectiveProfile(Options o) {
		UserProfile p = profile.get();
		if (o.diet() != null) {
			p = p.withDiet(o.diet());
		}
		return p;
	}

	private List<Recipe> allowed(UserProfile p) {
		List<Recipe> out = new ArrayList<>();
		for (Recipe r : book.getAll()) {
			if (ProfileCheck.conflicts(r, p).isEmpty()) {
				out.add(r);
			}
		}
		return out;
	}

	private Set<Long> recentFor(Options o) {
		return o.avoidRecent() ? recentRecipes(o.start()) : Set.of();
	}

	private Set<Long> recentRecipes(LocalDate start) {
		Set<Long> ids = new LinkedHashSet<>();
		for (int i = 1; i <= 7; i++) {
			for (PlannedMeal m : calendar.plannedOn(start.minusDays(i))) {
				ids.add(m.recipe().getId());
			}
		}
		return ids;
	}

	private Map<Long, Integer> counts(Draft draft, PlannedMeal leaving) {
		Map<Long, Integer> out = new HashMap<>();
		for (List<PlannedMeal> day : draft.meals.values()) {
			for (PlannedMeal m : day) {
				if (m != leaving) {
					out.merge(m.recipe().getId(), 1, Integer::sum);
				}
			}
		}
		return out;
	}

	private static List<String> orderedTags(List<String> tags) {
		List<String> out = new ArrayList<>();
		for (String t : TAGS) {
			if (tags.contains(t)) {
				out.add(t);
			}
		}
		return out;
	}

	private static final List<String> BREAKFAST_WORDS = List.of("oat", "pancake", "omelet", "omelette", "parfait",
			"toast", "granola", "smoothie", "porridge", "waffle", "breakfast", "scramble", "frittata", "shakshuka",
			"yogurt", "cereal", "muffin");

	static boolean looksLikeBreakfast(Recipe r) {
		String t = r.getTitle().toLowerCase(Locale.ROOT);
		for (String w : BREAKFAST_WORDS) {
			if (t.contains(w)) {
				return true;
			}
		}
		return false;
	}

	private Recipe pick(List<Recipe> allowed, String tag, Options o, List<PlannedMeal> sameDay, Map<Long, Integer> used,
			Set<Long> recent, Recipe avoid) {
		boolean breakfast = tag.equals("Breakfast");
		List<Recipe> pool = new ArrayList<>();
		for (Recipe r : allowed) {
			if (looksLikeBreakfast(r) == breakfast) {
				pool.add(r);
			}
		}
		if (pool.isEmpty()) {
			pool = new ArrayList<>(allowed);
		}
		List<Recipe> fresh = new ArrayList<>(pool);
		fresh.removeIf(r -> sameDay.stream().anyMatch(m -> m.recipe().getId() == r.getId())
				|| (avoid != null && r.getId() == avoid.getId()));
		if (!fresh.isEmpty()) {
			pool = fresh;
		}
		int target = o.calories() != null ? o.calories() : profile.get().calorieGoal();
		double share = breakfast ? 0.25 : tag.equals("Lunch") ? 0.35 : 0.40;
		List<String> expiring = new ArrayList<>();
		if (o.pantryFirst()) {
			for (PantryItem p : pantry.getExpiringItems()) {
				expiring.add(p.getName().toLowerCase(Locale.ROOT));
			}
		}
		Recipe best = null;
		double bestScore = Double.MAX_VALUE;
		for (Recipe r : pool) {
			double s = used.getOrDefault(r.getId(), 0) * 100.0;
			if (recent.contains(r.getId())) {
				s += 60;
			}
			if (!o.cuisines().isEmpty()) {
				s += matchesCuisine(r, o.cuisines()) ? -40 : 25;
			}
			s -= 30.0 * expiringHits(r, expiring);
			RecipeNutrition n = RecipeNutrition.of(r);
			if (!n.counted().isEmpty() && n.perServing().calories() > 0) {
				s += Math.abs(n.perServing().calories() - target * share) / 20.0;
				if (o.highProtein()) {
					s -= n.perServing().protein() / 2.0;
				}
			}
			s += random.nextDouble() * 10;
			if (s < bestScore) {
				bestScore = s;
				best = r;
			}
		}
		return best;
	}

	private static int expiringHits(Recipe r, List<String> expiring) {
		int hits = 0;
		for (String item : expiring) {
			for (RecipeIngredient i : r.getIngredients()) {
				String name = i.name().toLowerCase(Locale.ROOT);
				if (name.contains(item) || item.contains(name)) {
					hits++;
					break;
				}
			}
		}
		return hits;
	}

	private static final Map<String, List<String>> CUISINE_WORDS = Map.of("mediterranean",
			List.of("feta", "olive", "hummus", "greek", "tzatziki", "couscous", "pita", "fattoush", "shakshuka"),
			"asian", List.of("soy", "tofu", "ginger", "noodle", "stir-fry", "teriyaki", "sesame", "rice", "miso"),
			"mexican", List.of("taco", "tortilla", "salsa", "burrito", "cumin", "chili", "enchilada", "quesadilla"),
			"italian", List.of("pasta", "parmesan", "basil", "spaghetti", "pizza", "lasagna", "risotto"),
			"middle eastern", List.of("hummus", "falafel", "tahini", "sumac", "shakshuka", "pita", "fattoush"),
			"comfort", List.of("soup", "casserole", "mac", "stew", "chili", "pie", "pancake", "roast"));

	static boolean matchesCuisine(Recipe r, Set<String> cuisines) {
		StringBuilder text = new StringBuilder(r.getTitle().toLowerCase(Locale.ROOT));
		for (RecipeIngredient i : r.getIngredients()) {
			text.append(' ').append(i.name().toLowerCase(Locale.ROOT));
		}
		String all = text.toString();
		for (String c : cuisines) {
			String key = c.toLowerCase(Locale.ROOT);
			if (all.contains(key)) {
				return true;
			}
			for (String w : CUISINE_WORDS.getOrDefault(key, List.of())) {
				if (all.contains(w)) {
					return true;
				}
			}
		}
		return false;
	}

	private void describe(Draft draft, UserProfile used, int candidates, int pastDays) {
		Options o = draft.options();
		int target = o.calories() != null ? o.calories() : used.calorieGoal();
		String allergies = used.allergies().isEmpty() ? "no allergies"
				: "allergies: " + String.join(", ", used.allergies());
		draft.steps.add(new Step(true, "Read your profile", (target > 0 ? target + " kcal target · " : "") + allergies
				+ (o.diet() != null ? " · diet: " + o.diet().label() : ""), "profile"));
		List<PantryItem> expiring = pantry.getExpiringItems();
		List<String> names = new ArrayList<>();
		for (PantryItem p : expiring) {
			names.add(p.getName());
		}
		draft.steps.add(new Step(true, "Checked the pantry",
				pantry.getItems().size() + " items"
						+ (names.isEmpty() ? "" : " · " + String.join(", ", names) + " expire within 3 days"),
				"pantry"));
		draft.steps.add(new Step(true, "Searched recipes",
				candidates + " candidates"
						+ (o.cuisines().isEmpty() ? "" : " leaning toward " + String.join(", ", o.cuisines()))
						+ (o.avoidRecent() ? " · avoided last week’s meals" : "")
						+ (pastDays > 0 ? " · skipped " + pastDays + " past day" + (pastDays == 1 ? "" : "s") : ""),
				"recipes"));
		refreshNumbers(draft);
		draft.steps.add(new Step(true, "Checked nutrition",
				"Calories and macros for " + draft.distinctRecipes().size() + " selected recipes", "nutrition"));
		draft.steps.add(new Step(true, "All checks passed",
				"No allergen or diet clashes · about " + draft.avgKcal() + " kcal a day", "validate"));
	}

	private void refreshNumbers(Draft draft) {
		List<String> expiring = new ArrayList<>();
		for (PantryItem p : pantry.getExpiringItems()) {
			expiring.add(p.getName().toLowerCase(Locale.ROOT));
		}
		draft.expiringTotal = expiring.size();
		int used = 0;
		for (String item : expiring) {
			boolean hit = false;
			for (Recipe r : draft.distinctRecipes()) {
				if (expiringHits(r, List.of(item)) > 0) {
					hit = true;
					break;
				}
			}
			if (hit) {
				used++;
			}
		}
		draft.expiringUsed = used;
		Set<String> buy = new LinkedHashSet<>();
		for (Recipe r : draft.distinctRecipes()) {
			for (RecipeIngredient i : advisor.check(r.getId()).missing()) {
				buy.add(i.name().toLowerCase(Locale.ROOT));
			}
		}
		draft.toBuy = buy.size();
	}
}