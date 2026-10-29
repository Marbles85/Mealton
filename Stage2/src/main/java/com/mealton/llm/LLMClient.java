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
}