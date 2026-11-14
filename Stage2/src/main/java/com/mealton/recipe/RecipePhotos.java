package com.mealton.recipe;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Keeps the user's photos of their recipes: a picture is copied into the user's
 * own media folder ("mealton-data/users/&lt;login&gt;/media", never committed
 * to Git) and the recipe remembers the file name. Only pictures are accepted,
 * up to 10 MB.
 */
public final class RecipePhotos {
	public static final List<String> EXTENSIONS = List.of("png", "jpg", "jpeg", "gif", "bmp");
	private static final long MAX_BYTES = 10L * 1024 * 1024;

	private final Path directory;

	/**
	 * @param directory the media folder, or null when photos are not available
	 *                  (then every attach fails clearly)
	 */
	public RecipePhotos(Path directory) {
		this.directory = directory;
	}

	/** Copies a picture in and returns the file name to store on the recipe. */
	public String attach(Recipe recipe, Path source) {
		if (directory == null) {
			throw new IllegalArgumentException("Photos are not available right now.");
		}
		String name = source.getFileName().toString().toLowerCase(Locale.ROOT);
		int dot = name.lastIndexOf('.');
		String ext = dot < 0 ? "" : name.substring(dot + 1);
		if (!EXTENSIONS.contains(ext)) {
			throw new IllegalArgumentException("Choose a picture file (png, jpg, gif or bmp).");
		}
		try {
			if (!Files.isRegularFile(source)) {
				throw new IllegalArgumentException("That picture could not be found.");
			}
			if (Files.size(source) > MAX_BYTES) {
				throw new IllegalArgumentException("That picture is bigger than 10 MB. Choose a smaller one.");
			}
			Files.createDirectories(directory);
			String fileName = "recipe-" + recipe.getId() + "-" + System.currentTimeMillis() + "." + ext;
			Files.copy(source, directory.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
			remove(recipe);
			recipe.setPhoto(fileName);
			return fileName;
		} catch (IOException e) {
			throw new IllegalArgumentException("The picture could not be saved: " + e.getMessage());
		}
	}

	/** Deletes the recipe's photo file (if any) and forgets it. */
	public void remove(Recipe recipe) {
		String old = recipe.getPhoto();
		if (old != null && directory != null) {
			try {
				Files.deleteIfExists(directory.resolve(old));
			} catch (IOException ignored) {
				// an unused file left behind is harmless
			}
		}
		recipe.setPhoto(null);
	}

	/** The photo file of a recipe, if it has one and the file still exists. */
	public Optional<Path> find(Recipe recipe) {
		if (directory == null || recipe.getPhoto() == null) {
			return Optional.empty();
		}
		Path file = directory.resolve(recipe.getPhoto()).normalize();
		if (!file.startsWith(directory.normalize()) || !Files.isRegularFile(file)) {
			return Optional.empty();
		}
		return Optional.of(file);
	}
}