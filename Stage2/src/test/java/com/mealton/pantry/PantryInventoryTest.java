package com.mealton.pantry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mealton.repository.InMemoryPantryRepository;

class PantryInventoryTest {

	// "Today" is frozen at 2026-10-26 so the tests give the same answer every day
	private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneId.of("UTC"));
	private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);

	private PantryInventory inventory;
	private int changeCount;
	private List<String> expiringNames;

	// A tiny observer that just records what it was told
	private class RecordingObserver implements PantryObserver {
		@Override
		public void onItemExpiring(PantryItem item) {
			expiringNames.add(item.getName());
		}

		@Override
		public void onPantryChanged() {
			changeCount++;
		}
	}

	@BeforeEach
	void setUp() {
		inventory = new PantryInventory(new InMemoryPantryRepository(), FIXED_CLOCK);
		changeCount = 0;
		expiringNames = new ArrayList<>();
		inventory.attach(new RecordingObserver());
	}

	private PantryItem item(String name, double qty, String unit, LocalDate expiry) {
		return new PantryItem(name, qty, unit, "Other", expiry);
	}

	@Test
	void addItemStoresItAndNotifiesObservers() {
		inventory.addItem(item("Rice", 1, "kg", null));
		assertEquals(1, inventory.getItems().size());
		assertEquals(1, changeCount);
	}

	@Test
	void addingSameIngredientMergesQuantitiesIgnoringCase() {
		inventory.addItem(item("Milk", 1, "L", null));
		inventory.addItem(item("milk", 2, "l", null));
		assertEquals(1, inventory.getItems().size());
		assertEquals(3.0, inventory.getItems().get(0).getQuantity(), 0.0001);
	}

	@Test
	void mergingKeepsTheEarlierExpiryDate() {
		inventory.addItem(item("Milk", 1, "L", TODAY.plusDays(5)));
		inventory.addItem(item("Milk", 1, "L", TODAY.plusDays(2)));
		assertEquals(TODAY.plusDays(2), inventory.getItems().get(0).getExpiryDate());
	}

	@Test
	void sameNameWithDifferentUnitIsNotMerged() {
		inventory.addItem(item("Flour", 1, "kg", null));
		inventory.addItem(item("Flour", 2, "cups", null));
		assertEquals(2, inventory.getItems().size());
	}

	@Test
	void getExpiringItemsReturnsExpiredAndSoonOnesSoonestFirst() {
		inventory.addItem(item("Fresh", 1, "pcs", TODAY.plusDays(10)));
		inventory.addItem(item("Soon", 1, "pcs", TODAY.plusDays(2)));
		inventory.addItem(item("Old", 1, "pcs", TODAY.minusDays(1)));
		inventory.addItem(item("NoDate", 1, "pcs", null));

		List<PantryItem> expiring = inventory.getExpiringItems();
		assertEquals(2, expiring.size());
		assertEquals("Old", expiring.get(0).getName());
		assertEquals("Soon", expiring.get(1).getName());
	}

	@Test
	void checkFreshnessNotifiesObserversAboutExpiringItems() {
		inventory.addItem(item("Fresh", 1, "pcs", TODAY.plusDays(10)));
		inventory.addItem(item("Soon", 1, "pcs", TODAY.plusDays(1)));
		inventory.checkFreshness();
		assertEquals(1, expiringNames.size());
		assertEquals("Soon", expiringNames.get(0));
	}

	@Test
	void removeItemsDeletesThemAndIgnoresUnknownIds() {
		PantryItem milk = inventory.addItem(item("Milk", 1, "L", null));
		inventory.addItem(item("Rice", 1, "kg", null));
		List<PantryItem> removed = inventory.removeItems(List.of(milk.getId(), 999L));
		assertEquals(1, removed.size());
		assertEquals(1, inventory.getItems().size());
	}

	@Test
	void removingNothingDoesNotNotify() {
		inventory.removeItems(List.of(999L));
		assertEquals(0, changeCount);
	}

	@Test
	void updateItemWithUnknownIdThrows() {
		PantryItem ghost = item("Ghost", 1, "pcs", null);
		ghost.setId(123);
		assertThrows(IllegalArgumentException.class, () -> inventory.updateItem(ghost));
	}

	@Test
	void detachedObserverIsNoLongerNotified() {
		PantryObserver extra = new RecordingObserver();
		inventory.attach(extra);
		inventory.detach(extra);
		inventory.addItem(item("Rice", 1, "kg", null));
		assertEquals(1, changeCount); // only the observer from setUp() heard it
		assertFalse(inventory.getItems().isEmpty());
		assertTrue(inventory.findById(inventory.getItems().get(0).getId()).isPresent());
	}
}