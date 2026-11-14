package com.mealton.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.mealton.recipe.ProfileCheck;
import com.mealton.recipe.Recipe;
import com.mealton.recipe.RecipeIngredient;

/**
 * The Profile page's extra fields, the target calculator and multi-diet checks.
 */
class ProfileTargetsTest {

	private static UserProfile body() {
		return UserProfile.empty().withBirthDate("Mar 4, 2000").withSex("female").withBody(60, 165);
	}

	@Test
	void birthDateIsParsedAndShownNicely() {
		UserProfile p = UserProfile.empty().withBirthDate("Mar 4, 2005");
		assertEquals("2005-03-04", p.birthDate());
		assertEquals("Mar 4, 2005", p.birthDateText());
		assertEquals(21, (int) p.age(LocalDate.of(2026, 11, 14)));
		assertEquals("", p.withBirthDate("").birthDate());
		assertThrows(IllegalArgumentException.class, () -> UserProfile.empty().withBirthDate("someday"));
		assertThrows(IllegalArgumentException.class, () -> UserProfile.empty().withBirthDate("Jan 1, 1850"));
	}

	@Test
	void heightMustBeBetween100And250() {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> UserProfile.empty().withBody(60, 99));
		assertEquals("Enter a height between 100 and 250 cm.", e.getMessage());
		assertThrows(IllegalArgumentException.class, () -> UserProfile.empty().withBody(60, 251));
		assertThrows(IllegalArgumentException.class, () -> UserProfile.empty().withBody(5, 170));
		assertEquals(170, UserProfile.empty().withBody(60.5, 170).heightCm());
	}

	@Test
	void severalRestrictionsAtOnceAndTheMainDietFollows() {
		UserProfile p = UserProfile.empty().withRestrictions(Set.of("Vegetarian", "dairy free", "halal"));
		assertEquals(Set.of("vegetarian", "dairy-free", "halal"), p.restrictions());
		assertEquals(Diet.VEGETARIAN, p.diet());
		UserProfile vegan = p.withDiet(Diet.VEGAN);
		assertEquals(Set.of("vegan", "dairy-free", "halal"), vegan.restrictions());
		assertEquals(Diet.ANYTHING, vegan.withDiet(Diet.ANYTHING).diet());
		assertEquals(Set.of("dairy-free", "halal"), vegan.withDiet(Diet.ANYTHING).restrictions());
		assertThrows(IllegalArgumentException.class, () -> UserProfile.empty().withRestrictions(Set.of("carnivore")));
	}

	@Test
	void suggestionMatchesMifflinStJeor() {
		// female, 26, 60 kg, 165 cm: 600 + 1031.25 - 130 - 161 = 1340.25
		UserProfile p = body().withActivity(Activity.LIGHT).withGoal(Goal.LOSE);
		TargetCalculator.Suggestion s = TargetCalculator.suggest(p, LocalDate.of(2026, 11, 14)).get();
		assertEquals(1340, s.bmr());
		// 1340.25 x 1.375 = 1842.8 - 500 = 1342.8 -> 1345
		assertEquals(1345, s.calories());
		assertFalse(s.capped());
		assertEquals(Math.round(1345 * 0.27 / 4), s.protein());
		assertEquals(Math.round(1345 * 0.29 / 9), s.fat());
		assertTrue(Math.abs(TargetCalculator.macroCalories(s.protein(), s.carbs(), s.fat()) - 1345) <= 10);
		assertTrue(s.formulaText().contains("BMR 1,340"));
		assertTrue(s.explanation().contains("lose weight steadily"));
	}

	@Test
	void suggestionNeverGoesBelow1200() {
		UserProfile p = body().withActivity(Activity.SITTING).withGoal(Goal.LOSE_FAST);
		TargetCalculator.Suggestion s = TargetCalculator.suggest(p, LocalDate.of(2026, 11, 14)).get();
		assertTrue(s.capped());
		assertEquals(1200, s.calories());
		assertTrue(s.rawCalories() < 1200);
	}

	@Test
	void noSuggestionUntilBodyAndBirthdayAreKnown() {
		assertEquals(Optional.empty(), TargetCalculator.suggest(UserProfile.empty()));
		assertEquals(Optional.empty(), TargetCalculator.suggest(UserProfile.empty().withBody(60, 165)));
	}

	@Test
	void everyChosenDietIsChecked() {
		Recipe r = new Recipe("Pasta", 2, List.of(new RecipeIngredient("butter", 20, "g"),
				new RecipeIngredient("pork", 200, "g"), new RecipeIngredient("coconut milk", 1, "cup")),
				List.of("Cook."));
		List<String> warnings = ProfileCheck.conflicts(r,
				UserProfile.empty().withRestrictions(Set.of("dairy-free", "halal")));
		assertTrue(warnings.contains("Diet: butter is not dairy-free"));
		assertTrue(warnings.contains("Diet: pork is not halal"));
		assertEquals(2, warnings.size());
	}

	@Test
	void oldSevenValueProfilesStillWork() {
		UserProfile p = new UserProfile("Bex", Diet.VEGAN, Set.of("peanut"), 2000, 100, 200, 60);
		assertTrue(p.trackMacros());
		assertEquals(Set.of("vegan"), p.restrictions());
		assertEquals(Activity.LIGHT, p.activity());
	}

	@Test
	void serviceSavesWholeProfileOnceAndKeepsOldOneWhenInvalid() {
		ProfileService service = new ProfileService();
		java.util.List<UserProfile> heard = new java.util.ArrayList<>();
		service.addListener(heard::add);
		service.replace(body().withTrackMacros(false));
		assertEquals(1, heard.size());
		assertFalse(service.get().trackMacros());
		assertThrows(IllegalArgumentException.class, () -> service.setBody(60, 10));
		assertEquals(165, service.get().heightCm());
		assertEquals(1, heard.size());
	}
}