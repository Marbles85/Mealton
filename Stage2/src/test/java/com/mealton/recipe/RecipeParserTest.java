package com.mealton.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class RecipeParserTest {

	@Test
	void readsQuantityUnitAndName() {
		RecipeIngredient flour = RecipeParser.parseIngredient("200 g flour");
		assertEquals("flour", flour.name());
		assertEquals(200.0, flour.quantity(), 0.0001);
		assertEquals("g", flour.unit());
	}

	@Test
	void ingredientWithoutAUnitKeepsAllWordsAsTheName() {
		RecipeIngredient eggs = RecipeParser.parseIngredient("2 eggs");
		assertEquals("eggs", eggs.name());
		assertEquals("", eggs.unit());

		RecipeIngredient garlic = RecipeParser.parseIngredient("1 clove garlic");
		assertEquals("clove garlic", garlic.name());
	}

	@Test
	void understandsFractionsAndDecimals() {
		assertEquals(0.5, RecipeParser.parseIngredient("1/2 l milk").quantity(), 0.0001);
		assertEquals(1.5, RecipeParser.parseIngredient("1.5 cup sugar").quantity(), 0.0001);
	}

	@Test
	void multiWordNamesAndOddSpacingAreFine() {
		RecipeIngredient flour = RecipeParser.parseIngredient("  1.5   Cups   whole wheat flour ");
		assertEquals("whole wheat flour", flour.name());
		assertEquals("Cups", flour.unit());
	}

	@Test
	void splitsSeveralIngredientsAndSkipsEmptyPieces() {
		List<RecipeIngredient> list = RecipeParser.parseIngredients("200 g flour; 2 eggs;; 0.3 l milk ;");
		assertEquals(3, list.size());
		assertEquals("milk", list.get(2).name());
	}

	@Test
	void blankOrMissingTextGivesNoIngredientsOrSteps() {
		assertTrue(RecipeParser.parseIngredients(null).isEmpty());
		assertTrue(RecipeParser.parseIngredients("   ").isEmpty());
		assertTrue(RecipeParser.parseSteps(null).isEmpty());
		assertTrue(RecipeParser.parseSteps("").isEmpty());
	}

	@Test
	void rejectsAnIngredientThatDoesNotStartWithANumber() {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> RecipeParser.parseIngredient("flour"));
		assertTrue(e.getMessage().contains("must start with a quantity"));
		assertThrows(IllegalArgumentException.class, () -> RecipeParser.parseIngredient("abc g flour"));
		assertThrows(IllegalArgumentException.class, () -> RecipeParser.parseIngredient("1/2/3 g flour"));
	}

	@Test
	void rejectsAnIngredientWithNoName() {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> RecipeParser.parseIngredient("200 g"));
		assertTrue(e.getMessage().contains("has no name"));
		assertThrows(IllegalArgumentException.class, () -> RecipeParser.parseIngredient("200"));
	}

	@Test
	void rejectsZeroNegativeAndImpossibleQuantities() {
		assertThrows(IllegalArgumentException.class, () -> RecipeParser.parseIngredient("0 g flour"));
		assertThrows(IllegalArgumentException.class, () -> RecipeParser.parseIngredient("-3 g flour"));
		assertThrows(IllegalArgumentException.class, () -> RecipeParser.parseIngredient("1/0 g flour"));
	}

	@Test
	void splitsStepsOnTheBarCharacter() {
		List<String> steps = RecipeParser.parseSteps("Mix | Fry in a pan||  Serve ");
		assertEquals(List.of("Mix", "Fry in a pan", "Serve"), steps);
	}
}