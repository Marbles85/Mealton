package com.mealton.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

class UserProfileTest {

	@Test
	void emptyProfileHasNothingSet() {
		UserProfile p = UserProfile.empty();
		assertEquals("", p.name());
		assertEquals(Diet.ANYTHING, p.diet());
		assertTrue(p.allergies().isEmpty());
		assertEquals(0, p.calorieGoal());
	}

	@Test
	void withMethodsReturnNewProfilesAndLeaveTheOldOneUnchanged() {
		UserProfile original = UserProfile.empty();
		UserProfile changed = original.withName("Bex").withDiet(Diet.VEGAN);
		assertEquals("", original.name());
		assertEquals(Diet.ANYTHING, original.diet());
		assertEquals("Bex", changed.name());
		assertEquals(Diet.VEGAN, changed.diet());
	}

	@Test
	void allergiesAreTrimmedLowerCasedAndWithoutDuplicates() {
		UserProfile p = UserProfile.empty().withAllergy("  Peanut ").withAllergy("peanut").withAllergy("Soy");
		assertEquals(Set.of("peanut", "soy"), p.allergies());
	}

	@Test
	void blankAllergyIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> UserProfile.empty().withAllergy("  "));
		assertThrows(IllegalArgumentException.class, () -> UserProfile.empty().withAllergy(null));
	}

	@Test
	void removingAnAllergyThatIsNotListedIsRejected() {
		UserProfile p = UserProfile.empty().withAllergy("peanut");
		assertThrows(IllegalArgumentException.class, () -> p.withoutAllergy("soy"));
		assertTrue(p.withoutAllergy("PEANUT").allergies().isEmpty());
	}

	@Test
	void allergySetCannotBeChangedFromOutside() {
		UserProfile p = UserProfile.empty().withAllergy("peanut");
		assertThrows(UnsupportedOperationException.class, () -> p.allergies().add("soy"));
	}

	@Test
	void calorieGoalMustBeZeroOrInRange() {
		UserProfile p = UserProfile.empty();
		assertEquals(2000, p.withCalorieGoal(2000).calorieGoal());
		assertEquals(0, p.withCalorieGoal(2000).withCalorieGoal(0).calorieGoal());
		assertThrows(IllegalArgumentException.class, () -> p.withCalorieGoal(499));
		assertThrows(IllegalArgumentException.class, () -> p.withCalorieGoal(10001));
		assertThrows(IllegalArgumentException.class, () -> p.withCalorieGoal(-5));
	}

	@Test
	void macroGoalsMustBeInRange() {
		UserProfile p = UserProfile.empty().withMacroGoals(120, 200, 60);
		assertEquals(120, p.proteinGoal());
		assertEquals(200, p.carbsGoal());
		assertEquals(60, p.fatGoal());
		assertThrows(IllegalArgumentException.class, () -> p.withMacroGoals(-1, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> p.withMacroGoals(0, 1001, 0));
		assertThrows(IllegalArgumentException.class, () -> p.withMacroGoals(0, 0, 5000));
	}

	@Test
	void summaryDescribesWhatIsSetAndWhatIsNot() {
		assertEquals(
				"Name: (not set); Diet: anything; Allergies: none; "
						+ "Daily goals: calories not set, protein not set, carbs not set, fat not set",
				UserProfile.empty().summary());
		String full = UserProfile.empty().withName("Bex").withDiet(Diet.VEGETARIAN).withAllergy("peanut")
				.withCalorieGoal(2000).withMacroGoals(120, 200, 60).summary();
		assertTrue(full.contains("Name: Bex"));
		assertTrue(full.contains("Diet: vegetarian"));
		assertTrue(full.contains("Allergies: peanut"));
		assertTrue(full.contains("2000 kcal"));
		assertTrue(full.contains("protein 120 g"));
	}

	@Test
	void dietsAreUnderstoodFromText() {
		assertEquals(Diet.VEGAN, Diet.fromText("Vegan"));
		assertEquals(Diet.GLUTEN_FREE, Diet.fromText("gluten-free"));
		assertThrows(IllegalArgumentException.class, () -> Diet.fromText("carnivore"));
	}
}