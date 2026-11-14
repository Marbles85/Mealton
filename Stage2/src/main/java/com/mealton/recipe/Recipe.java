package com.mealton.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.mealton.nutrition.Nutrients;

/**
 * A recipe the user can save, rate and scale (Stage 1 domain class Recipe,
 * feature F13). The rating is 0 until the user rates it, then 1 to 5 stars.
 * <p>
 * A recipe is either one the user made themselves, or a "library" recipe that
 * comes with the app. Library recipes only appear in "My Recipes" after the
 * user saves them (the Save heart).
 * <p>
 * Besides the basics, a recipe can carry cooking times, where it came from (a
 * name and a web link), and the file name of the user's own photo of it.
 */
public class Recipe {
	private long id; // assigned by the repository when the recipe is saved
	private String title;
	private int servings;
	private List<RecipeIngredient> ingredients;
	private List<String> steps;
	private int rating;
	private boolean library; // true for recipes that come with the app
	private boolean saved; // true when the user saved a library recipe to My Recipes
	private int prepMinutes; // 0 = not given
	private int cookMinutes; // 0 = not given
	private String sourceName; // null = none
	private String sourceUrl; // null = none
	private String photo; // file name inside the user's media folder, null = no photo
	private boolean discovered; // true for recipes Claude generated for the All recipes tab
	private List<String> aiTags = new ArrayList<>(); // short filter words Claude gave this recipe (cuisine, style...)
	private java.util.Map<Integer, List<String>> stepUses = new java.util.TreeMap<>(); // step number -> ingredient
																						// names the cook chose to show
	private java.util.Map<Integer, Integer> stepTimes = new java.util.TreeMap<>(); // step number -> minutes shown on
																					// its timer tag (0 = hide it)
	private double publicRating; // the rating other people gave it on its website (0 = unknown)
	private int publicRatingCount; // how many people rated it
	private int totalOverride; // total time in minutes typed by the cook, 0 = estimate it
	private int freshOverride; // days the leftovers keep, typed by the cook, 0 = use the suggestion
	private Nutrients aiEstimate; // Claude's estimate for the WHOLE recipe as written, null = none

