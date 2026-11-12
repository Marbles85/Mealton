package com.mealton.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.DayOfWeek;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mealton.pantry.PantryInventory;
import com.mealton.pantry.PantryItem;
import com.mealton.profile.ProfileService;
import com.mealton.recipe.Recipe;
import com.mealton.recipe.RecipeAdvisor;
import com.mealton.recipe.RecipeBook;
import com.mealton.recipe.RecipeIngredient;
import com.mealton.recipe.RecipeParser;
import com.mealton.repository.InMemoryPantryRepository;
import com.mealton.shopping.ShoppingService;

class PlanServiceTest {

	private RecipeBook book;
	private ProfileService profile;
	private PantryInventory pantry;
	private ShoppingService shopping;
	private PlanService service;

	private Recipe save(String title, String ingredients) {
		return book.save(new Recipe(title, 2, RecipeParser.parseIngredients(ingredients), null));
	}

	@BeforeEach
	void setUp() {
		book = new RecipeBook();
		profile = new ProfileService();
		pantry = new PantryInventory(new InMemoryPantryRepository(), Clock.systemDefaultZone());
		shopping = new ShoppingService();
		service = new PlanService(book, new RecipeAdvisor(book, pantry, shopping, profile), profile);
	}

	@Test
	void cannotPlanWithoutRecipes() {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.generate("", ""));
		assertTrue(e.getMessage().contains("no saved recipes"));
		assertEquals(0, service.current().mealCount());
	}

	@Test
	void defaultPlansLunchAndDinnerForAllSevenDays() {
		save("Pancakes", "200 g flour");
		assertEquals(14, service.generate("", "").mealCount());
		assertEquals(7, service.generate("variety", "dinner").mealCount());
		assertEquals(21, service.generate("variety", "all").mealCount());
	}

	@Test
	void varietyUsesEveryRecipeBeforeRepeatingAndPrefersHigherRatings() {
		Recipe a = save("A", "1 onion");
		Recipe b = save("B", "1 onion");
		Recipe c = save("C", "1 onion");
		book.rate(c.getId(), 5);
		WeekPlan plan = service.generate("variety", "dinner");
		// first round: best rated first, then oldest
		assertEquals("C", plan.day(DayOfWeek.MONDAY).get(MealSlot.DINNER).orElseThrow().recipe().getTitle());
		assertEquals("A", plan.day(DayOfWeek.TUESDAY).get(MealSlot.DINNER).orElseThrow().recipe().getTitle());
		assertEquals("B", plan.day(DayOfWeek.WEDNESDAY).get(MealSlot.DINNER).orElseThrow().recipe().getTitle());
		// 7 dinners over 3 recipes: nobody is used more than 3 times, nobody less than
		// 2
		for (Recipe r : List.of(a, b, c)) {
			int times = plan.timesUsed(r.getId());
			assertTrue(times >= 2 && times <= 3, "times used: " + times);
		}
	}

	@Test
	void sameRecipeIsNotPlannedTwiceInOneDayWhenThereIsAChoice() {
		save("A", "1 onion");
		save("B", "1 onion");
		WeekPlan plan = service.generate("variety", "lunch,dinner");
		for (DayPlan day : plan.days()) {
			List<Recipe> recipes = day.recipes();
			assertFalse(recipes.get(0).getId() == recipes.get(1).getId(), day.dayName());
		}
	}

	@Test
	void aSingleRecipeIsRepeatedWhenItIsTheOnlyChoice() {
		save("Only", "1 onion");
		assertEquals(14, service.generate("", "").mealCount());
	}

	@Test
	void pantryStrategyPlansTheRecipeThePantryCanCookFirst() {
		save("Pancakes", "200 g flour; 2 egg");
		save("Chicken rice", "300 g chicken; 200 g rice");
		pantry.addItem(new PantryItem("rice", 500, "g", null, null));
		pantry.addItem(new PantryItem("chicken", 400, "g", null, null));
		WeekPlan plan = service.generate("pantry", "dinner");
		assertEquals("Chicken rice", plan.day(DayOfWeek.MONDAY).get(MealSlot.DINNER).orElseThrow().recipe().getTitle());
		assertEquals("Pancakes", plan.day(DayOfWeek.TUESDAY).get(MealSlot.DINNER).orElseThrow().recipe().getTitle());
	}

	@Test
	void unknownStyleIsRejected() {
		save("A", "1 onion");
		assertThrows(IllegalArgumentException.class, () -> service.generate("random", ""));
	}

	@Test
	void generatingSkipsRecipesThatClashWithTheProfile() {
		save("Chicken rice", "300 g chicken; 200 g rice");
		save("Salad", "1 lettuce");
		profile.setDiet("vegetarian");
		WeekPlan plan = service.generate("variety", "dinner");
		assertEquals(0, plan.timesUsed(1));
		assertEquals(7, plan.timesUsed(2));
	}

	@Test
	void generatingFailsClearlyWhenEveryRecipeClashes() {
		save("Chicken rice", "300 g chicken; 200 g rice");
		profile.setDiet("vegan");
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.generate("", ""));
		assertTrue(e.getMessage().contains("conflicts"));
	}

	@Test
	void validateReportsProfileConflictsAndRepeats() {
		Recipe chicken = save("Chicken rice", "300 g chicken");
		Recipe salad = save("Salad", "1 lettuce");
		service.generate("variety", "dinner");
		assertEquals(List.of(), service.validate().stream().filter(p -> p.contains("Diet")).toList());
		profile.setDiet("vegetarian");
		List<String> problems = service.validate();
		assertTrue(problems.stream().anyMatch(p -> p.contains("Chicken rice") && p.contains("not vegetarian")));
		service.generate("variety", "dinner"); // now only the salad is allowed: 7 times
		assertTrue(service.validate().contains("Salad is planned 7 times this week."));
		assertEquals(7, service.current().timesUsed(salad.getId()));
		assertEquals(0, service.current().timesUsed(chicken.getId()));
	}

	@Test
	void emptyPlanIsReportedByValidate() {
		assertEquals(List.of("The plan is empty."), service.validate());
	}

	@Test
	void setAndClearEditSingleMeals() {
		Recipe a = save("A", "1 onion");
		service.set("mon", "dinner", a.getId());
		assertEquals(1, service.current().mealCount());
		service.set("Monday", "Dinner", a.getId());
		assertEquals(1, service.current().mealCount());
		service.clearMeal("monday", "dinner");
		assertEquals(0, service.current().mealCount());
		assertThrows(IllegalArgumentException.class, () -> service.clearMeal("monday", "dinner"));
	}

	@Test
	void badDayMealOrRecipeIsRejected() {
		Recipe a = save("A", "1 onion");
		assertThrows(IllegalArgumentException.class, () -> service.set("funday", "dinner", a.getId()));
		assertThrows(IllegalArgumentException.class, () -> service.set("mon", "brunch", a.getId()));
		assertThrows(IllegalArgumentException.class, () -> service.set("mon", "dinner", 999));
		assertThrows(IllegalArgumentException.class, () -> service.set("m", "dinner", a.getId()));
	}

	@Test
	void clearAllThrowsThePlanAway() {
		save("A", "1 onion");
		service.generate("", "");
		service.clearAll();
		assertEquals(0, service.current().mealCount());
	}

	@Test
	void shoppingListGetsTheMissingIngredientsOfEveryPlannedRecipe() {
		save("Pancakes", "200 g flour; 2 egg");
		save("Salad", "1 lettuce");
		pantry.addItem(new PantryItem("flour", 500, "g", null, null));
		service.generate("variety", "dinner");
		List<RecipeIngredient> added = service.addMissingToShoppingList();
		assertEquals(2, added.size()); // 2 egg and 1 lettuce; the flour is already in the pantry
		assertEquals(2, shopping.getItems().size());
		assertTrue(service.addMissingToShoppingList().isEmpty()); // running it again adds nothing
		assertEquals(2, shopping.getItems().size());
	}
}