package com.mealton.pantry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mealton.facade.MealtonFacade;
import com.mealton.persistence.UserData;
import com.mealton.persistence.UserDataMapper;
import com.mealton.shopping.ShoppingItem;

/**
 * Pantry categories, undo, duplicate notice and pinning on the shopping list.
 */
class CategoryLayoutTest {
	private MealtonFacade facade;

	@BeforeEach
	void setUp() {
		facade = new MealtonFacade();
	}

	@Test
	void itemsLandInSuggestedGroupsByCategoryOrName() {
		CategoryLayout l = new CategoryLayout();
		assertEquals("Dairy & eggs", l.groupOf("Milk", "Dairy"));
		assertEquals("Produce", l.groupOf("Baby spinach", "Uncategorized"));
		assertEquals("Staples", l.groupOf("Rice", "Pantry staples"));
		assertEquals("Meat & fish", l.groupOf("Chicken thighs", "Meat & Fish"));
		assertEquals("Produce", l.groupOf("Eggplant", ""));
		assertEquals("Uncategorized", l.groupOf("Mystery", null));
	}

	@Test
	void switchingSuggestedOffSendsItemsToUncategorizedUnlessCustom() throws Exception {
		CategoryLayout l = new CategoryLayout();
		l.addCustom("Baking");
		l.setSuggestedOn(false);
		assertEquals("Uncategorized", l.groupOf("Milk", "Dairy"));
		assertEquals("Baking", l.groupOf("Flour", "Baking"));
		assertEquals(List.of("Baking", "Uncategorized"), l.visibleGroups(false, false));
	}

	@Test
	void customCategoryNamesAreValidated() {
		CategoryLayout l = new CategoryLayout();
		assertEquals("Type a name for the category first.",
				assertThrows(IllegalArgumentException.class, () -> l.addCustom("  ")).getMessage());
		l.addCustom("Snacks");
		assertEquals("“snacks” already exists.",
				assertThrows(IllegalArgumentException.class, () -> l.addCustom("snacks")).getMessage());
		assertThrows(IllegalArgumentException.class, () -> l.addCustom("produce"));
	}

	@Test
	void reorderRenameAndColour() {
		CategoryLayout l = new CategoryLayout();
		List<String> showing = l.visibleGroups(true, false);
		l.moveGroup("Dairy & eggs", -1, showing);
		List<String> after = l.visibleGroups(true, false);
		assertTrue(after.indexOf("Dairy & eggs") < after.indexOf("Produce"));
		l.rename("Produce", "Fruit & veg");
		assertEquals("Fruit & veg", l.labelOf("Produce"));
		l.rename("Produce", "");
		assertEquals("Produce", l.labelOf("Produce"));
		l.setColor("Staples", "#112233");
		assertEquals("#112233", l.colorOf("Staples"));
		l.setColor("Staples", null);
		assertFalse(l.colorOf("Staples").equals("#112233"));
	}

	@Test
	void pantryUndoPutsEverythingBack() {
		PantryItem milk = facade.addPantryItem(new PantryItem("Milk", 1, "l", "Dairy", null));
		assertTrue(facade.canUndoPantry());
		facade.removePantryItems(List.of(milk.getId()));
		assertEquals(0, facade.getPantryItems().size());
		assertTrue(facade.undoPantry());
		assertEquals(1, facade.getPantryItems().size());
		assertTrue(facade.undoPantry());
		assertEquals(0, facade.getPantryItems().size());
		assertFalse(facade.undoPantry());
	}

	@Test
	void duplicateCanBeMergedOrKeptSeparate() {
		facade.addPantryItem(new PantryItem("Baby spinach", 200, "g", "Produce", null));
		assertEquals(200.0, facade.findPantryDuplicate("baby spinach", "g").orElseThrow().getQuantity(), 0.001);
		facade.addPantryItemSeparate(new PantryItem("Baby spinach", 100, "g", "Produce", null));
		assertEquals(2, facade.getPantryItems().size());
		facade.addPantryItem(new PantryItem("Baby spinach", 50, "g", "Produce", null));
		assertEquals(2, facade.getPantryItems().size());
	}

	@Test
	void pinningIsUndoable() {
		ShoppingItem i = facade.addShoppingItem(new ShoppingItem("Oats", 1, "kg", "Staples"));
		facade.toggleShoppingPin(i.getId());
		assertTrue(facade.getShoppingItems().get(0).isPinned());
		facade.undo();
		assertFalse(facade.getShoppingItems().get(0).isPinned());
	}

	@Test
	void layoutsAndPinsSurviveSaveAndLoad() {
		facade.getCategoryLayout("pantry").addCustom("Baking");
		facade.getCategoryLayout("pantry").setColor("Baking", "#445566");
		facade.getCategoryLayout("list").setSuggestedOn(false);
		ShoppingItem i = facade.addShoppingItem(new ShoppingItem("Oats", 1, "kg", "Staples"));
		i.setSource("Overnight oats");
		facade.toggleShoppingPin(i.getId());

		UserData data = UserDataMapper.capture(facade);
		MealtonFacade again = new MealtonFacade();
		UserDataMapper.restore(again, data);

		assertEquals(List.of("Baking"), again.getCategoryLayout("pantry").getCustom());
		assertEquals("#445566", again.getCategoryLayout("pantry").colorOf("Baking"));
		assertFalse(again.getCategoryLayout("list").isSuggestedOn());
		assertTrue(again.getShoppingItems().get(0).isPinned());
		assertEquals("Overnight oats", again.getShoppingItems().get(0).getSource());
		assertFalse(again.canUndoPantry());
	}

	@Test
	void itemsAndGroupsCanBeReorderedByDragging() {
		PantryItem a = facade.addPantryItem(new PantryItem("Apples", 1, "", "Produce", null));
		PantryItem b = facade.addPantryItem(new PantryItem("Butter", 1, "", "Dairy", null));
		PantryItem c = facade.addPantryItem(new PantryItem("Carrots", 1, "", "Produce", null));
		facade.movePantryItem(c.getId(), a.getId());
		assertEquals("Carrots", facade.getPantryItems().get(0).getName());
		facade.movePantryItem(c.getId(), -1);
		assertEquals("Carrots", facade.getPantryItems().get(2).getName());
		assertEquals(b.getId(), facade.getPantryItems().get(1).getId());
		assertTrue(facade.undoPantry());
		assertEquals("Carrots", facade.getPantryItems().get(0).getName());

		ShoppingItem x = facade.addShoppingItem(new ShoppingItem("X", 1, "", "Staples"));
		ShoppingItem y = facade.addShoppingItem(new ShoppingItem("Y", 1, "", "Staples"));
		facade.moveShoppingItem(y.getId(), x.getId());
		assertEquals("Y", facade.getShoppingItems().get(0).getName());

		CategoryLayout l = new CategoryLayout();
		List<String> showing = l.visibleGroups(true, false);
		l.moveGroupBefore("Staples", "Produce", showing);
		List<String> after = l.visibleGroups(true, false);
		assertTrue(after.indexOf("Staples") < after.indexOf("Produce"));
	}
}