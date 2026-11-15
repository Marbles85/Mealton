package com.mealton.agent.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mealton.agent.ToolResult;
import com.mealton.facade.MealtonFacade;
import com.mealton.pantry.PantryInventory;
import com.mealton.pantry.PantryItem;
import com.mealton.recipe.Recipe;
import com.mealton.recipe.RecipeIngredient;
import com.mealton.recipe.StarterRecipes;
import com.mealton.repository.InMemoryPantryRepository;

/**
 * "Can I swap the butter?" "Yes" "Do it": the agent rewrites the recipe through
 * the same facade as the app.
 */
class RecipeEditToolTest {
	private MealtonFacade facade;
	private RecipeEditTool tool;

	@BeforeEach
	void setUp() {
		facade = new MealtonFacade();
		StarterRecipes.loadInto(facade);
		tool = new RecipeEditTool(facade);
	}

	private ToolResult run(String... kv) {
		Map<String, String> m = new HashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			m.put(kv[i], kv[i + 1]);
		}
		return tool.execute(m);
	}

	private Recipe own() {
		return facade.saveRecipe(new Recipe("Pan eggs", 2,
				List.of(new RecipeIngredient("butter", 1, "tbsp"), new RecipeIngredient("egg", 4, ""),
						new RecipeIngredient("salt", 1, "tsp")),
				List.of("Melt the butter in a pan", "Fry the egg 2 minutes", "Add salt")));
	}

	@Test
	void anIngredientIsSwappedInTheListAndInTheSteps() {
		Recipe r = own();
		ToolResult res = run("action", "substitute", "recipe", String.valueOf(r.getId()), "ingredient", "butter",
				"with", "olive oil");
		assertTrue(res.success(), res.content());
		Recipe now = facade.getRecipe(r.getId());
		assertEquals("olive oil", now.getIngredients().get(0).name());
		assertEquals(1.0, now.getIngredients().get(0).quantity(), 1e-9, "keeps the amount");
		assertEquals("tbsp", now.getIngredients().get(0).unit());
		assertEquals("Melt the olive oil in a pan", now.getSteps().get(0));
		assertEquals(r.getId(), now.getId(), "an own recipe is edited in place");
	}

	@Test
	void aReadyMadeRecipeIsCopiedAndTheCalendarCanFollow() {
		Recipe lib = facade.getRecipes().stream().filter(Recipe::isLibrary).filter(x -> x.getIngredients().size() > 1)
				.findFirst().orElseThrow();
		String first = lib.getIngredients().get(0).name();
		LocalDate day = facade.today().plusDays(1);
		facade.addPlannedMeal(day, "Dinner", lib.getId(), 1);
		int before = facade.getRecipes().size();
		ToolResult res = run("action", "substitute", "recipe", String.valueOf(lib.getId()), "ingredient", "1", "with",
				"tofu", "use_on_calendar", "true");
		assertTrue(res.success(), res.content());
		assertTrue(res.content().contains("my version"));
		assertEquals(before + 1, facade.getRecipes().size());
		assertEquals(first, lib.getIngredients().get(0).name(), "the ready-made recipe is untouched");
		Recipe onCalendar = facade.getPlannedMeals(day).get(0).recipe();
		assertNotEquals(lib.getId(), onCalendar.getId());
		assertEquals("tofu", onCalendar.getIngredients().get(0).name());
	}

	@Test
	void ingredientsAndStepsCanBeAddedChangedAndRemoved() {
		Recipe r = own();
		String id = String.valueOf(r.getId());
		assertTrue(run("action", "add_ingredient", "recipe", id, "name", "pepper", "quantity", "0.5", "unit", "tsp")
				.success());
		assertEquals(4, facade.getRecipe(r.getId()).getIngredients().size());
		assertTrue(run("action", "remove_ingredient", "recipe", id, "ingredient", "salt").success());
		assertTrue(run("action", "set_step", "recipe", id, "step", "2", "text", "Fry the egg 3 minutes").success());
		assertEquals("Fry the egg 3 minutes", facade.getRecipe(r.getId()).getSteps().get(1));
		assertTrue(run("action", "add_step", "recipe", id, "text", "Serve").success());
		assertTrue(run("action", "remove_step", "recipe", id, "step", "1").success());
		assertEquals(3, facade.getRecipe(r.getId()).getSteps().size());
		assertTrue(run("action", "set_details", "recipe", id, "title", "Better pan eggs", "servings", "3").success());
		assertEquals("Better pan eggs", facade.getRecipe(r.getId()).getTitle());
		assertEquals(3, facade.getRecipe(r.getId()).getServings());
		assertTrue(run("action", "delete", "recipe", id).success());
		assertFalse(run("action", "show", "recipe", id).success());
	}

	@Test
	void badRequestsAreErrorsAndNothingChanges() {
		Recipe r = own();
		String id = String.valueOf(r.getId());
		assertFalse(run("action", "substitute", "recipe", id, "ingredient", "caviar", "with", "x").success());
		assertFalse(run("action", "substitute", "recipe", id, "ingredient", "butter").success());
		assertFalse(run("action", "set_step", "recipe", id, "step", "9", "text", "x").success());
		assertFalse(run("action", "nope", "recipe", id).success());
		assertEquals("butter", facade.getRecipe(r.getId()).getIngredients().get(0).name());
	}

	@Test
	void thePantryToolCanUpdateAndRemoveItems() {
		PantryInventory inventory = new PantryInventory(new InMemoryPantryRepository());
		PantryTool pantry = new PantryTool(inventory);
		PantryItem milk = inventory.addItem(new PantryItem("Milk", 1, "l", "Dairy", null));
		Map<String, String> a = new HashMap<>();
		a.put("action", "update");
		a.put("id", String.valueOf(milk.getId()));
		a.put("quantity", "3");
		assertTrue(pantry.execute(a).success());
		assertEquals(3.0, inventory.getItems().get(0).getQuantity(), 1e-9);
		a.put("action", "remove");
		assertTrue(pantry.execute(a).success());
		assertTrue(inventory.getItems().isEmpty());
		assertFalse(pantry.execute(a).success());
	}
}