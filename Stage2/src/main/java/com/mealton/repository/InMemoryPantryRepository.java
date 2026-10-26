package com.mealton.repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mealton.pantry.PantryItem;

public class InMemoryPantryRepository implements PantryRepository {

	private final Map<Long, PantryItem> items = new LinkedHashMap<>();
	private long nextId = 1;

	@Override
	public PantryItem save(PantryItem item) {

		if (item.getId() == 0) {
			item.setId(nextId++);
		}
		items.put(item.getId(), item);
		return item;
	}

	@Override
	public Optional<PantryItem> findById(long id) {
		return Optional.ofNullable(items.get(id));
	}

	@Override
	public List<PantryItem> findAll() {
		return new ArrayList<>(items.values());
	}

	@Override
	public void delete(long id) {
		items.remove(id);
	}

	@Override
	public List<PantryItem> findbyUser(long userId) {
		return findAll();
	}

}
