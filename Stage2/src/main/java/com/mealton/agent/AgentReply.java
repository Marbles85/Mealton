package com.mealton.agent;

import java.util.List;

/**
 * What the agent gives back after handling one user message. "toolsUsed" lists
 * the tools it ran (in order), so the screen and the demo can show what it did.
 * "stoppedEarly" is true if the agent hit its step limit before it had a final
 * answer.
 */
public record AgentReply(String text, List<String> toolsUsed, int steps, boolean stoppedEarly) {

	public AgentReply {
		text = text == null ? "" : text;
		toolsUsed = toolsUsed == null ? List.of() : List.copyOf(toolsUsed);
	}
}