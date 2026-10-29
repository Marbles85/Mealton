package com.mealton.llm;

import java.util.List;

/**
 * What the LLM sent back: some text, and possibly a request to run tools. If
 * there are no tool calls, the text is the final answer.
 */
public record LLMResponse(String text, List<ToolCall> toolCalls) {

	public LLMResponse {
		text = text == null ? "" : text;
		toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
	}

	public boolean hasToolCalls() {
		return !toolCalls.isEmpty();
	}

	public static LLMResponse text(String text) {
		return new LLMResponse(text, null);
	}
}