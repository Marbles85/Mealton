package com.mealton.recipe;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;

/**
 * Somewhere new recipes for the All recipes tab can come from (Strategy
 * pattern): a recipe website's search service, or Claude writing them itself.
 * The facade picks a source; the screens never know which one was used.
 */
public interface RecipeSource {
	/** The name shown to the person, for example "TheMealDB". */
	String name();

	/**
	 * Finds up to {@code count} recipes that are not in {@code knownTitles}.
	 *
	 * @param hint     words the person is interested in (may be blank)
	 * @param progress called with how many recipes are ready so far; may be called
	 *                 from other threads
	 * @throws IOException when the service cannot be reached
	 */
	List<RecipeImporter.Imported> find(int count, String hint, Set<String> knownTitles, IntConsumer progress)
			throws IOException;
}