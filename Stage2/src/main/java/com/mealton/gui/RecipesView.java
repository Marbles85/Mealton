package com.mealton.gui;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.mealton.facade.MealtonFacade;
import com.mealton.recipe.Recipe;
import com.mealton.recipe.RecipeIngredient;
import com.mealton.recipe.RecipeMatch;
import com.mealton.recipe.RecipeParser;

import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The "Recipes" page (F13, F07, F08): your saved recipes with star ratings and
 * scaling, what you can cook with the pantry (with allergy and diet warnings),
 * and putting missing ingredients on the shopping list.
 */
public class RecipesView extends BorderPane {
	private final MealtonFacade facade;
	private final Runnable onDataChanged; // lets the pantry/list page refresh after we change the list
	private final ListView<Recipe> list = new ListView<>();
	private final TextArea detail = new TextArea();
	private final Label status = new Label();
	private final ComboBox<Integer> stars = new ComboBox<>();
	private final Spinner<Integer> servings = new Spinner<>(1, 100, 4);
	private final ComboBox<String> priority = new ComboBox<>();
	private final TextField titleField = new TextField();
	private final TextField servingsField = new TextField("2");
	private final TextArea ingredientsArea = new TextArea();
	private final TextField stepsField = new TextField();

	public RecipesView(MealtonFacade facade, Runnable onDataChanged) {
		this.facade = facade;
		this.onDataChanged = onDataChanged;
		setPadding(new Insets(12));

		// left: the list of saved recipes
		list.setPrefWidth(250);
		list.setCellFactory(v -> new ListCell<>() {
			@Override
			protected void updateItem(Recipe r, boolean empty) {
				super.updateItem(r, empty);
				setText(empty || r == null ? null
						: "#" + r.getId() + " " + r.getTitle() + "   "
								+ (r.isRated() ? r.getRating() + "/5" : "not rated"));
			}
		});
		list.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> showSelected());
		Label listTitle = new Label("My recipes");
		listTitle.setStyle("-fx-font-weight: bold;");
		VBox left = new VBox(8, listTitle, list);
		VBox.setVgrow(list, Priority.ALWAYS);
		left.setPadding(new Insets(0, 12, 0, 0));
		setLeft(left);

		// centre: details and actions
		detail.setEditable(false);
		detail.setWrapText(true);
		VBox.setVgrow(detail, Priority.ALWAYS);

		stars.getItems().addAll(1, 2, 3, 4, 5);
		stars.setPromptText("Stars");
		Button rate = new Button("Rate");
		rate.setOnAction(e -> attempt(() -> {
			if (stars.getValue() == null) {
				throw new IllegalArgumentException("Choose 1 to 5 stars first.");
			}
			Recipe rated = facade.rateRecipe(selectedId(), stars.getValue());
			refresh();
			ok("Rated '" + rated.getTitle() + "' " + rated.getRating() + "/5.");
		}));

		Button scale = new Button("Scale to servings");
		scale.setOnAction(e -> attempt(() -> {
			detail.setText(describe(facade.scaledRecipe(selectedId(), servings.getValue()))
					+ "\n\n(Scaled copy. The saved recipe is unchanged.)");
			ok("Showing a scaled copy.");
		}));

		Button missing = new Button("Add missing to shopping list");
		missing.setOnAction(e -> attempt(() -> {
			long id = selectedId();
			List<RecipeIngredient> added = facade.addMissingToShoppingList(id);
			onDataChanged.run();
			showSelected();
			ok(added.isEmpty() ? "Nothing to add: the pantry and shopping list already cover it."
					: "Added to the shopping list: " + joinIngredients(added) + ".");
		}));

		Button delete = new Button("Delete recipe");
		delete.setOnAction(e -> attempt(this::deleteSelected));

		HBox actions = new HBox(8, stars, rate, servings, scale);
		HBox actions2 = new HBox(8, missing, delete);

		priority.getItems().addAll("best", "expiring");
		priority.setValue("best");
		Button suggest = new Button("What can I cook with my pantry?");
		suggest.setOnAction(e -> attempt(() -> {
			List<RecipeMatch> matches = facade.suggestRecipes(priority.getValue());
			if (matches.isEmpty()) {
				detail.setText("You have no saved recipes to compare with the pantry. Add one below.");
			} else {
				detail.setText(matches.stream().map(RecipesView::describeMatch).collect(Collectors.joining("\n\n")));
			}
			ok("Recipes ranked against your pantry (" + priority.getValue() + " first).");
		}));
		HBox suggestBar = new HBox(8, suggest, new Label("Show first:"), priority);

		VBox centre = new VBox(8, detail, actions, actions2, suggestBar);
		setCenter(centre);

