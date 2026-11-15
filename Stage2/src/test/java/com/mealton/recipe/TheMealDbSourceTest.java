package com.mealton.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mealton.facade.MealtonFacade;

/**
 * All recipes come from a free recipe search service, with photo, steps, tags
 * and link.
 */
class TheMealDbSourceTest {
	private static String meal(int n, String area, String category) {
		return "{\"idMeal\":\"" + (1000 + n) + "\",\"strMeal\":\"Dish " + n + "\",\"strCategory\":\"" + category
				+ "\",\"strArea\":\"" + area
				+ "\",\"strInstructions\":\"STEP 1\\r\\n1. Boil the rice.\\r\\n2) Add the chicken.\\r\\nServe.\","
				+ "\"strMealThumb\":\"https://img.example/" + n + ".jpg\",\"strTags\":\"Spicy,Quick\","
				+ "\"strSource\":\"" + (n % 2 == 0 ? "https://food.example.com/r/" + n : "") + "\","
				+ "\"strIngredient1\":\"Rice\",\"strMeasure1\":\"200g\",\"strIngredient2\":\"Chicken\",\"strMeasure2\":\"1 cup\","
				+ "\"strIngredient3\":\"Salt\",\"strMeasure3\":\"Pinch\",\"strIngredient4\":\"\",\"strMeasure4\":\" \"}";
	}

	/** 26 letters, each with 4 recipes = 104 recipes; the first 52 are Japanese. */
	private final RecipeImporter.Fetcher fake = new RecipeImporter.Fetcher() {
		@Override
		public String page(String url) throws IOException {
			char letter = url.charAt(url.length() - 1);
			int base = (letter - 'a') * 4;
			StringBuilder sb = new StringBuilder("{\"meals\":[");
			for (int i = 0; i < 4; i++) {
				if (i > 0) {
					sb.append(',');
				}
				int n = base + i;
				sb.append(meal(n, n < 52 ? "Japanese" : "Italian", n % 3 == 0 ? "Vegetarian" : "Chicken"));
			}
			return sb.append("]}").toString();
		}

		@Override
		public Path image(String url) {
			return null;
		}
	};

	private TheMealDbSource source;

	@BeforeEach
	void setUp() {
		source = new TheMealDbSource(fake);
	}

	@Test
	void findsTheRequestedNumberWithTheirDetails() throws IOException {
		List<RecipeImporter.Imported> got = source.find(100, "", new HashSet<>(), n -> {
		});
		assertEquals(100, got.size());
		Recipe r = got.get(0).recipe();
		assertEquals(4, r.getServings());
		assertEquals(3, r.getIngredients().size());
		assertEquals("rice", r.getIngredients().get(0).name().toLowerCase());
		assertEquals(200.0, r.getIngredients().get(0).quantity(), 1e-9);
		assertEquals(List.of("Boil the rice.", "Add the chicken.", "Serve."), r.getSteps());
		assertTrue(r.getSourceUrl().startsWith("https://"));
		assertTrue(r.getAiTags().contains("spicy"));
	}

	@Test
	void japaneseRecipesAreTaggedAsianSoTheFilterFindsThem() throws IOException {
		List<RecipeImporter.Imported> got = source.find(60, "asian", new HashSet<>(), n -> {
		});
		assertEquals(52, got.size(), "only recipes that match the filter");
		assertTrue(got.stream().allMatch(i -> i.recipe().getAiTags().contains("asian")));
	}

	@Test
	void everyFilterMustMatchAndNothingRandomIsAdded() throws IOException {
		List<RecipeImporter.Imported> got = source.find(60, "asian, vegetarian", new HashSet<>(), n -> {
		});
		assertTrue(got.size() > 0 && got.size() < 52);
		assertTrue(got.stream().allMatch(
				i -> i.recipe().getAiTags().contains("vegetarian") && i.recipe().getAiTags().contains("asian")));
		assertEquals(0, source.find(60, "asian, unobtainium", new HashSet<>(), n -> {
		}).size());
	}

	@Test
	void knownTitlesAreNotRepeated() throws IOException {
		Set<String> known = new HashSet<>();
		for (int i = 0; i < 90; i++) {
			known.add("dish " + i);
		}
		List<RecipeImporter.Imported> got = source.find(100, "", known, n -> {
		});
		assertEquals(14, got.size());
	}

	@Test
	void theFacadeFillsAllRecipesFromTheServiceWithoutClaude() {
		MealtonFacade facade = new MealtonFacade();
		facade.setSpoonacularKey(""); // never use a real key in a test
		StarterRecipes.loadInto(facade);
		facade.setImportFetcher(fake);
		int want = facade.discoveryCount(100);
		List<Recipe> got = facade.generateRecipeDrafts(want, "", facade.recipeTitles());
		assertEquals(want, got.size());
		int before = facade.getRecipes().size();
		assertEquals(want, facade.swapInDiscoveredRecipes(got));
		assertTrue(facade.getRecipes().size() >= before + want - 1);
		assertTrue(facade.canFindRecipes());
	}

	@Test
	void whenNothingMatchesTheFiltersTheMessageNamesTheService() {
		MealtonFacade facade = new MealtonFacade();
		facade.setSpoonacularKey(""); // never use a real key in a test
		StarterRecipes.loadInto(facade);
		facade.setImportFetcher(fake);
		IllegalStateException e = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
				() -> facade.generateRecipeDrafts(30, "dessert, unobtainium", facade.recipeTitles()));
		assertTrue(e.getMessage().contains("does not have any new recipes for \"dessert, unobtainium\""));
	}

	@Test
	void glutenFreeAndGlutenfreeFindTheSameTaggedRecipes() throws IOException {
		RecipeImporter.Fetcher tagged = new RecipeImporter.Fetcher() {
			@Override
			public String page(String url) {
				if (!url.endsWith("f=a")) {
					return "{\"meals\":null}";
				}
				return "{\"meals\":[{\"idMeal\":\"1\",\"strMeal\":\"Flourless Cake\",\"strCategory\":\"Dessert\",\"strArea\":\"French\","
						+ "\"strInstructions\":\"Bake.\",\"strMealThumb\":\"\",\"strTags\":\"Cake,GlutenFree\",\"strSource\":\"\","
						+ "\"strIngredient1\":\"Egg\",\"strMeasure1\":\"2\"},{\"idMeal\":\"2\",\"strMeal\":\"Bread\",\"strCategory\":\"Side\","
						+ "\"strArea\":\"French\",\"strInstructions\":\"Bake.\",\"strMealThumb\":\"\",\"strTags\":\"\",\"strSource\":\"\","
						+ "\"strIngredient1\":\"Flour\",\"strMeasure1\":\"2 cup\"}]}";
			}

			@Override
			public Path image(String url) {
				return null;
			}
		};
		TheMealDbSource s = new TheMealDbSource(tagged);
		assertEquals(1, s.find(10, "glutenfree", new HashSet<>(), n -> {
		}).size());
		assertEquals(1, s.find(10, "Gluten Free", new HashSet<>(), n -> {
		}).size());
		assertEquals(1, s.find(10, "dessert, gluten-free", new HashSet<>(), n -> {
		}).size());
		assertEquals(0, s.find(10, "dessert, vegan", new HashSet<>(), n -> {
		}).size());
	}
}