package com.mealton.gui;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.mealton.facade.MealtonFacade;
import com.mealton.plan.CalendarService;
import com.mealton.plan.DayPlan;
import com.mealton.plan.MealEntry;
import com.mealton.plan.MealSlot;
import com.mealton.plan.WeekPlan;
import com.mealton.recipe.Recipe;
import com.mealton.recipe.RecipeIngredient;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The "Meal Plan" page (F02, F05 and F03), one page with two parts:
 * <ol>
 * <li><b>Build a plan</b>: generate a weekly plan from your recipes, tweak it,
 * check it against your profile, and make a prep-day list (one combined
 * ingredient list, with an optional AI schedule).</li>
 * <li><b>Calendar</b>: a month calendar. Pick a date and <i>load</i> the plan
 * onto that date's week (Monday to Sunday). Click any date to see or change its
 * meals.</li>
 * </ol>
 */
public class MealPlanView extends BorderPane {
	private final MealtonFacade facade;
	private final Runnable onDataChanged; // lets the pantry/list page refresh after we change the list

	// part 1: the plan
	private final GridPane planGrid = new GridPane();
	private final ComboBox<String> style = new ComboBox<>();
	private final ComboBox<String> meals = new ComboBox<>();
	private final ComboBox<String> planDayBox = new ComboBox<>();
	private final ComboBox<String> planMealBox = new ComboBox<>();
	private final ComboBox<Recipe> planRecipeBox = new ComboBox<>();
	private final TextArea notes = new TextArea();
	private final Label planStatus = new Label();
	private final TextArea prepArea = new TextArea();
	private final Button aiPrep = new Button("AI prep schedule");

	// part 2: the calendar
	private final GridPane monthGrid = new GridPane();
	private final Label monthTitle = new Label();
	private final Label dayTitle = new Label();
	private final VBox dayMeals = new VBox(4);
	private final ComboBox<String> dayMealBox = new ComboBox<>();
	private final ComboBox<Recipe> dayRecipeBox = new ComboBox<>();
	private final Label calendarStatus = new Label();
	private YearMonth shown = YearMonth.now();
	private LocalDate selected = LocalDate.now();

	public MealPlanView(MealtonFacade facade, Runnable onDataChanged) {
		this.facade = facade;
		this.onDataChanged = onDataChanged;
		setPadding(new Insets(12));

		VBox page = new VBox(14, buildPlanSection(), new Separator(), buildCalendarSection());
		page.setPadding(new Insets(0, 8, 8, 0));
		ScrollPane scroll = new ScrollPane(page);
		scroll.setFitToWidth(true);
		setCenter(scroll);

		refresh();
	}

	// ---------------- part 1: build a plan ----------------

