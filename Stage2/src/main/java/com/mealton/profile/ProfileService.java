package com.mealton.profile;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Keeps the user's profile (feature F01) and tells interested parties when it
 * changes (Observer pattern). The facade listens so the AI agent always knows
 * the current diet and allergies.
 */
public class ProfileService {
	private UserProfile profile = UserProfile.empty();
	private final List<Consumer<UserProfile>> listeners = new ArrayList<>();

	public UserProfile get() {
		return profile;
	}

	/**
	 * Registers a listener that is called with the new profile after every change.
	 */
	public void addListener(Consumer<UserProfile> listener) {
		listeners.add(listener);
	}

	public UserProfile setName(String name) {
		return change(p -> p.withName(name));
	}

	/** Sets the diet from text such as "vegan". */
	public UserProfile setDiet(String diet) {
		Diet parsed = Diet.fromText(diet);
		return change(p -> p.withDiet(parsed));
	}

	public UserProfile addAllergy(String allergy) {
		return change(p -> p.withAllergy(allergy));
	}

	public UserProfile removeAllergy(String allergy) {
		return change(p -> p.withoutAllergy(allergy));
	}

	public UserProfile setCalorieGoal(int calories) {
		return change(p -> p.withCalorieGoal(calories));
	}

	public UserProfile setMacroGoals(int protein, int carbs, int fat) {
		return change(p -> p.withMacroGoals(protein, carbs, fat));
	}

	/** Clears everything. */
	public UserProfile reset() {
		return change(p -> UserProfile.empty());
	}

	/**
	 * Applies a change; if it is invalid the old profile stays and no listener is
	 * called.
	 */
	private UserProfile change(UnaryOperator<UserProfile> edit) {
		UserProfile updated = edit.apply(profile);
		profile = updated;
		for (Consumer<UserProfile> listener : new ArrayList<>(listeners)) {
			listener.accept(updated);
		}
		return updated;
	}
}