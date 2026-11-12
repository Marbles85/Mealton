package com.mealton.gui;

import java.util.Arrays;

import com.mealton.facade.MealtonFacade;
import com.mealton.profile.Diet;
import com.mealton.profile.UserProfile;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * The "Profile" page (F01): name, diet, allergies and daily calorie and macro
 * goals. Diet and allergies are saved as soon as they are changed; name and
 * goals are saved with the Save button. If the AI changes the profile in the
 * chat, this page updates itself.
 */
public class ProfileView extends VBox {
	private final MealtonFacade facade;
	private final TextField nameField = new TextField();
	private final ComboBox<String> dietBox = new ComboBox<>();
	private final ListView<String> allergyList = new ListView<>();
	private final TextField allergyField = new TextField();
	private final TextField caloriesField = new TextField();
	private final TextField proteinField = new TextField();
	private final TextField carbsField = new TextField();
	private final TextField fatField = new TextField();
	private final Label status = new Label();
	private boolean loading; // true while the controls are being filled from the profile
	private boolean selfChange; // true while this page itself changes the profile

	public ProfileView(MealtonFacade facade) {
		this.facade = facade;
		setSpacing(14);
		setPadding(new Insets(16));

		Label title = new Label("Your profile");
		title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

		dietBox.getItems().addAll(Arrays.stream(Diet.values()).map(Diet::label).toList());
		dietBox.setOnAction(e -> {
			if (!loading && dietBox.getValue() != null) {
				change(() -> facade.setDiet(dietBox.getValue()), "Diet saved.");
			}
		});

		nameField.setPromptText("Your name (optional)");
		caloriesField.setPromptText("kcal per day, blank = none");
		proteinField.setPromptText("g");
		carbsField.setPromptText("g");
		fatField.setPromptText("g");

		GridPane form = new GridPane();
		form.setHgap(10);
		form.setVgap(8);
		form.addRow(0, new Label("Name"), nameField);
		form.addRow(1, new Label("Diet"), dietBox);
		form.addRow(2, new Label("Calories per day"), caloriesField);
		form.addRow(3, new Label("Protein (g)"), proteinField);
		form.addRow(4, new Label("Carbs (g)"), carbsField);
		form.addRow(5, new Label("Fat (g)"), fatField);

		Button save = new Button("Save name and goals");
		save.setOnAction(e -> saveDetails());
		Button reset = new Button("Reset profile");
		reset.setOnAction(e -> change(facade::resetProfile, "Profile cleared."));

		allergyList.setPrefHeight(120);
		allergyField.setPromptText("Food to avoid, e.g. peanut");
		allergyField.setOnAction(e -> addAllergy());
		Button add = new Button("Add");
		add.setOnAction(e -> addAllergy());
		Button remove = new Button("Remove selected");
		remove.setOnAction(e -> removeAllergy());
		HBox allergyBar = new HBox(8, allergyField, add, remove);

		Label allergyTitle = new Label("Allergies and foods to avoid");
		allergyTitle.setStyle("-fx-font-weight: bold;");
		Label hint = new Label("Recipe suggestions and meal plans warn about recipes that clash with your "
				+ "allergies and diet (a helpful warning, not a safety guarantee).");
		hint.setWrapText(true);
		hint.setStyle("-fx-text-fill: #777777; -fx-font-size: 11px;");

		getChildren().addAll(title, form, new HBox(8, save, reset), allergyTitle, allergyList, allergyBar, hint,
				status);

		// the AI can change the profile from the chat, on another thread
		facade.addProfileListener(p -> {
			if (!selfChange) {
				Platform.runLater(this::load);
			}
		});
		load();
	}

	/** Fills every control from the current profile. */
	private void load() {
		UserProfile p = facade.getProfile();
		loading = true;
		try {
			nameField.setText(p.name());
			dietBox.setValue(p.diet().label());
			allergyList.getItems().setAll(p.allergies());
			caloriesField.setText(p.calorieGoal() == 0 ? "" : String.valueOf(p.calorieGoal()));
			proteinField.setText(p.proteinGoal() == 0 ? "" : String.valueOf(p.proteinGoal()));
			carbsField.setText(p.carbsGoal() == 0 ? "" : String.valueOf(p.carbsGoal()));
			fatField.setText(p.fatGoal() == 0 ? "" : String.valueOf(p.fatGoal()));
		} finally {
			loading = false;
		}
	}

	private void saveDetails() {
		change(() -> {
			int calories = whole(caloriesField.getText(), "Calories");
			int protein = whole(proteinField.getText(), "Protein");
			int carbs = whole(carbsField.getText(), "Carbs");
			int fat = whole(fatField.getText(), "Fat");
			facade.setCalorieGoal(calories);
			facade.setMacroGoals(protein, carbs, fat);
			facade.setProfileName(nameField.getText());
		}, "Saved.");
	}

	private void addAllergy() {
		String text = allergyField.getText();
		change(() -> facade.addAllergy(text), "Added '" + (text == null ? "" : text.trim()) + "'.");
		if (status.getText().startsWith("Added")) {
			allergyField.clear();
		}
	}

	private void removeAllergy() {
		String selected = allergyList.getSelectionModel().getSelectedItem();
		if (selected == null) {
			showError("Select an allergy in the list first.");
			return;
		}
		change(() -> facade.removeAllergy(selected), "Removed '" + selected + "'.");
	}

	/**
	 * Runs a profile change, then reloads the page and shows the result or the
	 * problem.
	 */
	private void change(Runnable action, String successMessage) {
		selfChange = true;
		try {
			action.run();
			load();
			status.setStyle("-fx-text-fill: #2e7d32;");
			status.setText(successMessage);
		} catch (IllegalArgumentException e) {
			load();
			showError(e.getMessage());
		} finally {
			selfChange = false;
		}
	}

	private void showError(String message) {
		status.setStyle("-fx-text-fill: #c62828;");
		status.setText(message);
	}

	/** A blank field means "not set" (0). */
	private static int whole(String text, String what) {
		if (text == null || text.isBlank()) {
			return 0;
		}
		try {
			return Integer.parseInt(text.trim());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(what + " must be a whole number.");
		}
	}
}