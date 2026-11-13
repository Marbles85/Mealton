package com.mealton.gui;

import java.util.LinkedHashMap;
import java.util.Map;

import com.mealton.facade.MealtonFacade;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Main window: a header with page navigation and the current page in the centre
 * (Stage 1: MainWindow).
 */
public class MainWindow extends BorderPane {
	public static final String PANTRY_PAGE = "Pantry & List";
	public static final String CHAT_PAGE = "Mealton Chat";
	public static final String RECIPES_PAGE = "Recipes";
	public static final String MEAL_PLAN_PAGE = "Meal Plan";
	public static final String FOOD_DIARY_PAGE = "Food Diary";
	public static final String PROFILE_PAGE = "Profile";

	private final Map<String, Node> pages = new LinkedHashMap<>();

	private final Runnable onSave;

	/**
	 * @param username the logged-in user (shown in the header)
	 * @param onSave   saves the user's data; called whenever the user changes page
	 * @param onLogout called when the user presses "Log out"
	 */
	public MainWindow(MealtonFacade facade, String username, Runnable onSave, Runnable onLogout) {
		this.onSave = onSave;
		PantryShoppingView pantryView = new PantryShoppingView(facade);
		RecipeBrowserView recipesView = new RecipeBrowserView(facade, pantryView::refreshAll);
		MealPlanView planView = new MealPlanView(facade, pantryView::refreshAll);
		FoodDiaryView diaryView = new FoodDiaryView(facade);
		pages.put(PANTRY_PAGE, pantryView);
		// after the AI agent uses a tool, the pantry/shopping, recipe, plan and diary
		// pages reload what changed
		pages.put(CHAT_PAGE, new ChatView(facade, () -> {
			pantryView.refreshAll();
			recipesView.refresh();
			planView.refresh();
			diaryView.refresh();
		}));
		pages.put(RECIPES_PAGE, recipesView);
		pages.put(MEAL_PLAN_PAGE, planView);
		pages.put(FOOD_DIARY_PAGE, diaryView);
		pages.put(PROFILE_PAGE, new ProfileView(facade));

		Label title = new Label("Mealton");
		title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

		HBox nav = new HBox(8);
		nav.setAlignment(Pos.CENTER_LEFT);
		for (String name : pages.keySet()) {
			Button b = new Button(name);
			b.setOnAction(e -> showPage(name));
			nav.getChildren().add(b);
		}
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		Label signedIn = new Label("Signed in as " + username);
		Button logout = new Button("Log out");
		logout.setOnAction(e -> onLogout.run());
		HBox header = new HBox(20, title, nav, spacer, signedIn, logout);
		header.setAlignment(Pos.CENTER_LEFT);
		header.setPadding(new Insets(10, 16, 10, 16));
		header.setStyle("-fx-border-color: #dddddd; -fx-border-width: 0 0 1 0;");
		setTop(header);

		show(PANTRY_PAGE);
	}

	/**
	 * Shows the page with this name in the centre of the window, and saves the
	 * user's data first.
	 */
	public void showPage(String name) {
		onSave.run(); // leaving a page is a good moment to save
		show(name);
	}

	private void show(String name) {
		Node page = pages.get(name);
		if (page != null) {
			// the recipe and plan pages depend on the pantry, recipes and profile, so
			// reload them every time they are opened
			if (page instanceof RecipeBrowserView browser) {
				browser.refresh();
			} else if (page instanceof MealPlanView plan) {
				plan.refresh();
			} else if (page instanceof FoodDiaryView diary) {
				diary.refresh();
			}
			setCenter(page);
		}
	}
}