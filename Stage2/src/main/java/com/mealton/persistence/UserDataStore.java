package com.mealton.persistence;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Reads and writes one user's saved data as a JSON file (the Repository idea
 * again: the rest of the program does not care where the data lives). A save
 * writes a temporary file first and then swaps it in, so a crash in the middle
 * of a save cannot leave a half-written file.
 */
public class UserDataStore {
	private final Path file;
	private final ObjectMapper mapper = new ObjectMapper()
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
			.enable(SerializationFeature.INDENT_OUTPUT);

	public UserDataStore(Path file) {
		this.file = file;
	}

	public Path file() {
		return file;
	}

	/**
	 * The saved data, or empty if nothing was saved yet.
	 *
	 * @throws IllegalStateException if the file is damaged; it is renamed to
	 *                               "data.json.broken" so it is not lost
	 */
	public Optional<UserData> load() {
		if (!Files.isRegularFile(file)) {
			return Optional.empty();
		}
		try {
			UserData data = mapper.readValue(file.toFile(), UserData.class);
			return Optional.ofNullable(data);
		} catch (IOException e) {
			Path broken = file.resolveSibling(file.getFileName() + ".broken");
			try {
				Files.move(file, broken, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
				// keep going: the error below is the important part
			}
			throw new IllegalStateException("Your saved data could not be read, so you are starting fresh. "
					+ "The old file was kept as " + broken.getFileName() + ".");
		}
	}

	/**
	 * Writes the data. @throws IllegalStateException if the file cannot be written
	 */
	public void save(UserData data) {
		try {
			Files.createDirectories(file.getParent());
			Path temp = file.resolveSibling(file.getFileName() + ".tmp");
			mapper.writeValue(temp.toFile(), data);
			try {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			throw new IllegalStateException("Your data could not be saved: " + e.getMessage());
		}
	}
}