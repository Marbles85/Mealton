package com.mealton.shopping;

import com.mealton.command.Command;

/**
 * Pins an item to the top of the shopping list, or unpins it. Undo is the same
 * as execute.
 */
public class TogglePinCommand implements Command {

	private final ShoppingList list;
	private final long id;

	public TogglePinCommand(ShoppingList list, long id) {
		this.list = list;
		this.id = id;
	}

	@Override
	public void execute() {
		list.findById(id).ifPresent(i -> i.setPinned(!i.isPinned()));
	}

	@Override
	public void undo() {
		execute();
	}
}