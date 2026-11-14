package com.mealton.diary;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealton.llm.MockLLMClient;
import com.mealton.util.Formats;

/**
 * Turns "I only used one egg, no feta, half the spinach" into new ingredient
 * amounts (the Log food page's "Anything different?" box).
 * <p>
 * With an AI the note is sent to Claude, which answers with the new amounts as
 * JSON; without one (or when Claude's answer cannot be used) simple built-in
 * rules read phrases such as "no X", "half the X", "double X", "extra X", "300
 * g X" and "two X".
 */
public final class PortionNotes {

	/**
	 * What was understood.
	 *
	 * @param amounts new amount for the ingredient at this index (0 = not used)
	 * @param said    one line per change, for example "Eggs: 4 → 1"
	 * @param usedAi  true when Claude read the note
	 */
	public record Result(Map<Integer, Double> amounts, List<String> said, boolean usedAi) {
		public boolean isEmpty() {
			return amounts.isEmpty();
		}
	}

	/** One ingredient as it stands before the note is applied. */
	public record Line(String name, String unit, double amount) {
	}

	private static final Map<String, Double> WORDS = Map.of("one", 1.0, "two", 2.0, "three", 3.0, "four", 4.0, "five",
			5.0, "six", 6.0, "seven", 7.0, "eight", 8.0, "a", 1.0, "an", 1.0);
	private static final ObjectMapper JSON = new ObjectMapper();

	private PortionNotes() {
	}

	/**
	 * Reads the note. {@code llm} takes a prompt and returns Claude's answer; it
	 * may be null (no AI), and a failure falls back to the built-in rules.
	 */
	public static Result read(List<Line> lines, String notes, Function<String, String> llm) {
		if (notes == null || notes.isBlank()) {
			return new Result(Map.of(), List.of(), false);
		}
		if (llm != null) {
			try {
				Result ai = fromAi(lines, notes, llm);
				if (!ai.isEmpty()) {
					return ai;
				}
			} catch (RuntimeException e) {
				// fall through to the built-in rules
			}
		}
		return byRules(lines, notes);
	}

	// ---------------------------------------------------------------- AI

	private static Result fromAi(List<Line> lines, String notes, Function<String, String> llm) {
		StringBuilder list = new StringBuilder();
		for (int i = 0; i < lines.size(); i++) {
			Line l = lines.get(i);
			list.append(i).append(": ").append(l.name()).append(" - ").append(Formats.number(l.amount())).append(' ')
					.append(l.unit()).append('\n');
		}
		String prompt = "A cook made a recipe and describes how their cooking differed. Work out the amount they "
				+ "really used of each ingredient they mention. Ingredients (index: name - planned amount):\n" + list
				+ "Cook's note: \"" + notes.trim() + "\"\n"
				+ "Reply with ONLY JSON like {\"adjustments\":[{\"index\":0,\"amount\":1}]}. Use the same unit as "
				+ "the planned amount, use amount 0 for an ingredient that was left out, and leave out every "
				+ "ingredient the note does not mention.";
		String answer = llm.apply(prompt);
		if (answer == null || answer.isBlank() || answer.equals(MockLLMClient.DEFAULT_REPLY)) {
			return new Result(Map.of(), List.of(), false);
		}
		int start = answer.indexOf('{');
		int end = answer.lastIndexOf('}');
		if (start < 0 || end <= start) {
			return new Result(Map.of(), List.of(), false);
		}
		try {
			JsonNode root = JSON.readTree(answer.substring(start, end + 1));
			Map<Integer, Double> amounts = new LinkedHashMap<>();
			List<String> said = new ArrayList<>();
			for (JsonNode node : root.path("adjustments")) {
				int index = node.path("index").asInt(-1);
				double amount = node.path("amount").asDouble(-1);
				if (index < 0 || index >= lines.size() || amount < 0 || Double.isNaN(amount)) {
					continue;
				}
				Line l = lines.get(index);
				amounts.put(index, round(amount, l.unit()));
				said.add(describe(l, amounts.get(index)));
			}
			return new Result(amounts, said, true);
		} catch (java.io.IOException e) {
			return new Result(Map.of(), List.of(), false);
		}
	}

	// ------------------------------------------------------------- rules

	static Result byRules(List<Line> lines, String notes) {
		String t = " " + notes.toLowerCase(Locale.ROOT).replace("’", "").replace("'", "") + " ";
		Map<Integer, Double> amounts = new LinkedHashMap<>();
		List<String> said = new ArrayList<>();
		for (int i = 0; i < lines.size(); i++) {
			Line l = lines.get(i);
			Double value = null;
			for (String kw : keywords(l.name())) {
				value = ruleFor(t, kw, l);
				if (value != null) {
					break;
				}
			}
			if (value != null) {
				amounts.put(i, value);
				said.add(describe(l, value));
			}
		}
		return new Result(amounts, said, false);
	}

	private static Double ruleFor(String t, String kw, Line l) {
		String k = Pattern.quote(kw) + "(?:e?s)?";
		double def = l.amount();
		if (Pattern.compile("(no|without|skipped|skip|didnt use|left out)\\s+(the\\s+)?" + k).matcher(t).find()) {
			return 0.0;
		}
		if (Pattern.compile("half\\s+(the\\s+)?" + k).matcher(t).find()) {
			return round(def / 2, l.unit());
		}
		if (Pattern.compile("(double|twice the)\\s+(the\\s+)?" + k).matcher(t).find()) {
			return round(def * 2, l.unit());
		}
		if (Pattern.compile("extra\\s+" + k).matcher(t).find()) {
			return round(def * 1.5, l.unit());
		}
		Matcher m = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:g|ml|grams?)?\\s+(?:of\\s+)?(?:the\\s+)?" + k).matcher(t);
		if (m.find()) {
			return Double.parseDouble(m.group(1));
		}
		m = Pattern.compile("\\b(one|two|three|four|five|six|seven|eight|an?)\\s+" + k).matcher(t);
		if (m.find()) {
			return WORDS.get(m.group(1));
		}
		return null;
	}

	/**
	 * The full name and each longer word of it ("baby spinach" -> baby spinach,
	 * spinach).
	 */
	static List<String> keywords(String name) {
		String n = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z ]", " ").trim().replaceAll("\\s+", " ");
		List<String> result = new ArrayList<>();
		result.add(n.endsWith("s") && n.length() > 3 ? n.substring(0, n.length() - 1) : n);
		String[] words = n.split(" ");
		for (int i = words.length - 1; i >= 0; i--) {
			String w = words[i];
			if (w.length() >= 4 && !w.equals("fresh") && !w.equals("large") && !w.equals("small")) {
				w = w.endsWith("s") && w.length() > 4 ? w.substring(0, w.length() - 1) : w;
				if (!result.contains(w)) {
					result.add(w);
				}
			}
		}
		return result;
	}

	// ----------------------------------------------------------- helpers

	/** Pieces round to halves, everything else to one decimal. */
	public static double round(double value, String unit) {
		if (unit == null || unit.isBlank()) {
			return Math.round(value * 2) / 2.0;
		}
		return Math.round(value * 10) / 10.0;
	}

	private static String describe(Line l, double value) {
		return l.name() + ": " + Formats.quantityWithUnit(l.amount(), l.unit()) + " → "
				+ Formats.quantityWithUnit(value, l.unit());
	}
}