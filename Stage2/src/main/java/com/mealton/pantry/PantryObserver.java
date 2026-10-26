package com.mealton.pantry;

public interface PantryObserver {

	void onItemExpiring(PantryItem item);

	void onPantryChanged();
}
