package com.mealton.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mealton.facade.MealtonFacade;
import com.mealton.llm.MockLLMClient;

/**
 * All recipes are found on the web: Claude finds the pages, each page is read
 * like "Import from a link".
 */
class WebDiscoveryTest {
	/** A Claude that can search: it "finds" 12 pages per topic. */
	static class SearchingClaude extends MockLLMClient {
		int searches;

		@Override
		public boolean canSearchWeb() {
			return true;
		}

		@Override
		public synchronized List<String> searchRecipePages(String topic, String diet, int maxSearches) {
			searches++;
			List<String> links = new ArrayList<>();
			for (int i = 0; i < 12; i++) {
				links.add("https://example.com/" + topic.replace(' ', '-') + "-" + i);
			}
			links.add("https://example.com/not-a-recipe");
			return links;
		}
	}

	private MealtonFacade facade;
	private SearchingClaude claude;

	@BeforeEach
	void setUp() {
		facade = new MealtonFacade();
		StarterRecipes.loadInto(facade);
		claude = new SearchingClaude();
		facade.enableAgent(claude);
		facade.setImportFetcher(new RecipeImporter.Fetcher() {
			@Override
			public String page(String url) throws IOException {
				if (url.endsWith("not-a-recipe")) {
					return "<html>no recipe here</html>";
				}
				String name = "Dish " + url.substring(url.lastIndexOf('/') + 1);
				return "<script type=\"application/ld+json\">{\"@type\":\"Recipe\",\"name\":\"" + name + "\","
						+ "\"recipeIngredient\":[\"2 eggs\",\"100 g flour\"],\"recipeInstructions\":[\"Mix\",\"Cook\"],"
						+ "\"recipeYield\":\"4\",\"aggregateRating\":{\"ratingValue\":\"4.6\",\"ratingCount\":\"321\"}}</script>";
			}

			@Override
			public Path image(String url) {
				return null;
			}
		});
	}

	@Test
	void recipesComeFromWebPagesWithTheirLinkAndRating() {
		List<Recipe> got = facade.generateRecipeDrafts(40, "", facade.recipeTitles());
		assertEquals(40, got.size());
		Recipe r = got.get(0);
		assertTrue(r.getSourceUrl().startsWith("https://example.com/"));
		assertEquals(4.6, r.getPublicRating(), 1e-9);
		assertEquals(321, r.getPublicRatingCount());
		assertFalse(r.getSteps().isEmpty());
		long distinct = got.stream().map(Recipe::getTitle).distinct().count();
		assertEquals(40L, distinct, "no repeated titles");
		assertTrue(claude.searches >= 3);
	}

	@Test
	void theTabIsFilledUpToAboutAHundredRecipes() {
		int want = facade.discoveryCount(100);
		assertTrue(want >= 30);
		List<Recipe> got = facade.generateRecipeDrafts(want, "", facade.recipeTitles());
		assertNotNull(got);
		int before = facade.getRecipes().size();
		int added = facade.swapInDiscoveredRecipes(got);
		assertEquals(got.size(), added);
		assertTrue(facade.getRecipes().size() >= before + added - 1);
		assertTrue(facade.getRecipes().size() >= 99 || got.size() < want, "about 100 recipes when the web gave enough");
	}

	@Test
	void aCutOffReplyStillKeepsTheCompleteRecipes() {
		String reply = "[{\"title\":\"A\",\"servings\":2,\"ingredients\":[{\"name\":\"egg\",\"quantity\":2,\"unit\":\"\"}],\"steps\":[\"x\"]},"
				+ "{\"title\":\"B\",\"servings\":2,\"ingredients\":[{\"name\":\"egg\",\"quantity\":2,\"unit\":\"\"}],\"steps\":[\"y\"]},"
				+ "{\"title\":\"C\",\"servings\":2,\"ingredients\":[{\"name\":\"eg";
		assertEquals(2, RecipeDiscovery.parse(reply, List.of()).size());
	}
}