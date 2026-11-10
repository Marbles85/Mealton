package com.mealton.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecipeBookTest {

	private RecipeBook book;

	@BeforeEach
	void setUp() {
		book = new RecipeBook();
	}

	private static Recipe recipe(String title, int servings) {
		return new Recipe(title, servings, List.of(new RecipeIngredient("Flour", 100, "g")), List.of("Mix"));
	}

	// ---------- Recipe itself ----------

	@Test
	void aRecipeNeedsATitleServingsAndIngredients() {
		List<RecipeIngredient> one = List.of(new RecipeIngredient("Flour", 100, "g"));
		assertThrows(IllegalArgumentException.class, () -> new Recipe("  ", 2, one, null));
		assertThrows(IllegalArgumentException.class, () -> new Recipe("Cake", 0, one, null));
		assertThrows(IllegalArgumentException.class, () -> new Recipe("Cake", 2, List.of(), null));
		assertThrows(IllegalArgumentException.class, () -> new Recipe("Cake", 2, null, null));
	}

	@Test
	void ingredientRulesAreEnforced() {
		assertThrows(IllegalArgumentException.class, () -> new RecipeIngredient(" ", 1, "g"));
		assertThrows(IllegalArgumentException.class, () -> new RecipeIngredient("Flour", 0, "g"));
		assertThrows(IllegalArgumentException.class, () -> new RecipeIngredient("Flour", Double.NaN, "g"));
	}

	@Test
	void theIngredientListCannotBeChangedFromOutside() {
		Recipe r = recipe("Cake", 2);
		assertThrows(UnsupportedOperationException.class,
				() -> r.getIngredients().add(new RecipeIngredient("Sugar", 1, "g")));
	}

	@Test
	void scalingMakesACopyAndLeavesTheOriginalAlone() {
		Recipe r = recipe("Cake", 2);
		Recipe big = r.scaledTo(6);
		assertEquals(6, big.getServings());
		assertEquals(300.0, big.getIngredients().get(0).quantity(), 0.0001);
		assertEquals(2, r.getServings());
		assertEquals(100.0, r.getIngredients().get(0).quantity(), 0.0001);
		assertThrows(IllegalArgumentException.class, () -> r.scaledTo(0));
	}

	// ---------- RecipeBook ----------

	@Test
	void savingGivesEachRecipeItsOwnId() {
		Recipe a = book.save(recipe("Pancakes", 2));
		Recipe b = book.save(recipe("Soup", 4));
		assertEquals(1L, a.getId());
		assertEquals(2L, b.getId());
		assertEquals(2, book.getAll().size());
	}

	@Test
	void twoRecipesCannotShareATitleEvenWithDifferentCapitals() {
		book.save(recipe("Pancakes", 2));
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> book.save(recipe("  PANCAKES ", 4)));
		assertTrue(e.getMessage().contains("already have"));
		assertEquals(1, book.getAll().size());
	}

	@Test
	void savingNothingIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> book.save(null));
	}

	@Test
	void ratingIsStoredAndMustBeBetweenOneAndFive() {
		Recipe saved = book.save(recipe("Pancakes", 2));
		assertFalse(saved.isRated());
		book.rate(saved.getId(), 4);
		assertEquals(4, book.get(saved.getId()).getRating());
		assertThrows(IllegalArgumentException.class, () -> book.rate(saved.getId(), 0));
		assertThrows(IllegalArgumentException.class, () -> book.rate(saved.getId(), 6));
		assertEquals(4, book.get(saved.getId()).getRating());
	}

	@Test
	void unknownIdsGiveAClearError() {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> book.get(42));
		assertTrue(e.getMessage().contains("42"));
		assertThrows(IllegalArgumentException.class, () -> book.rate(42, 3));
		assertThrows(IllegalArgumentException.class, () -> book.delete(42));
		assertThrows(IllegalArgumentException.class, () -> book.scaled(42, 2));
	}

	@Test
	void topRatedListsOnlyRatedRecipesBestFirstThenByTitle() {
		book.save(recipe("Pancakes", 2)); // id 1
		book.save(recipe("Soup", 4)); // id 2
		book.save(recipe("apple pie", 6)); // id 3
		book.save(recipe("Unrated", 1)); // id 4
		book.rate(1, 4);
		book.rate(2, 4);
		book.rate(3, 5);

		List<Recipe> top = book.getTopRated();
		assertEquals(3, top.size());
		assertEquals("apple pie", top.get(0).getTitle());
		assertEquals("Pancakes", top.get(1).getTitle());
		assertEquals("Soup", top.get(2).getTitle());
	}

	@Test
	void scaledCopyIsNotSavedAndTheSavedRecipeIsUnchanged() {
		Recipe saved = book.save(recipe("Pancakes", 2));
		Recipe copy = book.scaled(saved.getId(), 4);
		assertEquals(4, copy.getServings());
		assertEquals(0L, copy.getId());
		assertEquals(2, book.get(saved.getId()).getServings());
		assertEquals(1, book.getAll().size());
	}

	@Test
	void deletingRemovesTheRecipeAndFreesTheTitle() {
		Recipe saved = book.save(recipe("Pancakes", 2));
		book.delete(saved.getId());
		assertTrue(book.getAll().isEmpty());
		book.save(recipe("Pancakes", 2)); // the title can be used again
		assertEquals(1, book.getAll().size());
	}
}