package com.mealton.recipe;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mealton.pantry.Freshness;
import com.mealton.pantry.PantryItem;
import com.mealton.util.UnitConverter;

/**
 * Compares saved recipes with the pantry (feature F07) and works out what is
 * missing (used by F08). It only does the matching; the order of the results
 * comes from a {@link RankingStrategy}. Rules: names are compared ignoring
 * capitals and simple plurals ("egg" matches "eggs"), expired pantry items do
 * not count, and amounts in different but compatible units are converted (200 g
 * needs 0.2 kg). If the units cannot be compared at all (a "bag" of flour
 * against 200 g), having the ingredient counts as enough.
 */
public class RecipeMatcher {

	/** Returns one match per recipe, ordered by the given strategy. */
	public List<RecipeMatch> match(List<Recipe> recipes, List<PantryItem> pantry, LocalDate today,
			RankingStrategy strategy) {
		List<RecipeMatch> matches = new ArrayList<>();
		for (Recipe recipe : recipes) {
			matches.add(matchOne(recipe, pantry, today));
		}
		matches.sort(strategy::compare);
		return matches;
	}

	/** Checks a single recipe against the pantry. */
	public RecipeMatch matchOne(Recipe recipe, List<PantryItem> pantry, LocalDate today) {
		List<RecipeIngredient> available = new ArrayList<>();
		List<RecipeIngredient> missing = new ArrayList<>();
		int expiringUsed = 0;

		for (RecipeIngredient needed : recipe.getIngredients()) {
			double have = 0;
			boolean comparable = true;
			boolean found = false;
			boolean usesExpiring = false;
			for (PantryItem item : pantry) {
				if (item.freshness(today) == Freshness.EXPIRED || !sameIngredient(needed.name(), item.getName())) {
					continue;
				}
				found = true;
				Double inNeededUnit = inUnit(item, needed.unit());
				if (inNeededUnit == null) {
					comparable = false;
				} else {
					have += inNeededUnit;
				}
				if (item.freshness(today) == Freshness.USE_SOON) {
					usesExpiring = true;
				}
			}
			if (found && (!comparable || have >= needed.quantity() - 1e-9)) {
				available.add(needed);
				if (usesExpiring) {
					expiringUsed++;
				}
			} else {
				missing.add(new RecipeIngredient(needed.name(), found ? needed.quantity() - have : needed.quantity(),
						needed.unit()));
			}
		}
		return new RecipeMatch(recipe, available, missing, expiringUsed);
	}

	/**
	 * The pantry amount expressed in the recipe's unit, or null if the two units
	 * cannot be compared.
	 */
	private static Double inUnit(PantryItem item, String recipeUnit) {
		String pantryUnit = item.getUnit();
		if (pantryUnit.equalsIgnoreCase(recipeUnit)) {
			return item.getQuantity();
		}
		if (UnitConverter.isKnown(pantryUnit) && UnitConverter.isKnown(recipeUnit)) {
			try {
				return UnitConverter.convert(item.getQuantity(), pantryUnit, recipeUnit);
			} catch (IllegalArgumentException e) {
				return null; // weight against volume
			}
		}
		return null;
	}

	private static boolean sameIngredient(String a, String b) {
		return normalize(a).equals(normalize(b));
	}

	/**
	 * Lower-cases and strips a simple plural ending so "Eggs", "egg" and
	 * "tomatoes"/"tomato" match.
	 */
	static String normalize(String name) {
		String n = name.trim().toLowerCase(Locale.ROOT);
		if (n.endsWith("ies") && n.length() > 4) {
			return n.substring(0, n.length() - 3) + "y";
		}
		if (n.endsWith("oes") && n.length() > 4) {
			return n.substring(0, n.length() - 2);
		}
		if (n.endsWith("s") && !n.endsWith("ss") && n.length() > 3) {
			return n.substring(0, n.length() - 1);
		}
		return n;
	}
}