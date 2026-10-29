package com.mealton.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mealton.llm.Message;

/**
 * The agent's memory. It keeps the conversation so far (so the user can say
 * "add those too" and the agent knows what "those" means) and a few facts about
 * the user, like "diet=vegetarian".
 */
public class AgentMemory {

	private final List<Message> history = new ArrayList<>();
	private final Map<String, String> preferences = new LinkedHashMap<>();

	/** Adds any message (user, assistant with tool calls, or a tool result). */
	public void add(Message message) {
		history.add(message);
	}

	/** Adds a plain text turn for the given role. */
	public void addTurn(Message.Role role, String text) {
		history.add(new Message(role, text, null, null));
	}

	/**
	 * The most recent messages, up to n. The slice always starts with a USER
	 * message, because the LLM rejects a tool result that has lost the tool request
	 * before it.
	 */
	public List<Message> getRecentContext(int n) {
		int from = Math.max(0, history.size() - Math.max(0, n));
		while (from < history.size() && history.get(from).role() != Message.Role.USER) {
			from++;
		}
		return new ArrayList<>(history.subList(from, history.size()));
	}

	public int size() {
		return history.size();
	}

	/**
	 * Remembers a fact about the user, for example setPreference("diet",
	 * "vegetarian").
	 */
	public void setPreference(String key, String value) {
		if (key == null || key.isBlank()) {
			throw new IllegalArgumentException("A preference needs a name.");
		}
		if (value == null || value.isBlank()) {
			preferences.remove(key.trim());
		} else {
			preferences.put(key.trim(), value.trim());
		}
	}

	/** A copy of the remembered facts. */
	public Map<String, String> getPreferences() {
		return new LinkedHashMap<>(preferences);
	}

	/** Forgets the conversation (the preferences stay). */
	public void clearConversation() {
		history.clear();
	}
}