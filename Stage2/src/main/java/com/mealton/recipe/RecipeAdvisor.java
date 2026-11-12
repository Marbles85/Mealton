package com.mealton.recipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.mealton.pantry.PantryInventory;
import com.mealton.profile.ProfileService;
import com.mealton.shopping.ShoppingItem;
import com.mealton.shopping.ShoppingService;

/**
 * Puts the recipe book, the pantry and the shopping list together: "what can I
 * cook with what I have?" (F07) and "put what is missing on my shopping list"
 * (F08). Recipes that clash with the user's profile (allergies, diet) get
 * warnings and are listed last. The facade and the AI agent both use this
 * class, so the logic lives in one place.
 */
public class RecipeAdvisor {
	private final RecipeBook book;
	private final PantryInventory pantry;
	private final ShoppingService shopping;
	private final ProfileService profile;
	private final RecipeMatcher matcher = new RecipeMatcher();

	public RecipeAdvisor(RecipeBook book, PantryInventory pantry, ShoppingService shopping) {
		this(book, pantry, shopping, new ProfileService());
	}

	public RecipeAdvisor(RecipeBook book, PantryInventory pantry, ShoppingService shopping, ProfileService profile) {
		this.book = book;
		this.pantry = pantry;
		this.shopping = shopping;
		this.profile = profile;
	}

	/**
	 * Ranks all saved recipes against the pantry.
	 *
	 * @param priority "best" (closest to cookable first) or "expiring" (use food
	 *                 about to spoil first); blank means best
	 */
	public List<RecipeMatch> suggest(String priority) {
		List<RecipeMatch> matches = new ArrayList<>();
		for (RecipeMatch match : matcher.match(book.getAll(), pantry.getItems(), pantry.today(),
				strategyFor(priority))) {
			matches.add(withWarnings(match));
		}
		// recipes with warnings go last; the sort is stable, so the chosen ranking is
		// kept inside each group
		matches.sort(Comparator.comparing(m -> !m.warnings().isEmpty()));
		return matches;
	}

	/**
	 * The "Can make now" tab: recipes where nothing is missing, ranked by the
	 * chosen priority.
	 */
	public List<RecipeMatch> canMakeNow(String priority) {
		return suggest(priority).stream().filter(RecipeMatch::isCookable).toList();
	}

	/**
	 * The "Almost there" tab: recipes that are missing only a few ingredients,
	 * fewest missing first. A recipe must have at least one ingredient in the
	 * pantry, so an empty pantry shows nothing here.
	 *
	 * @param maxMissing the most missing ingredients allowed (at least 1)
	 */
	public List<RecipeMatch> almostThere(int maxMissing) {
		if (maxMissing < 1) {
			throw new IllegalArgumentException("Almost there needs a limit of at least 1 missing ingredient.");
		}
		List<RecipeMatch> almost = new ArrayList<>();
		for (RecipeMatch match : suggest("best")) {
			if (!match.isCookable() && !match.available().isEmpty() && match.missing().size() <= maxMissing) {
				almost.add(match);
			}
		}
		// fewest missing first, then the better covered; the sort is stable, so warned
		// recipes stay last
		almost.sort(Comparator.comparing((RecipeMatch m) -> !m.warnings().isEmpty())
				.thenComparingInt(m -> m.missing().size())
				.thenComparing(Comparator.comparingDouble(RecipeMatch::coverage).reversed()));
		return almost;
	}

	/** How one saved recipe fits the pantry right now. */
	public RecipeMatch check(long recipeId) {
		return withWarnings(matcher.matchOne(book.get(recipeId), pantry.getItems(), pantry.today()));
	}

	private RecipeMatch withWarnings(RecipeMatch match) {
		return match.withWarnings(ProfileCheck.conflicts(match.recipe(), profile.get()));
	}

	/**
	 * Adds everything a recipe is missing to the shopping list (only the shortfall,
	 * for example 100 g flour when 500 g of 600 g is on hand). Amounts that are
	 * already on the list (not ticked off) are taken into account, so running this
	 * twice does not double the list.
	 *
	 * @return what was added; empty when the pantry and the list already cover the
	 *         recipe
	 */
	public List<RecipeIngredient> addMissingToShoppingList(long recipeId) {
		List<RecipeIngredient> added = new ArrayList<>();
		for (RecipeIngredient ingredient : check(recipeId).missing()) {
			double stillNeeded = ingredient.quantity() - alreadyOnList(ingredient);
			if (stillNeeded > 1e-9) {
				shopping.addItem(new ShoppingItem(ingredient.name(), stillNeeded, ingredient.unit(), null));
				added.add(new RecipeIngredient(ingredient.name(), stillNeeded, ingredient.unit()));
			}
		}
		return added;
	}

	/**
	 * How much of this ingredient (same name and unit) is already waiting on the
	 * shopping list.
	 */
	private double alreadyOnList(RecipeIngredient ingredient) {
		double total = 0;
		for (ShoppingItem item : shopping.getItems()) {
			if (!item.isChecked()
					&& RecipeMatcher.normalize(item.getName()).equals(RecipeMatcher.normalize(ingredient.name()))
					&& item.getUnit().equalsIgnoreCase(ingredient.unit())) {
				total += item.getQuantity();
			}
		}
		return total;
	}

	static RankingStrategy strategyFor(String priority) {
		String p = priority == null ? "" : priority.trim().toLowerCase();
		switch (p) {
		case "":
		case "best":
		case "best match":
			return new BestMatchStrategy();
		case "expiring":
		case "use expiring first":
			return new UseExpiringFirstStrategy();
		default:
			throw new IllegalArgumentException("Unknown priority '" + priority + "'. Use 'best' or 'expiring'.");
		}
	}
}