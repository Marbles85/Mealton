package com.mealton.recipe;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntConsumer;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A free recipe search service (themealdb.com): every recipe comes with its
 * photo, ingredients, instructions, category, cuisine and a link to the
 * original page. It does not need an account or Claude. It has no star ratings.
 */
public class TheMealDbSource implements RecipeSource {
	static final String BASE = "https://www.themealdb.com/api/json/v1/1/";
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final Pattern STEP_HEADING = Pattern
			.compile("(?i)^\\s*(step\\s*\\d+\\s*[:.\\-]?|\\d+\\s*[.):\\-])\\s*");
	private static final Pattern ONLY_HEADING = Pattern
			.compile("(?i)^\\s*step\\s*\\d+\\s*[:.\\-]?\\s*$|^\\s*\\d+\\s*[.):]?\\s*$");

	/** Cuisines grouped under a broad word people actually filter by. */
	private static final Map<String, String> REGION = Map.ofEntries(Map.entry("japanese", "asian"),
			Map.entry("chinese", "asian"), Map.entry("thai", "asian"), Map.entry("vietnamese", "asian"),
			Map.entry("malaysian", "asian"), Map.entry("filipino", "asian"), Map.entry("indian", "asian"),
			Map.entry("french", "european"), Map.entry("italian", "european"), Map.entry("greek", "european"),
			Map.entry("spanish", "european"), Map.entry("polish", "european"), Map.entry("portuguese", "european"),
			Map.entry("croatian", "european"), Map.entry("dutch", "european"), Map.entry("irish", "european"),
			Map.entry("british", "european"), Map.entry("mexican", "latin american"),
			Map.entry("jamaican", "caribbean"), Map.entry("moroccan", "african"), Map.entry("egyptian", "african"),
			Map.entry("kenyan", "african"), Map.entry("tunisian", "african"));

	private final RecipeImporter.Fetcher fetcher;

	public TheMealDbSource(RecipeImporter.Fetcher fetcher) {
		this.fetcher = fetcher;
	}

	@Override
	public String name() {
		return "TheMealDB";
	}

