package com.mealton.pantry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * How one list (the pantry, or the shopping list) is split into categories:
 * Mealton's suggested categories (which it can switch off), the categories the
 * user made, their order, names and colours. Items only keep a category name;
 * this class decides which group on the page an item lands in.
 */
public class CategoryLayout {

	public static final List<String> SUGGESTED = List.of("Produce", "Dairy & eggs", "Meat & fish", "Staples");
	public static final String UNCATEGORIZED = "Uncategorized";
	public static final String RECIPES = "Recipes";
	public static final String PINNED = "Pinned";

	private boolean suggestedOn = true;
	private final List<String> custom = new ArrayList<>();
	private final List<String> order = new ArrayList<>();
	private final Map<String, String> colors = new LinkedHashMap<>();
	private final Map<String, String> aliases = new LinkedHashMap<>();

	// ---- suggested on / off

	public boolean isSuggestedOn() {
		return suggestedOn;
	}

	public void setSuggestedOn(boolean on) {
		this.suggestedOn = on;
	}

	// ---- categories the user made

	public List<String> getCustom() {
		return List.copyOf(custom);
	}

	/**
	 * Adds a category of the user's own. @throws IllegalArgumentException if empty
	 * or taken
	 */
	public String addCustom(String name) {
		String n = name == null ? "" : name.trim();
		if (n.isEmpty()) {
			throw new IllegalArgumentException("Type a name for the category first.");
		}
		if (n.length() > 30) {
			throw new IllegalArgumentException("A category name can be at most 30 characters.");
		}
		if (exists(n)) {
			throw new IllegalArgumentException("“" + n + "” already exists.");
		}
		custom.add(n);
		return n;
	}

	public void removeCustom(String name) {
		custom.removeIf(c -> c.equals(name));
		order.remove(name);
		colors.remove(name);
		aliases.remove(name);
	}

	private boolean exists(String n) {
		for (String s : SUGGESTED) {
			if (s.equalsIgnoreCase(n)) {
				return true;
			}
		}
		for (String s : custom) {
			if (s.equalsIgnoreCase(n)) {
				return true;
			}
		}
		return n.equalsIgnoreCase(UNCATEGORIZED) || n.equalsIgnoreCase(RECIPES) || n.equalsIgnoreCase(PINNED);
	}

	// ---- display order, names and colours

	/** The group names in the order the page shows them (not counting Pinned). */
	public List<String> visibleGroups(boolean pantryView, boolean anyUncategorized) {
		List<String> base = new ArrayList<>();
		if (pantryView) {
			base.add(RECIPES);
		}
		if (suggestedOn) {
			base.addAll(SUGGESTED);
		}
		base.addAll(custom);
		if (anyUncategorized || !suggestedOn) {
			base.add(UNCATEGORIZED);
		}
		List<String> sorted = new ArrayList<>(base);
		sorted.sort((a, b) -> Integer.compare(rank(a, base), rank(b, base)));
		return sorted;
	}

	private int rank(String name, List<String> base) {
		int k = order.indexOf(name);
		return k >= 0 ? k : 1000 + base.indexOf(name);
	}

	/** Moves a group up (-1) or down (1) among the groups that are showing. */
	public void moveGroup(String name, int delta, List<String> showing) {
		List<String> a = new ArrayList<>(showing);
		int k = a.indexOf(name);
		int j = k + delta;
		if (k < 0 || j < 0 || j >= a.size()) {
			return;
		}
		a.set(k, a.get(j));
		a.set(j, name);
		order.clear();
		order.addAll(a);
	}

	/** Moves a group to sit just before another one (drag and drop). */
	public void moveGroupBefore(String name, String before, List<String> showing) {
		if (name.equals(before)) {
			return;
		}
		List<String> a = new ArrayList<>(showing);
		if (!a.remove(name)) {
			return;
		}
		int k = a.indexOf(before);
		a.add(k < 0 ? a.size() : k, name);
		order.clear();
		order.addAll(a);
	}

	public List<String> getOrder() {
		return List.copyOf(order);
	}

	public void setOrder(Collection<String> names) {
		order.clear();
		order.addAll(names);
	}

	/** The name shown for a group (the user may have renamed it). */
	public String labelOf(String group) {
		String alias = aliases.get(group);
		return alias == null || alias.isBlank() ? group : alias;
	}

