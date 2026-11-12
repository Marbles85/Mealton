package com.mealton.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AccountServiceTest {
	@TempDir
	Path dir;

	@Test
	void signUpThenLogIn() {
		AccountService accounts = new AccountService(dir);
		assertEquals("rebekah", accounts.signUp("Rebekah", "password123", "password123"));
		assertEquals("rebekah", accounts.logIn("REBEKAH", "password123"));
		assertTrue(accounts.exists("rebekah"));
	}

	@Test
	void wrongPasswordAndUnknownUserGiveTheSameMessage() {
		AccountService accounts = new AccountService(dir);
		accounts.signUp("sam", "password123", "password123");
		String wrongPassword = assertThrows(IllegalArgumentException.class, () -> accounts.logIn("sam", "nope nope"))
				.getMessage();
		String unknownUser = assertThrows(IllegalArgumentException.class, () -> accounts.logIn("ghost", "password123"))
				.getMessage();
		assertEquals("Wrong username or password.", wrongPassword);
		assertEquals(wrongPassword, unknownUser);
	}

	@Test
	void usernameRules() {
		AccountService accounts = new AccountService(dir);
		for (String bad : new String[] { "", "ab", "has space", "../evil", "way_too_long_username_here", "a/b",
				null }) {
			assertThrows(IllegalArgumentException.class, () -> accounts.signUp(bad, "password123", "password123"), bad);
		}
	}

	@Test
	void passwordRules() {
		AccountService accounts = new AccountService(dir);
		assertThrows(IllegalArgumentException.class, () -> accounts.signUp("sam", "short", "short"));
		assertThrows(IllegalArgumentException.class, () -> accounts.signUp("sam", "password123", "password124"));
		assertThrows(IllegalArgumentException.class, () -> accounts.signUp("sam", null, null));
		assertFalse(accounts.exists("sam"));
	}

	@Test
	void usernameIsUniqueIgnoringCase() {
		AccountService accounts = new AccountService(dir);
		accounts.signUp("sam", "password123", "password123");
		assertThrows(IllegalArgumentException.class, () -> accounts.signUp("SAM", "otherpass99", "otherpass99"));
	}

	@Test
	void passwordIsNotStoredInTheFile() throws IOException {
		AccountService accounts = new AccountService(dir);
		accounts.signUp("sam", "my-secret-pass", "my-secret-pass");
		String text = Files.readString(dir.resolve("accounts.json"));
		assertFalse(text.contains("my-secret-pass"));
		assertTrue(text.contains("sam"));
	}

	@Test
	void accountsSurviveARestart() {
		new AccountService(dir).signUp("sam", "password123", "password123");
		AccountService again = new AccountService(dir);
		assertEquals("sam", again.logIn("sam", "password123"));
	}

	@Test
	void userDirectoryStaysInsideTheDataFolder() {
		AccountService accounts = new AccountService(dir);
		assertTrue(accounts.userDirectory("Sam").startsWith(dir));
		assertThrows(IllegalArgumentException.class, () -> accounts.userDirectory("../x"));
	}

	@Test
	void brokenAccountsFileGivesAClearError() throws IOException {
		Files.writeString(dir.resolve("accounts.json"), "{ not json");
		AccountService accounts = new AccountService(dir);
		assertThrows(IllegalStateException.class, () -> accounts.logIn("sam", "password123"));
	}
}