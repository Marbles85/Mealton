package com.mealton.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
		facade.setSpoonacularKey(""); // never use a real key in a test
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

	private static RecipeSource source(String name, IOException failure) {
		return new RecipeSource() {
			@Override
			public String name() {
				return name;
			}

			@Override
			public List<RecipeImporter.Imported> find(int count, String hint, java.util.Set<String> knownTitles,
					java.util.function.IntConsumer progress) throws IOException {
				if (failure != null) {
					throw failure;
				}
				return List.of();
			}
		};
	}

	@Test
	void searchingNeverFallsBackToClaudeWebSearch() {
		facade.setRecipeSource(source("TheMealDB", null));
		IllegalStateException e = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
				() -> facade.generateRecipeDrafts(40, "burgers", facade.recipeTitles()));
		assertTrue(e.getMessage().contains("not have any new recipes"), e.getMessage());
		assertEquals(0, claude.searches, "no Claude web search, so no tokens are spent");
	}

	@Test
	void aUsedUpDailyLimitIsReportedAndClaudeStaysOff() {
		facade.setRecipeSource(source("Spoonacular", new IOException("the site answered with error 402")));
		IllegalStateException e = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
				() -> facade.generateRecipeDrafts(40, "", facade.recipeTitles()));
		assertTrue(e.getMessage().contains("free daily limit is used up"), e.getMessage());
		assertEquals(0, claude.searches);
	}

	@Test
	void aCutOffReplyStillKeepsTheCompleteRecipes() {
		String reply = "[{\"title\":\"A\",\"servings\":2,\"ingredients\":[{\"name\":\"egg\",\"quantity\":2,\"unit\":\"\"}],\"steps\":[\"x\"]},"
				+ "{\"title\":\"B\",\"servings\":2,\"ingredients\":[{\"name\":\"egg\",\"quantity\":2,\"unit\":\"\"}],\"steps\":[\"y\"]},"
				+ "{\"title\":\"C\",\"servings\":2,\"ingredients\":[{\"name\":\"eg";
		assertEquals(2, RecipeDiscovery.parse(reply, List.of()).size());
	}
}