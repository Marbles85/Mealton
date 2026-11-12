package com.mealton.account;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Sign up and log in. Accounts live in "accounts.json" inside the data folder;
 * each account stores only a username, a salt and a password hash (never the
 * password). Every user also gets their own folder for their saved data (see
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

	private record Entry(String username, String salt, String hash) {
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

	/** Lower-case username, or an error that says what is wrong with it. */
	public static String checkUsername(String username) {
		String name = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
		if (!USERNAME.matcher(name).matches()) {
			throw new IllegalArgumentException(
					"A username must be 3 to 20 letters, digits or underscores (no spaces).");
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
			throw new IllegalArgumentException("The username '" + name + "' is already taken. Choose another one.");
		}
		String salt = PasswordHasher.newSalt();
		all.add(new Entry(name, salt, PasswordHasher.hash(password, salt)));
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

	public synchronized boolean exists(String username) {
		return find(read(), username == null ? "" : username.trim().toLowerCase(Locale.ROOT)) != null;
	}

	/**
	 * The folder for one user's saved data. The name is checked, so it can never
	 * point outside the data folder.
	 */
	public Path userDirectory(String username) {
		return directory.resolve("users").resolve(checkUsername(username));
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