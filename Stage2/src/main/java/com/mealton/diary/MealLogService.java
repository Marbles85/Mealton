package com.mealton.diary;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mealton.nutrition.LoggedFood;
import com.mealton.nutrition.MacroTracker;
import com.mealton.nutrition.Nutrients;
import com.mealton.nutrition.RecipeNutrition;
import com.mealton.pantry.PantryInventory;
import com.mealton.pantry.PantryItem;
import com.mealton.recipe.Recipe;
import com.mealton.recipe.RecipeIngredient;
import com.mealton.util.Formats;
import com.mealton.util.UnitConverter;

/**
 * The Log food page's engine (features F04 + F06 working together): logging a
 * meal puts it in the food diary, takes what was used out of the pantry, can
 * save leftovers to the pantry, says what is now running low, and can be
 * undone.
 */
public class MealLogService {

	/** The leftovers are good for this many days. */
	public static final int LEFTOVER_DAYS = 3;
	/** Pantry category used for saved leftovers. */
	public static final String LEFTOVER_CATEGORY = "Recipes";

	/**
	 * One ingredient of the meal as the page shows it in the "Taken out of your
	 * pantry" table.
	 *
	 * @param planned  what the recipe calls for at this batch size
	 * @param used     what was really used (planned unless changed)
	 * @param inPantry true when a matching pantry item exists
	 * @param have     the pantry amount in the recipe's unit, or -1 when it cannot
	 *                 be compared (different kinds of unit) or not in the pantry
	 * @param after    what is left after using it (-1 when unknown)
	 */
	public record IngredientLine(int index, String name, String unit, double planned, double used, boolean inPantry,
			double have, double after, boolean changed) {
	}

	/** Something that is now running low (offered for the shopping list). */
	public record LowItem(String name, double left, double needed, String unit, String why) {
	}

	/** What logging did. */
	public record Result(LoggedFood entry, List<String> pantryChanges, List<LowItem> low, String leftoverText,
			Undo undo) {
	}

	private record Snapshot(long id, String name, double quantity, String unit, String category, LocalDate expiry) {
	}

	/** Everything needed to take a logged meal back. */
	public static final class Undo {
		private final long entryId;
		private final List<Snapshot> snapshots = new ArrayList<>();
		private long leftoverId = -1;
		private double leftoverAmount;
		private boolean used;

		private Undo(long entryId) {
			this.entryId = entryId;
		}
	}

	private final MacroTracker tracker;
	private final PantryInventory pantry;

	public MealLogService(MacroTracker tracker, PantryInventory pantry) {
		this.tracker = tracker;
		this.pantry = pantry;
	}

	// ----------------------------------------------------------- preview

	/**
	 * The ingredient table for a recipe made as {@code batch} servings, with any
	 * amounts the user changed ({@code overrides}: ingredient index to amount).
	 */
	public List<IngredientLine> preview(Recipe recipe, int batch, Map<Integer, Double> overrides) {
		checkBatch(batch);
		List<IngredientLine> lines = new ArrayList<>();
		List<RecipeIngredient> ingredients = recipe.getIngredients();
		for (int i = 0; i < ingredients.size(); i++) {
			RecipeIngredient ing = ingredients.get(i);
			double planned = PortionNotes.round(ing.quantity() * batch / recipe.getServings(), ing.unit());
			boolean changed = overrides != null && overrides.containsKey(i);
			double used = changed ? overrides.get(i) : planned;
			List<PantryItem> items = matching(ing.name());
			double have = items.isEmpty() ? -1 : totalIn(items, ing.unit());
			double after = have < 0 ? -1 : Math.max(0, have - used);
			lines.add(new IngredientLine(i, ing.name(), ing.unit(), planned, used, !items.isEmpty(), have, after,
					changed));
		}
		return lines;
	}

	// ------------------------------------------------------- log a recipe

	/**
	 * Logs a portion of a cooked recipe.
	 *
	 * @param batch         how many servings were made
	 * @param portion       how many servings were eaten (more than 0, at most
	 *                      batch)
	 * @param overrides     ingredient index to the amount really used
	 * @param saveLeftovers put the servings not eaten in the pantry
	 */
	public Result logRecipe(Recipe recipe, int batch, double portion, String meal, Map<Integer, Double> overrides,
			String note, boolean saveLeftovers, LocalDate date, String time) {
		return logRecipe(recipe, batch, portion, meal, overrides, note, saveLeftovers, date, time, true);
	}

