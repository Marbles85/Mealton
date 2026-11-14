package com.mealton.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Optional;

import com.mealton.account.AccountService;

/**
 * The text login screen shown before the CLI starts: 'login', 'signup' or
 * 'exit'. It talks to {@link AccountService} and never prints or stores a
 * password.
 */
public final class AuthPrompt {
	private final AccountService accounts;
	private final BufferedReader in;
	private final PrintStream out;
	private boolean warnedAboutVisibleTyping;

	public AuthPrompt(AccountService accounts, BufferedReader in, PrintStream out) {
		this.accounts = accounts;
		this.in = in;
		this.out = out;
	}

	/**
	 * Asks until someone logs in or signs up.
	 *
	 * @return the login name (email), or empty when the user chose 'exit' (or the
	 *         input ended)
	 */
	public Optional<String> run() {
		out.println("Welcome to Mealton. Log in, or create an account to get your own saved data.");
		out.println("  login [email]    signup [email]    help    exit");
		try {
			while (true) {
				out.print("mealton (not logged in)> ");
				out.flush();
				String line = in.readLine();
				if (line == null) {
					return Optional.empty();
				}
				List<String> t = CliApp.tokenize(line);
				if (t.isEmpty()) {
					continue;
				}
				try {
					switch (t.get(0).toLowerCase()) {
					case "login" -> {
						String user = t.size() > 1 ? t.get(1) : ask("Email: ");
						String password = askPassword("Password: ");
						String name = accounts.logIn(user, password);
						out.println("Welcome back, " + name + "!");
						return Optional.of(name);
					}
					case "signup", "register" -> {
						String user = t.size() > 1 ? t.get(1) : ask("Email address: ");
						AccountService.checkEmail(user); // say so before asking for a password
						if (accounts.exists(user)) {
							throw new IllegalArgumentException("The email '" + user.trim().toLowerCase()
									+ "' is already registered. Use 'login' instead.");
						}
						String password = askPassword(
								"Choose a password (at least " + AccountService.MIN_PASSWORD_LENGTH + " characters): ");
						String again = askPassword("Type the password again: ");
						String name = accounts.signUp(user, password, again);
						out.println("Account '" + name + "' created. You are logged in.");
						return Optional.of(name);
					}
					case "help" -> {
						out.println("  login [email]    log in to your account");
						out.println(
								"  signup [email]   create a new account (own profile, pantry, recipes, plan and diary)");
						out.println("  exit             leave Mealton");
					}
					case "exit", "quit" -> {
						return Optional.empty();
					}
					default -> out.println("Please type 'login', 'signup' or 'exit'.");
					}
				} catch (IllegalArgumentException | IllegalStateException e) {
					out.println("Error: " + e.getMessage());
				}
			}
		} catch (IOException e) {
			out.println("Input error: " + e.getMessage());
			return Optional.empty();
		}
	}

	private String ask(String question) throws IOException {
		out.print(question);
		out.flush();
		String answer = in.readLine();
		if (answer == null) {
			throw new IllegalArgumentException("No input.");
		}
		return answer.trim();
	}

	/**
	 * Hides the typing when the program runs in a real terminal; some consoles
	 * (like Eclipse's) cannot.
	 */
	private String askPassword(String question) throws IOException {
		java.io.Console console = System.console();
		if (console != null) {
			char[] typed = console.readPassword(question);
			if (typed == null) {
				throw new IllegalArgumentException("No input.");
			}
			String password = new String(typed);
			java.util.Arrays.fill(typed, ' ');
			return password;
		}
		if (!warnedAboutVisibleTyping) {
			out.println("(This console cannot hide what you type, so the password will be visible.)");
			warnedAboutVisibleTyping = true;
		}
		return ask(question);
	}
}