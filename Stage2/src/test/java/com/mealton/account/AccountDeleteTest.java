package com.mealton.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mealton.profile.Activity;
import com.mealton.profile.Goal;

class AccountDeleteTest {
	@TempDir
	Path dir;

	@Test
	void deletingAnAccountRemovesLoginAndAllItsData() throws Exception {
		AccountService accounts = new AccountService(dir);
		accounts.signUp("sam", "password123", "password123");
		accounts.signUp("kim", "password123", "password123");
		Session session = Session.start(accounts, "sam", null);
		session.facade().setProfileName("Sam");
		assertNull(session.save());
		Path folder = accounts.userDirectory("sam");
		assertTrue(Files.exists(folder.resolve("data.json")));

		accounts.deleteAccount("sam");

		assertFalse(accounts.exists("sam"));
		assertFalse(Files.exists(folder));
		assertTrue(accounts.exists("kim"));
		assertThrows(IllegalArgumentException.class, () -> accounts.logIn("sam", "password123"));
		assertThrows(IllegalArgumentException.class, () -> accounts.deleteAccount("sam"));
		// the same name can be used again, starting from nothing
		accounts.signUp("sam", "password123", "password123");
		assertEquals("", Session.start(accounts, "sam", null).facade().getProfile().name());
	}

	@Test
	void everyProfileFieldComesBackAtTheNextLogin() {
		AccountService accounts = new AccountService(dir);
		accounts.signUp("sam", "password123", "password123");
		Session first = Session.start(accounts, "sam", null);
		first.facade().setProfileEmail("sam@example.com");
		first.facade().setBirthDate("Mar 4, 2000");
		first.facade().setSex("female");
		first.facade().setBody(61.5, 168);
		first.facade().setActivity(Activity.ACTIVE);
		first.facade().setGoal(Goal.LOSE);
		first.facade().setRestrictions(Set.of("vegetarian", "dairy-free"));
		first.facade().addAllergy("peanut");
		first.facade().setTrackMacros(false);
		first.facade().setCalorieGoal(1800);
		assertNull(first.save());

		var p = Session.start(accounts, "sam", null).facade().getProfile();
		assertEquals("sam@example.com", p.email());
		assertEquals("Mar 4, 2000", p.birthDateText());
		assertEquals("female", p.sex());
		assertEquals(61.5, p.weightKg());
		assertEquals(168, p.heightCm());
		assertEquals(Activity.ACTIVE, p.activity());
		assertEquals(Goal.LOSE, p.goal());
		assertEquals(Set.of("vegetarian", "dairy-free"), p.restrictions());
		assertTrue(p.allergies().contains("peanut"));
		assertFalse(p.trackMacros());
		assertEquals(1800, p.calorieGoal());
	}
}