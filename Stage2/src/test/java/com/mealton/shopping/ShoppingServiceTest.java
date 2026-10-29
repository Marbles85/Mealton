package com.mealton.shopping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ShoppingServiceTest {

	private ShoppingService service;

	// Runs before every test, so each test starts with an empty list
	@BeforeEach
	void setUp() {
		service = new ShoppingService();
	}

	private ShoppingItem add(String name, double qty, String unit) {
		return service.addItem(new ShoppingItem(name, qty, unit, null));
	}

	private List<String> names() {
		return service.getItems().stream().map(ShoppingItem::getName).toList();
	}

	@Test
	void addedItemsGetIdsAndStartPending() {
		ShoppingItem milk = add("Milk", 2, "L");

		assertTrue(milk.getId() > 0);
		assertFalse(milk.isChecked());
		assertEquals(ItemState.STYLE_NORMAL, milk.getStyle());
	}

	@Test
	void identicalPendingItemsAreMergedAndUndoSplitsThemAgain() {
		ShoppingItem first = add("Milk", 2, "L");
		ShoppingItem second = add("milk", 1, "l");

		assertEquals(1, service.getItems().size());
		assertEquals(first.getId(), second.getId());
		assertEquals(3.0, first.getQuantity(), 0.0001);

		service.undo();
		assertEquals(2.0, first.getQuantity(), 0.0001);
		assertEquals(1, service.getItems().size());
	}

	@Test
	void tickedItemsAreNotMergedIntoNewAdditions() {
		ShoppingItem milk = add("Milk", 2, "L");
		service.toggle(milk.getId());

		add("Milk", 1, "L");

		assertEquals(2, service.getItems().size());
	}

	@Test
	void togglingSwitchesStateAndStyle() {
		ShoppingItem eggs = add("Eggs", 12, "");

		service.toggle(eggs.getId());
		assertTrue(eggs.isChecked());
		assertEquals(ItemState.STYLE_CHECKED, eggs.getStyle());

		service.toggle(eggs.getId());
		assertFalse(eggs.isChecked());
		assertEquals(ItemState.STYLE_NORMAL, eggs.getStyle());
	}

	@Test
	void togglingUnknownIdIsAnError() {
		assertThrows(IllegalArgumentException.class, () -> service.toggle(42));
	}

	@Test
	void undoOfToggleRestoresPreviousState() {
		ShoppingItem eggs = add("Eggs", 12, "");
		service.toggle(eggs.getId());

		service.undo();

		assertFalse(eggs.isChecked());
	}

	@Test
	void clearCheckedDeletesOnlyTickedItems() {
		ShoppingItem a = add("Apples", 3, "");
		add("Bread", 1, "");
		ShoppingItem c = add("Cheese", 1, "");
		service.toggle(a.getId());
		service.toggle(c.getId());

		int cleared = service.clearChecked();

		assertEquals(2, cleared);
		assertEquals(List.of("Bread"), names());
	}

	@Test
	void clearCheckedIsOneUndoStepAndRestoresOriginalOrder() {
		ShoppingItem a = add("Apples", 3, "");
		add("Bread", 1, "");
		ShoppingItem c = add("Cheese", 1, "");
		service.toggle(a.getId());
		service.toggle(c.getId());
		service.clearChecked();

		service.undo(); // ONE undo brings both back

		assertEquals(List.of("Apples", "Bread", "Cheese"), names());
		assertTrue(service.getItems().get(0).isChecked()); // ticked state preserved
	}

	@Test
	void deleteIgnoresUnknownIdsAndDoesNotCreateUndoStep() {
		assertEquals(0, service.deleteItems(List.of(7L, 8L)));
		assertFalse(service.canUndo());
	}

	@Test
	void redoReappliesAnUndoneDelete() {
		ShoppingItem a = add("Apples", 3, "");
		service.deleteItems(List.of(a.getId()));
		assertEquals(0, service.getItems().size());

		service.undo();
		assertEquals(1, service.getItems().size());

		service.redo();
		assertEquals(0, service.getItems().size());
	}

	@Test
	void newActionAfterUndoClearsRedo() {
		add("Apples", 3, "");
		service.undo();
		assertTrue(service.canRedo());

		add("Bread", 1, "");

		assertFalse(service.canRedo());
	}

	@Test
	void invalidItemsAreRejected() {
		assertThrows(IllegalArgumentException.class, () -> new ShoppingItem("", 1, "", null));
		assertThrows(IllegalArgumentException.class, () -> new ShoppingItem("Milk", 0, "L", null));
	}
}