package com.mealton.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

	@Test
	void rightPasswordMatches() {
		String salt = PasswordHasher.newSalt();
		String hash = PasswordHasher.hash("correct horse", salt);
		assertTrue(PasswordHasher.matches("correct horse", salt, hash));
	}

	@Test
	void wrongPasswordDoesNotMatch() {
		String salt = PasswordHasher.newSalt();
		String hash = PasswordHasher.hash("correct horse", salt);
		assertFalse(PasswordHasher.matches("correct Horse", salt, hash));
		assertFalse(PasswordHasher.matches("", salt, hash));
	}

	@Test
	void sameHashForSameInputs() {
		String salt = PasswordHasher.newSalt();
		assertEquals(PasswordHasher.hash("abcdefgh", salt), PasswordHasher.hash("abcdefgh", salt));
	}

	@Test
	void differentSaltsGiveDifferentHashes() {
		assertNotEquals(PasswordHasher.newSalt(), PasswordHasher.newSalt());
		assertNotEquals(PasswordHasher.hash("abcdefgh", PasswordHasher.newSalt()),
				PasswordHasher.hash("abcdefgh", PasswordHasher.newSalt()));
	}

	@Test
	void hashDoesNotContainThePassword() {
		String salt = PasswordHasher.newSalt();
		assertFalse(PasswordHasher.hash("supersecret1", salt).contains("supersecret1"));
	}

	@Test
	void nullInputs() {
		assertFalse(PasswordHasher.matches(null, "x", "y"));
		assertFalse(PasswordHasher.matches("a", null, "y"));
		assertFalse(PasswordHasher.matches("a", "x", null));
		assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hash(null, "x"));
	}
}