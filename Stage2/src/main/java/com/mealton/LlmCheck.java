package com.mealton;

import com.mealton.config.AppConfig;
import com.mealton.llm.ClaudeAdapter;
import com.mealton.llm.LLMClient;
import com.mealton.llm.LLMClientFactory;

/**
 * Quick check that the LLM connection works: loads your local .env, sends one
 * short question, prints the answer and the token usage. It never prints the
 * API key.
 */
public class LlmCheck {

	public static void main(String[] args) {
		AppConfig config = AppConfig.load();
		System.out.println(config); // shows provider, model and "apiKey=set", never the key itself

		// Safe diagnostics: describe the key without revealing it.
		String key = config.llmApiKey();
		if (key != null) {
			System.out.println("Key length: " + key.length() + " (real keys are roughly 100+ characters)");
			System.out.println("Starts with sk-ant-: " + key.startsWith("sk-ant-"));
			System.out
					.println("Has spaces or quotes: " + (key.contains(" ") || key.contains("\"") || key.contains("'")));
		}

		try {
			LLMClient client = LLMClientFactory.create(config);
			String reply = client.generate("Reply with exactly five words about cooking dinner.");
			System.out.println("Reply: " + reply);
			if (client instanceof ClaudeAdapter claude) {
				System.out.println(claude.usageSummary());
			}
		} catch (RuntimeException e) {
			System.out.println("Problem: " + e.getMessage());
		}
	}
}