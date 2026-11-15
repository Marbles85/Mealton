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
	private static final int MAX_TOKENS = 2048;
	/**
	 * Writing several whole recipes as JSON needs far more room than a chat reply.
	 */
	private static final int GENERATE_MAX_TOKENS = 8000;

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
		JsonNode reply = send(buildRequest(messages, toolSpecs, MAX_TOKENS));
		return parseResponse(reply);
	}

	@Override
	public String generate(String prompt) {
		return parseResponse(send(buildRequest(List.of(Message.user(prompt)), List.of(), GENERATE_MAX_TOKENS))).text();
	}

	// ---------------------------------------------------------------- our types ->
	// Claude JSON

	private ObjectNode buildRequest(List<Message> messages, List<ToolSpec> toolSpecs, int maxTokens) {
		ObjectNode body = json.createObjectNode();
		body.put("model", model);
		body.put("max_tokens", maxTokens);

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

	// ---------------------------------------------------------------- web search

	@Override
	public boolean canSearchWeb() {
		return true;
	}

	/**
	 * Uses Claude's built-in web search tool to find pages that each hold one
	 * recipe. The links come from the search results themselves (not from text
	 * Claude wrote), so they are real pages. Needs web search to be enabled for the
	 * API organisation in the Anthropic console.
	 */
	@Override
	public List<String> searchRecipePages(String topic, String diet, int maxSearches) {
		ObjectNode body = json.createObjectNode();
		body.put("model", model);
		body.put("max_tokens", 2000);
		ArrayNode tools = body.putArray("tools");
		ObjectNode search = tools.addObject();
		search.put("type", "web_search_20250305");
		search.put("name", "web_search");
		search.put("max_uses", Math.max(1, Math.min(8, maxSearches)));
		StringBuilder prompt = new StringBuilder("Use web search to find well-reviewed recipe pages for: ")
				.append(topic).append(". ")
				.append("I want links to individual recipe pages (one recipe per page, with ingredients, steps, photo and user ratings), ")
				.append("not lists, roundups or videos. Prefer well-known recipe sites. Vary the dishes. ");
		if (diet != null && !diet.isBlank() && !diet.equalsIgnoreCase("anything")) {
			prompt.append("Every recipe must suit this diet: ").append(diet).append(". ");
		}
		prompt.append("When you are done, reply with one short sentence.");
		ArrayNode messages = body.putArray("messages");
		messages.add(textMessage("user", prompt.toString()));
		JsonNode reply = send(body);
		parseResponse(reply); // counts the usage
		List<String> links = new ArrayList<>();
		for (JsonNode block : reply.path("content")) {
			if ("web_search_tool_result".equals(block.path("type").asText())) {
				JsonNode content = block.path("content");
				if (content.isArray()) {
					for (JsonNode hit : content) {
						String link = hit.path("url").asText("");
						if (link.startsWith("https://") && !links.contains(link)) {
							links.add(link);
						}
					}
				} else if (content.path("type").asText().endsWith("error")) {
					throw new IllegalStateException("Web search failed: " + content.path("error_code").asText("unknown")
							+ ". Check that web search is enabled for your API key in the Anthropic console.");
				}
			}
		}
		return links;
	}

	// ---------------------------------------------------------------- sending

	/**
	 * Prints which feature is calling Claude and the start of the prompt (never the
	 * key).
	 */
	private void logCall(ObjectNode body) {
		try {
			StringBuilder who = new StringBuilder();
			for (StackTraceElement frame : new Throwable().getStackTrace()) {
				String cls = frame.getClassName();
				if (cls.startsWith("com.mealton") && !cls.endsWith("ClaudeAdapter") && who.length() < 160) {
					who.append(cls.substring(cls.lastIndexOf('.') + 1)).append('.').append(frame.getMethodName())
							.append(" < ");
				}
			}
			String prompt = "";
			JsonNode messages = body.path("messages");
			if (messages.isArray() && messages.size() > 0) {
				JsonNode content = messages.get(messages.size() - 1).path("content");
				prompt = content.isArray() ? content.toString() : content.asText();
			}
			prompt = prompt.replaceAll("\\s+", " ");
			System.out.println("[Mealton] Claude call from " + who + "| prompt: "
					+ (prompt.length() > 200 ? prompt.substring(0, 200) + "..." : prompt));
		} catch (RuntimeException ignored) {
			// logging must never break a call
		}
	}

	private JsonNode send(ObjectNode body) {
		logCall(body);
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(120))
					.header("content-type", "application/json").header("x-api-key", apiKey)
					.header("anthropic-version", API_VERSION)
					.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() / 100 != 2) {
				throw new IllegalStateException(
						"Claude API error " + response.statusCode() + ": " + errorMessage(response.body()));
			}
			JsonNode tree = json.readTree(response.body());
			System.out.println("[Mealton] Claude replied: " + tree.path("usage").path("input_tokens").asLong(0)
					+ " input / " + tree.path("usage").path("output_tokens").asLong(0) + " output tokens");
			return tree;
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

	private synchronized LLMResponse parseResponse(JsonNode reply) {
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