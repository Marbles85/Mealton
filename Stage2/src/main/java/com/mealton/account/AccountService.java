package com.mealton.account;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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

/**
 * Sign up and log in. Accounts live in "accounts.json" inside the data folder; each account stores only
 * a login name (an email address), a salt and a password hash (never the password), plus, if the user ticked
 * "Remember me", a hash of a random token (see {@link #createRememberToken(String)}). Every user also gets
 * their own folder for their saved data (see {@link #userDirectory(String)}).
 * <p>
 * This is a local, single-computer login: it keeps one person's food data apart from another's,
 * it is not an online account system (there is no password reset).
 */
public class AccountService {
    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_PASSWORD_LENGTH = 100;
    private static final Pattern USERNAME = Pattern.compile("[a-z0-9_]{3,20}");
    private static final Pattern EMAIL = Pattern.compile("[a-z0-9._%+-]{1,40}@[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,}");
    private static final int MAX_EMAIL_LENGTH = 64;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** rememberHash is empty unless "Remember me" is on; old files without it still load. */
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
     * The data folder: the MEALTON_DATA_DIR environment variable if it is set, otherwise "mealton-data"
     * next to where the program runs. It is listed in .gitignore, so accounts and saved food never reach GitHub.
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
     * Lower-case login name, or an error that says what is wrong with it. A login name is an email address;
     * older accounts made with a plain username (3 to 20 letters, digits or underscores) are still accepted here
     * so they keep working.
     */
    public static String checkUsername(String username) {
        String name = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        boolean isUsername = USERNAME.matcher(name).matches();
        boolean isEmail = name.length() <= MAX_EMAIL_LENGTH && EMAIL.matcher(name).matches();
        if (!isUsername && !isEmail) {
            throw new IllegalArgumentException("Enter a valid email address, like you@example.com.");
        }
        return name;
    }

    /**
     * Lower-case email address, or an error. New accounts must use an email address as their login name
     * (older accounts made with a plain username still work for logging in).
     */
    public static