	/**
	 * Same, but with {@code deduct} false nothing is taken out of the pantry and no
	 * leftovers are saved (for a meal logged on a day that has already passed).
	 */
	public Result logRecipe(Recipe recipe, int batch, double portion, String meal, Map<Integer, Double> overrides,
			String note, boolean saveLeftovers, LocalDate date, String time, boolean deduct) {
		checkBatch(batch);
		if (Double.isNaN(portion) || portion <= 0 || portion > batch + 1e-9) {
			throw new IllegalArgumentException(
					"Choose how much you ate: more than 0 and no more than the " + batch + " servings you made.");
		}
		List<IngredientLine> lines = preview(recipe, batch, overrides);
		Nutrients eaten = nutrientsFor(recipe, lines, batch, portion);

		String description = recipe.getTitle() + (Math.abs(portion - 1) < 1e-9 ? "" : " (" + servings(portion) + ")");
		LoggedFood entry = tracker.log(date, meal, time, description, eaten, true, note, "recipe");
		Undo undo = new Undo(entry.id());

		List<String> changes = new ArrayList<>();
		List<LowItem> low = new ArrayList<>();
		for (IngredientLine l : lines) {
			if (!deduct || l.used() <= 0 || !l.inPantry() || l.have() < 0) {
				continue;
			}
			deduct(matching(l.name()), l.used(), l.unit(), undo);
			double after = Math.max(0, l.have() - l.used());
			changes.add(l.name() + " " + Formats.quantityWithUnit(round(l.have()), l.unit()) + " → "
					+ Formats.quantityWithUnit(round(after), l.unit()));
			double needed = recipe.getIngredients().get(l.index()).quantity();
			if (after < needed - 1e-9) {
				low.add(new LowItem(l.name(), after, needed, l.unit(),
						after <= 0 ? "All used up" : "Not enough left to make " + recipe.getTitle() + " again"));
			}
		}
		String leftoverText = "";
		double leftover = batch - portion;
		if (deduct && saveLeftovers && leftover > 1e-9) {
			PantryItem stored = pantry.addItem(new PantryItem(recipe.getTitle(), leftover, "servings",
					LEFTOVER_CATEGORY, date.plusDays(LEFTOVER_DAYS)));
			undo.leftoverId = stored.getId();
			undo.leftoverAmount = leftover;
			leftoverText = "Saved " + servings(leftover) + " leftover to your pantry.";
		}
		return new Result(entry, changes, low, leftoverText, undo);
	}

