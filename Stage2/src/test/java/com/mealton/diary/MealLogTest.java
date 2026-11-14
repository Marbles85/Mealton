package com.mealton.diary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mealton.facade.MealtonFacade;
import com.mealton.nutrition.LoggedFood;
import com.mealton.pantry.PantryItem;
import com.mealton.recipe.Recipe;
import com.mealton.recipe.RecipeIngredient;

/**
 * Logging a cooked meal: diary + pantry + leftovers + undo, and the "anything
 * different" note.
 */
class MealLogTest {
	private MealtonFacade facade;
	private long recipeId;

	@BeforeEach
	void setUp() {
		facade = new MealtonFacade();
		Recipe r = new Recipe("Egg scramble", 4, List.of(new RecipeIngredient("egg", 8, ""),
				new RecipeIngredient("milk", 200, "ml"), new RecipeIngredient("feta", 120, "g")),
				List.of("Whisk.", "Cook."));
		recipeId = facade.saveRecipe(r).getId();
		facade.addPantryItem(new PantryItem("Eggs", 10, "", "Dairy", null));
		facade.addPantryItem(new PantryItem("Milk", 1, "l", "Dairy", null));
		facade.addPantryItem(new PantryItem("Feta", 120, "g", "Dairy", null));
	}

	private double pantryQty(String name) {
		return facade.getPantryItems().stream().filter(p -> p.getName().equalsIgnoreCase(name))
				.mapToDouble(PantryItem::getQuantity).sum();
	}

	@Test
	void previewShowsWhatWillBeTakenOut() {
		List<MealLogService.IngredientLine> lines = facade.previewMeal(recipeId, 4, null);
		assertEquals(3, lines.size());
		assertEquals(8.0, lines.get(0).used());
		assertEquals(10.0, lines.get(0).have());
		assertEquals(2.0, lines.get(0).after());
		assertEquals(1000.0, lines.get(1).have()); // 1 l shown in ml
	}

	@Test
	void loggingTakesFoodOutOfThePantryAndAddsAPortion() {
		MealLogService.Result r = facade.logRecipeMeal(recipeId, 4, 1, "Dinner", null, "", true);
		assertEquals("Dinner", r.entry().meal());
		assertEquals("recipe", r.entry().source());
		assertTrue(r.entry().nutrients().calories() > 0);
		assertEquals(2.0, pantryQty("Eggs"));
		assertEquals(0.8, pantryQty("Milk"), 1e-6); // 1 l - 200 ml
		assertEquals(0.0, pantryQty("Feta")); // all used up -> removed
		assertEquals(1, facade.getTodaysFood().size());
		assertTrue(r.leftoverText().contains("3 servings"));
		assertEquals(3.0, pantryQty("Egg scramble"));
		assertTrue(r.low().stream().anyMatch(l -> l.name().equals("feta") || l.name().equals("Feta")));
	}

	@Test
	void changedAmountsAreUsedAndUndoRestoresEverything() {
		MealLogService.Result r = facade.logRecipeMeal(recipeId, 4, 2, "Lunch", Map.of(0, 1.0, 2, 0.0),
				"one egg, no feta", true);
		assertEquals(9.0, pantryQty("Eggs"));
		assertEquals(120.0, pantryQty("Feta"));
		assertEquals("one egg, no feta", r.entry().note());
		assertEquals(2.0, pantryQty("Egg scramble"));
		facade.undoMealLog(r.undo());
		assertEquals(10.0, pantryQty("Eggs"));
		assertEquals(1.0, pantryQty("Milk"), 1e-6);
		assertEquals(0.0, pantryQty("Egg scramble"));
		assertTrue(facade.getTodaysFood().isEmpty());
		facade.undoMealLog(r.undo()); // second time does nothing
		assertEquals(10.0, pantryQty("Eggs"));
	}

	@Test
	void undoBringsBackAnItemThatWasUsedUp() {
		MealLogService.Result r = facade.logRecipeMeal(recipeId, 4, 4, "Dinner", null, "", false);
		assertEquals(0.0, pantryQty("Feta"));
		facade.undoMealLog(r.undo());
		assertEquals(120.0, pantryQty("Feta"));
	}

