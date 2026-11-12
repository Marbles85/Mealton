package com.mealton.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mealton.facade.MealtonFacade;
import com.mealton.llm.MockLLMClient;
import com.mealton.pantry.PantryItem;

/**
 * The recipe browser: Can make now, Almost there, My Recipes (Save heart) and
 * the AI reason.
 */
class RecipeBrowserTest {

	private MealtonFacade facade;

	@BeforeEach
	void setUp() {
		facade = new MealtonFacade();
		StarterRecipes.loadInto(facade);
	}

	private void stock(String name, double qty, String unit) {
		facade.addPantryItem(new PantryItem(name, qty, unit, null, null));
	}

	private void stockOmelette() {
		stock("egg", 3, "");
		stock("cheese", 100, "g");
		stock("butter", 50, "g");
		stock("salt", 20, "g");
	}

	private static boolean contains(List<RecipeMatch> matches, String title) {
		return matches.stream().anyMatch(m -> m.recipe().getTitle().equals(title));
	}

	// ---------- Recipe / RecipeBook ----------

	@Test
	void yourOwnRecipesAreAlwaysInMyRecipes() {
		Recipe own = new Recipe("Toast", 1, List.of(new RecipeIngredient("bread", 2, "")), null);
		assertFalse(own.isLibrary());
		assertTrue(own.isInMyRecipes());
	}

	@Test
	void savingALibraryRecipeAddsItToMyRecipes() {
		long id = facade.getRecipes().get(0).getId();
		assertFalse(facade.getRecipe(id).isInMyRecipes());
		facade.saveToMyRecipes(id);
		assertTrue(facade.getRecipe(id).isInMyRecipes());
		assertEquals(1, facade.getMyRecipes().size());
	}

	@Test
	void savingTwiceChangesNothing() {
		long id = facade.getRecipes().get(0).getId();
		facade.saveToMyRecipes(id);
		facade.saveToMyRecipes(id);
		assertEquals(1, facade.getMyRecipes().size());
	}

	@Test
	void removingFromMyRecipesKeepsTheRecipeInTheLibrary() {
		long id = facade.getRecipes().get(0).getId();
		facade.saveToMyRecipes(id);
		facade.removeFromMyRecipes(id);
		assertTrue(facade.getMyRecipes().isEmpty());
		assertEquals(12, facade.getRecipes().size());
	}

	@Test
	void aRecipeYouMadeShowsUpInMyRecipesImmediately() {
		facade.saveRecipe(new Recipe("Toast", 1, List.of(new RecipeIngredient("bread", 2, "")), null));
		assertEquals(1, facade.getMyRecipes().size());
		assertEquals(13, facade.getRecipes().size());
	}

	@Test
	void savingAnUnknownRecipeGivesAClearError() {
		assertThrows(IllegalArgumentException.class, () -> facade.saveToMyRecipes(999));
	}

	@Test
	void aScaledCopyIsNotALibraryRecipe() {
		Recipe copy = facade.getRecipes().get(0).scaledTo(4);
		assertFalse(copy.isLibrary());
	}

	// ---------- Can make now / Almost there ----------

	@Test
	void withAnEmptyPantryBothTabsAreEmpty() {
		assertTrue(facade.canMakeNow("best").isEmpty());
		assertTrue(facade.almostThere().isEmpty());
	}

	@Test
	void canMakeNowOnlyListsRecipesWithNothingMissing() {
		stockOmelette();
		List<RecipeMatch> now = facade.canMakeNow("best");
		assertTrue(contains(now, "Cheese Omelette"));
		assertTrue(now.stream().allMatch(RecipeMatch::isCookable));
	}

	@Test
	void almostThereListsRecipesMissingAFewIngredients() {
		stockOmelette();
		stock("tomato", 2, "");
		List<RecipeMatch> almost = facade.almostThere();
		assertTrue(contains(almost, "Greek Salad"));
		assertFalse(contains(almost, "Cheese Omelette"));
		for (RecipeMatch m : almost) {
			assertFalse(m.isCookable());
			assertTrue(m.missing().size() <= MealtonFacade.ALMOST_THERE_LIMIT);
			assertFalse(m.available().isEmpty());
		}
	}

	@Test
	void almostThereShowsTheFewestMissingFirst() {
		stockOmelette();
		stock("tomato", 2, "");
		List<RecipeMatch> almost = facade.almostThere();
		for (int i = 1; i < almost.size(); i++) {
			assertTrue(almost.get(i - 1).missing().size() <= almost.get(i).missing().size());
		}
	}

	@Test
	void aRecipeNeedsAtLeastOnePantryIngredientToBeAlmostThere() {
		stock("egg", 1, "");
		for (RecipeMatch m : facade.almostThere()) {
			assertFalse(m.available().isEmpty());
		}
	}

	@Test
	void theAlmostThereLimitMustBeAtLeastOne() {
		assertThrows(IllegalArgumentException.class,
				() -> new RecipeAdvisor(new RecipeBook(), null, null).almostThere(0));
	}

	@Test
	void anUnknownPriorityIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> facade.canMakeNow("fast"));
	}

	@Test
	void addingTheMissingIngredientsOfAnAlmostThereRecipeMakesItCookable() {
		stockOmelette();
		stock("tomato", 2, "");
		long greek = facade.almostThere().stream().filter(m -> m.recipe().getTitle().equals("Greek Salad")).findFirst()
				.orElseThrow().recipe().getId();
		assertFalse(facade.addMissingToShoppingList(greek).isEmpty());
		assertFalse(facade.getShoppingItems().isEmpty());
	}

	@Test
	void profileWarningsAreKeptOnTheTabs() {
		stockOmelette();
		facade.setDiet("vegan");
		RecipeMatch omelette = facade.canMakeNow("best").stream()
				.filter(m -> m.recipe().getTitle().equals("Cheese Omelette")).findFirst().orElseThrow();
		assertFalse(omelette.warnings().isEmpty());
	}

	// ---------- the AI reason ----------

	@Test
	void theReasonUsesTheAiWhenItAnswers() {
		stockOmelette();
		facade.enableAgent(new MockLLMClient().thenSay("Quick, and it uses your eggs."));
		long id = facade.canMakeNow("best").get(0).recipe().getId();
		assertEquals("Quick, and it uses your eggs.", facade.explainRecipe(id));
	}

	@Test
	void theReasonFallsBackToPlainTextWithoutAnAi() {
		stockOmelette();
		long id = facade.canMakeNow("best").get(0).recipe().getId();
		assertTrue(facade.explainRecipe(id).contains("You have everything you need"));
	}

	@Test
	void theReasonFallsBackWhenTheMockHasNothingToSay() {
		stockOmelette();
		facade.enableAgent(new MockLLMClient());
		long id = facade.canMakeNow("best").get(0).recipe().getId();
		assertTrue(facade.explainRecipe(id).contains("You have everything you need"));
	}

	@Test
	void theReasonForAnAlmostThereRecipeNamesWhatIsMissing() {
		stockOmelette();
		stock("tomato", 2, "");
		long id = facade.almostThere().get(0).recipe().getId();
		assertTrue(facade.explainRecipe(id).contains("missing"));
	}
}