package com.mealton.llm;

import java.util.Map;

/**
 * Describes one tool to the LLM: its name, what it does, and its parameters.
 * "parameters" maps each parameter name to a short description of it. All
 * parameters are passed as text; each tool converts them to numbers if needed.
 */
public record ToolSpec(String name, String description, Map<String, String> parameters) {

	public ToolSpec {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("A tool needs a name.");
		}
		description = description == null ? "" : description;
		parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
	}
}