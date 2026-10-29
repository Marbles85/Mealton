package com.mealton.shopping;

import java.util.Optional;

import com.mealton.command.Command;

/**
 * Adds an item, or merges it into an identical unticked item. Can be undone.
 */
public class AddShoppingItemCommand implements Command {

	private final ShoppingList list;
	private final ShoppingItem item;
	private ShoppingItem mergedInto; // not null if this command merged instead of adding

	public AddShoppingItemCommand(ShoppingList list, ShoppingItem item) {
		this.list = list;
		this.item = item;
	}

	@Override
	public void execute() {
		Optional<ShoppingItem> match = list.findPendingMatch(item.getName(), item.getUnit());
		if (match.isPresent()) {
			mergedInto = match.get();
			mergedInto.addQuantity(item.getQuantity()); // merge: add to the existing line
		} else {
			mergedInto = null;
			list.add(item); // no match: add a new line
		}
	}

	@Override
	public void undo() {
		if (mergedInto != null) {
			mergedInto.addQuantity(-item.getQuantity()); // take the merged amount back out
		} else {
			list.removeById(item.getId()); // remove the line we added
		}
	}

	/** The item that now represents this addition on the list. */
	public ShoppingItem getResult() {
		return mergedInto != null ? mergedInto : item;
	}
}