		// bottom: add a recipe (collapsed to save space)
		titleField.setPromptText("Title, e.g. Pancakes");
		servingsField.setPrefColumnCount(4);
		ingredientsArea
				.setPromptText("Ingredients on one line, separated by semicolons:  200 g flour; 2 egg; 0.3 l milk");
		ingredientsArea.setPrefRowCount(2);
		ingredientsArea.setWrapText(true);
		stepsField.setPromptText("Steps separated by | (optional), e.g. Mix|Fry");
		Button save = new Button("Save recipe");
		save.setOnAction(e -> attempt(this::saveNew));
		HBox titleRow = new HBox(8, titleField, new Label("Servings"), servingsField);
		HBox.setHgrow(titleField, Priority.ALWAYS);
		VBox form = new VBox(6, titleRow, ingredientsArea, stepsField, save);
		TitledPane addPane = new TitledPane("Add a recipe", form);
		addPane.setExpanded(false);

		VBox bottom = new VBox(6, status, addPane);
		bottom.setPadding(new Insets(8, 0, 0, 0));
		setBottom(bottom);

		refresh();
	}

	/**
	 * Reloads the list (and the details) from the facade; keeps the selection if
	 * the recipe still exists.
	 */
	public void refresh() {
		Recipe selected = list.getSelectionModel().getSelectedItem();
		list.getItems().setAll(facade.getRecipes());
		if (selected != null) {
			selectById(selected.getId());
		}
		showSelected();
	}

	private void selectById(long id) {
		for (Recipe r : list.getItems()) {
			if (r.getId() == id) {
				list.getSelectionModel().select(r);
				return;
			}
		}
	}

	private long selectedId() {
		Recipe selected = list.getSelectionModel().getSelectedItem();
		if (selected == null) {
			throw new IllegalArgumentException("Select a recipe on the left first.");
		}
		return selected.getId();
	}

	private void showSelected() {
		Recipe selected = list.getSelectionModel().getSelectedItem();
		if (selected == null) {
			detail.setText(list.getItems().isEmpty()
					? "You have no saved recipes yet. Open 'Add a recipe' below to save your first one."
					: "Select a recipe on the left to see it, or press the pantry button to see what you can cook.");
			return;
		}
		try {
			detail.setText(describe(facade.getRecipe(selected.getId())) + "\n\n"
					+ describeMatch(facade.checkRecipe(selected.getId())));
		} catch (IllegalArgumentException e) {
			detail.setText("This recipe no longer exists.");
		}
	}

	private void saveNew() {
		int servingCount;
		try {
			servingCount = Integer.parseInt(servingsField.getText().trim());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Servings must be a whole number.");
		}
		Recipe recipe = new Recipe(titleField.getText(), servingCount,
				RecipeParser.parseIngredients(ingredientsArea.getText()),
				RecipeParser.parseSteps(stepsField.getText()));
		Recipe saved = facade.saveRecipe(recipe);
		titleField.clear();
		ingredientsArea.clear();
		stepsField.clear();
		refresh();
		selectById(saved.getId());
		ok("Saved '" + saved.getTitle() + "'.");
	}

	private void deleteSelected() {
		Recipe selected = list.getSelectionModel().getSelectedItem();
		if (selected == null) {
			throw new IllegalArgumentException("Select a recipe on the left first.");
		}
		Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete '" + selected.getTitle() + "'?", ButtonType.YES,
				ButtonType.NO);
		Optional<ButtonType> answer = confirm.showAndWait();
		if (answer.isPresent() && answer.get() == ButtonType.YES) {
			facade.deleteRecipe(selected.getId());
			list.getSelectionModel().clearSelection();
			refresh();
			ok("Deleted '" + selected.getTitle() + "'.");
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

	private static String describe(Recipe r) {
		StringBuilder sb = new StringBuilder(r.getTitle()).append(" (").append(r.getServings()).append(" servings, ")
				.append(r.isRated() ? r.getRating() + "/5 stars" : "not rated").append(")\n\nIngredients:");
		for (RecipeIngredient ingredient : r.getIngredients()) {
			sb.append("\n  - ").append(ingredient);
		}
		if (!r.getSteps().isEmpty()) {
			sb.append("\n\nSteps:");
			int number = 1;
			for (String step : r.getSteps()) {
				sb.append("\n  ").append(number++).append(". ").append(step);
			}
		}
		return sb.toString();
	}

	private static String describeMatch(RecipeMatch m) {
		int total = m.available().size() + m.missing().size();
		StringBuilder sb = new StringBuilder(
				"#" + m.recipe().getId() + " " + m.recipe().getTitle() + ": " + (m.isCookable() ? "can be cooked now"
						: m.available().size() + " of " + total + " ingredients available"));
		if (m.expiringUsed() > 0) {
			sb.append(" (uses ").append(m.expiringUsed()).append(" ingredient(s) that will spoil soon)");
		}
		if (!m.available().isEmpty()) {
			sb.append("\n    have:    ").append(joinIngredients(m.available()));
		}
		if (!m.missing().isEmpty()) {
			sb.append("\n    missing: ").append(joinIngredients(m.missing()));
		}
		for (String warning : m.warnings()) {
			sb.append("\n    WARNING: ").append(warning);
		}
		return sb.toString();
	}

	private static String joinIngredients(List<RecipeIngredient> ingredients) {
		return ingredients.stream().map(RecipeIngredient::toString).collect(Collectors.joining(", "));
	}
}