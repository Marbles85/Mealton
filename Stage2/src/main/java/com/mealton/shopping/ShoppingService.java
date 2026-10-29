package com.mealton.shopping;

import java.util.Collection;
import java.util.List;

import com.mealton.command.CommandHistory;

/**
 * Application logic for the shopping list (feature F12). Every change is
 * wrapped in a Command and run through the CommandHistory, which is what gives
 * us undo and redo.
 */
public class ShoppingService {

	private final ShoppingList list = new ShoppingList();
	private final CommandHistory history = new CommandHistory();

	/**
	 * Adds an item, merging with an identical unticked one. Returns the item on the
	 * list.
	 */
	public ShoppingItem addItem(ShoppingItem item) {
		AddShoppingItemCommand cmd = new AddShoppingItemCommand(list, item);
		history.execute(cmd);
		return cmd.getResult();
	}

	/**
	 * Ticks or un-ticks an item. Throws IllegalArgumentException if the id is
	 * unknown.
	 */
	public void toggle(long id) {
		if (list.findById(id).isEmpty()) {
			throw new IllegalArgumentException("No shopping item with id " + id);
		}
		history.execute(new ToggleItemCommand(list, id));
	}

	/**
	 * Deletes items as ONE undoable step. Returns how many existing items were
	 * deleted.
	 */
	public int deleteItems(Collection<Long> ids) {
		List<Long> existing = ids.stream().filter(id -> list.findById(id).isPresent()).distinct().toList();
		if (existing.isEmpty()) {
			return 0; // nothing selected: ignored (the "alternative flow" from Stage 1)
		}
		history.execute(new DeleteItemsCommand(list, existing));
		return existing.size();
	}

	/**
	 * Deletes every ticked item as ONE undoable step. Returns how many were
	 * removed.
	 */
	public int clearChecked() {
		List<Long> ids = list.checkedItems().stream().map(ShoppingItem::getId).toList();
		return deleteItems(ids);
	}

	public boolean undo() {
		return history.undo();
	}

	public boolean redo() {
		return history.redo();
	}

	public boolean canUndo() {
		return history.canUndo();
	}

	public boolean canRedo() {
		return history.canRedo();
	}

	public List<ShoppingItem> getItems() {
		return list.getItems();
	}
}