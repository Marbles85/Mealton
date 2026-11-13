package com.mealton.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mealton.facade.MealtonFacade;
import com.mealton.pantry.PantryItem;
import com.mealton.recipe.StarterRecipes;

class UserDataStoreTest {
	@TempDir
	Path dir;

	private static UserData sample() {
		MealtonFacade f = new MealtonFacade();
		StarterRecipes.loadInto(f);
		f.setProfileName("Sam");
		f.addPantryItem(new PantryItem("egg", 3, "", null, null));
		return UserDataMapper.capture(f);
	}

	@Test
	void nothingSavedYetGivesEmpty() {
		assertTrue(new UserDataStore(dir.resolve("a/data.json")).load().isEmpty());
	}

	@Test
	void saveThenLoadGivesTheSameData() {
		UserDataStore store = new UserDataStore(dir.resolve("a/data.json"));
		UserData data = sample();
		store.save(data);
		assertEquals(data, store.load().orElseThrow());
	}

	@Test
	void savingTwiceReplacesTheOldFile() {
		UserDataStore store = new UserDataStore(dir.resolve("data.json"));
		store.save(sample());
		MealtonFacade f = new MealtonFacade();
		StarterRecipes.loadInto(f);
		UserData empty = UserDataMapper.capture(f);
		store.save(empty);
		assertEquals(empty, store.load().orElseThrow());
		assertFalse(Files.exists(dir.resolve("data.json.tmp")));
	}

	@Test
	void aDamagedFileIsKeptAsBrokenAndReportedClearly() throws IOException {
		Path file = dir.resolve("data.json");
		Files.writeString(file, "{ this is not json");
		UserDataStore store = new UserDataStore(file);
		IllegalStateException e = assertThrows(IllegalStateException.class, () -> store.load());
		assertTrue(e.getMessage().contains("data.json.broken"), e.getMessage());
		assertTrue(Files.exists(dir.resolve("data.json.broken")));
		assertFalse(Files.exists(file));
		assertTrue(store.load().isEmpty()); // next time: simply nothing saved
	}

	@Test
	void unknownFieldsInTheFileAreIgnored() throws IOException {
		Path file = dir.resolve("data.json");
		Files.writeString(file, "{\"version\":1,\"somethingNew\":123}");
		UserData data = new UserDataStore(file).load().orElseThrow();
		assertEquals(1, data.version());
	}
}