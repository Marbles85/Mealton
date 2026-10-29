package com.mealton.llm;

import java.util.List;

/**
 * One message in a conversation with the LLM. USER: what the person said.
 * ASSISTANT: what the LLM said (it may ask for tools). TOOL: the result of a
 * tool, sent back to the LLM (toolCallId says which request it answers).
 */
public record Message(Role role, String text, List<ToolCall> toolCalls, String toolCallId) {

	public enum Role {
		USER, ASSISTANT, TOOL
	}

	public Message {
		if (role == null) {
			throw new IllegalArgumentException("A message needs a role.");
		}
		text = text == null ? "" : text;
		toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
	}

	public static Message user(String text) {
		return new Message(Role.USER, text, null, null);
	}

	public static Message assistant(String text, List<ToolCall> toolCalls) {
		return new Message(Role.ASSISTANT, text, toolCalls, null);
	}

	public static Message toolResult(String toolCallId, String text) {
		return new Message(Role.TOOL, text, null, toolCallId);
	}
}