package com.mealton.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mealton.facade.MealtonFacade;

/**
 * With a Spoonacular key, Refresh mixes Spoonacular and TheMealDB; without one
 * it uses TheMealDB.
 */
class MixedSourcesTest {
	private static String spoon(int n) {
		return "{\"id\":" + n + ",\"title\":\"Spoon " + n + "\",\"image\":\"https://img.example/s" + n
				+ ".jpg\",\"servings\":2," + "\"readyInMinutes\":25,\"sourceUrl\":\"https://site.example/" + n
				+ "\",\"sourceName\":\"Site\",\"spoonacularScore\":80,"
				+ "\"extendedIngredients\":[{\"name\":\"rice\",\"amount\":1,\"unit\":\"cup\"}],"
				+ "\"analyzedInstructions\":[{\"steps\":[{\"step\":\"Cook.\"}]}]}";
	}

	private static String meal(int n) {
		return "{\"idMeal\":\"" + n + "\",\"strMeal\":\"Meal " + n
				+ "\",\"strCategory\":\"Chicken\",\"strArea\":\"Thai\","
				+ "\"strInstructions\":\"Boil.\\nServe.\",\"strMealThumb\":\"https://img.example/m" + n
				+ ".jpg\",\"strTags\":\"\",\"strSource\":\"\","
				+ "\"strIngredient1\":\"Rice\",\"strMeasure1\":\"1 cup\"}";
	}

	private final RecipeImporter.Fetcher fake = new RecipeImporter.Fetcher() {
		@Override
		public String page(String url) throws IOException {
			if (url.startsWith("https://api.spoonacular.com")) {
				StringBuilder sb = new StringBuilder("{\"results\":[");
				for (int i = 0; i < 60; i++) {
					sb.append(i > 0 ? "," : "").append(spoon(i));
				}
				return sb.append("]}").toString();
			}
			char letter = url.charAt(url.length() - 1);
			int base = (letter - 'a') * 3;
			return "{\"meals\":[" + meal(base) + "," + meal(base + 1) + "," + meal(base + 2) + "]}";
		}

		@Override
		public Path image(String url) {
			return null;
		}
	};

	@Test
	void withAKeyBothServicesContribute() {
		MealtonFacade facade = new MealtonFacade();
		facade.setImportFetcher(fake);
		facade.setSpoonacularKey("abc");
		List<Recipe> got = facade.generateRecipeDrafts(30, "", List.of());
		assertEquals(30, got.size());
		long spoon = got.stream().filter(r -> r.getTitle().startsWith("Spoon")).count();
		long meal = got.stream().filter(r -> r.getTitle().startsWith("Meal")).count();
		assertTrue(spoon > 0 && meal > 0, "a mix of both: " + spoon + " / " + meal);
		assertEquals(30, new HashSet<>(got.stream().map(Recipe::getTitle).toList()).size());
	}

	@Test
	void withoutAKeyOnlyTheMealDbIsUsed() {
		MealtonFacade facade = new MealtonFacade();
		facade.setImportFetcher(fake);
		facade.setSpoonacularKey("");
		List<Recipe> got = facade.generateRecipeDrafts(20, "", List.of());
		assertEquals(20, got.size());
		assertTrue(got.stream().allMatch(r -> r.getTitle().startsWith("Meal")));
	}
}