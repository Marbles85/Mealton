package com.mealton.pantry;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.mealton.repository.PantryRepository;

public class PantryInventory {

	public static final int EXPIRY_THRESHOLD_DAYS = Freshness.USE_SOON_THRESHOLD_DAYS;

	private final PantryRepository repository;
	private final List<PantryObserver> observers = new ArrayList<>();
	private final Clock clock;

	public PantryInventory(PantryRepository repository) {
		this(repository, Clock.systemDefaultZone());
	}

	public PantryInventory(PantryRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	public PantryItem addItem(PantryItem item) {
		Optional<PantryItem> duplicate = findDuplicate(item);
		PantryItem stored;
		if (duplicate.isPresent()) {
			stored = duplicate.get();
			stored.setQuantity(stored.getQuantity() + item.getQuantity());
			stored.setExpiryDate(earlier(stored.getExpiryDate(), item.getExpiryDate()));
			repository.save(stored);
		} else {
			stored = repository.save(item);
		}

		notifyChanged();
		return stored;
	}

	private Optional<PantryItem> findDuplicate(PantryItem item) {
		return repository.findAll().stream().filter(
				p -> p.getName().equalsIgnoreCase(item.getName()) && p.getUnit().equalsIgnoreCase(item.getUnit()))
				.findFirst();
	}

	private static LocalDate earlier(LocalDate a, LocalDate b) {
		if (a == null) {
			return b;
		}

		if (b == null) {
			return a;
		}

		return a.isBefore(b) ? a : b;
	}

	public void updateItem(PantryItem item) {
		if (repository.findById(item.getId()).isEmpty()) {
			throw new IllegalArgumentException("No pantry item with id " + item.getId());
		}
		repository.save(item);
		notifyChanged();
	}

	public List<PantryItem> removeItems(Collection<Long> ids) {
		List<PantryItem> removed = new ArrayList<>();
		for (Long id : ids) {
			Optional<PantryItem> found = repository.findById(id);
			if (found.isPresent()) {
				repository.delete(id);
				removed.add(found.get());
			}
		}

		if (!removed.isEmpty()) {
			notifyChanged();
		}

		return removed;
	}

	public List<PantryItem> getItems() {
		return repository.findAll();
	}

	public Optional<PantryItem> findById(long id) {
		return repository.findById(id);
	}

	public List<PantryItem> getExpiringItems() {
		LocalDate today = today();
		return repository.findAll().stream()
				.filter(p -> p.getExpiryDate() != null && p.daysLeft(today) <= EXPIRY_THRESHOLD_DAYS)
				.sorted(Comparator.comparingInt(p -> p.daysLeft(today))).toList();
	}

	public void checkFreshness() {
		for (PantryItem item : getExpiringItems()) {
			notifyExpiring(item);
		}
	}

	public LocalDate today() {
		return LocalDate.now(clock);
	}

	public void attach(PantryObserver observer) {
		if (!observers.contains(observer)) {
			observers.add(observer);
		}
	}

	public void detach(PantryObserver observer) {
		observers.remove(observer);
	}

	public void notifyExpiring(PantryItem item) {
		for (PantryObserver o : new ArrayList<>(observers)) {
			o.onItemExpiring(item);
		}
	}

	public void notifyChanged() {
		for (PantryObserver o : new ArrayList<>(observers)) {
			o.onPantryChanged();
		}
	}
}
