package com.mealton.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mealton.llm.MockLLMClient;
import com.mealton.pantry.PantryItem;

class SessionTest {
	@TempDir
	Path dir;

	@Test
	void dataIsSavedAndComesBackAtTheNextLogin() {
		AccountService accounts = new AccountService(dir);
		accounts.signUp("sam", "password123", "password123");

		Session first = Session.start(accounts, "sam", null);
		first.facade().addPantryItem(new PantryItem("egg", 3, "", null, null));
		first.facade().setProfileName("Sam");
		assertNull(first.save());

		Session second = Session.start(accounts, "sam", null);
		assertEquals(1, second.facade().getPantryItems().size());
		assertEquals("Sam", second.facade().getProfile().name());
		assertTrue(second.warnings().isEmpty());
	}

	@Test
	void twoUsersNeverSeeEachOthersData() {
		AccountService accounts = new AccountService(dir);
		accounts.signUp("sam", "password123", "password123");
		accounts.signUp("alex", "password456", "password456");

		Session sam = Session.start(accounts, "sam", null);
		sam.facade().addPantryItem(new PantryItem("egg", 3, "", null, null));
		sam.save();

		Session alex = Session.start(accounts, "alex", null);
		assertTrue(alex.facade().getPantryItems().isEmpty());
		alex.facade().addPantryItem(new PantryItem("rice", 1, "kg", null, null));
		alex.save();

		assertEquals("egg", Session.start(accounts, "sam", null).facade().getPantryItems().get(0).getName());
		assertEquals("rice", Session.start(accounts, "alex", null).facade().getPantryItems().get(0).getName());
	}

	@Test
	void aNewUserStartsWithTheStarterRecipesOnly() {
		AccountService accounts = new AccountService(dir);
		accounts.signUp("sam", "password123", "password123");
		Session s = Session.start(accounts, "sam", null);
		assertEquals(12, s.facade().getRecipes().size());
		assertTrue(s.facade().getMyRecipes().isEmpty());
	}

	@Test
	void aDamagedSaveFileStartsFreshWithAWarning() throws IOException {
		AccountService accounts = new AccountService(dir);
		accounts.signUp("sam", "password123", "password123");
		Path folder = accounts.userDirectory("sam");
		Files.createDirectories(folder);
		Files.writeString(folder.resolve("data.json"), "garbage");
		Session s = Session.start(accounts, "sam", null);
		assertEquals(1, s.warnings().size());
		assertTrue(s.facade().getPantryItems().isEmpty());
	}

	@Test
	void theAiCanBeTurnedOnForASession() {
		AccountService accounts = new AccountService(dir);
		accounts.signUp("sam", "password123", "password123");
		assertTrue(Session.start(accounts, "sam", new MockLLMClient()).facade().hasAgent());
		assertEquals(false, Session.start(accounts, "sam", null).facade().hasAgent());
	}
}