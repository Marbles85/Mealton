package com.mealton.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mealton.agent.tools.PantryTool;
import com.mealton.llm.LLMClient;
import com.mealton.llm.LLMResponse;
import com.mealton.llm.Message;
import com.mealton.llm.MockLLMClient;
import com.mealton.llm.ToolSpec;
import com.mealton.pantry.PantryInventory;
import com.mealton.pantry.PantryItem;
import com.mealton.repository.InMemoryPantryRepository;

class MealtonAgentTest {

	private PantryInventory pantry;
	private ToolRegistry registry;
	private AgentMemory memory;

	// Runs before every test: a pantry with milk in it, one tool, and an empty
	// memory
	@BeforeEach
	void setUp() {
		pantry = new PantryInventory(new InMemoryPantryRepository());
		pantry.addItem(new PantryItem("Milk", 1, "L", "Dairy", null));
		registry = new ToolRegistry();
		registry.register(new PantryTool(pantry));
		memory = new AgentMemory();
	}

	private MealtonAgent agent(LLMClient llm) {
		return new MealtonAgent(llm, registry, memory);
	}

	private static Message lastMessageSentTo(MockLLMClient mock) {
		List<Message> sent = mock.lastConversation();
		return sent.get(sent.size() - 1);
	}

	@Test
	void answersDirectlyWhenNoToolIsNeeded() {
		MockLLMClient mock = new MockLLMClient().thenSay("Hello!");

		AgentReply reply = agent(mock).run("hi");

		assertEquals("Hello!", reply.text());
		assertEquals(1, reply.steps());
		assertTrue(reply.toolsUsed().isEmpty());
		assertFalse(reply.stoppedEarly());
	}

	@Test
	void runsAToolAndGivesTheResultBackToTheLlm() {
		MockLLMClient mock = new MockLLMClient().thenCallTool("c1", "pantry", Map.of("action", "list"))
				.thenSay("You have milk.");

		AgentReply reply = agent(mock).run("what do I have?");

		assertEquals("You have milk.", reply.text());
		assertEquals(2, reply.steps());
		assertEquals(List.of("pantry"), reply.toolsUsed());
		// the second call to the LLM must contain the tool's real output
		Message last = lastMessageSentTo(mock);
		assertEquals(Message.Role.TOOL, last.role());
		assertTrue(last.text().contains("Milk"));
	}

	@Test
	void aToolCallCanChangeTheRealPantry() {
		MockLLMClient mock = new MockLLMClient()
				.thenCallTool("c1", "pantry", Map.of("action", "add", "name", "Rice", "quantity", "2", "unit", "kg"))
				.thenSay("Added rice.");

		agent(mock).run("add 2 kg of rice");

		assertEquals(2, pantry.getItems().size());
	}

	@Test
	void anUnknownToolBecomesAnErrorTheLlmCanRead() {
		MockLLMClient mock = new MockLLMClient().thenCallTool("c1", "teleport", Map.of()).thenSay("Sorry about that.");

		AgentReply reply = agent(mock).run("do something odd");

		assertFalse(reply.stoppedEarly());
		Message last = lastMessageSentTo(mock);
		assertTrue(last.text().contains("There is no tool called 'teleport'"));
		assertTrue(last.text().contains("pantry")); // it lists the real tools
	}

	@Test
	void aBadToolArgumentComesBackAsAnError() {
		MockLLMClient mock = new MockLLMClient()
				.thenCallTool("c1", "pantry", Map.of("action", "add", "name", "Rice", "quantity", "abc"))
				.thenSay("Let me fix that.");

		agent(mock).run("add rice");

		assertTrue(lastMessageSentTo(mock).text().startsWith("Error:"));
		assertEquals(1, pantry.getItems().size()); // nothing was added
	}

	@Test
	void stopsAtTheStepLimitInsteadOfLoopingForever() {
		MockLLMClient mock = new MockLLMClient();
		for (int i = 0; i < 5; i++) {
			mock.thenCallTool("c" + i, "pantry", Map.of("action", "list"));
		}

		AgentReply reply = new MealtonAgent(mock, registry, memory, 3).run("loop forever");

		assertTrue(reply.stoppedEarly());
		assertEquals(3, reply.steps());
		assertEquals(3, mock.chatCalls());
	}

	@Test
	void reportsAFriendlyMessageWhenTheLlmFails() {
		LLMClient broken = new LLMClient() {
			@Override
			public LLMResponse chat(List<Message> messages, List<ToolSpec> toolSpecs) {
				throw new IllegalStateException("network down");
			}

			@Override
			public String generate(String prompt) {
				return "";
			}
		};

		AgentReply reply = agent(broken).run("hi");

		assertTrue(reply.stoppedEarly());
		assertTrue(reply.text().contains("network down"));
	}

	@Test
	void rejectsABlankMessage() {
		MockLLMClient mock = new MockLLMClient();
		assertThrows(IllegalArgumentException.class, () -> agent(mock).run("   "));
		assertEquals(0, mock.chatCalls());
	}

	@Test
	void rejectsAStepLimitBelowOne() {
		assertThrows(IllegalArgumentException.class, () -> new MealtonAgent(new MockLLMClient(), registry, memory, 0));
	}

	@Test
	void remembersEarlierMessagesOfTheConversation() {
		MockLLMClient mock = new MockLLMClient().thenSay("first answer").thenSay("second answer");
		MealtonAgent agent = agent(mock);

		agent.run("my name is Sam");
		agent.run("what did I just say?");

		List<Message> sent = mock.lastConversation();
		assertTrue(sent.stream().anyMatch(m -> m.text().equals("my name is Sam")));
	}

	@Test
	void putsSavedPreferencesIntoTheInstructions() {
		memory.setPreference("diet", "vegetarian");
		MockLLMClient mock = new MockLLMClient().thenSay("ok");

		agent(mock).run("hi");

		Message first = mock.lastConversation().get(0);
		assertEquals(Message.Role.SYSTEM, first.role());
		assertTrue(first.text().contains("diet=vegetarian"));
	}
}