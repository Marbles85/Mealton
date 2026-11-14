package com.mealton.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.mealton.profile.UserProfile;

/**
 * Checks a recipe against the user's profile and returns plain-language
 * warnings (feature F01 at work).
 * <ul>
 * <li>Allergies: an ingredient whose name contains an allergy word ("peanut"
 * matches "peanut butter").</li>
 * <li>Diets: vegetarian, vegan, pescatarian, dairy-free and halal are checked
 * with simple word lists (every chosen restriction is checked). Other diets
 * (gluten-free, high-protein, low-carb, keto) cannot be judged from ingredient
 * names, so they are not checked.</li>
 * </ul>
 * This is a helpful warning, not a safety guarantee: it only knows the words in
 * the ingredient names.
 */
public final class ProfileCheck {

	private static final Set<String> MEAT = Set.of("chicken", "beef", "pork", "bacon", "ham", "turkey", "lamb",
			"sausage", "steak", "veal", "duck", "salami", "prosciutto", "chorizo", "meat", "mince", "meatball");
	private static final Set<String> FISH = Set.of("fish", "salmon", "tuna", "shrimp", "prawn", "cod", "anchovy",
			"crab", "lobster", "sardine", "tilapia", "trout", "squid", "oyster", "mussel");
	private static final Set<String> ANIMAL_PRODUCTS = Set.of("milk", "cheese", "butter", "cream", "yogurt", "yoghurt",
			"egg", "honey", "ghee", "mayonnaise", "gelatin", "whey");
	private static final Set<String> DAIRY = Set.of("milk", "cheese", "butter", "cream", "yogurt", "yoghurt", "ghee",
			"whey", "custard");
	private static final Set<String> NOT_HALAL = Set.of("pork", "bacon", "ham", "prosciutto", "salami", "chorizo",
			"lard", "gelatin", "wine", "beer", "rum", "brandy", "whiskey", "vodka");
	/**
	 * Plant-based foods that contain a dairy word, so they must not be flagged for
	 * vegans.
	 */
	private static final List<String> PLANT_BASED = List.of("peanut butter", "almond butter", "nut butter",
			"cashew butter", "coconut milk", "coconut cream", "almond milk", "soy milk", "oat milk", "rice milk",
			"cocoa butter");

	private ProfileCheck() {
	}

	/**
	 * Warnings for this recipe, or an empty list when nothing conflicts with the
	 * profile.
	 */
	public static List<String> conflicts(Recipe recipe, UserProfile profile) {
		List<String> warnings = new ArrayList<>();
		for (RecipeIngredient ingredient : recipe.getIngredients()) {
			String name = ingredient.name();
			for (String allergy : profile.allergies()) {
				if (containsPhrase(name, allergy)) {
					warnings.add("Allergy: " + name + " contains " + allergy);
				}
			}
			for (String restriction : profile.restrictions()) {
				String problem = dietProblem(name, restriction);
				if (problem != null && !warnings.contains(problem)) {
					warnings.add(problem);
				}
			}
		}
		return warnings;
	}

	private static String dietProblem(String ingredient, String restriction) {
		boolean plantBased = PLANT_BASED.stream().anyMatch(p -> containsPhrase(ingredient, p));
		boolean meat = containsAny(ingredient, MEAT);
		boolean fish = containsAny(ingredient, FISH);
		boolean animalProduct = !plantBased && containsAny(ingredient, ANIMAL_PRODUCTS);
		switch (restriction) {
		case "vegetarian":
			return meat || fish ? "Diet: " + ingredient + " is not vegetarian" : null;
		case "pescatarian":
			return meat ? "Diet: " + ingredient + " is not pescatarian" : null;
		case "vegan":
			return meat || fish || animalProduct ? "Diet: " + ingredient + " is not vegan" : null;
		case "dairy-free":
			return !plantBased && containsAny(ingredient, DAIRY) ? "Diet: " + ingredient + " is not dairy-free" : null;
		case "halal":
			return containsAny(ingredient, NOT_HALAL) ? "Diet: " + ingredient + " is not halal" : null;
		default:
			return null;
		}
	}

	private static boolean containsAny(String ingredient, Set<String> words) {
		return words.stream().anyMatch(w -> containsPhrase(ingredient, w));
	}

	/**
	 * True if the words of {@code phrase} appear in {@code text} as whole words
	 * (plurals are ignored).
	 */
	static boolean containsPhrase(String text, String phrase) {
		return (" " + normalizeWords(text) + " ").contains(" " + normalizeWords(phrase) + " ");
	}

	private static String normalizeWords(String text) {
		StringBuilder sb = new StringBuilder();
		for (String word : text.toLowerCase(Locale.ROOT).split("[^a-z]+")) {
			if (!word.isEmpty()) {
				if (sb.length() > 0) {
					sb.append(' ');
				}
				sb.append(RecipeMatcher.normalize(word));
			}
		}
		return sb.toString();
	}
}