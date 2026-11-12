package com.mealton.gui;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.mealton.facade.MealtonFacade;
import com.mealton.nutrition.RecipeNutrition;
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
 * The "My Recipes" tab (F13): the recipes you made plus the library recipes you
 * saved with the Save heart, with star ratings, scaling, adding missing
 * ingredients to the shopping list, and adding your own recipes (DIY). It is
 * shown inside the {@link RecipeBrowserView}.
 */
public class RecipesView extends BorderPane {
	private final MealtonFacade facade;
	private final Runnable onDataChanged; // lets the pantry/list page refresh after we change the list
	private final Runnable onRecipesChanged; // lets the other recipe tabs refresh after My Recipes changes
	private final ListView<Recipe> list = new ListView<>();
	private final TextArea detail = new TextArea();
	private final Label status = new Label();
	private final ComboBox<Integer> stars = new ComboBox<>();
	private final Spinner<Integer> servings = new Spinner<>(1, 100, 4);
	private final Button remove = new Button("Delete recipe");
	private final TextField titleField = new TextField();
	private final TextField servingsField = new TextField("2");
	private final TextArea ingredientsArea = new TextArea();
	private final TextField stepsField = new TextField();

	public RecipesView(MealtonFacade facade, Runnable onDataChanged, Runnable onRecipesChanged) {
		this.facade = facade;
		this.onDataChanged = onDataChanged;
		this.onRecipesChanged = onRecipesChanged;
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
		Label listTitle = new Label("My Recipes");
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

		remove.setOnAction(e -> attempt(this::removeSelected));

		HBox actions = new HBox(8, stars, rate, servings, scale);
		HBox actions2 = new HBox(8, missing, remove);

		VBox centre = new VBox(8, detail, actions, actions2);
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
		list.getItems().setAll(facade.getMyRecipes());
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
			remove.setText("Delete recipe");
			detail.setText(list.getItems().isEmpty()
					? "My Recipes is empty. Save recipes you like with the Save button in 'Can make now' or 'Almost there', "
							+ "or open 'Add a recipe' below to write your own."
					: "Select a recipe on the left to see it.");
			return;
		}
		remove.setText(selected.isLibrary() ? "Remove from My Recipes" : "Delete recipe");
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
		onRecipesChanged.run();
		ok("Saved '" + saved.getTitle() + "' to My Recipes.");
	}

	/**
	 * A library recipe is only taken out of My Recipes (it stays in the browser
	 * tabs). A recipe you made yourself is deleted for good, after asking.
	 */
	private void removeSelected() {
		Recipe selected = list.getSelectionModel().getSelectedItem();
		if (selected == null) {
			throw new IllegalArgumentException("Select a recipe on the left first.");
		}
		if (selected.isLibrary()) {
			facade.removeFromMyRecipes(selected.getId());
			list.getSelectionModel().clearSelection();
			refresh();
			onRecipesChanged.run();
			ok("Removed '" + selected.getTitle() + "' from My Recipes.");
			return;
		}
		Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete '" + selected.getTitle() + "' for good?",
				ButtonType.YES, ButtonType.NO);
		Optional<ButtonType> answer = confirm.showAndWait();
		if (answer.isPresent() && answer.get() == ButtonType.YES) {
			facade.deleteRecipe(selected.getId());
			list.getSelectionModel().clearSelection();
			refresh();
			onRecipesChanged.run();
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

	static String describe(Recipe r) {
		StringBuilder sb = new StringBuilder(r.getTitle()).append(" (").append(r.getServings()).append(" servings, ")
				.append(r.isRated() ? r.getRating() + "/5 stars" : "not rated").append(")\n")
				.append(RecipeNutrition.of(r).line()).append("\n\nIngredients:");
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

	static String describeMatch(RecipeMatch m) {
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