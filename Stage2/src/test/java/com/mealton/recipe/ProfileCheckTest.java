package com.mealton.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.mealton.profile.Diet;
import com.mealton.profile.UserProfile;

class ProfileCheckTest {

	private static Recipe recipe(String ingredients) {
		return new Recipe("Test dish", 2, RecipeParser.parseIngredients(ingredients), null);
	}

	private static List<String> warnings(String ingredients, UserProfile profile) {
		return ProfileCheck.conflicts(recipe(ingredients), profile);
	}

	@Test
	void emptyProfileNeverWarns() {
		assertTrue(warnings("200 g chicken; 2 eggs; 1 tbsp peanut butter", UserProfile.empty()).isEmpty());
	}

	@Test
	void allergyMatchesWholeWordsInsideIngredientNames() {
		UserProfile p = UserProfile.empty().withAllergy("peanut");
		assertEquals(List.of("Allergy: peanut butter contains peanut"),
				warnings("2 tbsp peanut butter; 100 g flour", p));
	}

	@Test
	void allergyIgnoresPluralsAndCase() {
		UserProfile p = UserProfile.empty().withAllergy("Egg");
		assertEquals(1, warnings("3 eggs; 100 g flour", p).size());
	}

	@Test
	void allergyDoesNotMatchPartOfAnotherWord() {
		UserProfile p = UserProfile.empty().withAllergy("nut");
		assertTrue(warnings("1 tsp nutmeg; 100 g flour", p).isEmpty());
	}

	@Test
	void vegetarianFlagsMeatAndFishButNotDairy() {
		UserProfile p = UserProfile.empty().withDiet(Diet.VEGETARIAN);
		assertEquals(List.of("Diet: chicken is not vegetarian", "Diet: salmon is not vegetarian"),
				warnings("200 g chicken; 100 g salmon; 50 g cheese", p));
	}

	@Test
	void pescatarianAllowsFishButNotMeat() {
		UserProfile p = UserProfile.empty().withDiet(Diet.PESCATARIAN);
		assertEquals(List.of("Diet: bacon is not pescatarian"), warnings("100 g salmon; 50 g bacon", p));
	}

	@Test
	void veganFlagsAnimalProductsButAllowsPlantBasedLookalikes() {
		UserProfile p = UserProfile.empty().withDiet(Diet.VEGAN);
		assertEquals(List.of("Diet: cheese is not vegan", "Diet: eggs is not vegan"),
				warnings("50 g cheese; 2 eggs; 2 tbsp peanut butter; 1 cup coconut milk; 1 cup oat milk", p));
	}

	@Test
	void dietsThatCannotBeJudgedFromNamesAreNotChecked() {
		assertTrue(warnings("200 g pasta; 100 g chicken", UserProfile.empty().withDiet(Diet.GLUTEN_FREE)).isEmpty());
		assertTrue(warnings("200 g pasta; 100 g chicken", UserProfile.empty().withDiet(Diet.KETO)).isEmpty());
	}

	@Test
	void allergyAndDietWarningsCanAppearTogether() {
		UserProfile p = UserProfile.empty().withDiet(Diet.VEGETARIAN).withAllergy("chicken");
		List<String> w = warnings("200 g chicken", p);
		assertEquals(2, w.size());
		assertTrue(w.contains("Allergy: chicken contains chicken"));
		assertTrue(w.contains("Diet: chicken is not vegetarian"));
	}
}