package com.mealton.plan;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.mealton.pantry.PantryInventory;
import com.mealton.pantry.PantryItem;
import com.mealton.recipe.Recipe;
import com.mealton.recipe.RecipeIngredient;

/**
 * Schedules a batch-cooking "prep day" (feature F05). It takes the meals on the
 * calendar between two dates, groups them by recipe, and builds a timeline for
 * one cooking session with three lanes (Counter, Stovetop, Oven) so tasks that
 * can run at the same time are shown side by side. If the session would be
 * longer than the time available, the longest recipes are left out to be cooked
 * fresh on the day. Plain code, no AI.
 */
public final class PrepScheduler {

	public static final int MAX_DAYS = 14;
	/**
	 * Cooked meals keep about this many days in the fridge; later days are frozen.
	 */
	public static final int FRIDGE_DAYS = 4;
	public static final List<String> LANES = List.of("Counter", "Stovetop", "Oven");

	public record Options(LocalDate prepDate, LocalTime startTime, LocalDate start, LocalDate end, int limitMinutes) {
	}

	/**
	 * One recipe in the session: how many portions, for which days, how it is
	 * stored.
	 */
	public record PrepMeal(Recipe recipe, int portions, String tag, List<LocalDate> dates, int frozen, boolean fresh) {
		public PrepMeal {
			dates = List.copyOf(dates);
		}
	}

	/** A block on the timeline. {@code startMinute} counts from the start time. */
	public record Task(String lane, String name, int startMinute, int minutes, List<String> serves, List<String> how,
			String tip) {
		public Task {
			serves = List.copyOf(serves);
			how = List.copyOf(how);
		}

		public int endMinute() {
			return startMinute + minutes;
		}
	}

	public record Row(String name, String unit, double need, double have, double shortBy, List<String> forRecipes) {
		public Row {
			forRecipes = List.copyOf(forRecipes);
		}
	}

	public record Schedule(Options options, List<PrepMeal> meals, List<Task> tasks, List<Row> ingredients,
			List<String> leftOut, int totalMinutes) {
		public Schedule {
			meals = List.copyOf(meals);
			tasks = List.copyOf(tasks);
			ingredients = List.copyOf(ingredients);
			leftOut = List.copyOf(leftOut);
		}

		public boolean fits() {
			return totalMinutes <= options.limitMinutes();
		}

		public int portions() {
			int n = 0;
			for (PrepMeal m : meals) {
				if (!m.fresh()) {
					n += m.portions();
				}
			}
			return n;
		}

		public List<Row> shortRows() {
			List<Row> out = new ArrayList<>();
			for (Row r : ingredients) {
				if (r.shortBy() > 1e-9) {
					out.add(r);
				}
			}
			return out;
		}

		/** Tasks in the order they start. */
		public List<Task> ordered() {
			List<Task> out = new ArrayList<>(tasks);
			out.sort(Comparator.comparingInt(Task::startMinute).thenComparingInt(t -> LANES.indexOf(t.lane())));
			return out;
		}
	}

	private final CalendarService calendar;
	private final PantryInventory pantry;

	public PrepScheduler(CalendarService calendar, PantryInventory pantry) {
		this.calendar = calendar;
		this.pantry = pantry;
	}

	/** The message to show when the dates can't be used, or an empty string. */
	public static String error(LocalDate prepDate, LocalDate start, LocalDate end) {
		if (start == null || end == null) {
			return "Pick both dates.";
		}
		if (end.isBefore(start)) {
			return "The end date has to be on or after the start date.";
		}
		if (RangePlanner.days(start, end) > MAX_DAYS) {
			return "Prep covers up to 2 weeks — shorten the range.";
		}
		if (prepDate == null) {
			return "Pick a prep date.";
		}
		if (prepDate.isAfter(start)) {
			return "Your prep day has to be on or before the first day you’re cooking for.";
		}
		return "";
	}

