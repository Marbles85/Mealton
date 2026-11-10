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
import java.util.stream.Collectors;

import com.mealton.agent.AgentReply;
import com.mealton.facade.MealtonFacade;
import com.mealton.pantry.PantryItem;
import com.mealton.pantry.PantryObserver;
import com.mealton.recipe.Recipe;
import com.mealton.recipe.RecipeIngredient;
import com.mealton.recipe.RecipeMatch;
import com.mealton.recipe.RecipeParser;
import com.mealton.shopping.ShoppingItem;
import com.mealton.util.Formats;

/**
 * Command-line interface. It only talks to {@link MealtonFacade} (same entry
 * point as the GUI) and it is a {@link PantryObserver}, so expiry alerts are
 * printed as they happen. Interactive for now: state lives in memory until
 * persistence is added.
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

	/** Read-eval-print loop. */
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

	/** Runs one command line. @return false when the user asked to exit */
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
			case "recipe" -> recipe(t.subList(1, t.size()));
			case "profile" -> profile(t.subList(1, t.size()));
			case "chat" -> chat(line);
			case "convert" -> convert(t.subList(1, t.size()));
			case "scale" -> scale(t.subList(1, t.size()));
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
				facade.checkFreshness(); // observers (this CLI) print the alerts
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

	// ---------------- recipes (F13) ----------------

	private void recipe(List<String> a) {
		if (a.isEmpty()) {
			throw new IllegalArgumentException(
					"Usage: recipe add|list|top|show|rate|scale|suggest|check|missing|delete");
		}
		switch (a.get(0).toLowerCase()) {
		case "add" -> {
			if (a.size() < 4 || a.size() > 5) {
				throw new IllegalArgumentException(
						"Usage: recipe add \"<title>\" <servings> \"<ingredients>\" [\"<steps>\"]\n"
								+ "  e.g. recipe add \"Pancakes\" 2 \"200 g flour; 2 eggs; 0.3 l milk\" \"Mix|Fry\"");
			}
			Recipe recipe = new Recipe(a.get(1), parseWhole(a.get(2), "Servings"),
					RecipeParser.parseIngredients(a.get(3)), RecipeParser.parseSteps(a.size() == 5 ? a.get(4) : ""));
			Recipe saved = facade.saveRecipe(recipe);
			out.println("Saved recipe #" + saved.getId() + ": " + saved);
		}
		case "list" -> printRecipes(facade.getRecipes(), "You have no saved recipes yet.");
		case "top" -> printRecipes(facade.getTopRatedRecipes(), "No recipes have been rated yet.");
		case "show" -> printRecipe(facade.getRecipe(oneId(a, "recipe show <id>")));
		case "suggest" -> {
			if (a.size() > 2) {
				throw new IllegalArgumentException("Usage: recipe suggest [best|expiring]");
			}
			printMatches(facade.suggestRecipes(a.size() == 2 ? a.get(1) : "best"));
		}
		case "check" -> printMatch(facade.checkRecipe(oneId(a, "recipe check <id>")));
		case "missing" -> {
			long id = oneId(a, "recipe missing <id>");
			String title = facade.getRecipe(id).getTitle();
			List<RecipeIngredient> added = facade.addMissingToShoppingList(id);
			if (added.isEmpty()) {
				out.println("Nothing to add for '" + title + "': the pantry and shopping list already cover it.");
			} else {
				out.println("Added to the shopping list for '" + title + "':");
				added.forEach(i -> out.println("  + " + i));
			}
		}
		case "rate" -> {
			if (a.size() != 3) {
				throw new IllegalArgumentException("Usage: recipe rate <id> <stars 1-5>");
			}
			Recipe rated = facade.rateRecipe(parseId(a.get(1)), parseWhole(a.get(2), "Stars"));
			out.println("Rated '" + rated.getTitle() + "' " + rated.getRating() + "/5.");
		}
		case "scale" -> {
			if (a.size() != 3) {
				throw new IllegalArgumentException("Usage: recipe scale <id> <servings>");
			}
			printRecipe(facade.scaledRecipe(parseId(a.get(1)), parseWhole(a.get(2), "Servings")));
			out.println("(Scaled copy. The saved recipe is unchanged.)");
		}
		case "delete" -> {
			long id = oneId(a, "recipe delete <id>");
			String title = facade.getRecipe(id).getTitle();
			facade.deleteRecipe(id);
			out.println("Deleted recipe '" + title + "'.");
		}
		default -> throw new IllegalArgumentException("Unknown recipe command '" + a.get(0) + "'.");
		}
	}

	private void printRecipes(List<Recipe> recipes, String emptyMessage) {
		if (recipes.isEmpty()) {
			out.println(emptyMessage);
		}
		for (Recipe r : recipes) {
			out.println("  #" + r.getId() + " " + r + (r.isRated() ? "  " + r.getRating() + "/5" : "  not rated"));
		}
	}

	private void printMatches(List<RecipeMatch> matches) {
		if (matches.isEmpty()) {
			out.println("You have no saved recipes to compare with the pantry. Add one with 'recipe add'.");
		}
		matches.forEach(this::printMatch);
	}

	private void printMatch(RecipeMatch m) {
		int total = m.available().size() + m.missing().size();
		out.println("#" + m.recipe().getId() + " " + m.recipe().getTitle() + ": "
				+ (m.isCookable() ? "can be cooked now"
						: m.available().size() + " of " + total + " ingredients available")
				+ (m.expiringUsed() > 0 ? " (uses " + m.expiringUsed() + " ingredient(s) that will spoil soon)" : ""));
		if (!m.available().isEmpty()) {
			out.println("    have:    " + joinIngredients(m.available()));
		}
		if (!m.missing().isEmpty()) {
			out.println("    missing: " + joinIngredients(m.missing()));
		}
		for (String warning : m.warnings()) {
			out.println("    WARNING: " + warning);
		}
	}

	private static String joinIngredients(List<RecipeIngredient> ingredients) {
		return ingredients.stream().map(RecipeIngredient::toString).collect(Collectors.joining(", "));
	}

	private void printRecipe(Recipe r) {
		out.println(r.getTitle() + " (" + r.getServings() + " servings, "
				+ (r.isRated() ? r.getRating() + "/5 stars" : "not rated") + ")");
		out.println("Ingredients:");
		for (RecipeIngredient ingredient : r.getIngredients()) {
			out.println("  - " + ingredient);
		}
		if (!r.getSteps().isEmpty()) {
			out.println("Steps:");
			int number = 1;
			for (String step : r.getSteps()) {
				out.println("  " + number++ + ". " + step);
			}
		}
	}

	private static long oneId(List<String> a, String usage) {
		if (a.size() != 2) {
			throw new IllegalArgumentException("Usage: " + usage);
		}
		return parseId(a.get(1));
	}

	private static long parseId(String text) {
		try {
			return Long.parseLong(text.replace("#", ""));
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("'" + text + "' is not a valid id.");
		}
	}

	private static int parseWhole(String text, String what) {
		try {
			return Integer.parseInt(text);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(what + " must be a whole number, got '" + text + "'.");
		}
	}

	// ---------------- profile (F01) ----------------

	private void profile(List<String> a) {
		if (a.isEmpty() || a.get(0).equalsIgnoreCase("show")) {
			out.println(facade.getProfile().summary());
			return;
		}
		switch (a.get(0).toLowerCase()) {
		case "name" -> {
			requireSize(a, 2, "profile name <name>");
			out.println("Profile updated. " + facade.setProfileName(a.get(1)).summary());
		}
		case "diet" -> {
			requireSize(a, 2, "profile diet <anything|vegetarian|vegan|pescatarian|gluten-free|keto>");
			out.println("Profile updated. " + facade.setDiet(a.get(1)).summary());
		}
		case "allergy" -> {
			if (a.size() != 3) {
				throw new IllegalArgumentException("Usage: profile allergy add|remove <food>");
			}
			switch (a.get(1).toLowerCase()) {
			case "add" -> out.println("Profile updated. " + facade.addAllergy(a.get(2)).summary());
			case "remove" -> out.println("Profile updated. " + facade.removeAllergy(a.get(2)).summary());
			default -> throw new IllegalArgumentException("Usage: profile allergy add|remove <food>");
			}
		}
		case "calories" -> {
			requireSize(a, 2, "profile calories <kcal per day, or 0 for none>");
			out.println("Profile updated. " + facade.setCalorieGoal(parseWhole(a.get(1), "Calories")).summary());
		}
		case "macros" -> {
			requireSize(a, 4, "profile macros <protein g> <carbs g> <fat g>");
			out.println("Profile updated. " + facade.setMacroGoals(parseWhole(a.get(1), "Protein"),
					parseWhole(a.get(2), "Carbs"), parseWhole(a.get(3), "Fat")).summary());
		}
		case "reset" -> {
			facade.resetProfile();
			out.println("Profile cleared.");
		}
		default -> throw new IllegalArgumentException("Unknown profile command '" + a.get(0) + "'. Type 'help'.");
		}
	}

	private static void requireSize(List<String> a, int size, String usage) {
		if (a.size() != size) {
			throw new IllegalArgumentException("Usage: " + usage);
		}
	}

	// ---------------- unit conversion and scaling (F10) ----------------

	private void convert(List<String> args) {
		// accept the natural form "convert 1 cup to ml" as well as "convert 1 cup ml"
		List<String> a = new ArrayList<>(args);
		if (a.size() == 4 && (a.get(2).equalsIgnoreCase("to") || a.get(2).equalsIgnoreCase("in"))) {
			a.remove(2);
		}
		if (a.size() != 3) {
			throw new IllegalArgumentException("Usage: convert <amount> <from unit> <to unit>   e.g. convert 2 cup ml");
		}
		double amount = Formats.parseQuantity(a.get(0));
		double result = facade.convertUnits(amount, a.get(1), a.get(2));
		out.println(Formats.number(amount) + " " + a.get(1) + " = " + Formats.number(Math.round(result * 100.0) / 100.0)
				+ " " + a.get(2));
	}

	private void scale(List<String> a) {
		if (a.size() != 3) {
			throw new IllegalArgumentException(
					"Usage: scale <amount> <from servings> <to servings>   e.g. scale 200 2 5");
		}
		double amount = Formats.parseQuantity(a.get(0));
		int from = parseServings(a.get(1));
		int to = parseServings(a.get(2));
		double result = facade.scaleAmount(amount, from, to);
		out.println(Formats.number(amount) + " for " + from + " servings becomes "
				+ Formats.number(Math.round(result * 100.0) / 100.0) + " for " + to + " servings");
	}

	private static int parseServings(String text) {
		try {
			return Integer.parseInt(text);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Servings must be a whole number, got '" + text + "'.");
		}
	}

	// ---------------- AI chat (F14) ----------------

	/**
	 * "chat" alone starts a conversation (type 'back' to leave it). "chat
	 * <message>" sends one message and returns to the normal prompt.
	 */
	private void chat(String line) {
		if (!facade.hasAgent()) {
			throw new IllegalArgumentException("The AI agent is not set up. Check LLM_PROVIDER in your .env file.");
		}
		String message = line.trim().substring(4).trim(); // everything after the word "chat"
		if (!message.isEmpty()) {
			ask(message);
			return;
		}
		out.println("Chatting with Mealton. Type 'back' to return to the commands.");
		try {
			while (true) {
				out.print("you> ");
				out.flush();
				String text = in.readLine();
				if (text == null || text.trim().equalsIgnoreCase("back")) {
					break;
				}
				if (!text.isBlank()) {
					ask(text);
				}
			}
		} catch (IOException e) {
			out.println("Input error: " + e.getMessage());
		}
	}

	private void ask(String message) {
		out.println("(thinking...)");
		AgentReply reply = facade.chat(message);
		out.println("Mealton: " + reply.text());
		if (!reply.toolsUsed().isEmpty()) {
			out.println("  [tools used: " + String.join(", ", reply.toolsUsed()) + "]");
		}
	}

	// ---------------- Observer callbacks ----------------

	@Override
	public void onItemExpiring(PantryItem item) {
		out.println("! " + item.getName() + " " + expiryText(item));
	}

	@Override
	public void onPantryChanged() {
		// CLI prints its own confirmation after each command; nothing to do here.
	}

	// ---------------- formatting / parsing helpers ----------------

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

	/** Splits a line into tokens; double quotes keep words together. */
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

	/** Positional arguments plus --flag value pairs. */
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
				  recipe add "<title>" <servings> "<ing1; ing2; ...>" ["<step1|step2>"]
				  recipe list | top | show <id> | rate <id> <1-5> | scale <id> <servings> | delete <id>
				  recipe suggest [best|expiring]   (rank your recipes against your pantry)
				  recipe check <id>                (what you have and what is missing)
				  recipe missing <id>              (put the missing ingredients on the shopping list)
				  profile [show] | name <name> | diet <diet> | calories <kcal> | macros <p> <c> <f> | reset
				  profile allergy add|remove <food>   (recipe suggestions then warn about conflicts)
				  convert <amount> <from> <to>       (units: g kg oz lb ml l tsp tbsp cup)
				  scale <amount> <from servings> <to servings>
				  chat                 (talk to the AI assistant; 'back' to leave)
				  chat <message>       (send one message to the AI assistant)
				  help | exit
				Tip: use "double quotes" for names with spaces.""");
	}
}