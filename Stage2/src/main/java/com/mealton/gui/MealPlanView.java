package com.mealton.gui;

import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.mealton.facade.MealtonFacade;
import com.mealton.plan.DayPlan;
import com.mealton.plan.MealSlot;
import com.mealton.plan.WeekPlan;
import com.mealton.recipe.Recipe;
import com.mealton.recipe.RecipeIngredient;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The "Meal Plan" page (F02): the week as a grid (Monday to Sunday, breakfast /
 * lunch / dinner). Generate a plan from your recipes, change single meals,
 * check it against your profile, and put everything it needs on the shopping
 * list. (Stage 1 called this page the calendar.)
 */

public class MealPlanView extends BorderPane {
	private final MealtonFacade facade;
	private final Runnable onDataChanged; // lets the pantry/list page refresh after we change the list
	private final GridPane grid = new GridPane();
	private final ComboBox<String> style = new ComboBox<>();
	private final ComboBox<String> meals = new ComboBox<>();
	private final ComboBox<String> dayBox = new ComboBox<>();
	private final ComboBox<String> mealBox = new ComboBox<>();
	private final ComboBox<Recipe> recipeBox = new ComboBox<>();
	private final TextArea notes = new TextArea();
	private final Label status = new Label();

	public MealPlanView(MealtonFacade facade, Runnable onDataChanged) {
		this.facade = facade;
		this.onDataChanged = onDataChanged;
		setPadding(new Insets(12));

		// top: make a plan
		style.getItems().addAll("variety", "pantry");
		style.setValue("variety");
		meals.getItems().addAll("dinner", "lunch,dinner", "all");
		meals.setValue("lunch,dinner");
		Button generate = new Button("Generate plan");
		generate.setOnAction(e -> attempt(() -> {
			facade.generatePlan(style.getValue(), meals.getValue());
			refresh();
			showProblems();
			ok("New plan made (" + style.getValue() + ", " + meals.getValue() + ").");
		}));
		Button clearAll = new Button("Clear plan");
		clearAll.setOnAction(e -> attempt(() -> {
			facade.clearPlan();
			refresh();
			notes.clear();
			ok("The plan was cleared.");
		}));
		HBox top = new HBox(8, new Label("Plan style:"), style, new Label("Meals:"), meals, generate, clearAll);
		top.setPadding(new Insets(0, 0, 10, 0));
		setTop(top);

		// centre: the week
		grid.setHgap(6);
		grid.setVgap(6);
		ColumnConstraints first = new ColumnConstraints();
		first.setPercentWidth(10);
		grid.getColumnConstraints().add(first);
		for (int i = 0; i < 7; i++) {
			ColumnConstraints c = new ColumnConstraints();
			c.setPercentWidth(90.0 / 7);
			grid.getColumnConstraints().add(c);
		}
		setCenter(grid);

		// bottom: change one meal, check, shop
		for (var day : java.time.DayOfWeek.values()) {
			dayBox.getItems().add(day.getDisplayName(TextStyle.FULL, Locale.ENGLISH));
		}
		dayBox.setPromptText("Day");
		for (MealSlot slot : MealSlot.values()) {
			mealBox.getItems().add(slot.label());
		}
		mealBox.setPromptText("Meal");
		recipeBox.setPromptText("Recipe");
		recipeBox.setPrefWidth(260);

		Button set = new Button("Set meal");
		set.setOnAction(e -> attempt(() -> {
			if (dayBox.getValue() == null || mealBox.getValue() == null || recipeBox.getValue() == null) {
				throw new IllegalArgumentException("Choose a day, a meal and a recipe first.");
			}
			facade.setPlannedMeal(dayBox.getValue(), mealBox.getValue(), recipeBox.getValue().getId());
			refresh();
			showProblems();
			ok(dayBox.getValue() + " " + mealBox.getValue().toLowerCase() + ": " + recipeBox.getValue().getTitle()
					+ ".");
		}));
		Button clearMeal = new Button("Clear meal");
		clearMeal.setOnAction(e -> attempt(() -> {
			if (dayBox.getValue() == null || mealBox.getValue() == null) {
				throw new IllegalArgumentException("Choose the day and the meal to clear.");
			}
			facade.clearPlannedMeal(dayBox.getValue(), mealBox.getValue());
			refresh();
			showProblems();
			ok("Cleared " + dayBox.getValue() + " " + mealBox.getValue().toLowerCase() + ".");
		}));
		HBox edit = new HBox(8, dayBox, mealBox, recipeBox, set, clearMeal);

		Button check = new Button("Check plan");
		check.setOnAction(e -> attempt(() -> {
			showProblems();
			ok("Checked the plan against your profile.");
		}));
		Button shop = new Button("Add plan to shopping list");
		shop.setOnAction(e -> attempt(() -> {
			List<RecipeIngredient> added = facade.addPlanToShoppingList();
			onDataChanged.run();
			ok(added.isEmpty() ? "Nothing to add: the pantry and shopping list already cover the plan."
					: "Added to the shopping list: "
							+ added.stream().map(RecipeIngredient::toString).collect(Collectors.joining(", ")) + ".");
		}));
		HBox actions = new HBox(8, check, shop);

		notes.setEditable(false);
		notes.setWrapText(true);
		notes.setPrefRowCount(4);
		notes.setPromptText("Problems with the plan (allergy or diet clashes, too many repeats) show up here.");

		VBox bottom = new VBox(8, new Label("Change a meal:"), edit, actions, notes, status);
		bottom.setPadding(new Insets(10, 0, 0, 0));
		VBox.setVgrow(notes, Priority.NEVER);
		setBottom(bottom);

		refresh();
	}

	/**
	 * Redraws the week and reloads the recipe choices (call this whenever the page
	 * is shown).
	 */
	public void refresh() {
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

		WeekPlan plan = facade.getPlan();
		grid.getChildren().clear();
		List<DayPlan> days = plan.days();
		for (int col = 0; col < days.size(); col++) {
			Label header = new Label(days.get(col).day().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
			header.setStyle("-fx-font-weight: bold;");
			grid.add(header, col + 1, 0);
		}
		int row = 1;
		for (MealSlot slot : MealSlot.values()) {
			Label rowHeader = new Label(slot.label());
			rowHeader.setStyle("-fx-font-weight: bold;");
			grid.add(rowHeader, 0, row);
			for (int col = 0; col < days.size(); col++) {
				String text = days.get(col).get(slot).map(entry -> entry.recipe().getTitle()).orElse("-");
				Label cell = new Label(text);
				cell.setWrapText(true);
				cell.setMaxWidth(Double.MAX_VALUE);
				cell.setMinHeight(48);
				cell.setPadding(new Insets(4));
				cell.setStyle("-fx-border-color: #dddddd; -fx-border-radius: 4;");
				GridPane.setHgrow(cell, Priority.ALWAYS);
				grid.add(cell, col + 1, row);
			}
			row++;
		}
	}

	private void showProblems() {
		List<String> problems = facade.checkPlan();
		notes.setText(problems.isEmpty() ? "The plan looks fine."
				: problems.stream().map(p -> "! " + p).collect(Collectors.joining("\n")));
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