	public void rename(String group, String label) {
		if (label == null || label.isBlank() || label.trim().equals(group)) {
			aliases.remove(group);
		} else {
			aliases.put(group, label.trim());
		}
	}

	public Map<String, String> getAliases() {
		return Map.copyOf(aliases);
	}

	public Map<String, String> getColors() {
		return Map.copyOf(colors);
	}

	public void setColor(String group, String hex) {
		if (hex == null || hex.isBlank()) {
			colors.remove(group);
		} else {
			colors.put(group, hex);
		}
	}

	private static final List<String> PALETTE = List.of("#9CAF88", "#C9A66B", "#4F6B52", "#7FA7B8", "#C98B7B",
			"#A48BB8", "#D2B85A", "#7B9E9B", "#B98FA6", "#8FA0C9");

	public static List<String> palette() {
		return PALETTE;
	}

	/** The colour of a group: the one picked, otherwise a steady automatic one. */
	public String colorOf(String group) {
		String picked = colors.get(group);
		if (picked != null) {
			return picked;
		}
		List<String> stable = new ArrayList<>();
		stable.add(RECIPES);
		stable.addAll(SUGGESTED);
		stable.addAll(custom);
		stable.add(UNCATEGORIZED);
		int k = Math.max(0, stable.indexOf(group));
		return PALETTE.get(k % PALETTE.size());
	}

	// ---- which group an item belongs to

	/**
	 * The group an item lands in, given its name and the category it was saved
	 * with.
	 */
	public String groupOf(String itemName, String category) {
		String c = category == null ? "" : category.trim();
		if (c.equalsIgnoreCase(RECIPES)) {
			return RECIPES;
		}
		for (String own : custom) {
			if (own.equalsIgnoreCase(c)) {
				return own;
			}
		}
		if (!suggestedOn) {
			return UNCATEGORIZED;
		}
		String canon = canonical(c);
		if (canon != null) {
			return canon;
		}
		return classify(itemName);
	}

	/**
	 * Matches a saved category (including the older names) to one of the suggested
	 * ones, or null.
	 */
	private static String canonical(String c) {
		for (String s : SUGGESTED) {
			if (s.equalsIgnoreCase(c)) {
				return s;
			}
		}
		switch (c.toLowerCase(Locale.ROOT)) {
		case "dairy":
			return "Dairy & eggs";
		case "meat & fish":
			return "Meat & fish";
		case "grains & bread":
		case "pantry staples":
			return "Staples";
		default:
			return null;
		}
	}

	private static final List<String> MEAT = List.of("chicken", "beef", "pork", "turkey", "bacon", "sausage", "ham",
			"lamb", "steak", "mince", "salmon", "tuna", "fish", "shrimp", "prawn", "cod", "tilapia", "crab");
	private static final List<String> DAIRY = List.of("milk", "cheese", "yogurt", "yoghurt", "butter", "cream", "egg",
			"feta", "mozzarella", "parmesan", "cheddar");
	private static final List<String> PRODUCE = List.of("apple", "banana", "orange", "lemon", "lime", "berry",
			"spinach", "lettuce", "tomato", "cucumber", "carrot", "onion", "garlic", "pepper", "broccoli", "potato",
			"mushroom", "avocado", "parsley", "cilantro", "basil", "kale", "celery", "zucchini", "fruit", "vegetable",
			"cabbage", "squash", "pumpkin", "pear", "grape", "mango", "ginger", "corn", "eggplant", "herb");
	private static final List<String> STAPLES = List.of("rice", "pasta", "spaghetti", "noodle", "flour", "sugar",
			"salt", "oil", "vinegar", "oat", "lentil", "bean", "chickpea", "bread", "sauce", "stock", "broth", "spice",
			"honey", "cereal", "nut", "soy", "baking", "cocoa", "chocolate");

	/** Guesses a suggested category from the item's name, or Uncategorized. */
	public static String classify(String name) {
		String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
		if (n.contains("eggplant")) {
			return "Produce";
		}
		if (any(n, MEAT)) {
			return "Meat & fish";
		}
		if (any(n, DAIRY)) {
			return "Dairy & eggs";
		}
		if (any(n, PRODUCE)) {
			return "Produce";
		}
		if (any(n, STAPLES)) {
			return "Staples";
		}
		return UNCATEGORIZED;
	}

	private static boolean any(String n, List<String> words) {
		for (String w : words) {
			if (n.contains(w)) {
				return true;
			}
		}
		return false;
	}
}