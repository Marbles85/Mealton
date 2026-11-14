package com.mealton.recipe;

import java.util.List;
import java.util.Locale;

/**
 * Suggests how many days the leftovers of a recipe keep in the fridge. Rules go
 * by the most perishable ingredient (cautious, in line with common food-safety
 * advice); the cook can always type their own number on the recipe.
 */
public final class LeftoverFreshness {

	/** The suggested days and a short reason. */
	public record Suggestion(int days, String reason) {
	}

	private record Rule(int days, String reason, List<String> words) {
	}

	private static final List<Rule> RULES = List.of(
			new Rule(2, "seafood",
					List.of("fish", "salmon", "tuna", "cod", "tilapia", "shrimp", "prawn", "crab", "lobster", "scallop",
							"mussel", "clam", "oyster", "squid", "sardine", "anchov")),
			new Rule(2, "cooked rice", List.of("rice")),
			new Rule(3, "meat or poultry",
					List.of("chicken", "turkey", "beef", "pork", "lamb", "bacon", "sausage", "ham", "mince", "steak",
							"duck")),
			new Rule(3, "eggs, dairy or cream",
					List.of("egg", "cream", "milk", "cheese", "yogurt", "yoghurt", "custard", "mayo")),
			new Rule(4, "vegetables, beans or pasta",
					List.of("lentil", "bean", "chickpea", "pasta", "spaghetti", "noodle", "potato", "tomato",
							"broccoli", "carrot", "onion", "pepper", "spinach", "mushroom", "tofu", "vegetable")));

	private static final List<String> DRY = List.of("flour", "sugar", "oat", "butter", "chocolate", "baking");

	private LeftoverFreshness() {
	}

	/** The suggestion for a recipe (3 days when nothing is recognised). */
	public static Suggestion suggest(Recipe recipe) {
		Rule shortest = null;
		boolean dry = false;
		for (RecipeIngredient i : recipe.getIngredients()) {
			String name = i.name().toLowerCase(Locale.ROOT);
			for (Rule r : RULES) {
				for (String w : r.words()) {
					if (name.contains(w) && (shortest == null || r.days() < shortest.days())) {
						shortest = r;
					}
				}
			}
			for (String w : DRY) {
				if (name.contains(w)) {
					dry = true;
				}
			}
		}
		if (shortest != null) {
			return new Suggestion(shortest.days(), shortest.reason());
		}
		if (dry) {
			return new Suggestion(5, "baked goods keep longer");
		}
		return new Suggestion(3, "a safe general guess");
	}
}