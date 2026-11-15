package com.mealton.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The Spoonacular source turns its answer into recipes with rating, time, tags
 * and link.
 */
class SpoonacularSourceTest {
	private static String result(int n) {
		return "{\"id\":" + n + ",\"title\":\"Pad " + n + "\",\"image\":\"https://img.example/" + n
				+ ".jpg\",\"servings\":2," + "\"readyInMinutes\":35,\"sourceUrl\":\"https://site.example/r/" + n
				+ "\",\"sourceName\":\"Site\","
				+ "\"spoonacularScore\":84.0,\"aggregateLikes\":120,\"cuisines\":[\"Thai\"],\"dishTypes\":[\"main course\"],"
				+ "\"diets\":[\"gluten free\"],\"extendedIngredients\":[{\"name\":\"rice noodles\",\"amount\":200,\"unit\":\"g\"},"
				+ "{\"name\":\"eggs\",\"amount\":2,\"unit\":\"\"}],\"analyzedInstructions\":[{\"steps\":[{\"number\":1,\"step\":\"Soak the noodles.\"},"
				+ "{\"number\":2,\"step\":\"Stir fry.\"}]}]}";
	}

	private String lastUrl = "";

	private final RecipeImporter.Fetcher fake = new RecipeImporter.Fetcher() {
		@Override
		public String page(String url) throws IOException {
			lastUrl = url;
			if (url.contains("apiKey=bad")) {
				return "{\"status\":\"failure\",\"code\":402,\"message\":\"Your daily points limit of 150 has been reached.\"}";
			}
			StringBuilder sb = new StringBuilder("{\"results\":[");
			for (int i = 0; i < 20; i++) {
				sb.append(i > 0 ? "," : "").append(result(i));
			}
			return sb.append("],\"totalResults\":20}").toString();
		}

		@Override
		public Path image(String url) {
			return null;
		}
	};

	@Test
	void recipesCarryRatingTimeTagsAndLink() throws IOException {
		List<RecipeImporter.Imported> got = new SpoonacularSource(fake, "good").find(10, "thai", new HashSet<>(), n -> {
		});
		assertEquals(10, got.size());
		Recipe r = got.get(0).recipe();
		assertEquals(2, r.getServings());
		assertEquals(4.2, r.getPublicRating(), 1e-9);
		assertEquals(35, r.getTotalMinutes());
		assertEquals("https://site.example/r/" + r.getTitle().substring(4), r.getSourceUrl());
		assertTrue(r.getAiTags().contains("asian"));
		assertEquals(2, r.getSteps().size());
	}

	@Test
	void aRefusedKeyGivesAReadableError() {
		IOException e = org.junit.jupiter.api.Assertions.assertThrows(IOException.class,
				() -> new SpoonacularSource(fake, "bad").find(10, "", new HashSet<>(), n -> {
				}));
		assertTrue(e.getMessage().contains("daily points limit"));
	}

	@Test
	void knownFiltersAreSentAsDietTypeAndCuisineNotAsText() throws IOException {
		new SpoonacularSource(fake, "good").find(5, "Dessert, glutenfree, italian, chicken", new HashSet<>(), n -> {
		});
		assertTrue(lastUrl.contains("diet=gluten+free") || lastUrl.contains("diet=gluten%20free"), lastUrl);
		assertTrue(lastUrl.contains("type=dessert"), lastUrl);
		assertTrue(lastUrl.contains("cuisine=italian"), lastUrl);
		assertTrue(lastUrl.contains("query=chicken"), lastUrl);
	}
}