package com.mealton.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mealton.pantry.PantryItem;

class InMemoryPantryRepositoryTest {

	private InMemoryPantryRepository repo;

	@BeforeEach
	void setUp() {
		repo = new InMemoryPantryRepository();
	}

	private PantryItem item(String name) {
		return new PantryItem(name, 1, "pcs", "Produce", null);
	}

	@Test
	void saveAssignsDifferentIdsToNewItems() {
		PantryItem a = repo.save(item("Milk"));
		PantryItem b = repo.save(item("Eggs"));
		assertNotEquals(0, a.getId());
		assertNotEquals(a.getId(), b.getId());
	}

	@Test
	void findByIdReturnsTheSavedItem() {
		PantryItem saved = repo.save(item("Milk"));
		Optional<PantryItem> found = repo.findById(saved.getId());
		assertTrue(found.isPresent());
		assertEquals("Milk", found.get().getName());
	}

	@Test
	void findByIdIsEmptyForUnknownId() {
		assertTrue(repo.findById(999).isEmpty());
	}

	@Test
	void savingAnExistingItemUpdatesInsteadOfDuplicating() {
		PantryItem saved = repo.save(item("Milk"));
		saved.setQuantity(5);
		repo.save(saved);
		assertEquals(1, repo.findAll().size());
		assertEquals(5, repo.findById(saved.getId()).get().getQuantity());
	}

	@Test
	void deleteRemovesTheItem() {
		PantryItem saved = repo.save(item("Milk"));
		repo.delete(saved.getId());
		assertTrue(repo.findById(saved.getId()).isEmpty());
	}

	@Test
	void deletingAnUnknownIdDoesNothing() {
		repo.save(item("Milk"));
		repo.delete(999);
		assertEquals(1, repo.findAll().size());
	}

	@Test
	void findAllReturnsACopy() {
		repo.save(item("Milk"));
		List<PantryItem> all = repo.findAll();
		all.clear();
		assertEquals(1, repo.findAll().size());
	}

	@Test
	void findByUserReturnsEveryItemForNow() {
		repo.save(item("Milk"));
		repo.save(item("Eggs"));
		assertEquals(2, repo.findbyUser(1).size());
	}

}
