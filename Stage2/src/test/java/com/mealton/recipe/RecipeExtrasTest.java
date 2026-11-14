package com.mealton.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mealton.facade.MealtonFacade;
import com.mealton.persistence.UserDataMapper;

/** Times, source link, photo and editing of recipes. */
class RecipeExtrasTest {
	@TempDir
	Path temp;

	private static Recipe pancakes() {
		return new Recipe("Pancakes", 2, RecipeParser.parseIngredients("200 g flour; 2 egg"),
				RecipeParser.parseSteps("Mix|Fry"));
	}

	@Test
	void timesAreAddedAndValidated() {
		Recipe r = pancakes();
		r.setTimes(10, 15);
		assertEquals(25, r.getTotalMinutes());
		assertThrows(IllegalArgumentException.class, () -> r.setTimes(-1, 5));
		assertThrows(IllegalArgumentException.class, () -> r.setTimes(5, 2000));
	}

	@Test
	void sourceNeedsAWebLinkAndBlankClearsIt() {
		Recipe r = pancakes();
		r.setSource("The Dish", "https://example.com/pancakes");
		assertEquals("The Dish", r.getSourceName());
		assertThrows(IllegalArgumentException.class, () -> r.setSource("x", "not a link"));
		r.setSource("", "");
		assertNull(r.getSourceName());
		assertNull(r.getSourceUrl());
	}

	@Test
	void starterRecipesHaveTimes() {
		for (Recipe r : StarterRecipes.create()) {
			assertTrue(r.getTotalMinutes() > 0, r.getTitle());
		}
	}

	@Test
	void ownRecipeCanBeEditedInPlaceButLibraryCannot() {
		MealtonFacade f = new MealtonFacade();
		StarterRecipes.loadInto(f);
		Recipe mine = f.saveRecipe(pancakes());
		f.rateRecipe(mine.getId(), 5);
		Recipe edited = new Recipe("Thick Pancakes", 4, RecipeParser.parseIngredients("300 g flour; 3 egg"),
				List.of("Mix"));
		edited.setTimes(5, 10);
		Recipe result = f.updateRecipe(mine.getId(), edited);
		assertEquals("Thick Pancakes", f.getRecipe(mine.getId()).getTitle());
		assertEquals(4, result.getServings());
		assertEquals(5, result.getRating());
		assertEquals(15, result.getTotalMinutes());
		long libraryId = f.getRecipes().stream().filter(Recipe::isLibrary).findFirst().orElseThrow().getId();
		assertThrows(IllegalArgumentException.class, () -> f.updateRecipe(libraryId, edited));
	}

	@Test
	void editingCannotTakeAnotherRecipesTitle() {
		MealtonFacade f = new MealtonFacade();
		Recipe a = f.saveRecipe(pancakes());
		Recipe b = f.saveRecipe(new Recipe("Omelette", 1, RecipeParser.parseIngredients("3 egg"), List.of()));
		Recipe clash = new Recipe("pancakes", 1, RecipeParser.parseIngredients("3 egg"), List.of());
		assertThrows(IllegalArgumentException.class, () -> f.updateRecipe(b.getId(), clash));
		assertEquals("Pancakes", f.getRecipe(a.getId()).getTitle());
	}

	@Test
	void photoIsCopiedKeptAndRemoved() throws IOException {
		MealtonFacade f = new MealtonFacade();
		Path media = temp.resolve("media");
		f.setMediaDirectory(media);
		Recipe r = f.saveRecipe(pancakes());
		Path picture = temp.resolve("dish.PNG");
		Files.write(picture, new byte[] { 1, 2, 3 });
		f.setRecipePhoto(r.getId(), picture);
		assertTrue(f.getRecipePhoto(r.getId()).isPresent());
		assertTrue(Files.exists(f.getRecipePhoto(r.getId()).get()));
		Path notAPicture = temp.resolve("notes.txt");
		Files.write(notAPicture, new byte[] { 1 });
		assertThrows(IllegalArgumentException.class, () -> f.setRecipePhoto(r.getId(), notAPicture));
		Path first = f.getRecipePhoto(r.getId()).get();
		f.removeRecipePhoto(r.getId());
		assertFalse(Files.exists(first));
		assertTrue(f.getRecipePhoto(r.getId()).isEmpty());
	}

	@Test
	void photoWithoutAMediaFolderFailsClearly() throws IOException {
		MealtonFacade f = new MealtonFacade();
		Recipe r = f.saveRecipe(pancakes());
		Path picture = temp.resolve("dish.png");
		Files.write(picture, new byte[] { 1 });
		assertThrows(IllegalArgumentException.class, () -> f.setRecipePhoto(r.getId(), picture));
	}

	@Test
	void timesSourceAndPhotoSurviveSaveAndLoad() throws IOException {
		MealtonFacade f = new MealtonFacade();
		f.setMediaDirectory(temp.resolve("media"));
		Recipe made = pancakes();
		made.setTimes(10, 15);
		made.setSource("The Dish", "https://example.com/p");
		Recipe r = f.saveRecipe(made);
		Path picture = temp.resolve("dish.jpg");
		Files.write(picture, new byte[] { 9 });
		f.setRecipePhoto(r.getId(), picture);

		MealtonFacade g = new MealtonFacade();
		g.setMediaDirectory(temp.resolve("media"));
		UserDataMapper.restore(g, UserDataMapper.capture(f));
		Recipe back = g.getRecipes().stream().filter(x -> x.getTitle().equals("Pancakes")).findFirst().orElseThrow();
		assertEquals(25, back.getTotalMinutes());
		assertEquals("The Dish", back.getSourceName());
		assertEquals("https://example.com/p", back.getSourceUrl());
		assertTrue(g.getRecipePhoto(back.getId()).isPresent());
	}
}