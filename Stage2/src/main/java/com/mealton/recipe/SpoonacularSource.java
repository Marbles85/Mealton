package com.mealton.recipe;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntConsumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealton.util.TextMatch;

/**
 * A recipe search service (spoonacular.com) that needs a free API key
 * (SPOONACULAR_API_KEY in .env). Its recipes come with a photo, ingredients,
 * steps, cooking time, a score from 0 to 100 that Mealton shows as a star
 * rating, and a link to the original website.
 */
public class SpoonacularSource implements RecipeSource {
	static final String BASE = "https://api.spoonacular.com/recipes/complexSearch";
	private static final ObjectMapper JSON = new ObjectMapper();

	private final RecipeImporter.Fetcher fetcher;
	private final String apiKey;

	public SpoonacularSource(RecipeImporter.Fetcher fetcher, String apiKey) {
		this.fetcher = fetcher;
		this.apiKey = apiKey;
	}

	@Override
	public String name() {
		return "Spoonacular";
	}

	@Override
	public List<RecipeImporter.Imported> find(int count, String hint, Set<String> knownTitles, IntConsumer progress)
			throws IOException {
		// ask for extra, because some are already in the app
		int ask = Math.min(100, count + Math.min(40, knownTitles.size() / 4 + 10));
		JsonNode results = search(hint, ask, new Random().nextInt(300));
		if (results.isEmpty()) {
			results = search(hint, ask, 0); // that page was past the end
		}
		List<JsonNode> chosen = new ArrayList<>();
		Set<String> seen = new java.util.HashSet<>();
		for (JsonNode n : results) {
			String key = n.path("title").asText("").trim().toLowerCase(Locale.ROOT);
			if (!key.isEmpty() && !knownTitles.contains(key) && seen.add(key) && chosen.size() < count) {
				chosen.add(n);
			}
		}
		ExecutorService pool = Executors.newFixedThreadPool(6);
		List<Future<RecipeImporter.Imported>> jobs = new ArrayList<>();
		int[] ready = { 0 };
		for (JsonNode n : chosen) {
			jobs.add(pool.submit(() -> {
				Recipe recipe = toRecipe(n);
				if (recipe == null) {
					return null;
				}
				Path photo = null;
				String image = n.path("image").asText("");
				if (image.startsWith("http")) {
					try {
						photo = fetcher.image(image);
					} catch (IOException | RuntimeException noPicture) {
						// the recipe is fine without its picture
					}
				}
				synchronized (ready) {
					progress.accept(++ready[0]);
				}
				return new RecipeImporter.Imported(recipe, photo);
			}));
		}
		List<RecipeImporter.Imported> out = new ArrayList<>();
		for (Future<RecipeImporter.Imported> job : jobs) {
			try {
				RecipeImporter.Imported got = job.get();
				if (got != null) {
					out.add(got);
				}
			} catch (java.util.concurrent.ExecutionException ignored) {
				// one bad recipe is skipped
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
		pool.shutdownNow();
		return out;
	}

	private static final List<String> DIETS = List.of("gluten free", "ketogenic", "vegetarian", "lacto vegetarian",
			"ovo vegetarian", "vegan", "pescetarian", "paleo", "primal", "low fodmap", "whole30");
	private static final List<String> TYPES = List.of("main course", "side dish", "dessert", "appetizer", "salad",
			"bread", "breakfast", "soup", "beverage", "sauce", "marinade", "fingerfood", "snack", "drink");
	private static final List<String> CUISINES = List.of("african", "asian", "american", "british", "cajun",
			"caribbean", "chinese", "eastern european", "european", "french", "german", "greek", "indian", "irish",
			"italian", "japanese", "jewish", "korean", "latin american", "mediterranean", "mexican", "middle eastern",
			"nordic", "southern", "spanish", "thai", "vietnamese");

	/**
	 * Finds the exact name in a list for a filter such as "glutenfree" or "Gluten
	 * Free", or null.
	 */
	private static String known(List<String> names, String phrase) {
		String p = TextMatch.normalize(phrase).replace(" ", "");
		for (String n : names) {
			if (n.replace(" ", "").equals(p)) {
				return n;
			}
		}
		return null;
	}

	private JsonNode search(String hint, int number, int offset) throws IOException {
		StringBuilder url = new StringBuilder(BASE).append("?apiKey=")
				.append(URLEncoder.encode(apiKey, StandardCharsets.UTF_8))
				.append("&addRecipeInformation=true&fillIngredients=true&instructionsRequired=true&number=")
				.append(number).append("&offset=").append(offset);
		// filters that Spoonacular knows (diet, dish type, cuisine) are sent as such;
		// the rest is searched as text
		List<String> diets = new ArrayList<>();
		List<String> types = new ArrayList<>();
		List<String> cuisines = new ArrayList<>();
		List<String> text = new ArrayList<>();
		if (hint != null) {
			for (String phrase : hint.split(",")) {
				if (TextMatch.normalize(phrase).isEmpty()) {
					continue;
				}
				String d = known(DIETS, phrase);
				String t = known(TYPES, phrase);
				String c = known(CUISINES, phrase);
				if (d != null) {
					diets.add(d);
				} else if (t != null) {
					types.add(t);
				} else if (c != null) {
					cuisines.add(c);
				} else {
					text.add(TextMatch.normalize(phrase));
				}
			}
		}
		if (!diets.isEmpty()) {
			url.append("&diet=").append(URLEncoder.encode(String.join(",", diets), StandardCharsets.UTF_8));
		}
		if (!types.isEmpty()) {
			url.append("&type=").append(URLEncoder.encode(types.get(0), StandardCharsets.UTF_8));
		}
		if (!cuisines.isEmpty()) {
			url.append("&cuisine=").append(URLEncoder.encode(String.join(",", cuisines), StandardCharsets.UTF_8));
		}
		if (!text.isEmpty()) {
			url.append("&query=").append(URLEncoder.encode(String.join(" ", text), StandardCharsets.UTF_8));
		}
		JsonNode root = JSON.readTree(fetcher.page(url.toString()));
		if (root.path("status").asText("").equals("failure")) {
			throw new IOException("Spoonacular said: " + root.path("message").asText("request refused")
					+ " (check SPOONACULAR_API_KEY in .env, and the daily limit of the free plan)");
		}
		JsonNode results = root.path("results");
		return results.isArray() ? results : JSON.createArrayNode();
	}

	/** One result as a Mealton recipe; null when it has no ingredients or steps. */
	static Recipe toRecipe(JsonNode n) {
		String title = n.path("title").asText("").trim();
		List<RecipeIngredient> ingredients = new ArrayList<>();
		for (JsonNode i : n.path("extendedIngredients")) {
			String name = i.path("nameClean").asText(i.path("name").asText("")).trim();
			if (name.isEmpty()) {
				continue;
			}
			double amount = i.path("amount").asDouble(1);
			String unit = RecipeImporter.canonicalUnit(i.path("unit").asText("").trim().toLowerCase(Locale.ROOT));
			ingredients.add(new RecipeIngredient(name, amount > 0 ? amount : 1, unit == null ? "" : unit));
		}
		List<String> steps = new ArrayList<>();
		for (JsonNode block : n.path("analyzedInstructions")) {
			for (JsonNode s : block.path("steps")) {
				String text = s.path("step").asText("").trim();
				if (!text.isEmpty()) {
					steps.add(text);
				}
			}
		}
		if (title.isEmpty() || ingredients.isEmpty() || steps.isEmpty()) {
			return null;
		}
		Recipe recipe = new Recipe(title, Math.max(1, n.path("servings").asInt(4)), ingredients, steps);
		String link = n.path("sourceUrl").asText("").trim();
		if (link.matches("(?i)https?://\\S+")) {
			String name = n.path("sourceName").asText("").trim();
			recipe.setSource(name.isEmpty() ? "Spoonacular" : name, link);
		} else if (n.path("spoonacularSourceUrl").asText("").matches("(?i)https?://\\S+")) {
			recipe.setSource("Spoonacular", n.path("spoonacularSourceUrl").asText());
		}
		int minutes = n.path("readyInMinutes").asInt(0);
		if (minutes > 0) {
			recipe.setTimes(0, Math.min(minutes, 1440));
		}
		double score = n.path("spoonacularScore").asDouble(0);
		int likes = n.path("aggregateLikes").asInt(0);
		if (score > 0) {
			recipe.setPublicRating(Math.round(score / 20.0 * 10) / 10.0, Math.max(likes, 1));
		}
		List<String> tags = new ArrayList<>();
		for (String key : List.of("diets", "dishTypes", "cuisines")) {
			for (JsonNode t : n.path(key)) {
				tags.add(t.asText(""));
			}
		}
		for (JsonNode c : n.path("cuisines")) {
			String lower = c.asText("").toLowerCase(Locale.ROOT);
			if (List.of("japanese", "chinese", "thai", "vietnamese", "korean", "indian", "asian").contains(lower)) {
				tags.add("asian");
			}
		}
		recipe.setAiTags(tags);
		return recipe;
	}
}