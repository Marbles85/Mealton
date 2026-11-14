package com.mealton.recipe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealton.llm.LLMClient;
import com.mealton.llm.MockLLMClient;
import com.mealton.util.UnitConverter;

/**
 * Asks the language model to invent recipes for the "All recipes" tab. The
 * reply must be a JSON array; every recipe in it is checked by the normal
 * Recipe rules, and anything unusable is skipped, so a bad reply can never put
 * a broken recipe on screen.
 */
public final class RecipeDiscovery {
	public static final String CREATOR = "Created by Claude";
	private static final ObjectMapper JSON = new ObjectMapper();

	private RecipeDiscovery() {
	}

	/**
	 * @param count       how many recipes to ask for (1 to 12 per call)
	 * @param diet        the user's diet, for example "vegetarian" (may be blank)
	 * @param hint        extra wishes such as the filters the user typed (may be
	 *                    blank)
	 * @param avoidTitles titles that already exist, so the new ones are different
	 */
	public static List<Recipe> generate(LLMClient llm, int count, String diet, String hint,
			Collection<String> avoidTitles) {
		if (llm == null) {
			return List.of();
		}
		try {
			int n = Math.max(1, Math.min(12, count));
			StringBuilder prompt = new StringBuilder();
			prompt.append("Invent ").append(n).append(" different, realistic home-cooking recipes. ")
					.append("Reply with ONLY a JSON array. Each item: ")
					.append("{\"title\":\"...\",\"servings\":4,\"prepMinutes\":10,\"cookMinutes\":20,")
					.append("\"ingredients\":[{\"name\":\"flour\",\"quantity\":200,\"unit\":\"g\"}],")
					.append("\"steps\":[\"...\"],\"tags\":[\"italian\",\"vegetarian\",\"quick\"]}. ")
					.append("Give each recipe 2 to 5 short lowercase filter tags (cuisine, meal type, style, main ingredient, diet). ")
					.append("Use only these units: g, kg, oz, lb, ml, l, tsp, tbsp, cup, or \"\" for countable items. ")
					.append("Use common ingredient names (for example \"egg\", \"tomato\", \"rice\"). ")
					.append("Give 4 to 12 ingredients and 3 to 8 clear steps. Vary the cuisines and meals.\n");
			if (diet != null && !diet.isBlank()) {
				prompt.append("Every recipe must suit this diet: ").append(diet).append(".\n");
			}
			if (hint != null && !hint.isBlank()) {
				prompt.append("The cook is interested in: ").append(hint).append(".\n");
			}
			if (avoidTitles != null && !avoidTitles.isEmpty()) {
				prompt.append("Do not repeat these titles: ")
						.append(String.join("; ", avoidTitles.stream().limit(80).toList())).append(".\n");
			}
			return parse(llm.generate(prompt.toString()), avoidTitles);
		} catch (RuntimeException e) {
			return List.of();
		}
	}

	/** Reads the model's reply; public so it can be tested without a model. */
	public static List<Recipe> parse(String reply, Collection<String> avoidTitles) {
		List<Recipe> out = new ArrayList<>();
		if (reply == null || reply.equals(MockLLMClient.DEFAULT_REPLY)) {
			return out;
		}
		int start = reply.indexOf('[');
		int end = reply.lastIndexOf(']');
		if (start < 0 || end <= start) {
			return out;
		}
		JsonNode array;
		try {
			array = JSON.readTree(reply.substring(start, end + 1));
		} catch (Exception e) {
			return out;
		}
		Set<String> taken = new HashSet<>();
		if (avoidTitles != null) {
			avoidTitles.forEach(t -> taken.add(t.toLowerCase(Locale.ROOT)));
		}
		for (JsonNode item : array) {
			try {
				String title = item.path("title").asText("").trim();
				if (title.isEmpty() || !taken.add(title.toLowerCase(Locale.ROOT))) {
					continue;
				}
				List<RecipeIngredient> ingredients = new ArrayList<>();
				for (JsonNode i : item.path("ingredients")) {
					String unit = i.path("unit").asText("").trim().toLowerCase(Locale.ROOT);
					unit = unit.isEmpty() || !UnitConverter.isKnown(unit) ? "" : RecipeImporter.canonicalUnit(unit);
					try {
						ingredients.add(
								new RecipeIngredient(i.path("name").asText(""), i.path("quantity").asDouble(0), unit));
					} catch (IllegalArgumentException skipLine) {
						// leave out just this ingredient
					}
				}
				List<String> steps = new ArrayList<>();
				for (JsonNode s : item.path("steps")) {
					String text = s.asText("").trim();
					if (!text.isEmpty()) {
						steps.add(text);
					}
				}
				int servings = item.path("servings").asInt(4);
				Recipe recipe = new Recipe(title, servings < 1 || servings > 100 ? 4 : servings, ingredients, steps);
				recipe.setTimes(clamp(item.path("prepMinutes").asInt(0)), clamp(item.path("cookMinutes").asInt(0)));
				recipe.setSource(CREATOR, "");
				List<String> tags = new ArrayList<>();
				for (JsonNode t : item.path("tags")) {
					tags.add(t.asText(""));
				}
				recipe.setAiTags(tags);
				out.add(recipe);
			} catch (RuntimeException skipRecipe) {
				// an unusable recipe is dropped, the rest are kept
			}
		}
		return out;
	}

	private static int clamp(int minutes) {
		return Math.max(0, Math.min(1440, minutes));
	}
}