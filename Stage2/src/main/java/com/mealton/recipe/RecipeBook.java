package com.mealton.recipe;

import java.util.Comparator;
import java.util.List;

import com.mealton.repository.InMemoryRecipeRepository;
import com.mealton.repository.RecipeRepository;

/**
 * The recipe collection (feature F13): save, rate, list, scale and delete. It
 * checks the rules (no duplicate titles, ratings 1 to 5) and leaves storage to
 * a {@link RecipeRepository}. "My Recipes" is the part of the collection the
 * user made or saved.
 */
public class RecipeBook {
	private final RecipeRepository repository;

	public RecipeBook() {
		this(new InMemoryRecipeRepository());
	}

	public RecipeBook(RecipeRepository repository) {
		this.repository = repository;
	}

	/**
	 * Saves a recipe. Two recipes cannot have the same title (upper/lower case is
	 * ignored).
	 */
	public Recipe save(Recipe recipe) {
		if (recipe == null) {
			throw new IllegalArgumentException("There is no recipe to save.");
		}
		if (repository.findByTitle(recipe.getTitle()).isPresent()) {
			throw new IllegalArgumentException("You already have a recipe called '" + recipe.getTitle() + "'.");
		}
		return repository.save(recipe);
	}

	/** Gives a saved recipe 1 to 5 stars. */
	public Recipe rate(long id, int stars) {
		Recipe recipe = get(id);
		recipe.rate(stars);
		return repository.save(recipe);
	}

	/** Finds a saved recipe, or says clearly that it does not exist. */
	public Recipe get(long id) {
		return repository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("There is no saved recipe with id " + id + "."));
	}

	/** Every recipe (library and your own), in the order they were added. */
	public List<Recipe> getAll() {
		return repository.findAll();
	}

	/**
	 * "My Recipes": the recipes the user made plus the library recipes they saved.
	 */
	public List<Recipe> getMyRecipes() {
		return repository.findAll().stream().filter(Recipe::isInMyRecipes).toList();
	}

	/**
	 * The Save heart: adds a recipe to My Recipes (or takes a library recipe out
	 * again). Saving a recipe that is already saved changes nothing.
	 */
	public Recipe setSaved(long id, boolean saved) {
		Recipe recipe = get(id);
		if (recipe.isSaved() != saved) {
			recipe.setSaved(saved);
			repository.save(recipe);
		}
		return recipe;
	}

	/** Rated recipes only, best first (ties are sorted by title). */
	public List<Recipe> getTopRated() {
		return repository.findAll().stream().filter(Recipe::isRated).sorted(Comparator.comparingInt(Recipe::getRating)
				.reversed().thenComparing(Recipe::getTitle, String.CASE_INSENSITIVE_ORDER)).toList();
	}

	/**
	 * A copy of a saved recipe for another number of servings. The saved recipe is
	 * not changed.
	 */
	public Recipe scaled(long id, int servings) {
		return get(id).scaledTo(servings);
	}

	/** Removes a recipe. */
	public void delete(long id) {
		get(id); // gives a clear error if it does not exist
		repository.delete(id);
	}
}