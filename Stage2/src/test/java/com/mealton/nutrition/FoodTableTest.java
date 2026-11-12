package com.mealton.nutrition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FoodTableTest {

	private static Nutrients lookup(String name, double quantity, String unit) {
		return FoodTable.lookup(name, quantity, unit).orElseThrow();
	}

	@Test
	void weightsUseThePer100gValues() {
		Nutrients chicken = lookup("chicken breast", 200, "g");
		assertEquals(330, chicken.calories(), 0.01);
		assertEquals(62, chicken.protein(), 0.01);
		assertEquals(7.2, chicken.fat(), 0.01);
	}

	@Test
	void otherWeightUnitsAreConverted() {
		assertEquals(lookup("beef", 1000, "g").calories(), lookup("beef", 1, "kg").calories(), 0.01);
		assertEquals(lookup("beef", 453.592, "g").calories(), lookup("beef", 1, "lb").calories(), 0.01);
	}

	@Test
	void piecesWorkForFoodsWithAKnownPieceWeight() {
		// an egg is 50 g, so 2 eggs are 100 g
		assertEquals(143, lookup("egg", 2, "").calories(), 0.01);
		assertEquals(lookup("egg", 2, "").calories(), lookup("egg", 2, "pieces").calories(), 0.01);
	}

	@Test
	void volumesWorkForFoodsWithAKnownDensity() {
		Nutrients milk = lookup("milk", 1, "cup");
		assertEquals(149, milk.calories(), 1.0);
	}

	@Test
	void pluralsAndAlternativeNamesAreUnderstood() {
		assertTrue(FoodTable.knows("Eggs"));
		assertTrue(FoodTable.knows("tomatoes"));
		assertTrue(FoodTable.knows("chicken"));
		assertTrue(FoodTable.knows("  Peanut   Butter "));
		assertEquals(lookup("chicken breast", 100, "g"), lookup("chicken", 100, "g"));
	}

	@Test
	void unknownFoodGivesEmptyInsteadOfAGuess() {
		assertFalse(FoodTable.knows("pizza"));
		assertTrue(FoodTable.lookup("pizza", 1, "g").isEmpty());
	}

	@Test
	void knownFoodWithAnUnusableUnitExplainsWhy() {
		assertThrows(IllegalArgumentException.class, () -> FoodTable.lookup("chicken breast", 1, ""));
		assertThrows(IllegalArgumentException.class, () -> FoodTable.lookup("cheese", 1, "cup"));
		assertThrows(IllegalArgumentException.class, () -> FoodTable.lookup("milk", 1, "bag"));
	}

	@Test
	void amountMustBePositive() {
		assertThrows(IllegalArgumentException.class, () -> FoodTable.lookup("egg", 0, ""));
		assertThrows(IllegalArgumentException.class, () -> FoodTable.lookup("egg", -1, ""));
		assertThrows(IllegalArgumentException.class, () -> FoodTable.lookup("egg", Double.NaN, ""));
	}

	@Test
	void nutrientsAddScaleAndRejectBadNumbers() {
		Nutrients a = new Nutrients(100, 10, 20, 5);
		Nutrients b = new Nutrients(50, 1, 2, 3);
		assertEquals(new Nutrients(150, 11, 22, 8), a.plus(b));
		assertEquals(new Nutrients(200, 20, 40, 10), a.times(2));
		assertThrows(IllegalArgumentException.class, () -> new Nutrients(-1, 0, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> new Nutrients(0, Double.NaN, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> new Nutrients(1e9, 0, 0, 0));
		assertEquals("100 kcal, protein 10 g, carbs 20 g, fat 5 g", a.summary());
	}
}