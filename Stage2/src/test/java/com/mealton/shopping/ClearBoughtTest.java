package com.mealton.shopping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.mealton.facade.MealtonFacade;

/** Ticked items that are cleared were bought, so they land in the pantry. */
class ClearBoughtTest {
	@Test
	void clearedTickedItemsAreAddedToThePantry() {
		MealtonFacade facade = new MealtonFacade();
		var milk = facade.addShoppingItem(new ShoppingItem("milk", 2, "l", "Dairy & eggs"));
		facade.addShoppingItem(new ShoppingItem("flour", 1, "kg", "Staples"));
		facade.toggleShoppingItem(milk.getId());
		assertEquals(1, facade.clearChecked());
		assertTrue(facade.getPantryItems().stream().anyMatch(p -> p.getName().equals("milk") && p.getQuantity() == 2));
		assertTrue(facade.getPantryItems().stream().noneMatch(p -> p.getName().equals("flour")));
		assertEquals(1, facade.getShoppingItems().size());
	}
}