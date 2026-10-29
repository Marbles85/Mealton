package com.mealton.shopping;

import com.mealton.command.Command;

/**
 * Ticks or un-ticks one item. Toggling twice gets back to the start, so undo is
 * the same as execute.
 */
public class ToggleItemCommand implements Command {

	private final ShoppingList list;
	private final long id;

	public ToggleItemCommand(ShoppingList list, long id) {
		this.list = list;
		this.id = id;
	}

	@Override
	public void execute() {
		list.findById(id).ifPresent(ShoppingItem::toggle);
	}

	@Override
	public void undo() {
		execute(); // toggling again switches it back
	}
}