	/**
	 * What eating this portion adds up to, or null when the recipe has no nutrition
	 * numbers yet (the Log food page's live "this meal" figure).
	 */
	public Nutrients previewNutrients(Recipe recipe, int batch, double portion, Map<Integer, Double> overrides) {
		try {
			return nutrientsFor(recipe, preview(recipe, batch, overrides), batch, portion);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private Nutrients nutrientsFor(Recipe recipe, List<IngredientLine> lines, int batch, double portion) {
		List<RecipeIngredient> used = new ArrayList<>();
		for (IngredientLine l : lines) {
			if (l.used() > 0) {
				used.add(new RecipeIngredient(l.name(), l.used(), l.unit()));
			}
		}
		if (used.isEmpty()) {
			throw new IllegalArgumentException("Nothing was used: change at least one amount above 0.");
		}
		Recipe made = new Recipe(recipe.getTitle(), batch, used, recipe.getSteps());
		if (recipe.getAiEstimate() != null) {
			made.setAiEstimate(recipe.getAiEstimate().times((double) batch / recipe.getServings()));
		}
		RecipeNutrition nutrition = RecipeNutrition.of(made);
		if (nutrition.counted().isEmpty()) {
			throw new IllegalArgumentException("'" + recipe.getTitle() + "' has no nutrition estimate, so it cannot "
					+ "be logged yet. Log it by hand with numbers instead.");
		}

		return nutrition.total().times(portion / batch);
	}

	// ------------------------------------------------------ log pantry food

	/**
	 * Logs an amount of one pantry item (for example 2 oranges) and takes it out of
	 * the pantry.
	 *
	 * @param nutrients what that amount adds up to (the caller works it out; for
	 *                  saved leftovers it is the recipe's per-serving numbers)
	 */
	public Result logPantryItem(PantryItem item, double amount, String meal, Nutrients nutrients, boolean estimated,
			LocalDate date, String time) {
		return logPantryItem(item, amount, meal, nutrients, estimated, date, time, true);
	}

	/**
	 * Same, but with {@code deduct} false the pantry is left as it is (a day that
	 * has already passed).
	 */
	public Result logPantryItem(PantryItem item, double amount, String meal, Nutrients nutrients, boolean estimated,
			LocalDate date, String time, boolean deduct) {
		if (Double.isNaN(amount) || amount <= 0) {
			throw new IllegalArgumentException("Enter how much you ate: more than 0.");
		}
		double before = item.getQuantity();
		String unit = item.getUnit();
		String description = unit.isBlank() || unit.equalsIgnoreCase("pcs")
				? item.getName() + " × " + Formats.number(amount)
				: item.getName() + " · " + Formats.quantityWithUnit(amount, unit);
		LoggedFood entry = tracker.log(date, meal, time, description, nutrients, estimated, "", "pantry");
		Undo undo = new Undo(entry.id());
		if (!deduct) {
			return new Result(entry, List.of(), List.of(), "", undo);
		}
		double after = Math.max(0, before - amount);
		snapshot(undo, item);
		if (after <= 1e-9) {
			pantry.removeItems(List.of(item.getId()));
		} else {
			item.setQuantity(after);
			pantry.updateItem(item);
		}
		List<String> changes = List.of(item.getName() + " " + Formats.quantityWithUnit(round(before), unit) + " → "
				+ Formats.quantityWithUnit(round(after), unit));
		List<LowItem> low = new ArrayList<>();
		if (!LEFTOVER_CATEGORY.equals(item.getCategory())) {
			if (after <= 1e-9) {
				low.add(new LowItem(item.getName(), 0, before, unit, "All used up"));
			} else if (after < before * 0.2) {
				low.add(new LowItem(item.getName(), after, before, unit, "Running low"));
			}
		}
		return new Result(entry, changes, low, "", undo);
	}

	/** Logs something that was already logged once again (today, now). */
	public LoggedFood relog(LoggedFood old, LocalDate date, String time) {
		return tracker.log(date, old.meal(), time, old.description(), old.nutrients(), old.estimated(), old.note(),
				old.source());
	}

	// ------------------------------------------------------------- undo

	/**
	 * Takes a logged meal back: the diary entry goes, pantry amounts return and
	 * saved leftovers are removed. Does nothing the second time.
	 */
	public void undo(Undo undo) {
		if (undo == null || undo.used) {
			return;
		}
		undo.used = true;
		try {
			tracker.delete(undo.entryId);
		} catch (IllegalArgumentException alreadyGone) {
			// the entry was deleted some other way
		}
		for (Snapshot s : undo.snapshots) {
			java.util.Optional<PantryItem> still = pantry.findById(s.id());
			if (still.isPresent()) {
				still.get().setQuantity(s.quantity());
				pantry.updateItem(still.get());
			} else {
				pantry.addItem(new PantryItem(s.name(), s.quantity(), s.unit(), s.category(), s.expiry()));
			}
		}
		if (undo.leftoverId >= 0) {
			pantry.findById(undo.leftoverId).ifPresent(item -> {
				double rest = item.getQuantity() - undo.leftoverAmount;
				if (rest <= 1e-9) {
					pantry.removeItems(List.of(item.getId()));
				} else {
					item.setQuantity(rest);
					pantry.updateItem(item);
				}
			});
		}
	}

	// --------------------------------------------------------- internals

	private void deduct(List<PantryItem> items, double amount, String unit, Undo undo) {
		double remaining = amount;
		for (PantryItem item : items) {
			Double inUnit = convert(item.getQuantity(), item.getUnit(), unit);
			if (inUnit == null || inUnit <= 0) {
				continue;
			}
			double take = Math.min(inUnit, remaining);
			double left = inUnit - take;
			snapshot(undo, item);
			if (left <= 1e-6) {
				pantry.removeItems(List.of(item.getId()));
			} else {
				Double back = convert(left, unit, item.getUnit());
				item.setQuantity(back == null ? left : back);
				pantry.updateItem(item);
			}
			remaining -= take;
			if (remaining <= 1e-9) {
				break;
			}
		}
	}

	private static void snapshot(Undo undo, PantryItem item) {
		for (Snapshot s : undo.snapshots) {
			if (s.id() == item.getId()) {
				return; // keep the first (original) amount
			}
		}
		undo.snapshots.add(new Snapshot(item.getId(), item.getName(), item.getQuantity(), item.getUnit(),
				item.getCategory(), item.getExpiryDate()));
	}

	private List<PantryItem> matching(String ingredient) {
		LocalDate today = pantry.today();
		List<PantryItem> found = new ArrayList<>();
		for (PantryItem item : pantry.getItems()) {
			if (item.getExpiryDate() != null && item.daysLeft(today) < 0) {
				continue; // expired food is not used up
			}
			if (LEFTOVER_CATEGORY.equals(item.getCategory())) {
				continue;
			}
			if (sameIngredient(ingredient, item.getName())) {
				found.add(item);
			}
		}
		found.sort(Comparator.comparing(PantryItem::getExpiryDate, Comparator.nullsLast(Comparator.naturalOrder())));
		return found;
	}

	private static double totalIn(List<PantryItem> items, String unit) {
		double total = 0;
		for (PantryItem item : items) {
			Double part = convert(item.getQuantity(), item.getUnit(), unit);
			if (part == null) {
				return -1;
			}
			total += part;
		}
		return total;
	}

	/** Converts between units; null when the two cannot be compared. */
	private static Double convert(double amount, String from, String to) {
		String f = unitKey(from);
		String t = unitKey(to);
		if (f.equals(t)) {
			return amount;
		}
		if (UnitConverter.isKnown(from) && UnitConverter.isKnown(to)) {
			try {
				return UnitConverter.convert(amount, from, to);
			} catch (IllegalArgumentException e) {
				return null;
			}
		}
		return null;
	}

	private static String unitKey(String unit) {
		String u = unit == null ? "" : unit.trim().toLowerCase(Locale.ROOT);
		return u.equals("pc") || u.equals("pcs") || u.equals("piece") || u.equals("pieces") ? "" : u;
	}

	static boolean sameIngredient(String a, String b) {
		String na = norm(a);
		String nb = norm(b);
		if (na.isEmpty() || nb.isEmpty()) {
			return false;
		}
		return na.equals(nb) || (nb.length() >= 4 && (" " + na + " ").contains(" " + nb + " "))
				|| (na.length() >= 4 && (" " + nb + " ").contains(" " + na + " "));
	}

	private static String norm(String name) {
		StringBuilder sb = new StringBuilder();
		for (String word : name.toLowerCase(Locale.ROOT).split("[^a-z]+")) {
			if (word.isEmpty()) {
				continue;
			}
			if (word.endsWith("ies") && word.length() > 4) {
				word = word.substring(0, word.length() - 3) + "y";
			} else if (word.endsWith("oes") && word.length() > 4) {
				word = word.substring(0, word.length() - 2);
			} else if (word.endsWith("s") && !word.endsWith("ss") && word.length() > 3) {
				word = word.substring(0, word.length() - 1);
			}
			if (sb.length() > 0) {
				sb.append(' ');
			}
			sb.append(word);
		}
		return sb.toString();
	}

	private static void checkBatch(int batch) {
		if (batch < 1 || batch > 48) {
			throw new IllegalArgumentException("The number of servings made must be between 1 and 48.");
		}
	}

	private static double round(double v) {
		return Math.round(v * 100) / 100.0;
	}

	/** 1 -> "1 serving", 0.5 -> "0.5 servings". */
	static String servings(double n) {
		return Formats.number(n) + (Math.abs(n - 1) < 1e-9 ? " serving" : " servings");
	}
}