	private VBox buildPlanSection() {
		Label heading = new Label("1. Build a plan");
		heading.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

		style.getItems().addAll("variety", "pantry");
		style.setValue("variety");
		meals.getItems().addAll("dinner", "lunch,dinner", "all");
		meals.setValue("lunch,dinner");
		Button generate = new Button("Generate plan");
		generate.setOnAction(e -> attempt(planStatus, () -> {
			facade.generatePlan(style.getValue(), meals.getValue());
			refresh();
			showProblems();
			ok(planStatus, "New plan made (" + style.getValue() + ", " + meals.getValue()
					+ "). Load it onto the calendar below when you like it.");
		}));
		Button clearPlan = new Button("Clear plan");
		clearPlan.setOnAction(e -> attempt(planStatus, () -> {
			facade.clearPlan();
			refresh();
			notes.clear();
			ok(planStatus, "The plan was cleared. (Meals already on the calendar stay there.)");
		}));
		HBox controls = new HBox(8, new Label("Plan style:"), style, new Label("Meals:"), meals, generate, clearPlan);

		planGrid.setHgap(6);
		planGrid.setVgap(6);
		ColumnConstraints first = new ColumnConstraints();
		first.setPercentWidth(10);
		planGrid.getColumnConstraints().add(first);
		for (int i = 0; i < 7; i++) {
			ColumnConstraints c = new ColumnConstraints();
			c.setPercentWidth(90.0 / 7);
			planGrid.getColumnConstraints().add(c);
		}

		for (DayOfWeek day : DayOfWeek.values()) {
			planDayBox.getItems().add(day.getDisplayName(TextStyle.FULL, Locale.ENGLISH));
		}
		planDayBox.setPromptText("Day");
		for (MealSlot slot : MealSlot.values()) {
			planMealBox.getItems().add(slot.label());
		}
		planMealBox.setPromptText("Meal");
		planRecipeBox.setPromptText("Recipe");
		planRecipeBox.setPrefWidth(260);
		Button set = new Button("Set meal");
		set.setOnAction(e -> attempt(planStatus, () -> {
			if (planDayBox.getValue() == null || planMealBox.getValue() == null || planRecipeBox.getValue() == null) {
				throw new IllegalArgumentException("Choose a day, a meal and a recipe first.");
			}
			facade.setPlannedMeal(planDayBox.getValue(), planMealBox.getValue(), planRecipeBox.getValue().getId());
			refresh();
			showProblems();
			ok(planStatus, planDayBox.getValue() + " " + planMealBox.getValue().toLowerCase() + ": "
					+ planRecipeBox.getValue().getTitle() + ".");
		}));
		Button clearMeal = new Button("Clear meal");
		clearMeal.setOnAction(e -> attempt(planStatus, () -> {
			if (planDayBox.getValue() == null || planMealBox.getValue() == null) {
				throw new IllegalArgumentException("Choose the day and the meal to clear.");
			}
			facade.clearPlannedMeal(planDayBox.getValue(), planMealBox.getValue());
			refresh();
			showProblems();
			ok(planStatus, "Cleared " + planDayBox.getValue() + " " + planMealBox.getValue().toLowerCase() + ".");
		}));
		TitledPane change = new TitledPane("Change a meal in the plan",
				new HBox(8, planDayBox, planMealBox, planRecipeBox, set, clearMeal));
		change.setExpanded(false);

		Button check = new Button("Check plan");
		check.setOnAction(e -> attempt(planStatus, () -> {
			showProblems();
			ok(planStatus, "Checked the plan against your profile.");
		}));
		Button shop = new Button("Add plan to shopping list");
		shop.setOnAction(e -> attempt(planStatus, () -> {
			List<RecipeIngredient> added = facade.addPlanToShoppingList();
			onDataChanged.run();
			ok(planStatus, added.isEmpty() ? "Nothing to add: the pantry and shopping list already cover the plan."
					: "Added to the shopping list: " + joinIngredients(added) + ".");
		}));
		HBox actions = new HBox(8, check, shop);

		notes.setEditable(false);
		notes.setWrapText(true);
		notes.setPrefRowCount(3);
		notes.setPromptText("Problems with the plan (allergy or diet clashes, too many repeats) show up here.");

		// prep day (F05): one combined list for the week; the AI can turn it into a
		// short schedule
		Button prepList = new Button("Make prep list");
		prepList.setOnAction(e -> attempt(planStatus, () -> {
			prepArea.setText(facade.buildPrepPlan().describe());
			ok(planStatus, "Prep list made from the current plan.");
		}));
		aiPrep.setOnAction(e -> askPrepSchedule());
		prepArea.setEditable(false);
		prepArea.setWrapText(true);
		prepArea.setPrefRowCount(10);
		prepArea.setPromptText("Press 'Make prep list' to see everything the week needs in one list, "
				+ "which ingredients are shared, and which recipes to batch-cook.");
		TitledPane prep = new TitledPane("Prep day", new VBox(8, new HBox(8, prepList, aiPrep), prepArea));
		prep.setExpanded(false);

		return new VBox(8, heading, controls, planGrid, change, actions, notes, prep, planStatus);
	}

	/**
	 * Asks the AI for the prep-day schedule in the background, so the window never
	 * freezes.
	 */
	private void askPrepSchedule() {
		if (facade.getPlan().mealCount() == 0) {
			planStatus.setStyle("-fx-text-fill: #c62828;");
			planStatus.setText("The plan is empty. Generate a plan first, then make the prep list.");
			return;
		}
		aiPrep.setDisable(true);
		prepArea.setText("Asking the AI...");
		Task<String> task = new Task<>() {
			@Override
			protected String call() {
				return facade.prepSchedule();
			}
		};
		task.setOnSucceeded(e -> {
			prepArea.setText(task.getValue());
			aiPrep.setDisable(false);
			ok(planStatus, "Prep schedule ready.");
		});
		task.setOnFailed(e -> {
			prepArea.setText("Could not get a schedule right now. Try 'Make prep list' instead.");
			aiPrep.setDisable(false);
		});
		Thread thread = new Thread(task, "prep-schedule");
		thread.setDaemon(true);
		thread.start();
	}

	// ---------------- part 2: the calendar ----------------

