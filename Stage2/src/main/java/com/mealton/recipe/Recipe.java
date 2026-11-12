package com.mealton.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A recipe the user can save, rate and scale (Stage 1 domain class Recipe,
 * feature F13). The rating is 0 until the user rates it, then 1 to 5 stars.
 * <p>
 * A recipe is either one the user made themselves, or a "library" recipe that
 * comes with the app. Library recipes only appear in "My Recipes" after the
 * user saves them (the Save heart).
 */
public class Recipe {
	private long id; // assigned by the repository when the recipe is saved
	private final String title;
	private final int servings;
	private final List<RecipeIngredient> ingredients;
	private final List<String> steps;
	private int rating;
	private boolean library; // true for recipes that come with the app
	private boolean saved; // true when the user saved a library recipe to My Recipes

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
		return copy;
	}

	@Override
	public String toString() {
		return title + " (" + servings + " servings)";
	}
}