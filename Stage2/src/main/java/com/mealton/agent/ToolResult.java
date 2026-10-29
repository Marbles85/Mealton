package com.mealton.agent;

/**
 * What a tool returns after it runs: a success with some text, or an error with
 * a message. The agent sends this text back to the LLM so it can decide what to
 * do next.
 */
public record ToolResult(boolean success, String content) {

	public ToolResult {
		content = content == null ? "" : content;
	}

	public static ToolResult ok(String content) {
		return new ToolResult(true, content);
	}

	public static ToolResult error(String message) {
		return new ToolResult(false, "Error: " + message);
	}
}