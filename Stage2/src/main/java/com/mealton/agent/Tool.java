package com.mealton.agent;

import java.util.Map;

import com.mealton.llm.ToolSpec;

/**
 * One thing the agent is allowed to do (read the pantry, search recipes, add to
 * the list...). Every tool describes itself to the LLM with schema() and does
 * its work in execute().
 */
public interface Tool {

	/**
	 * The tool's name, exactly as the LLM will ask for it (for example "pantry").
	 */
	String name();

	/** Describes the tool and its parameters so the LLM knows how to use it. */
	ToolSpec schema();

	/**
	 * Runs the tool. Bad input should come back as ToolResult.error(...), not as a
	 * crash.
	 */
	ToolResult execute(Map<String, String> args);
}