	/**
	 * @throws IllegalArgumentException if the dates are wrong or nothing is planned
	 *                                  between them
	 */
	public Schedule build(Options o) {
		String err = error(o.prepDate(), o.start(), o.end());
		if (!err.isEmpty()) {
			throw new IllegalArgumentException(err);
		}
		// 1. group the planned meals by recipe
		Map<Long, List<PlannedMeal>> byRecipe = new LinkedHashMap<>();
		Map<Long, List<LocalDate>> dates = new LinkedHashMap<>();
		for (LocalDate d = o.start(); !d.isAfter(o.end()); d = d.plusDays(1)) {
			for (PlannedMeal m : calendar.plannedOn(d)) {
				byRecipe.computeIfAbsent(m.recipe().getId(), k -> new ArrayList<>()).add(m);
				dates.computeIfAbsent(m.recipe().getId(), k -> new ArrayList<>()).add(d);
			}
		}
		if (byRecipe.isEmpty()) {
			throw new IllegalArgumentException(
					"Nothing is planned between those dates. Plan some meals first, then schedule the prep day.");
		}
		List<PrepMeal> meals = new ArrayList<>();
		for (Map.Entry<Long, List<PlannedMeal>> e : byRecipe.entrySet()) {
			List<PlannedMeal> list = e.getValue();
			int portions = 0;
			for (PlannedMeal m : list) {
				portions += m.servings();
			}
			List<LocalDate> ds = dates.get(e.getKey());
			int frozen = 0;
			for (LocalDate d : ds) {
				if (java.time.temporal.ChronoUnit.DAYS.between(o.prepDate(), d) > FRIDGE_DAYS) {
					frozen++;
				}
			}
			meals.add(new PrepMeal(list.get(0).recipe(), portions, list.get(0).tag(), ds, frozen, false));
		}

		// 2. schedule, leaving out the longest recipes until it fits
		Set<Long> fresh = new LinkedHashSet<>();
		List<String> leftOut = new ArrayList<>();
		List<Task> tasks = schedule(meals, fresh, o);
		while (end(tasks) > o.limitMinutes() && fresh.size() < meals.size() - 1) {
			PrepMeal longest = null;
			int longestMinutes = -1;
			for (PrepMeal m : meals) {
				if (!fresh.contains(m.recipe().getId()) && cookMinutes(m) > longestMinutes) {
					longest = m;
					longestMinutes = cookMinutes(m);
				}
			}
			fresh.add(longest.recipe().getId());
			leftOut.add(longest.recipe().getTitle());
			tasks = schedule(meals, fresh, o);
		}
		List<PrepMeal> finalMeals = new ArrayList<>();
		for (PrepMeal m : meals) {
			finalMeals.add(fresh.contains(m.recipe().getId())
					? new PrepMeal(m.recipe(), m.portions(), m.tag(), m.dates(), m.frozen(), true)
					: m);
		}
		return new Schedule(o, finalMeals, tasks, ingredients(finalMeals), leftOut, end(tasks));
	}

	// ------------------------------------------------------------------ timeline

	private static int end(List<Task> tasks) {
		int end = 0;
		for (Task t : tasks) {
			end = Math.max(end, t.endMinute());
		}
		return end;
	}

	private static boolean usesOven(Recipe r) {
		return text(r).matches("(?s).*\\b(bake|baked|roast|roasted|oven|broil|grill)\\b.*");
	}

	private static boolean usesHob(Recipe r) {
		return text(r).matches("(?s).*\\b(simmer|boil|fry|saute|sauté|cook|stir|heat|brown|sear|steam|pan|pot)\\b.*");
	}

	private static String text(Recipe r) {
		return String.join(" ", r.getSteps()).toLowerCase(Locale.ROOT);
	}

	private static double scale(PrepMeal m) {
		return Math.max(1.0, (double) m.portions() / Math.max(1, m.recipe().getServings()));
	}

	private static int stretch(int base, PrepMeal m) {
		double s = scale(m);
		return (int) Math.round(base * Math.min(2.0, 1 + 0.2 * (s - 1)));
	}

	private static int prepMinutes(PrepMeal m) {
		int base = m.recipe().getPrepMinutes() > 0 ? m.recipe().getPrepMinutes() : 10;
		return Math.max(5, stretch(Math.min(base, 40), m));
	}

	private static int cookMinutes(PrepMeal m) {
		Recipe r = m.recipe();
		if (!usesOven(r) && !usesHob(r)) {
			return 0;
		}
		int base = r.getCookMinutes() > 0 ? r.getCookMinutes() : 20;
		return Math.max(5, stretch(Math.min(base, 120), m));
	}

