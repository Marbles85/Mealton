package com.mealton.agent.tools;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mealton.agent.Tool;
import com.mealton.agent.ToolResult;
import com.mealton.llm.ToolSpec;
import com.mealton.pantry.PantryInventory;
import com.mealton.pantry.PantryItem;
import com.mealton.util.Formats;

/**
 * Lets the agent look at and add to the user's pantry. Actions: "list"
 * (everything), "expiring" (items close to spoiling), "add" (a new ingredient).
 */
public class PantryTool implements Tool {

	private final PantryInventory pantry;

	public PantryTool(PantryInventory pantry) {
		this.pantry = pantry;
	}

	@Override
	public String name() {
		return "pantry";
	}

	@Override
	public ToolSpec schema() {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("action", "Required. One of: list, expiring, add.");
		params.put("name", "For add: the ingredient name, for example 'Milk'.");
		params.put("quantity", "For add: a positive number, for example '2'.");
		params.put("unit", "For add: optional unit, for example 'L' or 'g'.");
		params.put("expires", "For add: optional expiry date as yyyy-mm-dd.");
		params.put("category", "For add: optional category, for example 'Dairy'.");
		return new ToolSpec(name(),
				"Reads or changes the user's pantry (the ingredients they have at home). "
						+ "Use 'list' to see everything, 'expiring' to see what is about to spoil, "
						+ "and 'add' to put a new ingredient in.",
				params);
	}

	@Override
	public ToolResult execute(Map<String, String> args) {
		String action = args.getOrDefault("action", "").trim().toLowerCase();
		try {
			switch (action) {
			case "list":
				return ToolResult.ok(describeAll("Pantry", pantry.getItems()));
			case "expiring":
				return ToolResult.ok(describeAll("Expiring soon", pantry.getExpiringItems()));
			case "add":
				return add(args);
			default:
				return ToolResult.error("Unknown action '" + action + "'. Use list, expiring or add.");
			}
		} catch (DateTimeException e) {
			return ToolResult.error("The date must look like yyyy-mm-dd, for example 2026-11-02.");
		} catch (IllegalArgumentException e) {
			return ToolResult.error(e.getMessage());
		}
	}

	private ToolResult add(Map<String, String> args) {
		double quantity = Formats.parseQuantity(args.get("quantity"));
		LocalDate expires = blank(args.get("expires")) ? null : LocalDate.parse(args.get("expires").trim());
		PantryItem item = new PantryItem(args.get("name"), quantity, args.get("unit"), args.get("category"), expires);
		PantryItem stored = pantry.addItem(item);
		return ToolResult.ok("Added to pantry: " + describe(stored));
	}

	private String describeAll(String title, List<PantryItem> items) {
		if (items.isEmpty()) {
			return title + ": no items.";
		}
		StringBuilder sb = new StringBuilder(title + " (" + items.size() + " items):");
		for (PantryItem item : items) {
			sb.append("\n- ").append(describe(item));
		}
		return sb.toString();
	}

	private String describe(PantryItem item) {
		return item.getName() + " " + Formats.quantityWithUnit(item.getQuantity(), item.getUnit()) + " ["
				+ item.getCategory() + "] " + Formats.expiryText(item, pantry.today());
	}

	private static boolean blank(String s) {
		return s == null || s.isBlank();
	}
}