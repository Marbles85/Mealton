package com.mealton.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Plurals, capitals, spaces and small typos are all forgiven. */
class TextMatchTest {
	@Test
	void pluralsCapitalsAndSpacesAreTheSame() {
		assertTrue(TextMatch.sameName("Egg", "eggs"));
		assertTrue(TextMatch.sameName("  Chicken   Breasts ", "chicken breast"));
		assertTrue(TextMatch.sameName("Tomatoes", "tomato"));
		assertTrue(TextMatch.sameName("Berries", "berry"));
		assertTrue(TextMatch.sameName("Sweet-Potato", "sweet potatoes"));
		assertTrue(TextMatch.sameName("peaches", "peach"));
	}

	@Test
	void smallTyposInLongerWordsAreForgivenButDifferentFoodsAreNot() {
		assertTrue(TextMatch.sameName("chiken breast", "chicken breast"));
		assertTrue(TextMatch.sameName("brocoli", "broccoli"));
		assertFalse(TextMatch.sameName("beef", "beet"), "short words must match exactly");
		assertFalse(TextMatch.sameName("pea", "pear"));
		assertFalse(TextMatch.sameName("rice", "rice flour"));
		assertFalse(TextMatch.sameName("", "egg"));
	}

	@Test
	void searchFindsRecipesDespiteTyposPluralsAndPartialWords() {
		String recipe = "Chicken Fried Rice eggs soy sauce Gluten-Free dessert";
		assertTrue(TextMatch.containsAll(recipe, "egg"));
		assertTrue(TextMatch.containsAll(recipe, "  CHIKEN   rice "));
		assertTrue(TextMatch.containsAll(recipe, "gluten free"));
		assertTrue(TextMatch.containsAll(recipe, "chick"));
		assertTrue(TextMatch.containsAll(recipe, "glutenfree"), "no space is the same as a space");
		assertFalse(TextMatch.containsAll(recipe, "pizza"));
		assertFalse(TextMatch.containsAll(recipe, "   "), "blank is no search");
	}
}