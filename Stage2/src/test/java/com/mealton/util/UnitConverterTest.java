package com.mealton.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UnitConverterTest {

	private static final double TINY = 0.001;

	@Test
	void convertsVolumeWithinTheSameKind() {
		assertEquals(473.176, UnitConverter.convert(2, "cup", "ml"), TINY);
		assertEquals(1.0, UnitConverter.convert(3, "tsp", "tbsp"), TINY);
		assertEquals(2.5, UnitConverter.convert(2500, "ml", "l"), TINY);
	}

	@Test
	void convertsWeightWithinTheSameKind() {
		assertEquals(453.592, UnitConverter.convert(1, "lb", "g"), TINY);
		assertEquals(0.5, UnitConverter.convert(500, "g", "kg"), TINY);
		assertEquals(16.0, UnitConverter.convert(1, "lb", "oz"), 0.01);
	}

	@Test
	void sameUnitGivesTheSameAmount() {
		assertEquals(7.0, UnitConverter.convert(7, "g", "g"), TINY);
	}

	@Test
	void zeroAmountConvertsToZero() {
		assertEquals(0.0, UnitConverter.convert(0, "cup", "ml"), TINY);
	}

	@Test
	void ignoresCaseSpacesAndPluralForms() {
		assertEquals(500.0, UnitConverter.convert(0.5, " KG ", "Grams"), TINY);
		assertEquals(473.176, UnitConverter.convert(2, "Cups", "milliliters"), TINY);
	}

	@Test
	void refusesToConvertWeightToVolume() {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> UnitConverter.convert(1, "cup", "g"));
		assertTrue(e.getMessage().contains("Cannot convert"));
	}

	@Test
	void rejectsUnknownUnitsAndTellsWhichOnes() {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> UnitConverter.convert(1, "cup", "pinch"));
		assertTrue(e.getMessage().contains("pinch"));
		assertThrows(IllegalArgumentException.class, () -> UnitConverter.convert(1, null, "g"));
	}

	@Test
	void rejectsNegativeAndNonNumberAmounts() {
		assertThrows(IllegalArgumentException.class, () -> UnitConverter.convert(-1, "g", "kg"));
		assertThrows(IllegalArgumentException.class, () -> UnitConverter.convert(Double.NaN, "g", "kg"));
		assertThrows(IllegalArgumentException.class, () -> UnitConverter.convert(Double.POSITIVE_INFINITY, "g", "kg"));
	}

	@Test
	void knowsWhichUnitsAreSupported() {
		assertTrue(UnitConverter.isKnown("tbsp"));
		assertTrue(UnitConverter.isKnown("Litres"));
		assertFalse(UnitConverter.isKnown("bunch"));
		assertFalse(UnitConverter.isKnown(null));
	}

	@Test
	void scalesAmountsBetweenServingCounts() {
		assertEquals(500.0, UnitConverter.scale(200, 2, 5), TINY);
		assertEquals(100.0, UnitConverter.scale(200, 4, 2), TINY);
		assertEquals(200.0, UnitConverter.scale(200, 3, 3), TINY);
	}

	@Test
	void scaleRejectsBadServingsAndAmounts() {
		assertThrows(IllegalArgumentException.class, () -> UnitConverter.scale(1, 0, 2));
		assertThrows(IllegalArgumentException.class, () -> UnitConverter.scale(1, 2, 0));
		assertThrows(IllegalArgumentException.class, () -> UnitConverter.scale(-5, 2, 4));
	}
}