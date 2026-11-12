package com.mealton.shopping;

import java.util.Collection;
import java.util.List;

import com.mealton.command.CommandHistory;

/**
 * Application logic for the shopping list. Every change is wrapped in a Command
 * and run through the {@link CommandHistory}, which gives undo/redo (Stage 1:
 * ShoppingService, F12).
 */
public class ShoppingService {
	private final ShoppingList list = new ShoppingList();
	private final CommandHistory history = new CommandHistory();

	/**
	 * Adds an item (merging with an identical pending one). @return the item on the
	 * list
	 */
	public ShoppingItem addItem(ShoppingItem item) {
		AddShoppingItemCommand cmd = new AddShoppingItemCommand(list, item);
		history.execute(cmd);
		return cmd.getResult();
	}

	/**
	 * Ticks or un-ticks an item. @throws IllegalArgumentException if the id is
	 * unknown
	 */
	public void toggle(long id) {
		if (list.findById(id).isEmpty()) {
			throw new IllegalArgumentException("No shopping item with id " + id);
		}
		history.execute(new ToggleItemCommand(list, id));
	}

	/**
	 * Deletes items as a single undoable step. @return how many existing items were
	 * deleted
	 */
	public int deleteItems(Collection<Long> ids) {
		List<Long> existing = ids.stream().filter(id -> list.findById(id).isPresent()).distinct().toList();
		if (existing.isEmpty()) {
			return 0; // nothing selected -> ignored (Stage 1 alternative flow)
		}
		history.execute(new DeleteItemsCommand(list, existing));
		return existing.size();
	}

	/**
	 * Deletes all ticked items as a single undoable step. @return how many were
	 * removed
	 */
	public int clearChecked() {
		List<Long> ids = list.checkedItems().stream().map(ShoppingItem::getId).toList();
		return deleteItems(ids);
	}

	/** Forgets the undo/redo history, so loading saved items cannot be "undone". */
	public void forgetHistory() {
		history.clear();
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