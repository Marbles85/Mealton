package com.mealton.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BodyUnitsTest {
	@Test
	void weightInKgAndPounds() {
		assertEquals(62.0, BodyUnits.parseWeightKg("62"));
		assertEquals(62.5, BodyUnits.parseWeightKg("62,5 kg"));
		assertEquals(61.2, BodyUnits.parseWeightKg("135 lbs"));
		assertEquals(61.2, BodyUnits.parseWeightKg("135lb"));
		assertThrows(IllegalArgumentException.class, () -> BodyUnits.parseWeightKg("heavy"));
	}

	@Test
	void heightInCmFeetInchesAndMetres() {
		assertEquals(165, BodyUnits.parseHeightCm("165"));
		assertEquals(165, BodyUnits.parseHeightCm("165 cm"));
		assertEquals(168, BodyUnits.parseHeightCm("5'6\""));
		assertEquals(168, BodyUnits.parseHeightCm("5' 6"));
		assertEquals(168, BodyUnits.parseHeightCm("5 ft 6 in"));
		assertEquals(152, BodyUnits.parseHeightCm("5ft"));
		assertEquals(168, BodyUnits.parseHeightCm("66 in"));
		assertEquals(165, BodyUnits.parseHeightCm("1.65 m"));
		assertEquals(152, BodyUnits.parseHeightCm("5"));
		assertThrows(IllegalArgumentException.class, () -> BodyUnits.parseHeightCm("tall"));
	}

	@Test
	void metricDetection() {
		assertTrue(BodyUnits.isMetric("62"));
		assertTrue(BodyUnits.isMetric("165 cm"));
		assertFalse(BodyUnits.isMetric("135 lb"));
		assertFalse(BodyUnits.isMetric("5'6\""));
	}
}