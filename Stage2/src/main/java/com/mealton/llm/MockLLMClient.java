package com.mealton.llm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A fake LLM for tests and demos. It returns scripted answers in order, so the
 * agent can be developed and tested with no API key and no cost.
 */
public class MockLLMClient implements LLMClient {

	public static final String DEFAULT_REPLY = "(mock) I have no more scripted replies.";

	private final Deque<LLMResponse> script = new ArrayDeque<>();
	private final List<List<Message>> receivedConversations = new ArrayList<>();
	private int generateCalls;

	/**
	 * Adds the next answer this mock will give. Returns this, so calls can be
	 * chained.
	 */
	public MockLLMClient thenReply(LLMResponse response) {
		script.addLast(response);
		return this;
	}

	/** Adds a plain text answer (no tool calls). */
	public MockLLMClient thenSay(String text) {
		return thenReply(LLMResponse.text(text));
	}

	/** Adds an answer that asks to run one tool. */
	public MockLLMClient thenCallTool(String id, String toolName, java.util.Map<String, String> arguments) {
		return thenReply(new LLMResponse("", List.of(new ToolCall(id, toolName, arguments))));
	}

	@Override
	public LLMResponse chat(List<Message> messages, List<ToolSpec> toolSpecs) {
		receivedConversations.add(List.copyOf(messages)); // remember what we were sent, for tests
		LLMResponse next = script.pollFirst();
		return next != null ? next : LLMResponse.text(DEFAULT_REPLY);
	}

	@Override
	public String generate(String prompt) {
		generateCalls++;
		LLMResponse next = script.pollFirst();
		return next != null ? next.text() : DEFAULT_REPLY;
	}

	/** How many times chat() was called. */
	public int chatCalls() {
		return receivedConversations.size();
	}

	public int generateCalls() {
		return generateCalls;
	}

	/** The conversation that was sent in the most recent chat() call. */
	public List<Message> lastConversation() {
		return receivedConversations.isEmpty() ? List.of()
				: receivedConversations.get(receivedConversations.size() - 1);
	}
}