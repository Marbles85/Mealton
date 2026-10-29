package com.mealton.shopping;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.mealton.command.Command;

/**
 * Deletes several items as ONE undoable step (multi-select delete and "Clear
 * checked").
 */
public class DeleteItemsCommand implements Command {

	private final ShoppingList list;
	private final List<Long> ids; // which items to delete
	private List<ShoppingItem> before; // the whole list, saved just before deleting

	public DeleteItemsCommand(ShoppingList list, Collection<Long> ids) {
		this.list = list;
		this.ids = new ArrayList<>(ids); // our own copy, so later changes to the caller's list don't matter
	}

	@Override
	public void execute() {
		before = list.snapshot(); // remember how the list looked
		list.remove(ids);
	}

	@Override
	public void undo() {
		list.restore(before); // put everything back in its original order
	}
}