	public Recipe(String title, int servings, List<RecipeIngredient> ingredients, List<String> steps) {
		if (title == null || title.isBlank()) {
			throw new IllegalArgumentException("A recipe needs a title.");
		}
		if (servings < 1) {
			throw new IllegalArgumentException("Servings must be at least 1.");
		}
		if (ingredients == null || ingredients.isEmpty()) {
			throw new IllegalArgumentException("A recipe needs at least one ingredient.");
		}
		this.title = title.trim();
		this.servings = servings;
		this.ingredients = new ArrayList<>(ingredients);
		this.steps = steps == null ? new ArrayList<>() : new ArrayList<>(steps);
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getTitle() {
		return title;
	}

	public int getServings() {
		return servings;
	}

	public List<RecipeIngredient> getIngredients() {
		return Collections.unmodifiableList(ingredients);
	}

	public List<String> getSteps() {
		return Collections.unmodifiableList(steps);
	}

	/** 0 means not rated yet, otherwise 1 to 5 stars. */
	public int getRating() {
		return rating;
	}

	public boolean isRated() {
		return rating > 0;
	}

	/** Gives the recipe 1 to 5 stars. */
	public void rate(int stars) {
		if (stars < 1 || stars > 5) {
			throw new IllegalArgumentException("A rating must be between 1 and 5 stars.");
		}
		this.rating = stars;
	}

	/**
	 * True for ready-made recipes that come with the app (not made by the user).
	 */
	public boolean isLibrary() {
		return library;
	}

	/**
	 * True for recipes Claude generated for the All recipes tab (they are
	 * ready-made, not edited by the user).
	 */
	public boolean isDiscovered() {
		return discovered;
	}

	public void setDiscovered(boolean discovered) {
		this.discovered = discovered;
	}

	public void setLibrary(boolean library) {
		this.library = library;
	}

	/** True when the user saved this recipe with the Save heart. */
	public boolean isSaved() {
		return saved;
	}

	public void setSaved(boolean saved) {
		this.saved = saved;
	}

	/**
	 * "My Recipes" holds the user's own recipes and the library recipes they saved.
	 */
	public boolean isInMyRecipes() {
		return !library || saved;
	}

	// ---------------- times, source and photo ----------------

	/** Minutes of preparation, or 0 when it is not given. */
	public int getPrepMinutes() {
		return prepMinutes;
	}

	/** Minutes of cooking, or 0 when it is not given. */
	public int getCookMinutes() {
		return cookMinutes;
	}

	/** Prep plus cook time, or 0 when neither is given. */
	public int getTotalMinutes() {
		return prepMinutes + cookMinutes;
	}

	/**
	 * @throws IllegalArgumentException if a time is negative or longer than 24
	 *                                  hours
	 */
	public void setTimes(int prepMinutes, int cookMinutes) {
		if (prepMinutes < 0 || cookMinutes < 0 || prepMinutes > 1440 || cookMinutes > 1440) {
			throw new IllegalArgumentException("Times must be between 0 and 1440 minutes.");
		}
		this.prepMinutes = prepMinutes;
		this.cookMinutes = cookMinutes;
	}

	/**
	 * Where the recipe came from, for example "The Mediterranean Dish", or null.
	 */
	public String getSourceName() {
		return sourceName;
	}

	/**
	 * A web link to the original recipe (starts with http:// or https://), or null.
	 */
	public String getSourceUrl() {
		return sourceUrl;
	}

	/**
	 * Sets where the recipe came from. Blank values clear it.
	 *
	 * @throws IllegalArgumentException if the link does not start with http:// or
	 *                                  https://
	 */
	public void setSource(String name, String url) {
		String n = name == null ? "" : name.trim();
		String u = url == null ? "" : url.trim();
		if (!u.isEmpty() && !u.matches("(?i)https?://\\S+")) {
			throw new IllegalArgumentException("The recipe link must start with http:// or https://");
		}
		this.sourceName = n.isEmpty() ? null : n;
		this.sourceUrl = u.isEmpty() ? null : u;
	}

	/**
	 * The ingredient names the cook chose to show as chips under a step (0 = first
	 * step), or null when the step still shows the automatic ones.
	 */
	public List<String> getStepIngredients(int step) {
		List<String> chosen = stepUses.get(step);
		return chosen == null ? null : Collections.unmodifiableList(chosen);
	}

	/** All chosen chip lists, step number to ingredient names (for saving). */
	public java.util.Map<Integer, List<String>> getAllStepIngredients() {
		return Collections.unmodifiableMap(stepUses);
	}

	/**
	 * Chooses the ingredient chips shown under one step. Only names that are
	 * ingredients of this recipe are kept.
	 */
	public void setStepIngredients(int step, List<String> names) {
		if (step < 0 || step >= steps.size()) {
			throw new IllegalArgumentException("This recipe has no step " + (step + 1) + ".");
		}
		List<String> keep = new ArrayList<>();
		for (String n : names == null ? List.<String>of() : names) {
			boolean known = ingredients.stream().anyMatch(i -> i.name().equalsIgnoreCase(n));
			if (known && keep.stream().noneMatch(k -> k.equalsIgnoreCase(n))) {
				keep.add(n);
			}
		}
		stepUses.put(step, keep);
	}

	/**
	 * The minutes the cook set for a step's timer tag (0 = no tag), or null when
	 * the tag is still automatic.
	 */
	public Integer getStepTime(int step) {
		return stepTimes.get(step);
	}

	public java.util.Map<Integer, Integer> getAllStepTimes() {
		return Collections.unmodifiableMap(stepTimes);
	}

	/** Sets the timer tag of one step: minutes 1 to 1440, or 0 for no tag. */
	public void setStepTime(int step, int minutes) {
		if (step < 0 || step >= steps.size()) {
			throw new IllegalArgumentException("This recipe has no step " + (step + 1) + ".");
		}
		if (minutes < 0 || minutes > 1440) {
			throw new IllegalArgumentException("A step time must be between 0 and 1440 minutes.");
		}
		stepTimes.put(step, minutes);
	}

	public void resetStepTime(int step) {
		stepTimes.remove(step);
	}

	/**
	 * The average rating from the recipe's own website (0 to 5), or 0 when unknown.
	 * This is not the cook's own rating.
	 */
	public double getPublicRating() {
		return publicRating;
	}

	public int getPublicRatingCount() {
		return publicRatingCount;
	}

	/**
	 * Stores the public rating found on the recipe's website: an average from 0 to
	 * 5 and how many people gave it.
	 */
	public void setPublicRating(double average, int count) {
		if (Double.isNaN(average) || average < 0 || average > 5 || count < 0) {
			throw new IllegalArgumentException("A public rating is an average from 0 to 5 and a count of 0 or more.");
		}
		this.publicRating = average;
		this.publicRatingCount = count;
	}

	/** The total time the cook typed in minutes, or 0 when Mealton estimates it. */
	public int getTotalOverride() {
		return totalOverride;
	}

	public void setTotalOverride(int minutes) {
		if (minutes < 0 || minutes > 10080) {
			throw new IllegalArgumentException("Total time must be between 0 and 10080 minutes (one week).");
		}
		this.totalOverride = minutes;
	}

	/**
	 * Days the cook says the leftovers keep, or 0 when Mealton\u2019s suggestion is
	 * used.
	 */
	public int getFreshOverride() {
		return freshOverride;
	}

	public void setFreshOverride(int days) {
		if (days < 0 || days > 60) {
			throw new IllegalArgumentException("Leftovers can keep between 1 and 60 days.");
		}
		this.freshOverride = days;
	}

	/**
	 * Mealton\u2019s estimate of how many days the leftovers keep, with the reason.
	 */
	public LeftoverFreshness.Suggestion suggestedFresh() {
		return LeftoverFreshness.suggest(this);
	}

	/**
	 * Days the leftovers keep: the cook\u2019s number if given, otherwise the
	 * suggestion.
	 */
	public int freshDays() {
		return freshOverride > 0 ? freshOverride : suggestedFresh().days();
	}

	/** Goes back to the automatic chips for one step. */
	public void resetStepIngredients(int step) {
		stepUses.remove(step);
	}

	/** Filter words Claude gave this recipe, for example "italian" or "quick". */
	public List<String> getAiTags() {
		return Collections.unmodifiableList(aiTags);
	}

	/** Keeps up to 6 short lowercase words; blanks and duplicates are dropped. */
	public void setAiTags(List<String> tags) {
		List<String> clean = new ArrayList<>();
		if (tags != null) {
			for (String t : tags) {
				String w = t == null ? ""
						: t.trim().toLowerCase(java.util.Locale.ROOT).replace('-', ' ').replace('_', ' ');
				if (!w.isEmpty() && w.length() <= 24 && !clean.contains(w) && clean.size() < 6) {
					clean.add(w);
				}
			}
		}
		this.aiTags = clean;
	}

	/**
	 * Claude's nutrition estimate for the whole recipe (all servings), or null when
	 * there is none.
	 */
	public Nutrients getAiEstimate() {
		return aiEstimate;
	}

	public void setAiEstimate(Nutrients estimate) {
		this.aiEstimate = estimate;
	}

	/**
	 * File name of the user's photo of this recipe (inside their media folder), or
	 * null.
	 */
	public String getPhoto() {
		return photo;
	}

	public void setPhoto(String photo) {
		this.photo = photo == null || photo.isBlank() ? null : photo;
	}

	/**
	 * Replaces the content of this recipe (title, servings, ingredients, steps,
	 * times, source) with another one's, keeping its id, rating, photo and saved
	 * state. Used to edit a recipe in place, so the plan and calendar (which hold
	 * this very object) show the change too.
	 */
	public void edit(Recipe other) {
		stepUses = new java.util.TreeMap<>();
		other.stepUses.forEach((k, v) -> stepUses.put(k, new ArrayList<>(v)));
		stepTimes = new java.util.TreeMap<>(other.stepTimes);
		totalOverride = other.totalOverride;
		freshOverride = other.freshOverride;
		publicRating = other.publicRating;
		publicRatingCount = other.publicRatingCount;
		this.title = other.title;
		this.servings = other.servings;
		this.ingredients = new ArrayList<>(other.ingredients);
		this.steps = new ArrayList<>(other.steps);
		this.prepMinutes = other.prepMinutes;
		this.cookMinutes = other.cookMinutes;
		this.sourceName = other.sourceName;
		this.sourceUrl = other.sourceUrl;
		this.aiEstimate = other.aiEstimate;
		this.aiTags = new ArrayList<>(other.aiTags);
	}

	/**
	 * A copy of this recipe for a different number of servings (feature F10 in
	 * use). The copy is not saved: it has no id until you save it.
	 */
	public Recipe scaledTo(int newServings) {
		if (newServings < 1) {
			throw new IllegalArgumentException("Servings must be at least 1.");
		}
		List<RecipeIngredient> scaled = new ArrayList<>();
		for (RecipeIngredient ingredient : ingredients) {
			scaled.add(ingredient.scaled(servings, newServings));
		}
		Recipe copy = new Recipe(title, newServings, scaled, steps);
		copy.rating = rating;
		copy.prepMinutes = prepMinutes;
		copy.cookMinutes = cookMinutes;
		copy.sourceName = sourceName;
		copy.sourceUrl = sourceUrl;
		copy.photo = photo;
		copy.aiTags = new ArrayList<>(aiTags);
		stepUses.forEach((k, v) -> copy.stepUses.put(k, new ArrayList<>(v)));
		copy.stepTimes.putAll(stepTimes);
		copy.totalOverride = totalOverride;
		copy.freshOverride = freshOverride;
		copy.publicRating = publicRating;
		copy.publicRatingCount = publicRatingCount;
		copy.aiEstimate = aiEstimate == null ? null : aiEstimate.times((double) newServings / servings);
		return copy;
	}

	@Override
	public String toString() {
		return title + " (" + servings + " servings)";
	}
}