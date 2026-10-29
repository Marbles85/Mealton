package com.mealton.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

/**
 * Tests ClaudeAdapter against a tiny fake server on this computer, so no real
 * API call is made, no key is needed, and nothing costs money.
 */
class ClaudeAdapterTest {

	private static final String KEY = "test-key-not-real";
	private static final ObjectMapper JSON = new ObjectMapper();

	private HttpServer server;
	private String url;
	private int status;
	private String responseBody;
	private String receivedBody;
	private String receivedKeyHeader;
	private String receivedVersionHeader;

	@BeforeEach
	void startFakeServer() throws IOException {
		status = 200;
		responseBody = reply("Hello!", 10, 5);
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/messages", exchange -> {
			receivedBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			receivedKeyHeader = exchange.getRequestHeaders().getFirst("x-api-key");
			receivedVersionHeader = exchange.getRequestHeaders().getFirst("anthropic-version");
			byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("content-type", "application/json");
			exchange.sendResponseHeaders(status, out.length);
			exchange.getResponseBody().write(out);
			exchange.close();
		});
		server.start();
		url = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/messages";
	}

	@AfterEach
	void stopFakeServer() {
		server.stop(0);
	}

	private static String reply(String text, int in, int out) {
		return "{\"content\":[{\"type\":\"text\",\"text\":\"" + text + "\"}]," + "\"usage\":{\"input_tokens\":" + in
				+ ",\"output_tokens\":" + out + "}}";
	}

	private ClaudeAdapter adapter() {
		return new ClaudeAdapter(KEY, "claude-haiku-5-5", url);
	}

	private JsonNode sentJson() throws IOException {
		return JSON.readTree(receivedBody);
	}

	@Test
	void generateReturnsTheTextAndSendsTheKeyAndVersionHeaders() throws IOException {
		String text = adapter().generate("Say hello");

		assertEquals("Hello!", text);
		assertEquals(KEY, receivedKeyHeader);
		assertEquals("2023-06-01", receivedVersionHeader);
		assertEquals("claude-haiku-5-5", sentJson().get("model").asText());
		assertEquals("Say hello", sentJson().get("messages").get(0).get("content").asText());
	}

	@Test
	void systemMessagesGoInTheSystemFieldNotInTheMessageList() throws IOException {
		adapter().chat(List.of(Message.system("You are Mealton."), Message.user("hi")), List.of());

		assertEquals("You are Mealton.", sentJson().get("system").asText());
		assertEquals(1, sentJson().get("messages").size());
		assertFalse(sentJson().has("tools")); // no tools were offered
	}

	@Test
	void toolsAreSentWithTheirParameters() throws IOException {
		ToolSpec spec = new ToolSpec("pantry", "Reads the pantry.", Map.of("action", "list or add"));

		adapter().chat(List.of(Message.user("hi")), List.of(spec));

		JsonNode tool = sentJson().get("tools").get(0);
		assertEquals("pantry", tool.get("name").asText());
		assertEquals("object", tool.get("input_schema").get("type").asText());
		assertEquals("string", tool.get("input_schema").get("properties").get("action").get("type").asText());
	}

	@Test
	void aToolUseAnswerBecomesAToolCall() {
		responseBody = "{\"content\":[{\"type\":\"text\",\"text\":\"Checking.\"},"
				+ "{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"pantry\","
				+ "\"input\":{\"action\":\"add\",\"quantity\":2}}],"
				+ "\"usage\":{\"input_tokens\":1,\"output_tokens\":1}}";

		LLMResponse response = adapter().chat(List.of(Message.user("add milk")), List.of());

		assertTrue(response.hasToolCalls());
		ToolCall call = response.toolCalls().get(0);
		assertEquals("toolu_1", call.id());
		assertEquals("pantry", call.name());
		assertEquals("add", call.arguments().get("action"));
		assertEquals("2", call.arguments().get("quantity")); // a number from Claude becomes text
		assertEquals("Checking.", response.text());
	}

	@Test
	void toolResultsFromOneStepShareOneUserMessage() throws IOException {
		List<ToolCall> calls = List.of(new ToolCall("a", "pantry", Map.of("action", "list")),
				new ToolCall("b", "shopping_list", Map.of("action", "list")));
		List<Message> conversation = List.of(Message.user("hi"), Message.assistant("", calls),
				Message.toolResult("a", "pantry text"), Message.toolResult("b", "list text"));

		adapter().chat(conversation, List.of());

		JsonNode messages = sentJson().get("messages");
		assertEquals(3, messages.size()); // user, assistant, ONE user message holding both results
		assertEquals("tool_use", messages.get(1).get("content").get(0).get("type").asText());
		JsonNode results = messages.get(2).get("content");
		assertEquals(2, results.size());
		assertEquals("tool_result", results.get(0).get("type").asText());
		assertEquals("a", results.get(0).get("tool_use_id").asText());
		assertEquals("b", results.get(1).get("tool_use_id").asText());
	}

	@Test
	void tokenUsageAddsUpAcrossCalls() {
		ClaudeAdapter adapter = adapter();
		adapter.generate("one");
		adapter.generate("two");

		assertEquals(2, adapter.calls());
		assertEquals(20L, adapter.inputTokens());
		assertEquals(10L, adapter.outputTokens());
		assertFalse(adapter.usageSummary().contains(KEY));
	}

	@Test
	void anApiErrorBecomesAClearMessageWithoutTheKey() {
		status = 401;
		responseBody = "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}";

		IllegalStateException e = assertThrows(IllegalStateException.class, () -> adapter().generate("hi"));

		assertTrue(e.getMessage().contains("401"));
		assertTrue(e.getMessage().contains("invalid x-api-key"));
		assertFalse(e.getMessage().contains(KEY));
	}

	@Test
	void anUnreachableServerBecomesAClearMessage() {
		server.stop(0); // nothing is listening any more

		IllegalStateException e = assertThrows(IllegalStateException.class, () -> adapter().generate("hi"));

		assertTrue(e.getMessage().startsWith("Could not reach the Claude API"));
	}

	@Test
	void aMissingKeyIsRejectedBeforeAnyRequest() {
		assertThrows(IllegalArgumentException.class, () -> new ClaudeAdapter("", "claude-haiku-5-5", url));
		assertThrows(IllegalArgumentException.class, () -> new ClaudeAdapter(null, "claude-haiku-5-5", url));
	}
}