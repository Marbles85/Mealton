package com.mealton.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mealton.facade.MealtonFacade;
import com.mealton.pantry.PantryItem;
import com.mealton.pantry.PantryObserver;
import com.mealton.shopping.ShoppingItem;
import com.mealton.util.Formats;

/**
 * Command-line interface. It only talks to MealtonFacade (the same entry point
 * as the GUI), and it is a PantryObserver, so expiry alerts are printed as they
 * happen. Data lives in memory for now. Saving to a database comes later.
 */
public final class CliApp implements PantryObserver {

	private final MealtonFacade facade;
	private final BufferedReader in;
	private final PrintStream out;

	public CliApp(MealtonFacade facade, InputStream in, PrintStream out) {
		this.facade = facade;
		this.in = new BufferedReader(new InputStreamReader(in));
		this.out = out;
		facade.attachPantryObserver(this);
	}

	/** Read-eval-print loop: read a line, run it, print the result, repeat. */
	public void run() {
		out.println("Mealton CLI. Type 'help' for commands, 'exit' to quit.");
		try {
			while (true) {
				out.print("mealton> ");
				out.flush();
				String line = in.readLine();
				if (line == null || !execute(line)) {
					break;
				}
			}
		} catch (IOException e) {
			out.println("Input error: " + e.getMessage());
		}
	}

	/** Runs one command line. Returns false when the user asked to exit. */
	public boolean execute(String line) {
		List<String> t = tokenize(line);
		if (t.isEmpty()) {
			return true;
		}
		try {
			switch (t.get(0).toLowerCase()) {
			case "help" -> help();
			case "exit", "quit" -> {
				return false;
			}
			case "pantry" -> pantry(t.subList(1, t.size()));
			case "shop" -> shop(t.subList(1, t.size()));
			default -> out.println("Unknown command '" + t.get(0) + "'. Type 'help'.");
			}
		} catch (IllegalArgumentException | DateTimeException e) {
			out.println("Error: " + e.getMessage());
		}
		return true;
	}

	// ---------------- pantry ----------------

	private void pantry(List<String> a) {
		if (a.isEmpty()) {
			throw new IllegalArgumentException("Usage: pantry add|list|expiring|check|remove");
		}
		switch (a.get(0).toLowerCase()) {
		case "add" -> {
			Args p = Args.parse(a.subList(1, a.size()));
			if (p.positional.size() < 2) {
				throw new IllegalArgumentException(
						"Usage: pantry add <name> <qty> [unit] [--expires yyyy-mm-dd] [--category c]");
			}
			double qty = Formats.parseQuantity(p.positional.get(1));
			String unit = p.positional.size() > 2 ? p.positional.get(2) : "";
			LocalDate expires = p.flags.containsKey("expires") ? LocalDate.parse(p.flags.get("expires")) : null;
			PantryItem item = facade
					.addPantryItem(new PantryItem(p.positional.get(0), qty, unit, p.flags.get("category"), expires));
			out.println("Pantry: " + describe(item));
		}
		case "list" -> {
			List<PantryItem> items = facade.getPantryItems();
			if (items.isEmpty()) {
				out.println("The pantry is empty.");
			}
			items.forEach(i -> out.println("  " + describe(i)));
		}
		case "expiring" -> {
			List<PantryItem> items = facade.getExpiringItems();
			if (items.isEmpty()) {
				out.println("Nothing is close to expiring.");
			}
			items.forEach(i -> out.println("  " + describe(i)));
		}
		case "check" -> {
			if (facade.getExpiringItems().isEmpty()) {
				out.println("Nothing is close to expiring.");
			} else {
				facade.checkFreshness(); // the observers (this CLI) print the alerts
			}
		}
		case "remove" -> {
			int n = facade.removePantryItems(ids(a, 1));
			out.println("Removed " + n + " pantry item(s).");
		}
		default -> throw new IllegalArgumentException("Unknown pantry command '" + a.get(0) + "'.");
		}
	}

	// ---------------- shopping list ----------------

