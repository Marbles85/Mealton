package com.mealton.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mealton.pantry.PantryItem;

class RecipeMatcherTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

	private RecipeMatcher matcher;

	@BeforeEach
	void setUp() {
		matcher = new RecipeMatcher();
	}

	private static Recipe recipe(String title, String ingredients) {
		return new Recipe(title, 2, RecipeParser.parseIngredients(ingredients), null);
	}

	private static PantryItem item(String name, double qty, String unit, LocalDate expires) {
		return new PantryItem(name, qty, unit, null, expires);
	}

	// ---------- matching one recipe ----------

	@Test
	void aRecipeFullyCoveredByThePantryIsCookable() {
		Recipe r = recipe("Omelette", "2 eggs; 50 ml milk");
		RecipeMatch m = matcher.matchOne(r, List.of(item("eggs", 6, "", null), item("milk", 1, "l", null)), TODAY);
		assertTrue(m.isCookable());
		assertEquals(1.0, m.coverage(), 0.0001);
		assertEquals(2, m.available().size());
	}

	@Test
	void anEmptyPantryMissesEverything() {
		RecipeMatch m = matcher.matchOne(recipe("Omelette", "2 eggs; 50 ml milk"), List.of(), TODAY);
		assertFalse(m.isCookable());
		assertEquals(0.0, m.coverage(), 0.0001);
		assertEquals(2, m.missing().size());
		assertEquals(2.0, m.missing().get(0).quantity(), 0.0001);
	}

	@Test
	void capitalsAndPluralsDoNotMatter() {
		Recipe r = recipe("Salad", "2 tomato; 1 cherry");
		RecipeMatch m = matcher.matchOne(r, List.of(item("Tomatoes", 3, "", null), item("CHERRIES", 5, "", null)),
				TODAY);
		assertTrue(m.isCookable());
	}

	@Test
	void differentButCompatibleUnitsAreConverted() {
		Recipe r = recipe("Bread", "200 g flour");
		assertTrue(matcher.matchOne(r, List.of(item("Flour", 0.5, "kg", null)), TODAY).isCookable());
		assertFalse(matcher.matchOne(r, List.of(item("Flour", 0.1, "kg", null)), TODAY).isCookable());
	}

	@Test
	void aShortfallIsReportedAsTheAmountStillNeeded() {
		Recipe r = recipe("Cake", "600 g flour");
		RecipeMatch m = matcher.matchOne(r, List.of(item("Flour", 500, "g", null)), TODAY);
		assertEquals(1, m.missing().size());
		assertEquals(100.0, m.missing().get(0).quantity(), 0.0001);
		assertEquals("g", m.missing().get(0).unit());
	}

	@Test
	void severalPantryEntriesOfTheSameIngredientAreAddedTogether() {
		Recipe r = recipe("Bread", "200 g flour");
		List<PantryItem> pantry = List.of(item("Flour", 100, "g", null), item("flour", 150, "g", null));
		assertTrue(matcher.matchOne(r, pantry, TODAY).isCookable());
	}

	@Test
	void expiredFoodDoesNotCount() {
		Recipe r = recipe("Cake", "200 g sugar");
		RecipeMatch m = matcher.matchOne(r, List.of(item("Sugar", 500, "g", TODAY.minusDays(1))), TODAY);
		assertFalse(m.isCookable());
		assertEquals(200.0, m.missing().get(0).quantity(), 0.0001);
	}

	@Test
	void unitsThatCannotBeComparedGiveTheBenefitOfTheDoubt() {
		Recipe r = recipe("Toast", "1 tbsp butter");
		assertTrue(matcher.matchOne(r, List.of(item("Butter", 1, "bag", null)), TODAY).isCookable());
	}

	@Test
	void useSoonIngredientsAreCounted() {
		Recipe r = recipe("Omelette", "2 eggs; 50 ml milk");
		List<PantryItem> pantry = List.of(item("eggs", 6, "", TODAY.plusDays(1)),
				item("milk", 1, "l", TODAY.plusDays(30)));
		assertEquals(1, matcher.matchOne(r, pantry, TODAY).expiringUsed());
	}

	@Test
	void normalizeHandlesCommonPluralEndings() {
		assertEquals("egg", RecipeMatcher.normalize("Eggs"));
		assertEquals("tomato", RecipeMatcher.normalize("tomatoes"));
		assertEquals("berry", RecipeMatcher.normalize("berries"));
		assertEquals("rice", RecipeMatcher.normalize(" Rice "));
		assertEquals("grass", RecipeMatcher.normalize("grass"));
	}

	// ---------- ordering with strategies ----------

	@Test
	void bestMatchPutsTheMostCoveredRecipeFirstAndBreaksTiesByRating() {
		Recipe full = recipe("Full", "2 eggs");
		Recipe half = recipe("Half", "2 eggs; 1 cucumber");
		Recipe tieLow = recipe("TieLow", "1 cucumber");
		Recipe tieHigh = recipe("TieHigh", "1 cucumber");
		tieHigh.rate(5);
		List<PantryItem> pantry = List.of(item("eggs", 6, "", null));

		List<RecipeMatch> ranked = matcher.match(List.of(tieLow, half, tieHigh, full), pantry, TODAY,
				new BestMatchStrategy());
		assertEquals("Full", ranked.get(0).recipe().getTitle());
		assertEquals("Half", ranked.get(1).recipe().getTitle());
		assertEquals("TieHigh", ranked.get(2).recipe().getTitle());
		assertEquals("TieLow", ranked.get(3).recipe().getTitle());
	}

	@Test
	void useExpiringFirstPutsRecipesThatSaveSpoilingFoodAhead() {
		Recipe usesFood = recipe("Rescue", "2 eggs; 1 cucumber"); // half covered, but uses the spoiling eggs
		Recipe fullyCovered = recipe("Fresh", "100 g flour"); // fully covered, nothing spoiling
		List<PantryItem> pantry = List.of(item("eggs", 6, "", TODAY.plusDays(1)), item("flour", 500, "g", null));

		List<RecipeMatch> best = matcher.match(List.of(usesFood, fullyCovered), pantry, TODAY, new BestMatchStrategy());
		assertEquals("Fresh", best.get(0).recipe().getTitle());

		List<RecipeMatch> rescue = matcher.match(List.of(usesFood, fullyCovered), pantry, TODAY,
				new UseExpiringFirstStrategy());
		assertEquals("Rescue", rescue.get(0).recipe().getTitle());
	}

	@Test
	void strategiesHaveReadableNames() {
		assertEquals("best match", new BestMatchStrategy().name());
		assertEquals("use expiring first", new UseExpiringFirstStrategy().name());
	}

	@Test
	void noRecipesGivesAnEmptyResult() {
		assertTrue(matcher.match(List.of(), List.of(), TODAY, new BestMatchStrategy()).isEmpty());
	}
}