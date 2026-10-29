package com.mealton.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mealton.llm.ToolSpec;

/**
 * The list of tools the agent is allowed to use, looked up by name. The agent
 * sends specs() to the LLM and calls get(name) when the LLM asks for a tool.
 */
public class ToolRegistry {

	// LinkedHashMap keeps the tools in the order they were registered
	private final Map<String, Tool> tools = new LinkedHashMap<>();

	/** Adds a tool. Registering a second tool with the same name is an error. */
	public void register(Tool tool) {
		if (tools.containsKey(tool.name())) {
			throw new IllegalArgumentException("A tool named '" + tool.name() + "' is already registered.");
		}
		tools.put(tool.name(), tool);
	}

	/** Finds a tool by name. Empty if there is no such tool. */
	public Optional<Tool> get(String name) {
		return Optional.ofNullable(tools.get(name));
	}

	/** The descriptions of all tools, to send to the LLM. */
	public List<ToolSpec> specs() {
		List<ToolSpec> specs = new ArrayList<>();
		for (Tool tool : tools.values()) {
			specs.add(tool.schema());
		}
		return specs;
	}
}