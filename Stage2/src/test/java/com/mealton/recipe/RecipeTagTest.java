package com.mealton.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mealton.facade.MealtonFacade;
import com.mealton.pantry.PantryItem;

/** Tags and filters for recipes (F09). */
class RecipeTagTest {

	private MealtonFacade facade;

	@BeforeEach
	void setUp() {
		facade = new MealtonFacade();
		StarterRecipes.loadInto(facade);
	}

	private RecipeMatch match(String title) {
		return facade.suggestRecipes("best").stream().filter(m -> m.recipe().getTitle().equals(title)).findFirst()
				.orElseThrow();
	}

	@Test
	void dietTagsFollowTheIngredients() {
		Set<RecipeTag> chicken = RecipeTag.tagsOf(match("Chicken Fried Rice"));
		assertFalse(chicken.contains(RecipeTag.VEGETARIAN));
		assertFalse(chicken.contains(RecipeTag.VEGAN));

		Set<RecipeTag> omelette = RecipeTag.tagsOf(match("Cheese Omelette"));
		assertTrue(omelette.contains(RecipeTag.VEGETARIAN));
		assertFalse(omelette.contains(RecipeTag.VEGAN));

		Set<RecipeTag> tacos = RecipeTag.tagsOf(match("Black Bean Tacos"));
		assertTrue(tacos.contains(RecipeTag.VEGETARIAN));
		assertTrue(tacos.contains(RecipeTag.VEGAN));
	}

	@Test
	void nutritionTagsFollowTheEstimate() {
		assertTrue(RecipeTag.tagsOf(match("Chicken Fried Rice")).contains(RecipeTag.HIGH_PROTEIN));
		assertFalse(RecipeTag.tagsOf(match("Banana Oatmeal")).contains(RecipeTag.HIGH_PROTEIN));
		assertTrue(RecipeTag.tagsOf(match("Lentil Soup")).contains(RecipeTag.LIGHT));
		assertFalse(RecipeTag.tagsOf(match("Fluffy Pancakes")).contains(RecipeTag.LIGHT));
	}

	@Test
	void fitsMyProfileFollowsTheProfile() {
		assertTrue(RecipeTag.tagsOf(match("Chicken Fried Rice")).contains(RecipeTag.FITS_PROFILE));
		facade.setDiet("vegetarian");
		assertFalse(RecipeTag.tagsOf(match("Chicken Fried Rice")).contains(RecipeTag.FITS_PROFILE));
		assertTrue(RecipeTag.tagsOf(match("Black Bean Tacos")).contains(RecipeTag.FITS_PROFILE));
	}

	@Test
	void usesExpiringFollowsThePantry() {
		facade.addPantryItem(new PantryItem("egg", 3, "", null, facade.today().plusDays(1)));
		assertTrue(RecipeTag.tagsOf(match("Cheese Omelette")).contains(RecipeTag.USES_EXPIRING));
		assertFalse(RecipeTag.tagsOf(match("Lentil Soup")).contains(RecipeTag.USES_EXPIRING));
	}

	@Test
	void filterKeepsOnlyMatchesWithAllTheWantedTags() {
		List<RecipeMatch> all = facade.suggestRecipes("best");
		List<RecipeMatch> vegan = RecipeTag.filter(all, RecipeTag.setFromText("vegan"));
		assertFalse(vegan.isEmpty());
		assertTrue(vegan.size() < all.size());
		for (RecipeMatch m : vegan) {
			assertTrue(RecipeTag.tagsOf(m).contains(RecipeTag.VEGAN));
		}
		List<RecipeMatch> veganAndLight = RecipeTag.filter(all, RecipeTag.setFromText("vegan, light"));
		for (RecipeMatch m : veganAndLight) {
			assertTrue(RecipeTag.tagsOf(m).containsAll(Set.of(RecipeTag.VEGAN, RecipeTag.LIGHT)));
		}
		assertTrue(veganAndLight.size() <= vegan.size());
	}

	@Test
	void anEmptyFilterKeepsEverything() {
		List<RecipeMatch> all = facade.suggestRecipes("best");
		assertEquals(all.size(), RecipeTag.filter(all, Set.of()).size());
		assertEquals(all.size(), RecipeTag.filter(all, null).size());
		assertTrue(RecipeTag.setFromText("").isEmpty());
		assertTrue(RecipeTag.setFromText(null).isEmpty());
	}

	@Test
	void tagNamesAreReadFlexibly() {
		assertEquals(RecipeTag.HIGH_PROTEIN, RecipeTag.fromText("high protein"));
		assertEquals(RecipeTag.HIGH_PROTEIN, RecipeTag.fromText("HIGH_PROTEIN"));
		assertEquals(RecipeTag.HIGH_PROTEIN, RecipeTag.fromText("  high-protein "));
	}

	@Test
	void anUnknownTagListsTheChoices() {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> RecipeTag.fromText("tasty"));
		assertTrue(e.getMessage().contains("vegetarian"));
		assertThrows(IllegalArgumentException.class, () -> RecipeTag.setFromText("vegan,tasty"));
	}
}