package com.mealton.llm;

import com.mealton.config.AppConfig;

/**
 * Picks which LLM client to use based on the settings in your local .env file.
 * LLM_PROVIDER=claude -> the real Claude API (needs LLM_API_KEY)
 * LLM_PROVIDER=mock -> the offline fake used for tests and demos
 */
public final class LLMClientFactory {

	private LLMClientFactory() {
	}

	public static LLMClient create(AppConfig config) {
		switch (config.llmProvider()) {
		case "claude":
			if (!config.hasApiKey()) {
				throw new IllegalStateException("LLM_PROVIDER is 'claude' but no LLM_API_KEY was found. "
						+ "Put your key in the local .env file (never commit it).");
			}
			return new ClaudeAdapter(config.llmApiKey(), config.llmModel());
		case "mock":
			return new MockLLMClient();
		default:
			throw new IllegalStateException(
					"Unknown LLM_PROVIDER '" + config.llmProvider() + "'. Use 'claude' or 'mock'.");
		}
	}
}