	@Test
	void badPortionsAreRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> facade.logRecipeMeal(recipeId, 4, 0, "Dinner", null, "", false));
		assertThrows(IllegalArgumentException.class,
				() -> facade.logRecipeMeal(recipeId, 4, 5, "Dinner", null, "", false));
		assertThrows(IllegalArgumentException.class, () -> facade.logRecipeMeal(recipeId, 4, 1,
				"A meal tag that is far too long to be allowed", null, "", false));
		assertThrows(IllegalArgumentException.class,
				() -> facade.logRecipeMeal(recipeId, 4, 1, "Dinner", Map.of(0, 0.0, 1, 0.0, 2, 0.0), "", false));
	}

	@Test
	void customMealTagsCanBeUsed() {
		assertEquals("Post-workout", facade.addMealTag("Post-workout"));
		assertEquals("Post-workout", facade.addMealTag("post-workout"));
		assertTrue(facade.getMealTags().contains("Post-workout"));
		MealLogService.Result r = facade.logRecipeMeal(recipeId, 4, 1, "Post-workout", null, "", false);
		assertEquals("Post-workout", r.entry().meal());
		assertThrows(IllegalArgumentException.class, () -> facade.addMealTag("  "));
	}

	@Test
	void pastDaysDoNotTouchThePantry() {
		long eggId = facade.getPantryItems().stream().filter(p -> p.getName().equals("Eggs")).findFirst().get().getId();
		double before = pantryQty("Eggs");
		MealLogService.Result r = facade.logPantryMeal(eggId, 2, "Breakfast", null, facade.today().minusDays(2));
		assertEquals(before, pantryQty("Eggs"));
		assertTrue(r.pantryChanges().isEmpty());
		assertEquals(facade.today().minusDays(2), r.entry().date());
		MealLogService.Result ahead = facade.logPantryMeal(eggId, 2, "Breakfast", null, facade.today().plusDays(1));
		assertEquals(before - 2, pantryQty("Eggs"));
		assertEquals(1, ahead.pantryChanges().size());
	}

	@Test
	void pantryFoodCanBeLoggedWithTableNumbers() {
		long eggId = facade.getPantryItems().stream().filter(p -> p.getName().equals("Eggs")).findFirst().get().getId();
		MealLogService.Result r = facade.logPantryMeal(eggId, 2, "Breakfast", null);
		assertEquals("pantry", r.entry().source());
		assertTrue(r.entry().description().contains("Eggs"));
		assertEquals(8.0, pantryQty("Eggs"));
		facade.undoMealLog(r.undo());
		assertEquals(10.0, pantryQty("Eggs"));
	}

	@Test
	void historyDeleteRestoreAndLogAgain() {
		MealLogService.Result r = facade.logRecipeMeal(recipeId, 4, 1, "Dinner", null, "", false);
		LocalDate today = facade.today();
		assertEquals(1, facade.getLoggedFoodBetween(today.minusDays(7), today).size());
		LoggedFood entry = r.entry();
		facade.deleteLoggedFood(entry.id());
		assertTrue(facade.getLoggedFoodBetween(today.minusDays(7), today).isEmpty());
		facade.restoreLoggedEntry(entry);
		facade.logAgain(entry);
		assertEquals(2, facade.getLoggedFoodBetween(today.minusDays(7), today).size());
	}

	@Test
	void notesAreReadWithRulesWhenThereIsNoAi() {
		PortionNotes.Result r = facade.readMealNotes(recipeId, 4, "I only used one egg, no feta, half the milk");
		assertEquals(1.0, r.amounts().get(0));
		assertEquals(0.0, r.amounts().get(2));
		assertEquals(100.0, r.amounts().get(1));
		assertFalse(r.usedAi());
		assertEquals(3, r.said().size());
		assertTrue(facade.readMealNotes(recipeId, 4, "had a lovely time").isEmpty());
	}

	@Test
	void claudesAnswerIsUsedWhenItIsUsable() {
		List<PortionNotes.Line> lines = List.of(new PortionNotes.Line("Eggs", "", 8),
				new PortionNotes.Line("Feta", "g", 120));
		PortionNotes.Result ai = PortionNotes.read(lines, "forgot the cheese",
				p -> "Sure! {\"adjustments\":[{\"index\":1,\"amount\":0}]}");
		assertTrue(ai.usedAi());
		assertEquals(0.0, ai.amounts().get(1));
		// nonsense from the model: the built-in rules still work
		PortionNotes.Result fallback = PortionNotes.read(lines, "no feta", p -> "I cannot help");
		assertFalse(fallback.usedAi());
		assertEquals(0.0, fallback.amounts().get(1));
	}

	@Test
	void lowItemsGoOnTheShoppingList() {
		MealLogService.Result r = facade.logRecipeMeal(recipeId, 4, 1, "Dinner", null, "", false);
		int added = facade.addLowItemsToShoppingList(r.low());
		assertEquals(r.low().size(), added);
		assertEquals(added, facade.getShoppingItems().size());
	}
}