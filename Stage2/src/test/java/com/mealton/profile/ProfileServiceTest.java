package com.mealton.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProfileServiceTest {

	private ProfileService service;
	private List<UserProfile> heard;

	@BeforeEach
	void setUp() {
		service = new ProfileService();
		heard = new ArrayList<>();
		service.addListener(heard::add);
	}

	@Test
	void startsEmpty() {
		assertEquals(UserProfile.empty(), service.get());
	}

	@Test
	void settersUpdateTheProfile() {
		service.setName("Bex");
		service.setDiet("vegetarian");
		service.addAllergy("peanut");
		service.setCalorieGoal(1800);
		service.setMacroGoals(100, 180, 50);
		UserProfile p = service.get();
		assertEquals("Bex", p.name());
		assertEquals(Diet.VEGETARIAN, p.diet());
		assertTrue(p.allergies().contains("peanut"));
		assertEquals(1800, p.calorieGoal());
		assertEquals(50, p.fatGoal());
	}

	@Test
	void listenersHearEveryValidChange() {
		service.setDiet("vegan");
		service.addAllergy("soy");
		assertEquals(2, heard.size());
		assertEquals(Diet.VEGAN, heard.get(1).diet());
		assertTrue(heard.get(1).allergies().contains("soy"));
	}

	@Test
	void invalidChangeKeepsTheOldProfileAndTellsNobody() {
		service.setDiet("vegan");
		int before = heard.size();
		assertThrows(IllegalArgumentException.class, () -> service.setDiet("carnivore"));
		assertThrows(IllegalArgumentException.class, () -> service.setCalorieGoal(50));
		assertThrows(IllegalArgumentException.class, () -> service.removeAllergy("soy"));
		assertEquals(Diet.VEGAN, service.get().diet());
		assertEquals(0, service.get().calorieGoal());
		assertEquals(before, heard.size());
	}

	@Test
	void resetClearsEverythingAndNotifies() {
		service.setDiet("vegan");
		service.addAllergy("soy");
		service.reset();
		assertEquals(UserProfile.empty(), service.get());
		assertEquals(UserProfile.empty(), heard.get(heard.size() - 1));
	}
}