	private List<Task> schedule(List<PrepMeal> all, Set<Long> fresh, Options o) {
		List<PrepMeal> meals = new ArrayList<>();
		for (PrepMeal m : all) {
			if (!fresh.contains(m.recipe().getId())) {
				meals.add(m);
			}
		}
		meals.sort(Comparator.comparingInt(PrepScheduler::cookMinutes).reversed());
		List<Task> out = new ArrayList<>();
		boolean oven = false;
		for (PrepMeal m : meals) {
			oven |= usesOven(m.recipe());
		}
		List<String> everyone = new ArrayList<>();
		for (PrepMeal m : meals) {
			everyone.add(m.recipe().getTitle());
		}
		int counter = 0;
		int hob = 0;
		int ovenFree = 0;
		if (oven) {
			out.add(new Task("Oven", "Preheat oven", 0, 10, everyone,
					List.of("Set the oven to 200°C (400°F) and line a sheet pan with parchment.",
							"Pull every ingredient from the list onto the counter so nothing is missing mid-cook."),
					"Clear the dishwasher first — you’ll fill it fast."));
			ovenFree = 10;
		}
		int chop = Math.min(25, 5 + 4 * meals.size());
		out.add(new Task("Counter", "Wash & chop veg", 0, chop, everyone,
				List.of("Wash and cut all the vegetables for the session in one go.",
						"Put each recipe’s chopped ingredients in its own bowl."),
				"Label each prep bowl with the recipe it’s for."));
		counter = chop;
		List<int[]> cookEnds = new ArrayList<>(); // {index in meals, cook end}
		for (int i = 0; i < meals.size(); i++) {
			PrepMeal m = meals.get(i);
			Recipe r = m.recipe();
			double s = scale(m);
			List<String> gather = new ArrayList<>();
			for (RecipeIngredient ing : r.getIngredients()) {
				gather.add("Measure " + com.mealton.util.Formats.quantityWithUnit(ing.quantity() * s, ing.unit()) + " "
						+ ing.name() + ".");
			}
			int cook = cookMinutes(m);
			int prep = prepMinutes(m);
			if (cook == 0) {
				List<String> how = new ArrayList<>(
						r.getSteps().size() > 0 ? r.getSteps() : List.of("Assemble " + r.getTitle() + "."));
				out.add(new Task("Counter", "Assemble " + r.getTitle(), counter, prep + 5, List.of(r.getTitle()),
						limit(how, 4), null));
				counter += prep + 5;
				cookEnds.add(new int[] { i, counter });
				continue;
			}
			out.add(new Task("Counter", "Prep " + r.getTitle(), counter, prep, List.of(r.getTitle()), limit(gather, 5),
					null));
			counter += prep;
			boolean inOven = usesOven(r);
			int startCook = Math.max(counter, inOven ? ovenFree : hob);
			String lane = inOven ? "Oven" : "Stovetop";
			String tip = m.frozen() > 0 ? "Freeze the portions for the last " + m.frozen()
					+ (m.frozen() == 1 ? " day" : " days") + " — move them to the fridge the night before." : null;
			out.add(new Task(lane, (inOven ? "Bake " : "Cook ") + r.getTitle(), startCook, cook, List.of(r.getTitle()),
					limit(r.getSteps(), 5), tip));
			if (inOven) {
				ovenFree = startCook + cook;
			} else {
				hob = startCook + cook;
			}
			cookEnds.add(new int[] { i, startCook + cook });
		}
		cookEnds.sort(Comparator.comparingInt(a -> a[1]));
		for (int[] ce : cookEnds) {
			PrepMeal m = meals.get(ce[0]);
			int at = Math.max(counter, ce[1]);
			List<String> days = new ArrayList<>();
			for (LocalDate d : m.dates()) {
				days.add(d.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH));
			}
			out.add(new Task("Counter", "Portion " + m.recipe().getTitle(), at, 5, List.of(m.recipe().getTitle()),
					List.of("Divide into " + m.portions() + (m.portions() == 1 ? " container" : " containers")
							+ " and label each with the day it’s for (" + String.join(", ", days) + ")."),
					null));
			counter = at + 5;
		}
		out.add(new Task(
				"Counter", "Label & refrigerate", counter, 10, everyone, List
						.of("Let everything cool for up to 20 minutes — no longer — before the lids go on.",
								"Fridge: the first " + FRIDGE_DAYS
										+ " days. Freezer: anything later, moved to the fridge the night before."),
				null));
		return out;
	}

	private static List<String> limit(List<String> list, int n) {
		return list.size() <= n ? list : new ArrayList<>(list.subList(0, n));
	}

	// ------------------------------------------------------------------
	// ingredients

	private static String key(String name, String unit) {
		String n = name.trim().toLowerCase(Locale.ROOT);
		if (n.endsWith("s") && n.length() > 3) {
			n = n.substring(0, n.length() - 1);
		}
		return n + "|" + unit.trim().toLowerCase(Locale.ROOT);
	}

	private List<Row> ingredients(List<PrepMeal> meals) {
		Map<String, double[]> need = new LinkedHashMap<>();
		Map<String, String[]> names = new LinkedHashMap<>();
		Map<String, Set<String>> forWhom = new LinkedHashMap<>();
		for (PrepMeal m : meals) {
			if (m.fresh()) {
				continue;
			}
			double s = scale(m);
			for (RecipeIngredient ing : m.recipe().getIngredients()) {
				String k = key(ing.name(), ing.unit());
				need.computeIfAbsent(k, x -> new double[1])[0] += ing.quantity() * s;
				names.putIfAbsent(k, new String[] { ing.name().trim(), ing.unit().trim() });
				forWhom.computeIfAbsent(k, x -> new LinkedHashSet<>()).add(m.recipe().getTitle());
			}
		}
		Map<String, Double> have = new LinkedHashMap<>();
		for (PantryItem p : pantry.getItems()) {
			have.merge(key(p.getName(), p.getUnit()), p.getQuantity(), Double::sum);
		}
		List<Row> rows = new ArrayList<>();
		for (Map.Entry<String, double[]> e : need.entrySet()) {
			double h = have.getOrDefault(e.getKey(), 0.0);
			String[] n = names.get(e.getKey());
			rows.add(new Row(n[0], n[1], e.getValue()[0], h, Math.max(0, e.getValue()[0] - h),
					new ArrayList<>(forWhom.get(e.getKey()))));
		}
		rows.sort(Comparator.comparing((Row r) -> r.shortBy() <= 1e-9)
				.thenComparing(r -> r.name().toLowerCase(Locale.ROOT)));
		return rows;
	}
}