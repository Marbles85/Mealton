package com.mealton;

import com.mealton.cli.CliApp;
import com.mealton.config.AppConfig;
import com.mealton.facade.MealtonFacade;
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

		MealtonFacade facade = new MealtonFacade();
		LLMClient llm = null;

		// Read the local .env and build the AI client (real Claude or the offline
		// mock).
		AppConfig config = AppConfig.load();
		try {
			llm = LLMClientFactory.create(config);
			facade.enableAgent(llm);
			System.out.println("AI assistant ready (" + config.llmProvider() + ", " + config.llmModel() + ").");
		} catch (RuntimeException e) {
			// The rest of the app still works without the AI.
			System.out.println("AI assistant is OFF: " + e.getMessage());
		}

		new CliApp(facade, System.in, System.out).run();

		// Useful for the AI usage log in the report.
		if (llm instanceof ClaudeAdapter claude) {
			System.out.println(claude.usageSummary());
		}
	}
}