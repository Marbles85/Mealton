package com.mealton.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mealton.facade.MealtonFacade;
import com.mealton.nutrition.Nutrients;
import com.mealton.pantry.PantryItem;
import com.mealton.recipe.Recipe;
import com.mealton.recipe.RecipeIngredient;
import com.mealton.recipe.StarterRecipes;
import com.mealton.shopping.ShoppingItem;

class UserDataMapperTest {

	private static MealtonFacade fresh() {
		MealtonFacade facade = new MealtonFacade();
		StarterRecipes.loadInto(facade);
		return facade;
	}

	/** A facade with something in every part of the app. */
	private static MealtonFacade busy() {
		MealtonFacade f = fresh();
		f.setProfileName("Sam");
		f.setDiet("vegetarian");
		f.addAllergy("peanut");
		f.setCalorieGoal(2000);
		f.setMacroGoals(100, 200, 70);
		f.addPantryItem(new PantryItem("egg", 6, "", "Dairy", LocalDate.of(2026, 12, 1)));
		f.addPantryItem(new PantryItem("rice", 500, "g", null, null));
		ShoppingItem milk = f.addShoppingItem(new ShoppingItem("milk", 1, "l", "Dairy"));
		f.addShoppingItem(new ShoppingItem("bread", 1, "", null));
		f.toggleShoppingItem(milk.getId());
		f.saveToMyRecipes(2);
		f.rateRecipe(2, 4);
		Recipe own = f.saveRecipe(
				new Recipe("My Toast", 2, List.of(new RecipeIngredient("bread", 4, "")), List.of("Toast it")));
		f.rateRecipe(own.getId(), 5);
		f.generatePlan("variety", "dinner");
		f.loadPlanOnCalendar(LocalDate.of(2026, 11, 12));
		f.setCalendarMeal(LocalDate.of(2026, 12, 25), "Lunch", own.getId());
		f.restoreLoggedFood(LocalDate.of(2026, 11, 1), "oats", new Nutrients(300, 10, 50, 5), true);
		f.logFood("egg", 2, "");
		return f;
	}

	@Test
	void everythingComesBackExactlyAsItWas() {
		UserData saved = UserDataMapper.capture(busy());
		MealtonFacade loaded = fresh();
		List<String> warnings = UserDataMapper.restore(loaded, saved);
		assertTrue(warnings.isEmpty(), warnings.toString());
		assertEquals(saved, UserDataMapper.capture(loaded));
	}

	@Test
	void restoredFacadeReallyHasTheData() {
		MealtonFacade loaded = fresh();
		UserDataMapper.restore(loaded, UserDataMapper.capture(busy()));
		assertEquals("Sam", loaded.getProfile().name());
		assertEquals(2000, loaded.getProfile().calorieGoal());
		assertEquals(2, loaded.getPantryItems().size());
		assertEquals(2, loaded.getShoppingItems().size());
		assertEquals(2,
				loaded.getShoppingItems().stream().filter(i -> i.isChecked() || i.getName().equals("bread")).count());
		assertEquals(1, loaded.getCalendarMeals(LocalDate.of(2026, 12, 25)).size());
		assertEquals(7, loaded.getPlan().mealCount());
		assertTrue(loaded.getRecipes().stream().anyMatch(r -> r.getTitle().equals("My Toast") && r.getRating() == 5));
	}

	@Test
	void diaryEntriesKeepTheirOwnDay() {
		MealtonFacade loaded = fresh();
		UserDataMapper.restore(loaded, UserDataMapper.capture(busy()));
		assertEquals(2, loaded.getAllLoggedFood().size());
		assertEquals(LocalDate.of(2026, 11, 1), loaded.getAllLoggedFood().get(0).date());
		assertEquals(1, loaded.getTodaysFood().size());
	}

	@Test
	void loadingCannotBeUndone() {
		MealtonFacade loaded = fresh();
		UserDataMapper.restore(loaded, UserDataMapper.capture(busy()));
		assertFalse(loaded.canUndo());
		loaded.addShoppingItem(new ShoppingItem("salt", 1, "", null));
		assertTrue(loaded.canUndo());
	}

	@Test
	void anEmptyUserSavesAndLoadsFine() {
		UserData saved = UserDataMapper.capture(fresh());
		MealtonFacade loaded = fresh();
		assertTrue(UserDataMapper.restore(loaded, saved).isEmpty());
		assertEquals(saved, UserDataMapper.capture(loaded));
	}

	@Test
	void nullDataIsFine() {
		assertTrue(UserDataMapper.restore(fresh(), null).isEmpty());
	}

	@Test
	void aMealWhoseRecipeIsGoneIsSkippedWithAWarning() {
		UserData saved = UserDataMapper.capture(busy());
		// simulate: the user's own recipe is gone from the saved file
		UserData withoutOwn = new UserData(saved.version(), saved.profile(), saved.pantry(), saved.shopping(),
				saved.libraryRecipes(), List.of(), saved.plan(), saved.calendar(), saved.diary());
		MealtonFacade loaded = fresh();
		List<String> warnings = UserDataMapper.restore(loaded, withoutOwn);
		assertFalse(warnings.isEmpty());
		for (String warning : warnings) {
			assertTrue(warning.contains("My Toast"), warning); // only the meals that used it are skipped
		}
		assertTrue(loaded.getCalendarMeals(LocalDate.of(2026, 12, 25)).isEmpty());
		assertTrue(loaded.getPlan().mealCount() >= 6); // the rest still came back
	}

	@Test
	void oneBadItemDoesNotStopTheRest() {
		UserData saved = UserDataMapper.capture(busy());
		List<UserData.PantryRow> pantry = new java.util.ArrayList<>(saved.pantry());
		pantry.add(0, new UserData.PantryRow("bad", -5, "", null, null));
		pantry.add(new UserData.PantryRow("worse", 1, "", null, "not-a-date"));
		UserData damaged = new UserData(saved.version(), saved.profile(), pantry, saved.shopping(),
				saved.libraryRecipes(), saved.ownRecipes(), saved.plan(), saved.calendar(), saved.diary());
		MealtonFacade loaded = fresh();
		List<String> warnings = UserDataMapper.restore(loaded, damaged);
		assertEquals(2, warnings.size());
		assertEquals(2, loaded.getPantryItems().size());
		assertEquals("Sam", loaded.getProfile().name());
	}

	@Test
	void missingListsInTheFileAreTreatedAsEmpty() {
		UserData sparse = new UserData(1, null, null, null, null, null, null, null, null);
		MealtonFacade loaded = fresh();
		assertTrue(UserDataMapper.restore(loaded, sparse).isEmpty());
		assertEquals(12, loaded.getRecipes().size());
	}
}