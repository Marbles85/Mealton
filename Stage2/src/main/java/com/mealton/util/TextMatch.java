package com.mealton.util;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Forgiving word matching used across Mealton (recipe search, filters, pantry
 * and shopping lists, ingredient matching): capitals, extra spaces, hyphens,
 * accents, plurals ("egg" = "eggs", "tomato" = "tomatoes") and small typos
 * ("chiken" = "chicken") are all ignored.
 */
public final class TextMatch {
	private TextMatch() {
	}

	/**
	 * Lower case, no accents, hyphens and punctuation become single spaces. null
	 * becomes "".
	 */
	public static String normalize(String text) {
		if (text == null) {
			return "";
		}
		String t = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
		return t.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
	}

	/** The words of a text after {@link #normalize}. */
	public static List<String> words(String text) {
		String n = normalize(text);
		return n.isEmpty() ? List.of() : List.of(n.split(" "));
	}

	/**
	 * One word in its singular form: "eggs" to "egg", "tomatoes" to "tomato",
	 * "berries" to "berry".
	 */
	public static String singular(String word) {
		String w = word;
		if (w.length() <= 3) {
			return w;
		}
		if (w.endsWith("ies") && w.length() > 4) {
			return w.substring(0, w.length() - 3) + "y";
		}
		if (w.endsWith("oes") && w.length() > 4) {
			return w.substring(0, w.length() - 2);
		}
		if (w.endsWith("sses") || w.endsWith("ches") || w.endsWith("shes") || w.endsWith("xes") || w.endsWith("zes")) {
			return w.substring(0, w.length() - 2);
		}
		if (w.endsWith("ves") && w.length() > 4) {
			return w.substring(0, w.length() - 3) + "f"; // loaves, halves
		}
		if (w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us")) {
			return w.substring(0, w.length() - 1);
		}
		return w;
	}

	/**
	 * True when the two words are the same ignoring plurals, or differ by a small
	 * typo: one wrong, missing, extra or swapped letter in words of at least
	 * {@code minTypoLength} letters (two letters off for words of 10 or more).
	 */
	public static boolean sameWord(String a, String b, int minTypoLength) {
		String x = singular(a);
		String y = singular(b);
		if (x.equals(y)) {
			return true;
		}
		int shortest = Math.min(x.length(), y.length());
		if (shortest < minTypoLength || isNumber(x) || isNumber(y)) {
			return false;
		}
		int allowed = shortest >= 10 ? 2 : 1;
		return distance(x, y, allowed) <= allowed;
	}

	/**
	 * True when two names mean the same thing ("Eggs" and "egg", " chicken breasts
	 * " and "Chicken Breast", "chiken breast" too): same words in the same order.
	 */
	public static boolean sameName(String a, String b) {
		List<String> x = words(a);
		List<String> y = words(b);
		if (x.isEmpty() || y.size() != x.size()) {
			return false;
		}
		for (int i = 0; i < x.size(); i++) {
			if (!sameWord(x.get(i), y.get(i), 6)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * True when every word of {@code query} is found in {@code text}: as the same
	 * word (plural and typos allowed) or as the start of a longer word ("chick"
	 * finds "chicken"). A blank query matches nothing; callers treat blank as "no
	 * search".
	 */
	public static boolean containsAll(String text, String query) {
		List<String> have = words(text);
		List<String> need = words(query);
		if (need.isEmpty()) {
			return false;
		}
		// "glutenfree" and "gluten free" are the same thing
		String compactQuery = String.join("", need);
		if (compactQuery.length() >= 5 && String.join("", have).contains(compactQuery)) {
			return true;
		}
		for (String n : need) {
			boolean found = false;
			String ns = singular(n);
			for (String h : have) {
				if (sameWord(h, n, 4) || (ns.length() >= 3 && singular(h).startsWith(ns))) {
					found = true;
					break;
				}
			}
			if (!found) {
				return false;
			}
		}
		return true;
	}

	private static boolean isNumber(String s) {
		return !s.isEmpty() && s.chars().allMatch(Character::isDigit);
	}

	/**
	 * Edit distance with swapped neighbours counting as one; gives up (returns
	 * max+1) once it is over {@code max}.
	 */
	static int distance(String a, String b, int max) {
		if (Math.abs(a.length() - b.length()) > max) {
			return max + 1;
		}
		int[][] d = new int[a.length() + 1][b.length() + 1];
		for (int i = 0; i <= a.length(); i++) {
			d[i][0] = i;
		}
		for (int j = 0; j <= b.length(); j++) {
			d[0][j] = j;
		}
		for (int i = 1; i <= a.length(); i++) {
			for (int j = 1; j <= b.length(); j++) {
				int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
				d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
				if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
					d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
				}
			}
		}
		return d[a.length()][b.length()];
	}

	/** Several names in one list, for callers that need a unique key. */
	public static List<String> normalizedAll(List<String> names) {
		List<String> out = new ArrayList<>();
		for (String n : names) {
			out.add(normalize(n));
		}
		return out;
	}
}