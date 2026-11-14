package com.mealton.account;

import java.util.ArrayList;
import java.util.List;

import com.mealton.facade.MealtonFacade;
import com.mealton.llm.LLMClient;
import com.mealton.persistence.UserData;
import com.mealton.persistence.UserDataMapper;
import com.mealton.persistence.UserDataStore;
import com.mealton.recipe.StarterRecipes;

/**
 * One logged-in user: their own {@link MealtonFacade} (so nobody sees anyone
 * else's data) and the file their data is saved to. The GUI and the CLI both
 * start a session after a successful login.
 */
public final class Session {
	private final String username;
	private final MealtonFacade facade;
	private final UserDataStore store;
	private final List<String> warnings;

	private Session(String username, MealtonFacade facade, UserDataStore store, List<String> warnings) {
		this.username = username;
		this.facade = facade;
		this.store = store;
		this.warnings = warnings;
	}

	/**
	 * Builds a fresh facade for this user, loads the ready-made recipes, turns on
	 * the AI (if there is one) and puts the user's saved data back.
	 *
	 * @param llm the AI client to use, or null to run without the AI
	 */
	public static Session start(AccountService accounts, String username, LLMClient llm) {
		String name = AccountService.checkUsername(username);
		MealtonFacade facade = new MealtonFacade();
		StarterRecipes.loadInto(facade);
		if (llm != null) {
			facade.enableAgent(llm);
		}
		facade.setMediaDirectory(accounts.userDirectory(name).resolve("media"));
		UserDataStore store = new UserDataStore(accounts.userDirectory(name).resolve("data.json"));
		List<String> warnings = new ArrayList<>();
		try {
			UserData data = store.load().orElse(null);
			warnings.addAll(UserDataMapper.restore(facade, data));
		} catch (IllegalStateException e) {
			warnings.add(e.getMessage()); // damaged file: start fresh, but tell the user
		}
		return new Session(name, facade, store, warnings);
	}

	public String username() {
		return username;
	}

	public MealtonFacade facade() {
		return facade;
	}

	/** Things that could not be loaded (empty when all went well). */
	public List<String> warnings() {
		return List.copyOf(warnings);
	}

	/**
	 * Saves everything. @return null when it worked, otherwise a message that says
	 * what went wrong
	 */
	public String save() {
		try {
			store.save(UserDataMapper.capture(facade));
			return null;
		} catch (RuntimeException e) {
			return e.getMessage();
		}
	}
}