package com.mealton.repository;

import java.util.List;

import com.mealton.pantry.PantryItem;

public interface PantryRepository extends Repository<PantryItem> {

	List<PantryItem> findbyUser(long userId);
}
