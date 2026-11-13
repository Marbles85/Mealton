package com.mealton;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Optional;

import com.mealton.account.AccountService;
import com.mealton.account.Session;
import com.mealton.cli.AuthPrompt;
import com.mealton.cli.CliApp;
import com.mealton.config.AppConfig;
import com.mealton.llm.ClaudeAdapter;
import com.mealton.llm.LLMClient;
import com.mealton.llm.LLMClientFactory;

/**
 * Entry point for the command-line version. The GUI starts from MealtonApp
 * instead.
 */
public class Main {
	public static void main(String[] args) {
		if (args.length > 0 && args[0].equalsIgnoreCase("gui")) {
			System.out.println("To start the GUI run: mvn javafx:run   (starting the CLI instead)");
		}

		// Read the local .env and build the AI client once (real Claude or the offline
		// mock).
		LLMClient llm = null;
		AppConfig config = AppConfig.load();
		try {
			llm = LLMClientFactory.create(config);
			System.out.println("AI assistant ready (" + config.llmProvider() + ", " + config.llmModel() + ").");
		} catch (RuntimeException e) {
			// The rest of the app still works without the AI.
			System.out.println("AI assistant is OFF: " + e.getMessage());
		}

		AccountService accounts = new AccountService(AccountService.defaultDirectory());
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		AuthPrompt auth = new AuthPrompt(accounts, in, System.out);

		while (true) {
			Optional<String> user = auth.run();
			if (user.isEmpty()) {
				break;
			}
			Session session = Session.start(accounts, user.get(), llm);
			session.warnings().forEach(w -> System.out.println("Note: " + w));

			CliApp cli = new CliApp(session.facade(), in, System.out);
			cli.setUsername(session.username());
			cli.setAfterCommand(() -> {
				String problem = session.save();
				if (problem != null) {
					System.out.println("Warning: " + problem);
				}
			});
			System.out.println("Logged in as " + session.username() + ". Your data is saved automatically.");
			cli.run();
			String problem = session.save();
			if (problem != null) {
				System.out.println("Warning: " + problem);
			}
			if (!cli.loggedOut()) {
				break;
			}
		}

		// Useful for the AI usage log in the report.
		if (llm instanceof ClaudeAdapter claude) {
			System.out.println(claude.usageSummary());
		}
	}
}