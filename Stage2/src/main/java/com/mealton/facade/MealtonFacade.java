package com.mealton.facade;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import com.mealton.pantry.FreshnessScheduler;
import com.mealton.pantry.PantryInventory;
import com.mealton.pantry.PantryItem;
import com.mealton.pantry.PantryObserver;
import com.mealton.repository.InMemoryPantryRepository;
import com.mealton.shopping.ShoppingItem;
import com.mealton.shopping.ShoppingService;

/**
 * Facade pattern and MVC controller: the single entry point used by BOTH the
 * GUI and the CLI. More services (plans, recipes, the agent) will be added here
 * as we build them.
 */
public class MealtonFacade {

	private final PantryInventory pantry;
	private final ShoppingService shopping;
	private final FreshnessScheduler scheduler;

	public MealtonFacade() {
		this(Clock.systemDefaultZone());
	}

	/** The clock can be replaced so tests can use a fixed "today". */
	public MealtonFacade(Clock clock) {
		this.pantry = new PantryInventory(new InMemoryPantryRepository(), clock);
		this.shopping = new ShoppingService();
		this.scheduler = new FreshnessScheduler(pantry);
	}

	// ---------------- Pantry (feature F06) ----------------

	public PantryItem addPantryItem(PantryItem item) {
		return pantry.addItem(item);
	}

	/** Removes the items with these ids. Returns how many were removed. */
	public int removePantryItems(Collection<Long> ids) {
		return pantry.removeItems(ids).size();
	}

	public List<PantryItem> getPantryItems() {
		return pantry.getItems();
	}

	public List<PantryItem> getExpiringItems() {
		return pantry.getExpiringItems();
	}

	/** Runs a freshness check now. Observers are told about each expiring item. */
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

	// ---------------- Shopping list (feature F12) ----------------

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
}