package com.mealton.llm;

import java.util.List;

/**
 * The one interface the rest of the app uses to talk to a language model
 * (Adapter pattern). ClaudeAdapter, OpenAIAdapter and MockLLMClient all
 * implement it, so the agent never knows which provider is behind it.
 */
public interface LLMClient {

	/**
	 * Sends the conversation and the available tools. The reply may ask to run
	 * tools.
	 */
	LLMResponse chat(List<Message> messages, List<ToolSpec> toolSpecs);

	/** Simple one-shot call: a prompt in, text out. */
	String generate(String prompt);

	/**
	 * True when this client can search the web (used to find real recipe pages).
	 */
	default boolean canSearchWeb() {
		return false;
	}

	/**
	 * Searches the web for pages that hold one recipe each and returns their
	 * addresses (https links). Clients that cannot search return an empty list.
	 *
	 * @param topic       what to look for, for example "vegetarian dinner"
	 * @param diet        the diet every recipe must suit (may be blank)
	 * @param maxSearches how many web searches Claude may run for this topic
	 */
	default List<String> searchRecipePages(String topic, String diet, int maxSearches) {
		return List.of();
	}
}