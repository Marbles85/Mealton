package com.mealton.recipe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.mealton.nutrition.RecipeNutrition;
import com.mealton.profile.Diet;
import com.mealton.profile.UserProfile;

/**
 * Tags that describe a recipe, used to filter the recipe lists (feature F09).
 * They are worked out by plain code from the ingredients, the nutrition
 * estimate and your profile, so the same recipe always gets the same tags.
 * (Stage 1 planned AI tagging; the AI is not needed for these.)
 */
public enum RecipeTag {
	VEGETARIAN("vegetarian"), VEGAN("vegan"), HIGH_PROTEIN("high-protein"), LIGHT("light"),
	FITS_PROFILE("fits-my-profile"), USES_EXPIRING("uses-expiring");

	/** A serving with at least this much protein is "high-protein". */
	public static final double HIGH_PROTEIN_GRAMS = 20;
	/** A serving with at most this many calories is "light". */
	public static final double LIGHT_KCAL = 400;
	/**
	 * Nutrition tags are only given when at least this share of the ingredients
	 * could be counted.
	 */
	private static final double MIN_COVERAGE = 0.75;

	private final String label;

	RecipeTag(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

	/**
	 * The tags of a recipe match: recipe tags plus tags that depend on your pantry
	 * and profile.
	 */
	public static Set<RecipeTag> tagsOf(RecipeMatch match) {
		Set<RecipeTag> tags = EnumSet.noneOf(RecipeTag.class);
		Recipe recipe = match.recipe();
		if (ProfileCheck.conflicts(recipe, UserProfile.empty().withDiet(Diet.VEGETARIAN)).isEmpty()) {
			tags.add(VEGETARIAN);
		}
		if (ProfileCheck.conflicts(recipe, UserProfile.empty().withDiet(Diet.VEGAN)).isEmpty()) {
			tags.add(VEGAN);
		}
		RecipeNutrition nutrition = RecipeNutrition.of(recipe);
		if (nutrition.coverage() >= MIN_COVERAGE) {
			if (nutrition.perServing().protein() >= HIGH_PROTEIN_GRAMS) {
				tags.add(HIGH_PROTEIN);
			}
			if (nutrition.perServing().calories() > 0 && nutrition.perServing().calories() <= LIGHT_KCAL) {
				tags.add(LIGHT);
			}
		}
		if (match.warnings().isEmpty()) {
			tags.add(FITS_PROFILE);
		}
		if (match.expiringUsed() > 0) {
			tags.add(USES_EXPIRING);
		}
		return tags;
	}

	/**
	 * Keeps the matches that have ALL the wanted tags. An empty filter keeps
	 * everything.
	 */
	public static List<RecipeMatch> filter(List<RecipeMatch> matches, Collection<RecipeTag> wanted) {
		if (wanted == null || wanted.isEmpty()) {
			return matches;
		}
		List<RecipeMatch> kept = new ArrayList<>();
		for (RecipeMatch match : matches) {
			if (tagsOf(match).containsAll(wanted)) {
				kept.add(match);
			}
		}
		return kept;
	}

	/** Reads one tag such as "vegan", "high protein" or "HIGH_PROTEIN". */
	public static RecipeTag fromText(String text) {
		String wanted = text == null ? "" : text.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
		for (RecipeTag tag : values()) {
			if (tag.label.equals(wanted)) {
				return tag;
			}
		}
		throw new IllegalArgumentException(
				"Unknown tag '" + (text == null ? "" : text.trim()) + "'. Choose from: " + options() + ".");
	}

	/**
	 * Reads several tags separated by commas, for example "vegan,light". Blank
	 * means none.
	 */
	public static Set<RecipeTag> setFromText(String text) {
		Set<RecipeTag> tags = EnumSet.noneOf(RecipeTag.class);
		if (text != null && !text.isBlank()) {
			for (String part : text.split(",")) {
				tags.add(fromText(part));
			}
		}
		return tags;
	}

	/** All tag names in one string, for help and error messages. */
	public static String options() {
		StringBuilder sb = new StringBuilder();
		for (RecipeTag tag : values()) {
			if (sb.length() > 0) {
				sb.append(", ");
			}
			sb.append(tag.label);
		}
		return sb.toString();
	}
}