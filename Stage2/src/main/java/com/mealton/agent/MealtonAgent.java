package com.mealton.agent;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.mealton.llm.LLMClient;
import com.mealton.llm.LLMResponse;
import com.mealton.llm.Message;
import com.mealton.llm.ToolCall;

/**
 * The Mealton agent. For each user message it runs a loop: ask the LLM -> if it
 * wants tools, run them and give it the results -> ask again ... until the LLM
 * gives a final answer or the step limit is reached. This is the "reasoning,
 * planning and tool use" part of the project.
 */
public class MealtonAgent {

	public static final int DEFAULT_MAX_STEPS = 8;
	private static final int CONTEXT_MESSAGES = 20; // how much past conversation we send each time

	private final LLMClient llm;
	private final ToolRegistry tools;
	private final AgentMemory memory;
	private final int maxSteps;

	public MealtonAgent(LLMClient llm, ToolRegistry tools, AgentMemory memory) {
		this(llm, tools, memory, DEFAULT_MAX_STEPS);
	}

	public MealtonAgent(LLMClient llm, ToolRegistry tools, AgentMemory memory, int maxSteps) {
		if (maxSteps < 1) {
			throw new IllegalArgumentException("The agent needs at least one step.");
		}
		this.llm = llm;
		this.tools = tools;
		this.memory = memory;
		this.maxSteps = maxSteps;
	}

	/** Handles one message from the user and returns the agent's final answer. */
	public AgentReply run(String userMessage) {
		if (userMessage == null || userMessage.isBlank()) {
			throw new IllegalArgumentException("Please type a message.");
		}
		memory.addTurn(Message.Role.USER, userMessage.trim());
		List<String> toolsUsed = new ArrayList<>();

		for (int step = 1; step <= maxSteps; step++) {
			LLMResponse response;
			try {
				response = llm.chat(buildConversation(), tools.specs());
			} catch (RuntimeException e) {
				String text = "Sorry, I could not reach the AI service: " + e.getMessage();
				memory.addTurn(Message.Role.ASSISTANT, text);
				return new AgentReply(text, toolsUsed, step, true);
			}

			// remember what the LLM said (including any tool requests)
			memory.add(Message.assistant(response.text(), response.toolCalls()));

			if (!response.hasToolCalls()) {
				return new AgentReply(response.text(), toolsUsed, step, false); // final answer
			}

			// the LLM asked for tools: run each one and remember the result
			for (ToolCall call : response.toolCalls()) {
				ToolResult result = runTool(call);
				toolsUsed.add(call.name());
				memory.add(Message.toolResult(call.id(), result.content()));
			}
		}

		// step limit reached without a final answer
		String text = "I could not finish that within " + maxSteps + " steps. " + "I used: "
				+ String.join(", ", toolsUsed) + ". Please try a simpler request.";
		memory.addTurn(Message.Role.ASSISTANT, text);
		return new AgentReply(text, toolsUsed, maxSteps, true);
	}

	/**
	 * Runs one tool request. A missing tool or a crashing tool becomes an error the
	 * LLM can read.
	 */
	private ToolResult runTool(ToolCall call) {
		Tool tool = tools.get(call.name()).orElse(null);
		if (tool == null) {
			String available = tools.specs().stream().map(s -> s.name()).collect(Collectors.joining(", "));
			return ToolResult.error("There is no tool called '" + call.name() + "'. Available tools: " + available);
		}
		try {
			return tool.execute(call.arguments());
		} catch (RuntimeException e) {
			return ToolResult.error("The tool '" + call.name() + "' failed: " + e.getMessage());
		}
	}

	/** The system instructions plus the recent conversation. */
	private List<Message> buildConversation() {
		List<Message> conversation = new ArrayList<>();
		conversation.add(Message.system(systemPrompt()));
		conversation.addAll(memory.getRecentContext(CONTEXT_MESSAGES));
		return conversation;
	}

	private String systemPrompt() {
		StringBuilder sb = new StringBuilder();
		sb.append("You are Mealton, a friendly meal-planning assistant. ");
		sb.append("Today's date is ").append(LocalDate.now()).append(". ");
		sb.append("Use the tools to look at the user's real pantry and shopping list instead of guessing. ");
		sb.append("Never invent items the user does not have. ");
		sb.append("If a tool returns an error, read it and fix your request. ");
		sb.append("Keep your answers short and clear.");
		Map<String, String> prefs = memory.getPreferences();
		if (!prefs.isEmpty()) {
			sb.append(" Known preferences of the user: ");
			sb.append(prefs.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
					.collect(Collectors.joining(", "))).append('.');
		}
		return sb.toString();
	}
}