	private VBox buildCalendarSection() {
		Label heading = new Label("2. Calendar");
		heading.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
		Label hint = new Label("Click a date, then load your plan onto that date's week (Monday to Sunday).");

		Button previous = new Button("<");
		previous.setOnAction(e -> {
			shown = shown.minusMonths(1);
			drawMonth();
		});
		Button next = new Button(">");
		next.setOnAction(e -> {
			shown = shown.plusMonths(1);
			drawMonth();
		});
		Button today = new Button("Today");
		today.setOnAction(e -> {
			selected = LocalDate.now();
			shown = YearMonth.from(selected);
			refreshCalendar();
		});
		monthTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
		monthTitle.setMinWidth(150);

		Button load = new Button("Load plan onto this week");
		load.setOnAction(e -> attempt(calendarStatus, () -> {
			int loaded = facade.loadPlanOnCalendar(selected);
			refreshCalendar();
			ok(calendarStatus,
					"Loaded " + loaded + " meal(s) onto the week of " + CalendarService.weekStart(selected) + ".");
		}));
		Button clearWeek = new Button("Clear this week");
		clearWeek.setOnAction(e -> attempt(calendarStatus, () -> {
			int removed = facade.clearCalendarWeek(selected);
			refreshCalendar();
			ok(calendarStatus,
					"Removed " + removed + " meal(s) from the week of " + CalendarService.weekStart(selected) + ".");
		}));
		Button shopWeek = new Button("Add this week to shopping list");
		shopWeek.setOnAction(e -> attempt(calendarStatus, () -> {
			List<RecipeIngredient> added = facade.addCalendarWeekToShoppingList(selected);
			onDataChanged.run();
			ok(calendarStatus, added.isEmpty() ? "Nothing to add: the pantry and shopping list already cover that week."
					: "Added to the shopping list: " + joinIngredients(added) + ".");
		}));
		HBox nav = new HBox(8, previous, monthTitle, next, today);
		HBox weekActions = new HBox(8, load, clearWeek, shopWeek);

		monthGrid.setHgap(4);
		monthGrid.setVgap(4);
		for (int i = 0; i < 7; i++) {
			ColumnConstraints c = new ColumnConstraints();
			c.setPercentWidth(100.0 / 7);
			monthGrid.getColumnConstraints().add(c);
		}

		// the selected day
		dayTitle.setStyle("-fx-font-weight: bold;");
		for (MealSlot slot : MealSlot.values()) {
			dayMealBox.getItems().add(slot.label());
		}
		dayMealBox.setPromptText("Meal");
		dayRecipeBox.setPromptText("Recipe");
		dayRecipeBox.setPrefWidth(260);
		Button setDay = new Button("Set meal");
		setDay.setOnAction(e -> attempt(calendarStatus, () -> {
			if (dayMealBox.getValue() == null || dayRecipeBox.getValue() == null) {
				throw new IllegalArgumentException("Choose a meal and a recipe first.");
			}
			facade.setCalendarMeal(selected, dayMealBox.getValue(), dayRecipeBox.getValue().getId());
			refreshCalendar();
			ok(calendarStatus, selected + " " + dayMealBox.getValue().toLowerCase() + ": "
					+ dayRecipeBox.getValue().getTitle() + ".");
		}));
		Button clearDay = new Button("Clear meal");
		clearDay.setOnAction(e -> attempt(calendarStatus, () -> {
			if (dayMealBox.getValue() == null) {
				throw new IllegalArgumentException("Choose the meal to clear.");
			}
			facade.clearCalendarMeal(selected, dayMealBox.getValue());
			refreshCalendar();
			ok(calendarStatus, "Cleared " + selected + " " + dayMealBox.getValue().toLowerCase() + ".");
		}));
		HBox dayEdit = new HBox(8, dayMealBox, dayRecipeBox, setDay, clearDay);
		VBox dayBox = new VBox(6, dayTitle, dayMeals, dayEdit);
		dayBox.setPadding(new Insets(8));
		dayBox.setStyle("-fx-border-color: #dddddd; -fx-border-radius: 4;");

		return new VBox(8, heading, hint, nav, monthGrid, weekActions, dayBox, calendarStatus);
	}

	// ---------------- drawing ----------------

	/**
	 * Redraws everything (call this whenever the page is shown or the data may have
	 * changed).
	 */
	public void refresh() {
		reloadRecipeChoices(planRecipeBox);
		reloadRecipeChoices(dayRecipeBox);
		drawPlan();
		refreshCalendar();
	}

	private void refreshCalendar() {
		drawMonth();
		drawSelectedDay();
	}

	private void reloadRecipeChoices(ComboBox<Recipe> box) {
		Recipe chosen = box.getValue();
		box.getItems().setAll(facade.getRecipes());
		if (chosen != null) {
			for (Recipe r : box.getItems()) {
				if (r.getId() == chosen.getId()) {
					box.setValue(r);
					break;
				}
			}
		}
	}

