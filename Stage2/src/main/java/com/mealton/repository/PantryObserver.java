package com.mealton.repository;

public interface PantryObserver {

	void onItemExpiring(PantryItem item);

	void onPantryChanged();
}