	@Override
	public List<RecipeImporter.Imported> find(int count, String hint, Set<String> knownTitles, IntConsumer progress)
			throws IOException {
		List<JsonNode> meals = loadAllMeals();
		if (meals.isEmpty()) {
			throw new IOException(
					"TheMealDB did not return any recipes. Check your internet connection and try Refresh again.");
		}
		Collections.shuffle(meals, new Random());
		List<JsonNode> chosen = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		List<List<String>> phrases = hintPhrases(hint);
		// only recipes that match EVERY filter the person asked for
		for (JsonNode meal : meals) {
			if (chosen.size() >= count) {
				break;
			}
			String title = meal.path("strMeal").asText("").trim();
			String key = title.toLowerCase(Locale.ROOT);
			if (title.isEmpty() || knownTitles.contains(key) || seen.contains(key)) {
				continue;
			}
			if (phrases.isEmpty() || matchesAll(meal, phrases)) {
				seen.add(key);
				chosen.add(meal);
			}
		}
		// photos are downloaded several at a time
		ExecutorService pool = Executors.newFixedThreadPool(6);
		List<Future<RecipeImporter.Imported>> jobs = new ArrayList<>();
		int[] ready = { 0 };
		for (JsonNode meal : chosen) {
			jobs.add(pool.submit(() -> {
				Recipe recipe = toRecipe(meal);
				if (recipe == null) {
					return null;
				}
				Path photo = null;
				String thumb = meal.path("strMealThumb").asText("");
				if (thumb.startsWith("http")) {
					try {
						photo = fetcher.image(thumb);
					} catch (IOException | RuntimeException noPicture) {
						// the recipe is fine without its picture
					}
				}
				synchronized (ready) {
					ready[0]++;
					progress.accept(ready[0]);
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

	/** The whole catalogue: one request per first letter, a few at a time. */
	private List<JsonNode> loadAllMeals() throws IOException {
		ExecutorService pool = Executors.newFixedThreadPool(6);
		List<Future<List<JsonNode>>> jobs = new ArrayList<>();
		for (char c = 'a'; c <= 'z'; c++) {
			String url = BASE + "search.php?f=" + c;
			jobs.add(pool.submit(() -> {
				JsonNode meals = JSON.readTree(fetcher.page(url)).path("meals");
				List<JsonNode> list = new ArrayList<>();
				if (meals.isArray()) {
					meals.forEach(list::add);
				}
				return list;
			}));
		}
		List<JsonNode> all = new ArrayList<>();
		IOException failure = null;
		for (Future<List<JsonNode>> job : jobs) {
			try {
				all.addAll(job.get());
			} catch (java.util.concurrent.ExecutionException e) {
				failure = e.getCause() instanceof IOException io ? io : new IOException(String.valueOf(e.getCause()));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
		pool.shutdownNow();
		if (all.isEmpty() && failure != null) {
			throw failure;
		}
		return all;
	}

	/**
	 * "gluten free, dessert" becomes [[gluten, free], [dessert]]: each filter is a
	 * phrase of words.
	 */
	private static List<List<String>> hintPhrases(String hint) {
		List<List<String>> phrases = new ArrayList<>();
		if (hint != null) {
			for (String phrase : hint.toLowerCase(Locale.ROOT).split(",")) {
				List<String> words = new ArrayList<>();
				for (String w : phrase.split("[^a-z]+")) {
					if (w.length() >= 2) {
						words.add(w);
					}
				}
				if (!words.isEmpty()) {
					phrases.add(words);
				}
			}
		}
		return phrases;
	}

	private static boolean matchesAll(JsonNode meal, List<List<String>> phrases) {
		StringBuilder text = new StringBuilder();
		text.append(meal.path("strMeal").asText("")).append(' ').append(meal.path("strCategory").asText("")).append(' ')
				.append(meal.path("strArea").asText("")).append(' ').append(meal.path("strTags").asText(","));
		for (int i = 1; i <= 20; i++) {
			text.append(' ').append(meal.path("strIngredient" + i).asText(""));
		}
		String area = meal.path("strArea").asText("").toLowerCase(Locale.ROOT);
		text.append(' ').append(REGION.getOrDefault(area, ""));
		// the same matching as the recipe list on screen, so what is found is what is
		// shown
		for (List<String> phrase : phrases) {
			if (!com.mealton.util.TextMatch.containsAll(text.toString().replace(',', ' '), String.join(" ", phrase))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * One TheMealDB entry as a Mealton recipe; null when it has no usable
	 * ingredients or steps.
	 */
	static Recipe toRecipe(JsonNode meal) {
		String title = meal.path("strMeal").asText("").trim();
		List<RecipeIngredient> ingredients = new ArrayList<>();
		for (int i = 1; i <= 20; i++) {
			String name = meal.path("strIngredient" + i).asText("").trim();
			if (name.isEmpty()) {
				continue;
			}
			String measure = meal.path("strMeasure" + i).asText("").trim();
			ingredients.add(ingredient(name, measure));
		}
		List<String> steps = steps(meal.path("strInstructions").asText(""));
		if (title.isEmpty() || ingredients.isEmpty() || steps.isEmpty()) {
			return null;
		}
		Recipe recipe = new Recipe(title, 4, ingredients, steps);
		String link = meal.path("strSource").asText("").trim();
		if (!link.matches("(?i)https?://\\S+")) {
			link = "https://www.themealdb.com/meal/" + meal.path("idMeal").asText("");
			recipe.setSource("TheMealDB", link);
		} else {
			recipe.setSource(hostOf(link), link);
		}
		List<String> tags = new ArrayList<>();
		String area = meal.path("strArea").asText("").trim().toLowerCase(Locale.ROOT);
		if (!area.isEmpty() && !area.equals("unknown")) {
			tags.add(area);
			if (REGION.containsKey(area)) {
				tags.add(REGION.get(area));
			}
		}
		String category = meal.path("strCategory").asText("").trim().toLowerCase(Locale.ROOT);
		if (!category.isEmpty()) {
			tags.add(category);
			if (category.equals("vegetarian") || category.equals("vegan")) {
				tags.add("meatless");
			}
		}
		for (String t : meal.path("strTags").asText("").split(",")) {
			tags.add(t);
		}
		recipe.setAiTags(tags);
		return recipe;
	}

	private static String hostOf(String url) {
		String host = url.replaceFirst("(?i)^https?://", "").replaceFirst("/.*$", "").replaceFirst("(?i)^www\\.", "");
		return host.isEmpty() ? "TheMealDB" : host;
	}

	/**
	 * "1 cup" + "Rice" becomes 1 cup of rice; "Pinch" + "Salt" becomes 1 of salt.
	 */
	private static RecipeIngredient ingredient(String name, String measure) {
		String m = measure.replaceAll("(?<=\\d)(?=[a-zA-Z])", " ").trim();
		if (!m.isEmpty() && Character.isDigit(m.charAt(0))) {
			RecipeIngredient parsed = RecipeImporter.parseLine(m + " " + name);
			if (parsed != null) {
				return parsed;
			}
		}
		return new RecipeIngredient(name, 1, "");
	}

	/**
	 * Splits the instructions into steps: one per line, or per few sentences if it
	 * is one block.
	 */
	static List<String> steps(String text) {
		List<String> steps = new ArrayList<>();
		for (String line : text.replace("\r", "").split("\n")) {
			String s = line.trim();
			if (s.isEmpty() || ONLY_HEADING.matcher(s).matches()) {
				continue;
			}
			s = STEP_HEADING.matcher(s).replaceFirst("").trim();
			if (!s.isEmpty()) {
				steps.add(s);
			}
		}
		if (steps.size() <= 2 && !steps.isEmpty()) {
			String all = String.join(" ", steps);
			String[] sentences = all.split("(?<=[.!?])\\s+(?=[A-Z])");
			if (sentences.length > 3) {
				steps.clear();
				StringBuilder cur = new StringBuilder();
				int n = 0;
				for (String s : sentences) {
					cur.append(cur.length() == 0 ? "" : " ").append(s);
					if (++n == 2) {
						steps.add(cur.toString());
						cur.setLength(0);
						n = 0;
					}
				}
				if (cur.length() > 0) {
					steps.add(cur.toString());
				}
			}
		}
		return steps;
	}
}