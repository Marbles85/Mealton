package com.mealton.facade;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import com.mealton.agent.AgentMemory;
import com.mealton.agent.AgentReply;
import com.mealton.agent.MealtonAgent;
import com.mealton.agent.ToolRegistry;
import com.mealton.agent.tools.ConvertTool;
import com.mealton.agent.tools.PantryTool;
import com.mealton.agent.tools.ShoppingListTool;
import com.mealton.llm.LLMClient;
import com.mealton.pantry.FreshnessScheduler;
import com.mealton.pantry.PantryInventory;
import com.mealton.pantry.PantryItem;
import com.mealton.pantry.PantryObserver;
import com.mealton.repository.InMemoryPantryRepository;
import com.mealton.shopping.ShoppingItem;
import com.mealton.shopping.ShoppingService;
import com.mealton.util.UnitConverter;

/**
 * Facade pattern + MVC controller: the single entry point used by BOTH the GUI
 * and the CLI (Stage 1: MealtonFacade). More services (plans, recipes,
 * agent...) will be added here.
 */
public class MealtonFacade {
	private final PantryInventory pantry;
	private final ShoppingService shopping;
	private final FreshnessScheduler scheduler;
	private MealtonAgent agent; // null until enableAgent() is called

	public MealtonFacade() {
		this(Clock.systemDefaultZone());
	}

	public MealtonFacade(Clock clock) {
		this.pantry = new PantryInventory(new InMemoryPantryRepository(), clock);
		this.shopping = new ShoppingService();
		this.scheduler = new FreshnessScheduler(pantry);
	}

	// ---------------- Pantry (F06) ----------------

	public PantryItem addPantryItem(PantryItem item) {
		return pantry.addItem(item);
	}

	public int removePantryItems(Collection<Long> ids) {
		return pantry.removeItems(ids).size();
	}

	public List<PantryItem> getPantryItems() {
		return pantry.getItems();
	}

	public List<PantryItem> getExpiringItems() {
		return pantry.getExpiringItems();
	}

	/**
	 * Runs a freshness check now; observers are notified about each expiring item.
	 */
	public void checkFreshness() {
		scheduler.tick();
	}

	public void startFreshnessScheduler() {
		scheduler.start();
	}

	public void attachPantryObserver(PantryObserver observer) {
		pantry.attach(observer);
	}

	public LocalDate today() {
		return pantry.today();
	}

	// ---------------- Shopping list (F12) ----------------

	public ShoppingItem addShoppingItem(ShoppingItem item) {
		return shopping.addItem(item);
	}

	public int deleteShoppingItems(Collection<Long> ids) {
		return shopping.deleteItems(ids);
	}

	public void toggleShoppingItem(long id) {
		shopping.toggle(id);
	}

	public int clearChecked() {
		return shopping.clearChecked();
	}

	public boolean undo() {
		return shopping.undo();
	}

	public boolean redo() {
		return shopping.redo();
	}

	public boolean canUndo() {
		return shopping.canUndo();
	}

	public boolean canRedo() {
		return shopping.canRedo();
	}

	public List<ShoppingItem> getShoppingItems() {
		return shopping.getItems();
	}

	// ---------------- Unit conversion and scaling (F10) ----------------

	/** Converts an amount between two units of the same kind, e.g. 2 cup to ml. */
	public double convertUnits(double amount, String from, String to) {
		return UnitConverter.convert(amount, from, to);
	}

	/** Scales an ingredient amount from one number of servings to another. */
	public double scaleAmount(double amount, int fromServings, int toServings) {
		return UnitConverter.scale(amount, fromServings, toServings);
	}

	// ---------------- AI chat agent (F14) ----------------

	/**
	 * Creates the chat agent with the given LLM. It can use the pantry, shopping
	 * list and convert tools.
	 */
	public void enableAgent(LLMClient llm) {
		ToolRegistry registry = new ToolRegistry();
		registry.register(new PantryTool(pantry));
		registry.register(new ShoppingListTool(shopping));
		registry.register(new ConvertTool());
		this.agent = new MealtonAgent(llm, registry, new AgentMemory());
	}

	public boolean hasAgent() {
		return agent != null;
	}

	/**
	 * Sends one message to the agent and returns its reply (and which tools it
	 * used).
	 */
	public AgentReply chat(String message) {
		if (agent == null) {
			throw new IllegalStateException("The AI agent is not set up. Check LLM_PROVIDER in your .env file.");
		}
		return agent.run(message);
	}
}