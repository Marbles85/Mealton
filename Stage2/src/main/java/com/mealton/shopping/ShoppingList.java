package com.mealton.shopping;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The shopping list. In the Command pattern it is the receiver the commands act
 * on.
 */
public class ShoppingList {

	private final List<ShoppingItem> items = new ArrayList<>();
	private long nextId = 1;

	/** Adds an item. It gets an id the first time it is added. */
	public ShoppingItem add(ShoppingItem item) {
		if (item.getId() == 0) {
			item.setId(nextId++);
		}
		items.add(item);
		return item;
	}

	/**
	 * Removes the items with these ids. Unknown ids are ignored.
	 *
	 * @return the items that were removed
	 */
	public List<ShoppingItem> remove(Collection<Long> ids) {
		Set<Long> wanted = new HashSet<>(ids);
		List<ShoppingItem> removed = new ArrayList<>();
		items.removeIf(i -> {
			if (wanted.contains(i.getId())) {
				removed.add(i);
				return true;
			}
			return false;
		});
		return removed;
	}

	public boolean removeById(long id) {
		return !remove(List.of(id)).isEmpty();
	}

	public Optional<ShoppingItem> findById(long id) {
		return items.stream().filter(i -> i.getId() == id).findFirst();
	}

	/**
	 * An unticked item with the same name and unit (ignoring case). Used to merge
	 * duplicates.
	 */
	public Optional<ShoppingItem> findPendingMatch(String name, String unit) {
		return items.stream()
				.filter(i -> !i.isChecked() && i.getName().equalsIgnoreCase(name) && i.getUnit().equalsIgnoreCase(unit))
				.findFirst();
	}

	public List<ShoppingItem> checkedItems() {
		return items.stream().filter(ShoppingItem::isChecked).toList();
	}

	/** A copy of the list, so callers cannot change our items by accident. */
	public List<ShoppingItem> getItems() {
		return new ArrayList<>(items);
	}

	/**
	 * A copy of the current order. Delete commands keep it so undo can put
	 * everything back.
	 */
	public List<ShoppingItem> snapshot() {
		return new ArrayList<>(items);
	}

	public void restore(List<ShoppingItem> snapshot) {
		items.clear();
		items.addAll(snapshot);
	}
}