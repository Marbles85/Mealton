package com.mealton.agent.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mealton.agent.Tool;
import com.mealton.agent.ToolResult;
import com.mealton.llm.ToolSpec;
import com.mealton.shopping.ShoppingItem;
import com.mealton.shopping.ShoppingService;
import com.mealton.util.Formats;

/**
 * Lets the agent work with the user's shopping list. Actions: "list", "add",
 * "tick" (tick or un-tick an item), "clear_checked" and "undo". Everything it
 * changes can be undone, because it goes through ShoppingService's command
 * history.
 */
public class ShoppingListTool implements Tool {

	private final ShoppingService shopping;

	public ShoppingListTool(ShoppingService shopping) {
		this.shopping = shopping;
	}

	@Override
	public String name() {
		return "shopping_list";
	}

	@Override
	public ToolSpec schema() {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("action", "Required. One of: list, add, tick, clear_checked, undo.");
		params.put("name", "For add: the item name, for example 'Eggs'.");
		params.put("quantity", "For add: optional positive number. Defaults to 1.");
		params.put("unit", "For add: optional unit, for example 'kg'.");
		params.put("category", "For add: optional category, for example 'Dairy'.");
		params.put("id", "For tick: the item's id number, as shown by 'list'.");
		return new ToolSpec(name(),
				"Reads or changes the user's shopping list. "
						+ "'list' shows all items with their ids, 'add' adds an item "
						+ "(it merges with an identical unticked item), 'tick' ticks or un-ticks one item, "
						+ "'clear_checked' removes all ticked items, and 'undo' reverses the last change.",
				params);
	}

	@Override
	public ToolResult execute(Map<String, String> args) {
		String action = args.getOrDefault("action", "").trim().toLowerCase();
		try {
			switch (action) {
			case "list":
				return ToolResult.ok(describeAll(shopping.getItems()));
			case "add":
				return add(args);
			case "tick":
				return tick(args);
			case "clear_checked":
				int cleared = shopping.clearChecked();
				return ToolResult.ok("Cleared " + cleared + " ticked item(s). Action 'undo' brings them back.");
			case "undo":
				return ToolResult.ok(shopping.undo() ? "Undone." : "Nothing to undo.");
			default:
				return ToolResult.error("Unknown action '" + action + "'. Use list, add, tick, clear_checked or undo.");
			}
		} catch (IllegalArgumentException e) {
			return ToolResult.error(e.getMessage());
		}
	}

	private ToolResult add(Map<String, String> args) {
		double quantity = Formats.parseQuantityOrDefault(args.get("quantity"), 1);
		ShoppingItem item = new ShoppingItem(args.get("name"), quantity, args.get("unit"), args.get("category"));
		ShoppingItem stored = shopping.addItem(item);
		return ToolResult.ok("Added to shopping list: " + describe(stored));
	}

	private ToolResult tick(Map<String, String> args) {
		String text = args.get("id");
		if (text == null || text.isBlank()) {
			return ToolResult.error("Please give the id of the item to tick.");
		}
		long id;
		try {
			id = Long.parseLong(text.trim().replace("#", ""));
		} catch (NumberFormatException e) {
			return ToolResult.error("'" + text + "' is not a valid id.");
		}
		shopping.toggle(id);
		return ToolResult.ok("Toggled item #" + id + ".");
	}

	private String describeAll(List<ShoppingItem> items) {
		if (items.isEmpty()) {
			return "The shopping list is empty.";
		}
		StringBuilder sb = new StringBuilder("Shopping list (" + items.size() + " items):");
		for (ShoppingItem item : items) {
			sb.append("\n- ").append(describe(item));
		}
		return sb.toString();
	}

	private String describe(ShoppingItem item) {
		return (item.isChecked() ? "[x] " : "[ ] ") + "#" + item.getId() + " " + item.getName() + " "
				+ Formats.quantityWithUnit(item.getQuantity(), item.getUnit()) + " [" + item.getCategory() + "]";
	}
}