	private void drawPlan() {
		WeekPlan plan = facade.getPlan();
		planGrid.getChildren().clear();
		List<DayPlan> days = plan.days();
		for (int col = 0; col < days.size(); col++) {
			Label header = new Label(days.get(col).day().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
			header.setStyle("-fx-font-weight: bold;");
			planGrid.add(header, col + 1, 0);
		}
		int row = 1;
		for (MealSlot slot : MealSlot.values()) {
			Label rowHeader = new Label(slot.label());
			rowHeader.setStyle("-fx-font-weight: bold;");
			planGrid.add(rowHeader, 0, row);
			for (int col = 0; col < days.size(); col++) {
				String text = days.get(col).get(slot).map(entry -> entry.recipe().getTitle()).orElse("-");
				Label cell = new Label(text);
				cell.setWrapText(true);
				cell.setMaxWidth(Double.MAX_VALUE);
				cell.setMinHeight(44);
				cell.setPadding(new Insets(4));
				cell.setStyle("-fx-border-color: #dddddd; -fx-border-radius: 4;");
				GridPane.setHgrow(cell, Priority.ALWAYS);
				planGrid.add(cell, col + 1, row);
			}
			row++;
		}
	}

	private void drawMonth() {
		monthTitle.setText(shown.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + shown.getYear());
		monthGrid.getChildren().clear();
		for (DayOfWeek day : DayOfWeek.values()) {
			Label header = new Label(day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
			header.setStyle("-fx-font-weight: bold;");
			monthGrid.add(header, day.ordinal(), 0);
		}
		LocalDate date = CalendarService.weekStart(shown.atDay(1));
		LocalDate last = shown.atEndOfMonth();
		int row = 1;
		while (!date.isAfter(last)) {
			for (int col = 0; col < 7; col++) {
				monthGrid.add(dayButton(date), col, row);
				date = date.plusDays(1);
			}
			row++;
		}
	}

	private Button dayButton(LocalDate date) {
		int count = facade.getCalendarMeals(date).size();
		Button b = new Button(
				date.getDayOfMonth() + (count > 0 ? "\n" + count + (count == 1 ? " meal" : " meals") : "\n "));
		b.setMaxWidth(Double.MAX_VALUE);
		b.setMinHeight(46);
		GridPane.setHgrow(b, Priority.ALWAYS);
		StringBuilder css = new StringBuilder();
		if (count > 0) {
			css.append("-fx-background-color: #e8f5e9;");
		}
		if (!YearMonth.from(date).equals(shown)) {
			css.append("-fx-text-fill: #9e9e9e;");
		}
		if (date.equals(LocalDate.now())) {
			css.append("-fx-font-weight: bold;");
		}
		if (date.equals(selected)) {
			css.append("-fx-border-color: #1565c0; -fx-border-width: 2;");
		}
		b.setStyle(css.toString());
		b.setOnAction(e -> {
			selected = date;
			shown = YearMonth.from(date);
			refreshCalendar();
		});
		return b;
	}

	private void drawSelectedDay() {
		dayTitle.setText(selected.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + selected);
		dayMeals.getChildren().clear();
		List<MealEntry> entries = facade.getCalendarMeals(selected);
		if (entries.isEmpty()) {
			dayMeals.getChildren().add(new Label("Nothing planned for this day."));
		}
		for (MealEntry entry : entries) {
			dayMeals.getChildren().add(new Label(entry.slot().label() + ": " + entry.recipe().getTitle()));
		}
		for (String warning : facade.getCalendarWarnings(selected)) {
			Label w = new Label("WARNING: " + warning);
			w.setWrapText(true);
			w.setStyle("-fx-text-fill: #c62828;");
			dayMeals.getChildren().add(w);
		}
	}

	private void showProblems() {
		List<String> problems = facade.checkPlan();
		notes.setText(problems.isEmpty() ? "The plan looks fine."
				: problems.stream().map(p -> "! " + p).collect(Collectors.joining("\n")));
	}

	private static String joinIngredients(List<RecipeIngredient> ingredients) {
		return ingredients.stream().map(RecipeIngredient::toString).collect(Collectors.joining(", "));
	}

	/** Runs an action and shows a red message if it fails with a normal error. */
	private static void attempt(Label status, Runnable action) {
		try {
			action.run();
		} catch (IllegalArgumentException | java.time.DateTimeException e) {
			status.setStyle("-fx-text-fill: #c62828;");
			status.setText(e.getMessage());
		}
	}

	private static void ok(Label status, String message) {
		status.setStyle("-fx-text-fill: #2e7d32;");
		status.setText(message);
	}
}