	private void shop(List<String> a) {
		if (a.isEmpty()) {
			throw new IllegalArgumentException("Usage: shop add|list|check|delete|clear-checked|undo|redo");
		}
		switch (a.get(0).toLowerCase()) {
		case "add" -> {
			Args p = Args.parse(a.subList(1, a.size()));
			if (p.positional.isEmpty()) {
				throw new IllegalArgumentException("Usage: shop add <name> [qty] [unit] [--category c]");
			}
			double qty = p.positional.size() > 1 ? Formats.parseQuantity(p.positional.get(1)) : 1;
			String unit = p.positional.size() > 2 ? p.positional.get(2) : "";
			ShoppingItem item = facade
					.addShoppingItem(new ShoppingItem(p.positional.get(0), qty, unit, p.flags.get("category")));
			out.println("List: " + describe(item));
		}
		case "list" -> {
			List<ShoppingItem> items = facade.getShoppingItems();
			if (items.isEmpty()) {
				out.println("The shopping list is empty.");
			}
			items.forEach(i -> out.println("  " + describe(i)));
		}
		case "check" -> {
			for (long id : ids(a, 1)) {
				facade.toggleShoppingItem(id);
			}
			out.println("Toggled.");
		}
		case "delete" -> out.println(
				"Deleted " + facade.deleteShoppingItems(ids(a, 1)) + " item(s). ('shop undo' brings them back)");
		case "clear-checked" ->
			out.println("Cleared " + facade.clearChecked() + " ticked item(s). ('shop undo' brings them back)");
		case "undo" -> out.println(facade.undo() ? "Undone." : "Nothing to undo.");
		case "redo" -> out.println(facade.redo() ? "Redone." : "Nothing to redo.");
		default -> throw new IllegalArgumentException("Unknown shop command '" + a.get(0) + "'.");
		}
	}

	// ---------------- Observer callbacks ----------------

	@Override
	public void onItemExpiring(PantryItem item) {
		out.println("! " + item.getName() + " " + expiryText(item));
	}

	@Override
	public void onPantryChanged() {
		// The CLI prints its own confirmation after each command, so nothing to do
		// here.
	}

	// ---------------- formatting and parsing helpers ----------------

	private String describe(PantryItem i) {
		return String.format("#%d %s %s%s [%s] %s", i.getId(), i.getName(), Formats.number(i.getQuantity()),
				i.getUnit().isEmpty() ? "" : " " + i.getUnit(), i.getCategory(), expiryText(i));
	}

	private String describe(ShoppingItem i) {
		return String.format("%s #%d %s %s%s [%s]", i.isChecked() ? "[x]" : "[ ]", i.getId(), i.getName(),
				Formats.number(i.getQuantity()), i.getUnit().isEmpty() ? "" : " " + i.getUnit(), i.getCategory());
	}

	private String expiryText(PantryItem i) {
		return Formats.expiryText(i, facade.today());
	}

	private static List<Long> ids(List<String> a, int from) {
		List<Long> ids = new ArrayList<>();
		for (int i = from; i < a.size(); i++) {
			try {
				ids.add(Long.parseLong(a.get(i).replace("#", "")));
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("'" + a.get(i) + "' is not a valid id.");
			}
		}
		if (ids.isEmpty()) {
			throw new IllegalArgumentException("Give at least one id.");
		}
		return ids;
	}

	/** Splits a line into words. Double quotes keep words together. */
	static List<String> tokenize(String line) {
		List<String> tokens = new ArrayList<>();
		StringBuilder cur = new StringBuilder();
		boolean inQuotes = false;
		boolean has = false;
		for (char c : line.toCharArray()) {
			if (c == '"') {
				inQuotes = !inQuotes;
				has = true;
			} else if (Character.isWhitespace(c) && !inQuotes) {
				if (has) {
					tokens.add(cur.toString());
					cur.setLength(0);
					has = false;
				}
			} else {
				cur.append(c);
				has = true;
			}
		}
		if (has) {
			tokens.add(cur.toString());
		}
		return tokens;
	}

	/** Plain words plus --flag value pairs. */
	private static final class Args {
		final List<String> positional = new ArrayList<>();
		final Map<String, String> flags = new HashMap<>();

		static Args parse(List<String> tokens) {
			Args a = new Args();
			for (int i = 0; i < tokens.size(); i++) {
				String t = tokens.get(i);
				if (t.startsWith("--")) {
					if (i + 1 >= tokens.size()) {
						throw new IllegalArgumentException("Flag " + t + " needs a value.");
					}
					a.flags.put(t.substring(2).toLowerCase(), tokens.get(++i));
				} else {
					a.positional.add(t);
				}
			}
			return a;
		}
	}

	private void help() {
		out.println("""
				Commands:
				  pantry add <name> <qty> [unit] [--expires yyyy-mm-dd] [--category c]
				  pantry list | expiring | check | remove <id...>
				  shop add <name> [qty] [unit] [--category c]
				  shop list | check <id...> | delete <id...> | clear-checked | undo | redo
				  help | exit
				Tip: use "double quotes" for names with spaces.""");
	}
}