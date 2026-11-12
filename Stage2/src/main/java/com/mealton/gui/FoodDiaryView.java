package com.mealton.gui;

import com.mealton.facade.MealtonFacade;
import com.mealton.nutrition.LoggedFood;
import com.mealton.nutrition.Nutrients;
import com.mealton.recipe.Recipe;
import com.mealton.util.Formats;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The "Food Diary" page (F04, with F11): what you ate today, the totals
 * compared with your profile goals, and three ways to log food: a food from the
 * built-in table, one serving of a recipe (using its nutrition estimate), or
 * your own numbers.
 */
public class FoodDiaryView extends BorderPane {
	private final MealtonFacade facade;
	private final Label progress = new Label();
	private final ListView<LoggedFood> entries = new ListView<>();
	private final Label status = new Label();

	private final TextField foodField = new TextField();
	private final TextField amountField = new TextField("1");
	private final TextField unitField = new TextField();

	private final ComboBox<Recipe> recipeBox = new ComboBox<>();
	private final TextField servingsField = new TextField("1");

	private final TextField customName = new TextField();
	private final TextField customKcal = new TextField();
	private final TextField customProtein = new TextField();
	private final TextField customCarbs = new TextField();
	private final TextField customFat = new TextField();

	public FoodDiaryView(MealtonFacade facade) {
		this.facade = facade;
		setPadding(new Insets(12));

		Label title = new Label("Today's food diary");
		title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
		progress.setStyle("-fx-font-family: monospace;");
		progress.setWrapText(true);

		entries.setPrefHeight(180);
		Button delete = new Button("Delete selected entry");
		delete.setOnAction(e -> attempt(() -> {
			LoggedFood selected = entries.getSelectionModel().getSelectedItem();
			if (selected == null) {
				throw new IllegalArgumentException("Select an entry in the list first.");
			}
			facade.deleteLoggedFood(selected.id());
			refresh();
			ok("Deleted " + selected.description() + ".");
		}));

		VBox top = new VBox(8, title, progress, new Label("Eaten today:"), entries, delete);
		VBox.setVgrow(entries, Priority.ALWAYS);

		// 1. a food from the built-in table
		foodField.setPromptText("Food, e.g. egg");
		amountField.setPrefColumnCount(5);
		unitField.setPromptText("unit (g, ml, cup...) or empty for pieces");
		Button logFood = new Button("Log food");
		logFood.setOnAction(e -> attempt(() -> {
			LoggedFood logged = facade.logFood(foodField.getText().trim(),
					Formats.parseQuantity(amountField.getText().trim()), unitField.getText().trim());
			foodField.clear();
			refresh();
			ok("Logged " + logged + ".");
		}));
		HBox foodRow = new HBox(8, foodField, new Label("Amount"), amountField, unitField, logFood);
		HBox.setHgrow(foodField, Priority.ALWAYS);
		HBox.setHgrow(unitField, Priority.ALWAYS);
		TitledPane foodPane = new TitledPane("Log a food", foodRow);

		// 2. a serving of a recipe
		recipeBox.setPromptText("Recipe");
		recipeBox.setPrefWidth(280);
		servingsField.setPrefColumnCount(5);
		Button logRecipe = new Button("Log servings");
		logRecipe.setOnAction(e -> attempt(() -> {
			if (recipeBox.getValue() == null) {
				throw new IllegalArgumentException("Choose a recipe first.");
			}
			LoggedFood logged = facade.logRecipeServings(recipeBox.getValue().getId(),
					Formats.parseQuantity(servingsField.getText().trim()));
			refresh();
			ok("Logged " + logged + ".");
		}));
		TitledPane recipePane = new TitledPane("Log a recipe serving",
				new HBox(8, recipeBox, new Label("Servings"), servingsField, logRecipe));

		// 3. your own numbers
		customName.setPromptText("What you ate");
		customKcal.setPromptText("kcal");
		customProtein.setPromptText("protein g");
		customCarbs.setPromptText("carbs g");
		customFat.setPromptText("fat g");
		for (TextField f : new TextField[] { customKcal, customProtein, customCarbs, customFat }) {
			f.setPrefColumnCount(6);
		}
		Button logCustom = new Button("Log with my numbers");
		logCustom.setOnAction(e -> attempt(() -> {
			if (customName.getText().isBlank()) {
				throw new IllegalArgumentException("Write what you ate first.");
			}
			Nutrients n = new Nutrients(Formats.parseQuantity(customKcal.getText().trim()),
					Formats.parseQuantity(customProtein.getText().trim()),
					Formats.parseQuantity(customCarbs.getText().trim()),
					Formats.parseQuantity(customFat.getText().trim()));
			LoggedFood logged = facade.logCustomFood(customName.getText().trim(), n, true);
			customName.clear();
			customKcal.clear();
			customProtein.clear();
			customCarbs.clear();
			customFat.clear();
			refresh();
			ok("Logged " + logged + ".");
		}));
		HBox customRow = new HBox(8, customName, customKcal, customProtein, customCarbs, customFat, logCustom);
		HBox.setHgrow(customName, Priority.ALWAYS);
		TitledPane customPane = new TitledPane("Log with my own numbers", customRow);
		customPane.setExpanded(false);

		VBox page = new VBox(12, top, foodPane, recipePane, customPane, status);
		page.setPadding(new Insets(0, 8, 8, 0));
		ScrollPane scroll = new ScrollPane(page);
		scroll.setFitToWidth(true);
		setCenter(scroll);

		refresh();
	}

	/**
	 * Reloads today's entries, the totals and the recipe choices (call when the
	 * page is shown or data changed).
	 */
	public void refresh() {
		progress.setText(facade.getTodaysProgress().summary());
		entries.getItems().setAll(facade.getTodaysFood());
		Recipe chosen = recipeBox.getValue();
		recipeBox.getItems().setAll(facade.getRecipes());
		if (chosen != null) {
			for (Recipe r : recipeBox.getItems()) {
				if (r.getId() == chosen.getId()) {
					recipeBox.setValue(r);
					break;
				}
			}
		}
	}

	/** Runs an action and shows a red message if it fails with a normal error. */
	private void attempt(Runnable action) {
		try {
			action.run();
		} catch (IllegalArgumentException e) {
			status.setStyle("-fx-text-fill: #c62828;");
			status.setText(e.getMessage());
		}
	}

	private void ok(String message) {
		status.setStyle("-fx-text-fill: #2e7d32;");
		status.setText(message);
	}
}