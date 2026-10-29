package com.mealton.llm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Adapter pattern: lets the app talk to Anthropic's Claude API through our own
 * LLMClient interface. It converts our Message and ToolSpec objects into the
 * JSON that Claude expects, sends them, and converts Claude's JSON answer back
 * into an LLMResponse. It also counts tokens, for the AI-usage log in the
 * report. The API key is never printed.
 */
public class ClaudeAdapter implements LLMClient {

	public static final String DEFAULT_URL = "https://api.anthropic.com/v1/messages";
	private static final String API_VERSION = "2023-06-01";
	private static final int MAX_TOKENS = 1024;

	private final String apiKey;
	private final String model;
	private final String url;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
	private final ObjectMapper json = new ObjectMapper();

	private int calls;
	private long inputTokens;
	private long outputTokens;

	public ClaudeAdapter(String apiKey, String model) {
		this(apiKey, model, DEFAULT_URL);
	}

	/**
	 * The URL can be replaced so tests can talk to a fake server instead of the
	 * real API.
	 */
	public ClaudeAdapter(String apiKey, String model, String url) {
		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalArgumentException("No API key. Put LLM_API_KEY in your local .env file.");
		}
		this.apiKey = apiKey;
		this.model = model;
		this.url = url;
	}

	@Override
	public LLMResponse chat(List<Message> messages, List<ToolSpec> toolSpecs) {
		JsonNode reply = send(buildRequest(messages, toolSpecs));
		return parseResponse(reply);
	}

	@Override
	public String generate(String prompt) {
		return chat(List.of(Message.user(prompt)), List.of()).text();
	}

	// ---------------------------------------------------------------- our types ->
	// Claude JSON

	private ObjectNode buildRequest(List<Message> messages, List<ToolSpec> toolSpecs) {
		ObjectNode body = json.createObjectNode();
		body.put("model", model);
		body.put("max_tokens", MAX_TOKENS);

		// SYSTEM messages go in a separate "system" field
		StringBuilder system = new StringBuilder();
		ArrayNode chat = body.putArray("messages");
		for (Message m : messages) {
			switch (m.role()) {
			case SYSTEM -> system.append(system.length() == 0 ? "" : "\n").append(m.text());
			case USER -> chat.add(textMessage("user", m.text()));
			case ASSISTANT -> chat.add(assistantMessage(m));
			case TOOL -> addToolResult(chat, m);
			}
		}
		if (system.length() > 0) {
			body.put("system", system.toString());
		}

		if (!toolSpecs.isEmpty()) {
			ArrayNode tools = body.putArray("tools");
			for (ToolSpec spec : toolSpecs) {
				tools.add(toolJson(spec));
			}
		}
		return body;
	}

	private ObjectNode textMessage(String role, String text) {
		ObjectNode node = json.createObjectNode();
		node.put("role", role);
		node.put("content", text);
		return node;
	}

	private ObjectNode assistantMessage(Message m) {
		ObjectNode node = json.createObjectNode();
		node.put("role", "assistant");
		ArrayNode blocks = node.putArray("content");
		if (!m.text().isBlank()) {
			blocks.addObject().put("type", "text").put("text", m.text());
		}
		for (ToolCall call : m.toolCalls()) {
			ObjectNode block = blocks.addObject();
			block.put("type", "tool_use");
			block.put("id", call.id());
			block.put("name", call.name());
			ObjectNode input = block.putObject("input");
			call.arguments().forEach(input::put);
		}
		if (blocks.isEmpty()) {
			blocks.addObject().put("type", "text").put("text", "(no reply)"); // Claude rejects empty content
		}
		return node;
	}

	/**
	 * Tool results go back as a "user" message. Results from the same step share
	 * one message.
	 */
	private void addToolResult(ArrayNode chat, Message m) {
		ObjectNode block = json.createObjectNode();
		block.put("type", "tool_result");
		block.put("tool_use_id", m.toolCallId());
		block.put("content", m.text());

		JsonNode last = chat.isEmpty() ? null : chat.get(chat.size() - 1);
		if (last != null && "user".equals(last.get("role").asText()) && last.get("content").isArray()
				&& last.get("content").size() > 0
				&& "tool_result".equals(last.get("content").get(0).path("type").asText())) {
			((ArrayNode) last.get("content")).add(block);
		} else {
			ObjectNode node = json.createObjectNode();
			node.put("role", "user");
			node.putArray("content").add(block);
			chat.add(node);
		}
	}

	private ObjectNode toolJson(ToolSpec spec) {
		ObjectNode tool = json.createObjectNode();
		tool.put("name", spec.name());
		tool.put("description", spec.description());
		ObjectNode schema = tool.putObject("input_schema");
		schema.put("type", "object");
		ObjectNode properties = schema.putObject("properties");
		spec.parameters().forEach((name, description) -> {
			ObjectNode p = properties.putObject(name);
			p.put("type", "string");
			p.put("description", description);
		});
		return tool;
	}

	// ---------------------------------------------------------------- sending

	private JsonNode send(ObjectNode body) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
					.header("content-type", "application/json").header("x-api-key", apiKey)
					.header("anthropic-version", API_VERSION)
					.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() / 100 != 2) {
				throw new IllegalStateException(
						"Claude API error " + response.statusCode() + ": " + errorMessage(response.body()));
			}
			return json.readTree(response.body());
		} catch (IOException e) {
			throw new IllegalStateException("Could not reach the Claude API: " + e.getMessage());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("The request to the Claude API was interrupted.");
		}
	}

	private String errorMessage(String body) {
		try {
			JsonNode message = json.readTree(body).path("error").path("message");
			return message.isMissingNode() ? "unexpected reply" : message.asText();
		} catch (IOException e) {
			return "unexpected reply";
		}
	}

	// ---------------------------------------------------------------- Claude JSON
	// -> our types

	private LLMResponse parseResponse(JsonNode reply) {
		calls++;
		inputTokens += reply.path("usage").path("input_tokens").asLong(0);
		outputTokens += reply.path("usage").path("output_tokens").asLong(0);

		StringBuilder text = new StringBuilder();
		List<ToolCall> toolCalls = new ArrayList<>();
		for (JsonNode block : reply.path("content")) {
			String type = block.path("type").asText();
			if ("text".equals(type)) {
				text.append(block.path("text").asText());
			} else if ("tool_use".equals(type)) {
				Map<String, String> args = new LinkedHashMap<>();
				Iterator<Map.Entry<String, JsonNode>> fields = block.path("input").fields();
				while (fields.hasNext()) {
					Map.Entry<String, JsonNode> field = fields.next();
					args.put(field.getKey(), field.getValue().asText()); // numbers become text too
				}
				toolCalls.add(new ToolCall(block.path("id").asText(), block.path("name").asText(), args));
			}
		}
		return new LLMResponse(text.toString(), toolCalls);
	}

	// ---------------------------------------------------------------- usage (for
	// the AI log)

	public int calls() {
		return calls;
	}

	public long inputTokens() {
		return inputTokens;
	}

	public long outputTokens() {
		return outputTokens;
	}

	/** Safe to print: never contains the key. */
	public String usageSummary() {
		return "Claude usage: " + calls + " calls, " + inputTokens + " input tokens, " + outputTokens
				+ " output tokens (model " + model + ")";
	}
}