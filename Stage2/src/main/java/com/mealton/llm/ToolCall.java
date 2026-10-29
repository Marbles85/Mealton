package com.mealton.llm;

import java.util.Map;

/**
 * The LLM asking us to run a tool: which tool, with which arguments. The id
 * lets us match our answer to the right request.
 */
public record ToolCall(String id, String name, Map<String, String> arguments) {

	public ToolCall {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("A tool call needs a tool name.");
		}
		arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
	}
}