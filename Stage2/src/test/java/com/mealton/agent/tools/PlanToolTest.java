package com.mealton.agent.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mealton.agent.ToolResult;
import com.mealton.pantry.PantryInventory;
import com.mealton.plan.PlanService;
import com.mealton.profile.ProfileService;
import com.mealton.recipe.Recipe;
import com.mealton.recipe.RecipeAdvisor;
import com.mealton.recipe.RecipeBook;
import com.mealton.recipe.RecipeParser;
import com.mealton.repository.InMemoryPantryRepository;
import com.mealton.shopping.ShoppingService;

class PlanToolTest {

	private RecipeBook book;
	private ProfileService profile;
	private ShoppingService shopping;
	private PlanTool tool;

	/** What a tool call returned: whether it failed, and its text. */
	private record Out(boolean isError, String content) {
	}

	private Out run(String... keyValues) {
		Map<String, String> args = new HashMap<>();
		for (int i = 0; i < keyValues.length; i += 2) {
			args.put(keyValues[i], keyValues[i + 1]);
		}
		ToolResult result = tool.execute(args);
		return new Out(!result.success(), result.content());
	}

	@BeforeEach
	void setUp() {
		book = new RecipeBook();
		profile = new ProfileService();
		shopping = new ShoppingService();
		PantryInventory pantry = new PantryInventory(new InMemoryPantryRepository(), Clock.systemDefaultZone());
		RecipeAdvisor advisor = new RecipeAdvisor(book, pantry, shopping, profile);
		tool = new PlanTool(new PlanService(book, advisor, profile));
		book.save(new Recipe("Pancakes", 2, RecipeParser.parseIngredients("200 g flour; 2 egg"), null));
		book.save(new Recipe("Chicken rice", 2, RecipeParser.parseIngredients("300 g chicken"), null));
	}

	@Test
	void showIsEmptyBeforeAnythingIsPlanned() {
		assertEquals("No meals are planned yet.", run("action", "show").content());
	}

	@Test
	void generateThenShow() {
		Out generated = run("action", "generate", "style", "variety", "meals", "dinner");
		assertFalse(generated.isError());
		assertTrue(generated.content().startsWith("New plan:\nMonday"));
		assertTrue(run("action", "show").content().contains("Dinner: "));
	}

	@Test
	void generateReportsProblemsSoTheAgentCanMentionThem() {
		run("action", "generate", "meals", "dinner");
		profile.setDiet("vegetarian");
		Out check = run("action", "check");
		assertTrue(check.content().startsWith("Problems:"));
		assertTrue(check.content().contains("Chicken rice"));
	}

	@Test
	void setAndClearEditOneMeal() {
		Out set = run("action", "set", "day", "friday", "meal", "dinner", "recipe_id", "1");
		assertFalse(set.isError());
		assertTrue(set.content().contains("Friday\n  Dinner: Pancakes (#1)"));
		assertFalse(run("action", "clear", "day", "friday", "meal", "dinner").isError());
		assertTrue(run("action", "clear", "day", "friday", "meal", "dinner").isError());
	}

	@Test
	void badInputIsAnErrorNotACrash() {
		assertTrue(run("action", "set", "day", "funday", "meal", "dinner", "recipe_id", "1").isError());
		assertTrue(run("action", "set", "day", "monday", "meal", "dinner", "recipe_id", "abc").isError());
		assertTrue(run("action", "set", "day", "monday", "meal", "dinner", "recipe_id", "99").isError());
		assertTrue(run("action", "generate", "style", "random").isError());
		assertTrue(run("action", "fly").isError());
	}

	@Test
	void shoppingAddsMissingIngredientsAndResetDeletesThePlan() {
		run("action", "generate", "meals", "dinner");
		Out shop = run("action", "shopping");
		assertTrue(shop.content().startsWith("Added to the shopping list:"));
		assertEquals(3, shopping.getItems().size());
		assertTrue(run("action", "shopping").content().startsWith("Nothing to add"));
		assertEquals("The plan was deleted.", run("action", "reset").content());
		assertEquals("No meals are planned yet.", run("action", "show").content());
	}

	@Test
	void generateWithoutRecipesExplainsWhy() {
		book.delete(1);
		book.delete(2);
		Out out = run("action", "generate");
		assertTrue(out.isError());
		assertTrue(out.content().contains("no saved recipes"));
	}
}