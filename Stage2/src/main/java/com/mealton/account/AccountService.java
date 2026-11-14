package com.mealton.account;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Sign up and log in. Accounts live in "accounts.json" inside the data folder;
 * each account stores only a username (or email address), a salt and a password
 * hash (never the password), plus, if the user ticked "Remember me", a hash of
 * a random token (see {@link #createRememberToken(String)}). Every user also
 * gets their own folder for their saved data (see
 * {@link #userDirectory(String)}).
 * <p>
 * This is a local, single-computer login: it keeps one person's food data apart
 * from another's, it is not an online account system (there is no password
 * reset).
 */
public class AccountService {
	public static final int MIN_PASSWORD_LENGTH = 8;
	public static final int MAX_PASSWORD_LENGTH = 100;
	private static final Pattern USERNAME = Pattern.compile("[a-z0-9_]{3,20}");
	private static final Pattern EMAIL = Pattern.compile("[a-z0-9._%+-]{1,40}@[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,}");
	private static final int MAX_EMAIL_LENGTH = 64;
	private static final SecureRandom RANDOM = new SecureRandom();

	/**
	 * rememberHash is empty unless "Remember me" is on; old files without it still
	 * load.
	 */
	private record Entry(String username, String salt, String hash, String rememberHash) {
	}

	private record Accounts(List<Entry> accounts) {
	}

	private final Path directory;
	private final Path file;
	private final ObjectMapper mapper = new ObjectMapper()
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
			.enable(SerializationFeature.INDENT_OUTPUT);

	/**
	 * The data folder: the MEALTON_DATA_DIR environment variable if it is set,
	 * otherwise "mealton-data" next to where the program runs. It is listed in
	 * .gitignore, so accounts and saved food never reach GitHub.
	 */
	public static Path defaultDirectory() {
		String fromEnv = System.getenv("MEALTON_DATA_DIR");
		return Path.of(fromEnv == null || fromEnv.isBlank() ? "mealton-data" : fromEnv.trim());
	}

	/** @param directory the data folder (created when the first account is made) */
	public AccountService(Path directory) {
		this.directory = directory;
		this.file = directory.resolve("accounts.json");
	}

	/**
	 * Lower-case login name, or an error that says what is wrong with it. A login
	 * name is either a username (3 to 20 letters, digits or underscores) or an
	 * email address.
	 */
	public static String checkUsername(String username) {
		String name = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
		boolean isUsername = USERNAME.matcher(name).matches();
		boolean isEmail = name.length() <= MAX_EMAIL_LENGTH && EMAIL.matcher(name).matches();
		if (!isUsername && !isEmail) {
			throw new IllegalArgumentException("Enter a username (3 to 20 letters, digits or underscores, no spaces) "
					+ "or a valid email address.");
		}
		return name;
	}

	/**
	 * Makes a new account.
	 *
	 * @return the (lower-case) username
	 * @throws IllegalArgumentException for a bad username, a weak password,
	 *                                  mismatching passwords or a taken name
	 */
	public synchronized String signUp(String username, String password, String passwordAgain) {
		String name = checkUsername(username);
		if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
			throw new IllegalArgumentException("The password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
		}
		if (password.length() > MAX_PASSWORD_LENGTH) {
			throw new IllegalArgumentException("The password can be at most " + MAX_PASSWORD_LENGTH + " characters.");
		}
		if (!password.equals(passwordAgain)) {
			throw new IllegalArgumentException("The two passwords are not the same.");
		}
		List<Entry> all = read();
		if (find(all, name) != null) {
			throw new IllegalArgumentException("'" + name + "' is already taken. Choose another one.");
		}
		String salt = PasswordHasher.newSalt();
		all.add(new Entry(name, salt, PasswordHasher.hash(password, salt), null));
		write(all);
		return name;
	}

	/**
	 * Checks a username and password.
	 *
	 * @return the (lower-case) username
	 * @throws IllegalArgumentException with the same message whether the user or
	 *                                  the password is wrong
	 */
	public synchronized String logIn(String username, String password) {
		String wrong = "Wrong username or password.";
		String name = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
		Entry entry = find(read(), name);
		if (entry == null) {
			PasswordHasher.hash(password == null ? "" : password, PasswordHasher.newSalt()); // same wait as a real
																								// check
			throw new IllegalArgumentException(wrong);
		}
		if (!PasswordHasher.matches(password, entry.salt(), entry.hash())) {
			throw new IllegalArgumentException(wrong);
		}
		return name;
	}

	/**
	 * Turns on "Remember me" for this user: makes a random token, keeps only its
	 * hash in accounts.json and returns the token, which the app keeps on this
	 * computer. The password itself is never stored anywhere. Making a new token
	 * replaces the old one.
	 */
	public synchronized String createRememberToken(String username) {
		String name = checkUsername(username);
		List<Entry> all = read();
		Entry entry = find(all, name);
		if (entry == null) {
			throw new IllegalArgumentException("Unknown account.");
		}
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		replace(all, new Entry(entry.username(), entry.salt(), entry.hash(), sha256(token)));
		write(all);
		return token;
	}

	/**
	 * Logs in with the token from {@link #createRememberToken(String)} (the
	 * "Remember me" autofill).
	 *
	 * @return the (lower-case) login name
	 * @throws IllegalArgumentException if the token is missing, wrong or was turned
	 *                                  off
	 */
	public synchronized String logInWithToken(String username, String token) {
		String expired = "The saved login is no longer valid. Please type your password.";
		String name = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
		Entry entry = find(read(), name);
		if (entry == null || entry.rememberHash() == null || token == null) {
			throw new IllegalArgumentException(expired);
		}
		boolean same = MessageDigest.isEqual(sha256(token).getBytes(StandardCharsets.UTF_8),
				entry.rememberHash().getBytes(StandardCharsets.UTF_8));
		if (!same) {
			throw new IllegalArgumentException(expired);
		}
		return name;
	}

	/** Turns "Remember me" off for this user (the saved token stops working). */
	public synchronized void forgetRememberToken(String username) {
		List<Entry> all = read();
		Entry entry = find(all, username == null ? "" : username.trim().toLowerCase(Locale.ROOT));
		if (entry != null && entry.rememberHash() != null) {
			replace(all, new Entry(entry.username(), entry.salt(), entry.hash(), null));
			write(all);
		}
	}

	public synchronized boolean exists(String username) {
		return find(read(), username == null ? "" : username.trim().toLowerCase(Locale.ROOT)) != null;
	}

	/**
	 * The folder for one user's saved data. The name is checked, so it can never
	 * point outside the data folder. An email's "@" becomes "_at_" (usernames
	 * cannot contain dots, so the two kinds can never clash).
	 */
	public Path userDirectory(String username) {
		return directory.resolve("users").resolve(checkUsername(username).replace("@", "_at_"));
	}

	private static void replace(List<Entry> all, Entry updated) {
		for (int i = 0; i < all.size(); i++) {
			if (all.get(i).username().equals(updated.username())) {
				all.set(i, updated);
				return;
			}
		}
	}

	private static String sha256(String text) {
		try {
			return HexFormat.of()
					.formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is not available", e);
		}
	}

	private static Entry find(List<Entry> all, String name) {
		for (Entry entry : all) {
			if (entry.username().equals(name)) {
				return entry;
			}
		}
		return null;
	}

	private List<Entry> read() {
		if (!Files.isRegularFile(file)) {
			return new ArrayList<>();
		}
		try {
			Accounts accounts = mapper.readValue(file.toFile(), Accounts.class);
			return accounts == null || accounts.accounts() == null ? new ArrayList<>()
					: new ArrayList<>(accounts.accounts());
		} catch (IOException e) {
			throw new IllegalStateException("The accounts file could not be read (" + file + "): " + e.getMessage());
		}
	}

	private void write(List<Entry> all) {
		try {
			Files.createDirectories(directory);
			Path temp = directory.resolve("accounts.json.tmp");
			mapper.writeValue(temp.toFile(), new Accounts(all));
			try {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			throw new IllegalStateException("The account could not be saved: " + e.getMessage());
		}
	}
}