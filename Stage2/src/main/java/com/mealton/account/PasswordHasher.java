package com.mealton.account;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Turns a password into a hash that is safe to store. The real password is
 * never written anywhere.
 * <p>
 * PBKDF2 with SHA-256 is a slow, standard way to hash passwords. Every account
 * gets its own random "salt", so two users with the same password still get
 * different hashes.
 */
public final class PasswordHasher {
	static final int ITERATIONS = 120_000;
	private static final int KEY_BITS = 256;
	private static final int SALT_BYTES = 16;
	private static final SecureRandom RANDOM = new SecureRandom();

	private PasswordHasher() {
		// utility class: nobody creates an instance
	}

	/** A new random salt, as text. */
	public static String newSalt() {
		byte[] salt = new byte[SALT_BYTES];
		RANDOM.nextBytes(salt);
		return Base64.getEncoder().encodeToString(salt);
	}

	/** The hash of a password with the given salt, as text. */
	public static String hash(String password, String salt) {
		if (password == null || salt == null) {
			throw new IllegalArgumentException("A password and a salt are needed.");
		}
		try {
			PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), Base64.getDecoder().decode(salt), ITERATIONS,
					KEY_BITS);
			byte[] bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
			spec.clearPassword();
			return Base64.getEncoder().encodeToString(bytes);
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			throw new IllegalStateException("Password hashing is not available on this computer: " + e.getMessage());
		}
	}

	/**
	 * True when the password matches the stored hash. The comparison takes the same
	 * time either way.
	 */
	public static boolean matches(String password, String salt, String expectedHash) {
		if (password == null || salt == null || expectedHash == null) {
			return false;
		}
		byte[] actual = hash(password, salt).getBytes(StandardCharsets.UTF_8);
		return MessageDigest.isEqual(actual, expectedHash.getBytes(StandardCharsets.UTF_8));
	}
}