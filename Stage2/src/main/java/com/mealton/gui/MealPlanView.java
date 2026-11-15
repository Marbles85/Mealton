package com.mealton.gui;

import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongConsumer;

import com.mealton.facade.MealtonFacade;
import com.mealton.nutrition.Nutrients;
import com.mealton.nutrition.RecipeNutrition;
import com.mealton.plan.CalendarService;
import com.mealton.plan.PlannedMeal;
import com.mealton.profile.UserProfile;
import com.mealton.recipe.ProfileCheck;
import com.mealton.recipe.Recipe;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

/**
 * The Meal plan page: a calendar with Day, Week and Month views, meals with
 * tags (Breakfast, Dinner, or your own), a macro box, and a pop-up to add or
 * change meals. Meals can be dragged: drop on a meal to swap, on the edge of a
 * meal to slot in before or after it, or on a day to add it there. Only draws
 * the page; the work is done by {@link MealtonFacade}.
 */
public class MealPlanView extends StackPane {

	private enum View {
		DAY, WEEK, MONTH
	}

	private record DragRef(LocalDate date, int index) {
	}

	private static final Map<String, String> TIMES = Map.of("Breakfast", "8:00", "Lunch", "12:30", "Snack", "15:00",
			"Post-workout", "17:30", "Dinner", "19:00");

	private final MealtonFacade facade;
	private final Runnable onDataChanged;
	private LongConsumer onLogRecipe;

	private View view = View.WEEK;
	private LocalDate focus;
	private DragRef drag;
	private String toast = "";

	private final StackPane overlay = new StackPane();
	private DragRef panel;
	private boolean swapOpen;
	private LocalDate qaDay;
	private String qaTag = "Lunch";
	private boolean qaNewTag;
	private String qaNewTagText = "";
	private String qaSrc = "recipes";
	private String qaQuery = "";
	private Recipe qaPick;
	private int qaServ = 1;
	private String qaErr = "";
	private final VBox qaBox = new VBox(14);

	private final VBox body = new VBox(16);
	private final ScrollPane scroll = new ScrollPane(body);

	public MealPlanView(MealtonFacade facade, Runnable onDataChanged) {
		this.facade = facade;
		this.onDataChanged = onDataChanged;
		this.focus = facade.today();
		var css = MealPlanView.class.getResource("/plan.css");
		if (css != null) {
			getStylesheets().add(css.toExternalForm());
		}
		getStyleClass().add("mp-page");
		body.setPadding(new Insets(28, 32, 32, 32));
		scroll.setFitToWidth(true);
		scroll.getStyleClass().add("mp-scroll");
		getChildren().add(scroll);
		overlay.setVisible(false);
		getChildren().add(overlay);
		rebuild();
	}

	/**
	 * What a meal's "Log it" button does (the app opens Log food with that recipe).
	 */
	public void setOnLogRecipe(LongConsumer action) {
		this.onLogRecipe = action;
	}

	/** Reloads everything from the facade. */
	public void refresh() {
		rebuild();
	}

	// ------------------------------------------------------------------ helpers

	private LocalDate today() {
		return facade.today();
	}

	private boolean isPast(LocalDate d) {
		return d.isBefore(today());
	}

	private static String shortDay(LocalDate d) {
		return d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
	}

	private static String monthDay(LocalDate d) {
		return d.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + d.getDayOfMonth();
	}

	private static String longDay(LocalDate d) {
		return d.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + ", " + monthDay(d) + ", "
				+ d.getYear();
	}

	private String rangeLabel() {
		switch (view) {
		case DAY:
			return longDay(focus);
		case MONTH:
			return focus.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + focus.getYear();
		default:
			LocalDate a = CalendarService.weekStart(focus);
			LocalDate b = a.plusDays(6);
			String end = a.getMonth() == b.getMonth() ? String.valueOf(b.getDayOfMonth()) : monthDay(b);
			return monthDay(a) + " – " + end + ", " + b.getYear();
		}
	}

	private void say(String text) {
		toast = text;
	}

	private void run(Runnable action) {
		try {
			action.run();
		} catch (IllegalArgumentException e) {
			say(e.getMessage());
		}
		onDataChanged.run();
		rebuild();
	}

	private static Label label(String text, String style) {
		Label l = new Label(text);
		l.getStyleClass().add(style);
		return l;
	}

	private static Button button(String text, String style, Runnable action) {
		Button b = new Button(text);
		b.getStyleClass().setAll("button", style);
		b.setOnAction(e -> action.run());
		return b;
	}

	private String kcalText(PlannedMeal m) {
		StringBuilder sb = new StringBuilder();
		if (facade.tracksMacros()) {
			Optional<Double> k = kcalOf(m);
			sb.append(k.map(v -> Math.round(v) + " kcal · ").orElse("Nutrition incomplete · "));
		}
		sb.append(m.servings() == 1 ? "1 serving" : m.servings() + " servings");
		return sb.toString();
	}

	private Optional<Double> kcalOf(PlannedMeal m) {
		RecipeNutrition n = facade.estimateNutrition(m.recipe());
		if (n.counted().isEmpty() || n.perServing().calories() <= 0) {
			return Optional.empty();
		}
		return Optional.of(n.perServing().calories() * m.servings());
	}

	// ------------------------------------------------------------------ page

	/**
	 * The message bar: the text plus a Redo link right after an Undo, or an Undo
	 * link after a change.
	 */
	private Node toastBar() {
		Label text = label(toast, "mp-toast-text");
		text.setWrapText(true);
		HBox bar = new HBox(12, text);
		bar.setAlignment(Pos.CENTER_LEFT);
		bar.getStyleClass().add("mp-toast-bar");
		if (facade.canRedoCalendar()) {
			bar.getChildren().add(button("Redo", "mp-toast-link",
					() -> run(() -> say(facade.redoCalendar() ? "Redone." : "Nothing to redo."))));
		} else if (facade.canUndoCalendar() && !toast.startsWith("Undone") && !toast.startsWith("Nothing")) {
			bar.getChildren().add(button("Undo", "mp-toast-link",
					() -> run(() -> say(facade.undoCalendar() ? "Undone." : "Nothing to undo."))));
		}
		Region grow = new Region();
		HBox.setHgrow(grow, Priority.ALWAYS);
		bar.getChildren().add(grow);
		bar.getChildren().add(button("\u00D7", "mp-toast-x", () -> {
			toast = "";
			rebuild();
		}));
		return bar;
	}

	private void rebuild() {
		body.getChildren().clear();
		body.getChildren().add(header());
		if (!toast.isEmpty()) {
			body.getChildren().add(toastBar());
		}
		VBox main = new VBox(10);
		main.setMinWidth(0);
		if (view == View.WEEK) {
			Label hint = label(
					"Drop a meal on another to swap them. Drop it on the line between two meals to slot it in \u2014 it keeps its tag and the rest shift down.",
					"mp-hint");
			hint.setWrapText(true);
			main.getChildren().add(hint);
		}
		switch (view) {
		case DAY:
			main.getChildren().add(dayView());
			break;
		case MONTH:
			main.getChildren().add(monthView());
			break;
		default:
			main.getChildren().add(weekView());
			break;
		}
		HBox bottom = new HBox(24);
		bottom.setAlignment(Pos.TOP_LEFT);
		if (facade.tracksMacros()) {
			Region macros = (Region) macroCard();
			HBox.setHgrow(macros, Priority.ALWAYS);
			macros.setPrefWidth(420);
			bottom.getChildren().add(macros);
		}
		Region quick = (Region) quickAddCard();
		HBox.setHgrow(quick, Priority.ALWAYS);
		quick.setPrefWidth(520);
		bottom.getChildren().add(quick);
		VBox content = new VBox(24, main, bottom);
		body.getChildren().add(content);
	}

	private Node header() {
		Label title = label("Meal plan", "mp-title");
		Label range = label(rangeLabel(), "mp-range");
		Button prev = button("‹", "mp-arrow", () -> step(-1));
		Button next = button("›", "mp-arrow", () -> step(1));
		HBox rangeRow = new HBox(10, prev, range, next);
		rangeRow.setAlignment(Pos.CENTER_LEFT);
		VBox left = new VBox(6, title, rangeRow);
		HBox.setHgrow(left, Priority.ALWAYS);

		Button today = button("Today", "mp-outline", () -> {
			focus = today();
			rebuild();
		});
		HBox seg = new HBox(2);
		seg.getStyleClass().add("mp-seg-bar");
		for (View v : View.values()) {
			String name = v.name().charAt(0) + v.name().substring(1).toLowerCase(Locale.ROOT);
			Button b = button(name, v == view ? "mp-seg-on" : "mp-seg", () -> {
				view = v;
				rebuild();
			});
			seg.getChildren().add(b);
		}
		Button undo = button("Undo", "mp-outline",
				() -> run(() -> say(facade.undoCalendar() ? "Undone." : "Nothing to undo.")));
		undo.setDisable(!facade.canUndoCalendar());
		Button redo = button("Redo", "mp-outline",
				() -> run(() -> say(facade.redoCalendar() ? "Redone." : "Nothing to redo.")));
		Button prep = button("Schedule prep day", "mp-outline", this::showPrepDay);
		Button plan = button("Plan meals", "mp-primary", this::planMeals);
		HBox right = new HBox(10, today, seg, undo, prep, plan);
		if (facade.canRedoCalendar()) {
			right.getChildren().add(right.getChildren().indexOf(undo) + 1, redo);
		}
		right.setAlignment(Pos.CENTER_RIGHT);
		right.setFillHeight(false);
		seg.setMaxHeight(Region.USE_PREF_SIZE);
		seg.setAlignment(Pos.CENTER);
		HBox row = new HBox(16, left, right);
		row.setAlignment(Pos.CENTER_LEFT);
		return row;
	}

	private void step(int dir) {
		switch (view) {
		case DAY:
			focus = focus.plusDays(dir);
			break;
		case MONTH:
			focus = focus.plusMonths(dir);
			break;
		default:
			focus = focus.plusWeeks(dir);
			break;
		}
		rebuild();
	}

	// ------------------------------------------------------------------ week

	private Node weekView() {
		GridPane grid = new GridPane();
		grid.setHgap(10);
		LocalDate monday = CalendarService.weekStart(focus);
		for (int i = 0; i < 7; i++) {
			javafx.scene.layout.ColumnConstraints c = new javafx.scene.layout.ColumnConstraints();
			c.setPercentWidth(100.0 / 7);
			c.setHgrow(Priority.ALWAYS);
			grid.getColumnConstraints().add(c);
			grid.add(dayColumn(monday.plusDays(i)), i, 0);
		}
		return grid;
	}

	private Node weekSum(LocalDate monday) {
		int meals = 0;
		double kcal = 0;
		double protein = 0;
		double carbs = 0;
		double fat = 0;
		for (int i = 0; i < 7; i++) {
			List<PlannedMeal> day = facade.getPlannedMeals(monday.plusDays(i));
			meals += day.size();
			Nutrients t = totalOf(day);
			kcal += t.calories();
			protein += t.protein();
			carbs += t.carbs();
			fat += t.fat();
		}
		String text = meals == 0 ? "Nothing planned this week yet."
				: meals + (meals == 1 ? " meal" : " meals") + "  \u00B7  " + String.format("%,d", Math.round(kcal))
						+ " kcal" + "  \u00B7  " + Math.round(protein) + " g protein  \u00B7  " + Math.round(carbs)
						+ " g carbs  \u00B7  " + Math.round(fat) + " g fat";
		Label l = label("WEEK TOTAL   " + text, "mp-meal-meta");
		HBox box = new HBox(l);
		box.setPadding(new Insets(0, 2, 0, 2));
		return box;
	}

	private Node dayColumn(LocalDate date) {
		boolean past = isPast(date);
		boolean isToday = date.equals(today());
		Label name = label(shortDay(date), "mp-col-name");
		Label num = label(monthDay(date), isToday ? "mp-col-today" : "mp-col-date");
		Label note = label(isToday ? "Today" : past ? "Past" : "", "mp-col-note");
		VBox head = new VBox(2, name, num, note);
		VBox col = new VBox(8, head);
		col.setPadding(new Insets(12));
		col.getStyleClass().add(past ? "mp-col-past" : isToday ? "mp-col-now" : "mp-col");
		var prepEvent = facade.getPrepDay(date);
		if (prepEvent != null) {
			if (!past) {
				col.getStyleClass().add("mp-col-prep");
			}
			col.getChildren().add(prepChip(prepEvent, false));
		}
		col.setMinWidth(0);
		col.setOnMouseClicked(e -> {
			focus = date;
			rebuild();
		});

		List<PlannedMeal> meals = facade.getPlannedMeals(date);
		for (int i = 0; i < meals.size(); i++) {
			col.getChildren().add(mealCard(date, i, meals.get(i), false));
		}
		if (!past) {
			Button add = button("+ Add meal", "mp-add", () -> addMeal(date));
			add.setMaxWidth(Double.MAX_VALUE);
			col.getChildren().add(add);
		}
		col.setMinHeight(360);
		if (!past) {
			col.setOnDragOver(e -> {
				if (drag != null) {
					e.acceptTransferModes(TransferMode.MOVE);
				}
				e.consume();
			});
			col.setOnDragEntered(e -> {
				if (drag != null) {
					col.getStyleClass().add("mp-col-drop");
				}
			});
			col.setOnDragExited(e -> col.getStyleClass().remove("mp-col-drop"));
			col.setOnDragDropped(e -> {
				if (drag != null) {
					DragRef from = drag;
					drag = null;
					e.setDropCompleted(true);
					moveTo(from, date, -1);
				}
				e.consume();
			});
		}
		return col;
	}

	private void moveTo(DragRef from, LocalDate date, int index) {
		run(() -> {
			PlannedMeal m = facade.getPlannedMeals(from.date()).get(from.index());
			facade.movePlannedMeal(from.date(), from.index(), date, index);
			say("Moved " + m.recipe().getTitle() + " to " + shortDay(date) + " — kept its "
					+ (m.tag().isEmpty() ? "no-tag setting" : m.tag().toLowerCase(Locale.ROOT) + " tag") + ".");
		});
	}

	private Node mealCard(LocalDate date, int index, PlannedMeal m, boolean big) {
		boolean past = isPast(date);
		boolean defaultTag = m.tag().isEmpty() || TIMES.containsKey(m.tag()) && !m.tag().equals("Post-workout");
		Label tag = label(m.tag().isEmpty() ? "" : defaultTag ? m.tag().toUpperCase(Locale.ROOT) : m.tag(),
				defaultTag ? "mp-tag" : "mp-tag-custom");
		tag.setVisible(!m.tag().isEmpty());
		tag.setManaged(!m.tag().isEmpty());
		Label name = label(m.recipe().getTitle(), "mp-meal-name");
		name.setWrapText(true);
		name.setMinHeight(Region.USE_PREF_SIZE);
		Label kcal = label(kcalText(m), "mp-meal-meta");
		kcal.setWrapText(true);
		kcal.setMinHeight(Region.USE_PREF_SIZE);
		VBox card = new VBox(3, tag, name, kcal);
		card.getStyleClass().add(past ? "mp-card-past" : "mp-card");
		card.setStyle(card.getStyle() + "-fx-cursor: hand;");
		card.setOnMouseClicked(e -> {
			focus = date;
			details(date, index);
			e.consume();
		});
		if (!past) {
			card.setOnDragDetected(e -> {
				Dragboard db = card.startDragAndDrop(TransferMode.MOVE);
				ClipboardContent c = new ClipboardContent();
				c.putString(m.recipe().getTitle());
				db.setContent(c);
				drag = new DragRef(date, index);
				card.setOpacity(0.5);
				e.consume();
			});
			card.setOnDragDone(e -> {
				drag = null;
				card.setOpacity(1);
			});
			card.setOnDragOver(e -> {
				if (drag != null) {
					e.acceptTransferModes(TransferMode.MOVE);
					showHint(card, zone(e, card));
				}
				e.consume();
			});
			card.setOnDragExited(e -> clearHint(card));
			card.setOnDragDropped(e -> {
				if (drag != null) {
					DragRef from = drag;
					String z = zone(e, card);
					drag = null;
					clearHint(card);
					e.setDropCompleted(true);
					dropOnMeal(from, date, index, z);
				}
				e.consume();
			});
		}
		return card;
	}

	private static String zone(DragEvent e, Node card) {
		double f = e.getY() / Math.max(1, card.getBoundsInLocal().getHeight());
		return f < 0.3 ? "before" : f > 0.7 ? "after" : "swap";
	}

	private static void showHint(Node card, String zone) {
		clearHint(card);
		card.getStyleClass().add("mp-drop-" + zone);
	}

	private static void clearHint(Node card) {
		card.getStyleClass().removeAll("mp-drop-before", "mp-drop-after", "mp-drop-swap");
	}

	private void dropOnMeal(DragRef from, LocalDate date, int index, String zone) {
		if (from.date().equals(date) && from.index() == index) {
			rebuild();
			return;
		}
		if (zone.equals("swap")) {
			run(() -> {
				PlannedMeal a = facade.getPlannedMeals(from.date()).get(from.index());
				PlannedMeal b = facade.getPlannedMeals(date).get(index);
				facade.swapPlannedMeals(from.date(), from.index(), date, index);
				say("Swapped " + a.recipe().getTitle() + " (" + shortDay(from.date()) + ") with "
						+ b.recipe().getTitle() + " (" + shortDay(date) + ").");
			});
		} else {
			moveTo(from, date, zone.equals("before") ? index : index + 1);
		}
	}

	// ------------------------------------------------------------------ day

	private Node dayView() {
		boolean past = isPast(focus);
		VBox box = new VBox(10);
		if (past) {
			box.getChildren()
					.add(label("This day has passed. You can still look at what was planned and log it.", "mp-hint"));
		}
		var prepEvent = facade.getPrepDay(focus);
		if (prepEvent != null) {
			box.getChildren().add(prepChip(prepEvent, true));
		}
		List<PlannedMeal> meals = facade.getPlannedMeals(focus);
		if (meals.isEmpty()) {
			box.getChildren().add(label("Nothing planned for this day yet.", "mp-hint"));
		}
		for (int i = 0; i < meals.size(); i++) {
			box.getChildren().add(dayRow(focus, i, meals.get(i)));
		}
		if (!past) {
			Button add = button("+ Add meal", "mp-add", () -> addMeal(focus));
			add.setMaxWidth(260);
			box.getChildren().add(add);
		}
		for (String w : facade.getCalendarWarnings(focus)) {
			box.getChildren().add(label("⚠ " + w, "mp-warning"));
		}
		return box;
	}

	private Node dayRow(LocalDate date, int index, PlannedMeal m) {
		boolean past = isPast(date);
		StackPane photo = photoBox(m.recipe(), 72, 54);
		Label tag = label(m.tag().isEmpty() ? "No tag" : m.tag(), "mp-tag-custom");
		Label time = label(TIMES.getOrDefault(m.tag(), ""), "mp-meal-meta");
		HBox top = new HBox(8, tag, time);
		top.setAlignment(Pos.CENTER_LEFT);
		Label name = label(m.recipe().getTitle(), "mp-day-name");
		Label kcal = label(kcalText(m), "mp-meal-meta");
		VBox text = new VBox(3, top, name, kcal);
		HBox.setHgrow(text, Priority.ALWAYS);
		Button log = button("Log it", "mp-outline", () -> logRecipe(m.recipe()));
		HBox row = new HBox(14, photo, text, log);
		row.setAlignment(Pos.CENTER_LEFT);
		row.getStyleClass().add(past ? "mp-row-past" : "mp-row");
		row.setStyle("-fx-cursor: hand;");
		row.setOnMouseClicked(e -> details(date, index));
		return row;
	}

	/**
	 * The big first letter shown on a recipe with no picture (the same idea as the
	 * recipe cards).
	 */
	static Label letterOf(Recipe r, double height) {
		String t = r.getTitle() == null || r.getTitle().isBlank() ? "?"
				: r.getTitle().trim().substring(0, 1).toUpperCase(Locale.ROOT);
		Label l = new Label(t);
		l.getStyleClass().add("mp-letter");
		l.setStyle("-fx-font-size: " + Math.round(Math.min(54, Math.max(16, height * 0.5))) + "px;");
		return l;
	}

	private StackPane photoBox(Recipe r, double w, double h) {
		StackPane box = new StackPane();
		box.setMinSize(w, h);
		box.setPrefSize(w, h);
		box.setMaxSize(w, h);
		box.getStyleClass().add("mp-photo");
		box.getChildren().add(letterOf(r, h));
		Rectangle clip = new Rectangle(w, h);
		clip.setArcWidth(16);
		clip.setArcHeight(16);
		box.setClip(clip);
		Optional<Path> file = facade.getRecipePhoto(r.getId());
		if (file.isPresent()) {
			ImageView v = PhotoFit.cover(file.get(), w, h);
			if (v != null) {
				box.getChildren().add(v);
			}
		}
		return box;
	}

	private void logRecipe(Recipe r) {
		if (onLogRecipe != null) {
			onLogRecipe.accept(r.getId());
		}
	}

	// ------------------------------------------------------------------ month

	private Node monthView() {
		YearMonth ym = YearMonth.from(focus);
		LocalDate first = ym.atDay(1);
		LocalDate start = CalendarService.weekStart(first);
		GridPane grid = new GridPane();
		grid.setHgap(6);
		grid.setVgap(6);
		for (int i = 0; i < 7; i++) {
			javafx.scene.layout.ColumnConstraints c = new javafx.scene.layout.ColumnConstraints();
			c.setPercentWidth(100.0 / 7);
			c.setHgrow(Priority.ALWAYS);
			grid.getColumnConstraints().add(c);
			grid.add(label(DayOfWeek.SUNDAY.plus(i).getDisplayName(TextStyle.SHORT, Locale.ENGLISH), "mp-col-name"), i,
					0);
		}
		int rows = (int) Math.ceil((first.getDayOfWeek().getValue() % 7 + ym.lengthOfMonth()) / 7.0);
		for (int r = 0; r < rows; r++) {
			for (int c = 0; c < 7; c++) {
				LocalDate d = start.plusDays(r * 7L + c);
				grid.add(monthCell(d, YearMonth.from(d).equals(ym)), c, r + 1);
			}
		}
		return grid;
	}

	private Node monthCell(LocalDate d, boolean inMonth) {
		VBox cell = new VBox(3);
		cell.setPadding(new Insets(8));
		cell.setMinHeight(96);
		cell.getStyleClass().add(
				!inMonth ? "mp-cell-out" : d.equals(today()) ? "mp-cell-now" : isPast(d) ? "mp-cell-past" : "mp-cell");
		cell.getChildren()
				.add(label(String.valueOf(d.getDayOfMonth()), d.equals(today()) ? "mp-col-today" : "mp-col-date"));
		if (inMonth) {
			var prepEvent = facade.getPrepDay(d);
			if (prepEvent != null) {
				Label pl = label(
						"PREP DAY \u00B7 "
								+ prepEvent.start().format(java.time.format.DateTimeFormatter.ofPattern("H:mm")),
						"mp-prep-mini");
				pl.setOnMouseClicked(e -> {
					showPrepDay(d);
					e.consume();
				});
				cell.getChildren().add(pl);
			}
			List<PlannedMeal> meals = facade.getPlannedMeals(d);
			for (int i = 0; i < Math.min(2, meals.size()); i++) {
				Label l = label("• " + meals.get(i).recipe().getTitle(), "mp-mini");
				l.setMaxWidth(Double.MAX_VALUE);
				cell.getChildren().add(l);
			}
			if (meals.size() > 2) {
				cell.getChildren().add(label("+" + (meals.size() - 2) + " more", "mp-meal-meta"));
			}
		}
		cell.setStyle("-fx-cursor: hand;");
		cell.setOnMouseClicked(e -> {
			focus = d;
			view = View.DAY;
			rebuild();
		});
		return cell;
	}

	// ------------------------------------------------------------------ macros

	private Node prepChip(com.mealton.plan.CalendarService.PrepEvent ev, boolean wide) {
		java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("H:mm");
		String hours = ev.minutes() / 60 + "h " + ev.minutes() % 60 + "m";
		Label top = label(ev.start().format(f) + " \u2013 " + ev.end().format(f) + " \u00B7 Prep day", "mp-prep-top");
		VBox chip = new VBox(2, top);
		if (wide) {
			chip.getChildren()
					.add(label(
							"Batch cook the week\u2019s meals \u00B7 " + hours + " \u00B7 " + ev.meals() + " portions",
							"mp-prep-title"));
		} else {
			chip.getChildren().add(label(hours, "mp-meal-meta"));
		}
		chip.getStyleClass().add("mp-prep-chip");
		chip.setStyle("-fx-cursor: hand;");
		chip.setOnMouseClicked(e -> {
			showPrepDay(ev.date());
			e.consume();
		});
		return chip;
	}

	/** The day the macro tracker shows, or null for the whole week. */
	private LocalDate macroDay;

	private Node macroCard() {
		UserProfile p = facade.getProfile();
		LocalDate monday = CalendarService.weekStart(focus);
		if (macroDay == null || !CalendarService.weekStart(macroDay).equals(monday)) {
			LocalDate t = today();
			macroDay = CalendarService.weekStart(t).equals(monday) ? t : monday; // today if it is this week, else the
																					// first day
		}
		List<PlannedMeal> shown = new ArrayList<>(facade.getPlannedMeals(macroDay));
		Nutrients total = totalOf(shown);

		Label heading = label("Macro tracker", "mp-h2");
		Region g = new Region();
		HBox.setHgrow(g, Priority.ALWAYS);
		Label range = label(shortDay(macroDay) + ", " + monthDay(macroDay), "mp-meal-meta");
		HBox head = new HBox(8, heading, g, range);
		head.setAlignment(Pos.BASELINE_LEFT);

		Label kcal = label(String.format("%,d / %,d kcal", Math.round(total.calories()), p.calorieGoal()), "mp-kcal");
		VBox card = new VBox(14, head, kcal, bar(total.calories() / Math.max(1, p.calorieGoal()), "mp-bar-fill"));
		card.getStyleClass().add("mp-card-big");
		card.getChildren().addAll(macroBit("Protein", total.protein(), p.proteinGoal()),
				macroBit("Carbs", total.carbs(), p.carbsGoal()), macroBit("Fat", total.fat(), p.fatGoal()));
		boolean missing = false;
		for (PlannedMeal m : shown) {
			missing |= kcalOf(m).isEmpty();
		}
		if (missing) {
			Label l = label("Some meals this day have no nutrition data, so the totals are low.", "mp-hint");
			l.setWrapText(true);
			card.getChildren().add(l);
		}

		HBox bars = new HBox(8);
		bars.setAlignment(Pos.BOTTOM_LEFT);
		int dayGoal = Math.max(1, p.calorieGoal());
		for (int i = 0; i < 7; i++) {
			LocalDate d = monday.plusDays(i);
			double k = totalOf(facade.getPlannedMeals(d)).calories();
			Region col = new Region();
			double hgt = Math.max(3, Math.min(60, 60 * k / dayGoal));
			col.setMinSize(26, hgt);
			col.setPrefSize(26, hgt);
			col.setMaxSize(26, hgt);
			col.getStyleClass().add(d.equals(macroDay) ? "mp-wk-bar-on" : "mp-wk-bar");
			Label ini = label(shortDay(d).substring(0, 1), "mp-meal-meta");
			VBox c = new VBox(3, col, ini);
			c.setAlignment(Pos.BOTTOM_CENTER);
			c.setStyle("-fx-cursor: hand;");
			c.setOnMouseClicked(e -> {
				macroDay = d;
				rebuild();
			});
			bars.getChildren().add(c);
		}
		Label tip = label("Click a day to see its macros.", "mp-hint");
		VBox week = new VBox(10, label("Day by day", "mp-pick-name"), bars, tip);
		week.getStyleClass().add("mp-rv-top");
		card.getChildren().add(week);
		return card;
	}

	private Node macroBit(String name, double value, int goal) {
		Label l = label(name + " " + Math.round(value) + " / " + goal + " g", "mp-meal-meta");
		String fill = name.equals("Protein") ? "mp-bar-protein" : name.equals("Carbs") ? "mp-bar-carbs" : "mp-bar-fat";
		return new VBox(4, l, bar(goal <= 0 ? 0 : value / goal, fill));
	}

	private Node bar(double fraction, String fillClass) {
		StackPane track = new StackPane();
		track.getStyleClass().add("mp-bar");
		track.setMinHeight(8);
		track.setPrefHeight(8);
		track.setMaxHeight(8);
		track.setPrefWidth(120);
		track.setMaxWidth(Double.MAX_VALUE);
		Region fill = new Region();
		fill.getStyleClass().add(fillClass);
		StackPane.setAlignment(fill, Pos.CENTER_LEFT);
		double f = Math.max(0, Math.min(1, fraction));
		fill.setMaxHeight(8);
		fill.prefWidthProperty().bind(track.widthProperty().multiply(f));
		fill.maxWidthProperty().bind(track.widthProperty().multiply(f));
		track.getChildren().add(fill);
		return track;
	}

	private Nutrients totalOf(List<PlannedMeal> meals) {
		double c = 0;
		double p = 0;
		double cb = 0;
		double f = 0;
		for (PlannedMeal m : meals) {
			RecipeNutrition n = facade.estimateNutrition(m.recipe());
			if (n.counted().isEmpty()) {
				continue;
			}
			Nutrients s = n.perServing();
			c += s.calories() * m.servings();
			p += s.protein() * m.servings();
			cb += s.carbs() * m.servings();
			f += s.fat() * m.servings();
		}
		return new Nutrients(c, p, cb, f);
	}

	// ------------------------------------------------------------------ pop-ups

	private Dialog<ButtonType> dialog(String title) {
		Dialog<ButtonType> d = new Dialog<>();
		d.setTitle(title);
		d.setHeaderText(null);
		d.setGraphic(null);
		var main = MealPlanView.class.getResource("/mealton.css");
		if (main != null) {
			d.getDialogPane().getStylesheets().add(main.toExternalForm());
		}
		var css = MealPlanView.class.getResource("/plan.css");
		if (css != null) {
			d.getDialogPane().getStylesheets().add(css.toExternalForm());
		}
		d.getDialogPane().getStyleClass().add("mp-dialog");
		if (getScene() != null && getScene().getWindow() != null) {
			d.initOwner(getScene().getWindow());
		}
		return d;
	}

	/**
	 * A search box plus a list of recipes; returns the list so the caller can read
	 * the choice.
	 */
	private ListView<Recipe> recipePicker(VBox into) {
		ComboBox<String> source = new ComboBox<>();
		source.getItems().addAll("All recipes", "Saved", "My recipes");
		source.setValue("All recipes");
		source.getStyleClass().add("mp-combo");
		TextField search = new TextField();
		search.setPromptText("Search recipes");
		search.getStyleClass().add("mp-input");
		HBox.setHgrow(search, Priority.ALWAYS);
		ListView<Recipe> list = new ListView<>();
		list.setPrefHeight(230);
		list.getStyleClass().add("mp-list");
		UserProfile profile = facade.getProfile();
		list.setCellFactory(v -> new ListCell<>() {
			@Override
			protected void updateItem(Recipe r, boolean empty) {
				super.updateItem(r, empty);
				if (empty || r == null) {
					setGraphic(null);
					setText(null);
					return;
				}
				RecipeNutrition n = facade.estimateNutrition(r);
				String meta = (n.counted().isEmpty() || n.perServing().calories() <= 0 ? "No nutrition data"
						: Math.round(n.perServing().calories()) + " kcal") + " · " + r.getServings() + " servings";
				VBox box = new VBox(2, label(r.getTitle(), "mp-pick-name"), label(meta, "mp-meal-meta"));
				List<String> bad = ProfileCheck.conflicts(r, profile);
				if (!bad.isEmpty()) {
					box.getChildren().add(label("⚠ " + bad.get(0), "mp-warning"));
				}
				setGraphic(box);
				setText(null);
			}
		});
		Runnable fill = () -> {
			String q = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
			List<Recipe> out = new ArrayList<>();
			List<Recipe> pool = "My recipes".equals(source.getValue()) ? facade.getMyRecipes() : facade.getRecipes();
			for (Recipe r : pool) {
				if ("Saved".equals(source.getValue()) && !r.isSaved()) {
					continue;
				}
				if (q.isEmpty() || r.getTitle().toLowerCase(Locale.ROOT).contains(q)) {
					out.add(r);
				}
			}
			list.getItems().setAll(out);
			if (out.isEmpty()) {
				list.setPlaceholder(label("Nothing matches “" + search.getText() + "”.", "mp-hint"));
			}
		};
		search.textProperty().addListener((o, a, b) -> fill.run());
		source.valueProperty().addListener((o, a, b) -> fill.run());
		fill.run();
		HBox bar = new HBox(8, source, search);
		into.getChildren().addAll(bar, list);
		return list;
	}

	// ------------------------------------------------------------------ add a meal
	// (side panel)

	private LocalDate addDate;
	private String apTag = "";
	private boolean apNewTag;
	private String apNewTagText = "";
	private String apTagError = "";
	private String apSrc = "recipes";
	private String apQuery = "";
	private Recipe apPick;
	private int apServ = 1;
	private String apError = "";

	private void addMeal(LocalDate preset) {
		addDate = preset == null || isPast(preset) ? today() : preset;
		apTag = qaTag;
		apNewTag = false;
		apNewTagText = "";
		apTagError = "";
		apSrc = "recipes";
		apQuery = "";
		apPick = null;
		apServ = 1;
		apError = "";
		panel = null;
		renderAddPanel();
	}

	private void renderAddPanel() {
		overlay.getChildren().clear();
		if (addDate == null) {
			overlay.setVisible(false);
			return;
		}
		Region scrim = new Region();
		scrim.setStyle("-fx-background-color: rgba(31,42,34,0.25);");
		scrim.setOnMouseClicked(e -> closeAddPanel());

		Label title = label("Add a meal \u00B7 " + shortDay(addDate) + ", " + monthDay(addDate), "mp-rv-h3");
		title.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(title, Priority.ALWAYS);
		HBox head = new HBox(8, title, button("\u00D7", "mp-rv-close", this::closeAddPanel));
		head.setAlignment(Pos.CENTER_LEFT);
		head.getStyleClass().add("mp-rv-head");

		VBox body = new VBox(12);
		body.setPadding(new Insets(18));

		// tag
		body.getChildren().add(tagHeader("TAG", "mp-rv-kicker"));
		FlowPane chips = new FlowPane(6, 6);
		List<String> tags = new ArrayList<>(facade.getMealTags());
		if (!apTag.isEmpty() && !tags.contains(apTag)) {
			tags.add(apTag);
		}
		tags.add("");
		for (String t : tags) {
			boolean on = t.equals(apTag);
			chips.getChildren().add(button(t.isEmpty() ? "No tag" : t, on ? "mp-chip-on" : "mp-chip-solid", () -> {
				apTag = t;
				renderAddPanel();
			}));
		}
		body.getChildren().add(chips);
		TextField newTag = new TextField(apNewTagText);
		newTag.setPromptText("New tag, e.g. Pre-game");
		newTag.getStyleClass().add("mp-input");
		newTag.textProperty().addListener((o, a, b) -> apNewTagText = b);
		HBox.setHgrow(newTag, Priority.ALWAYS);
		Runnable createTag = () -> {
			try {
				apTag = facade.addMealTag(newTag.getText());
				apNewTagText = "";
				apTagError = "";
			} catch (IllegalArgumentException e) {
				apTagError = e.getMessage();
			}
			renderAddPanel();
		};
		newTag.setOnAction(e -> createTag.run());
		HBox newRow = new HBox(8, newTag, button("+ Add", "mp-outline", createTag));
		newRow.setAlignment(Pos.CENTER_LEFT);
		body.getChildren().add(newRow);
		if (!apTagError.isEmpty()) {
			body.getChildren().add(label(apTagError, "mp-error"));
		}
		Label tagHint = label(
				"Tags are optional \u2014 pick \u201CNo tag\u201D to leave a meal untagged. Tags you add work across your whole plan.",
				"mp-hint");
		tagHint.setWrapText(true);
		tagHint.setMinHeight(Region.USE_PREF_SIZE);
		body.getChildren().add(tagHint);

		// pick a recipe
		body.getChildren()
				.add(label(apTag.isEmpty() ? "PICK A RECIPE" : "PICK A RECIPE FOR " + apTag.toUpperCase(Locale.ROOT),
						"mp-rv-kicker"));
		HBox tabs = new HBox(3);
		tabs.getStyleClass().add("mp-limbar");
		String[][] src = { { "recipes", "Recipes" }, { "pantry", "From pantry" } };
		for (String[] t : src) {
			Button b = button(t[1], t[0].equals(apSrc) ? "mp-lim-on" : "mp-lim", () -> {
				apSrc = t[0];
				apQuery = "";
				renderAddPanel();
			});
			b.setMaxWidth(Double.MAX_VALUE);
			HBox.setHgrow(b, Priority.ALWAYS);
			tabs.getChildren().add(b);
		}
		body.getChildren().add(tabs);
		TextField search = new TextField(apQuery);
		search.setPromptText(
				apSrc.equals("pantry") ? "Search what you can cook from your pantry" : "Search your recipes");
		search.getStyleClass().add("mp-input");
		VBox list = new VBox(6);
		search.textProperty().addListener((o, a, b) -> {
			apQuery = b == null ? "" : b;
			recipeRows(list, apSrc, apQuery, apPick, r -> pickForAdd(r));
		});
		body.getChildren().addAll(search, list);
		recipeRows(list, apSrc, apQuery, apPick, r -> pickForAdd(r));

		ScrollPane sp = new ScrollPane(body);
		sp.setFitToWidth(true);
		sp.getStyleClass().add("mp-scroll");
		VBox.setVgrow(sp, Priority.ALWAYS);

		// footer: servings and the add button (or the allergy warning)
		Label servLabel = label(String.valueOf(apServ), "mp-day-name");
		servLabel.setMinWidth(32);
		servLabel.setAlignment(Pos.CENTER);
		Button minus = button("\u2212", "mp-step", () -> {
			apServ = Math.max(1, apServ - 1);
			servLabel.setText(String.valueOf(apServ));
		});
		Button plus = button("+", "mp-step", () -> {
			apServ = Math.min(20, apServ + 1);
			servLabel.setText(String.valueOf(apServ));
		});
		Region grow = new Region();
		HBox.setHgrow(grow, Priority.ALWAYS);
		HBox serv = new HBox(6, label("Servings", "mp-label"), grow, minus, servLabel, plus);
		serv.setAlignment(Pos.CENTER_LEFT);
		VBox footer = new VBox(10, serv);
		footer.setPadding(new Insets(12, 18, 16, 18));
		footer.getStyleClass().add("mp-rv-foot");
		List<String> bad = apPick == null ? List.of() : ProfileCheck.conflicts(apPick, facade.getProfile());
		if (!bad.isEmpty()) {
			Label warn = label("\u26A0 " + bad.get(0) + " \u2014 listed in your profile. Add anyway?", "mp-rv-alert");
			warn.setWrapText(true);
			warn.setMinHeight(Region.USE_PREF_SIZE);
			Button anyway = button("Add anyway", "mp-outline", this::commitAdd);
			Button another = button("Pick another", "mp-primary", () -> {
				apPick = null;
				renderAddPanel();
			});
			anyway.setMaxWidth(Double.MAX_VALUE);
			another.setMaxWidth(Double.MAX_VALUE);
			HBox.setHgrow(anyway, Priority.ALWAYS);
			HBox.setHgrow(another, Priority.ALWAYS);
			footer.getChildren().addAll(warn, new HBox(8, anyway, another));
		} else {
			String name = apPick == null ? "a recipe"
					: apPick.getTitle().length() > 26 ? apPick.getTitle().substring(0, 25) + "\u2026"
							: apPick.getTitle();
			Button add = button(
					"Add " + name + " to " + shortDay(addDate) + (apTag.isEmpty() ? "" : " \u00B7 " + apTag),
					"mp-primary", this::commitAdd);
			add.setMaxWidth(Double.MAX_VALUE);
			add.setDisable(apPick == null);
			footer.getChildren().add(add);
		}
		if (!apError.isEmpty()) {
			Label e = label(apError, "mp-error");
			e.setWrapText(true);
			footer.getChildren().add(e);
		}

		VBox side = new VBox(head, sp, footer);
		side.getStyleClass().add("mp-rv-panel");
		side.setMinWidth(440);
		side.setPrefWidth(440);
		side.setMaxWidth(440);
		StackPane.setAlignment(side, Pos.CENTER_RIGHT);
		overlay.getChildren().addAll(scrim, side);
		overlay.setVisible(true);
	}

	private void pickForAdd(Recipe r) {
		apPick = r;
		apError = "";
		renderAddPanel();
	}

	private void closeAddPanel() {
		addDate = null;
		overlay.setVisible(false);
		overlay.getChildren().clear();
	}

	private void commitAdd() {
		if (apPick == null) {
			apError = "Pick a recipe first.";
			renderAddPanel();
			return;
		}
		LocalDate d = addDate;
		Recipe r = apPick;
		String tag = apTag;
		int servings = apServ;
		qaTag = tag;
		closeAddPanel();
		run(() -> {
			facade.addPlannedMeal(d, tag, r.getId(), servings);
			focus = d;
			say("Added " + r.getTitle() + " to " + shortDay(d) + (tag.isEmpty() ? "." : " \u00B7 " + tag + "."));
		});
	}

	private static Node labeled(String text, Node control) {
		Label l = new Label(text);
		l.getStyleClass().add("mp-label");
		return new VBox(4, l, control);
	}

	/** Look at one meal: change tag/servings, swap recipe, move, remove, log. */
	private void details(LocalDate date, int index) {
		openPanel(date, index);
	}

	// ------------------------------------------------------------------ edit tags

	private Node tagHeader(String text, String style) {
		Region grow = new Region();
		HBox.setHgrow(grow, Priority.ALWAYS);
		HBox row = new HBox(8, label(text, style), grow, button("Edit tags", "mp-link", this::manageTags));
		row.setAlignment(Pos.CENTER_LEFT);
		return row;
	}

	private String tagMessage = "";

	private void manageTags() {
		tagMessage = "";
		Dialog<ButtonType> d = dialog("Edit tags");
		VBox box = new VBox(12);
		box.setPrefWidth(500);
		box.setPadding(new Insets(6, 6, 0, 6));
		fillTagEditor(box);
		d.getDialogPane().setContent(box);
		ButtonType done = new ButtonType("Done", ButtonBar.ButtonData.OK_DONE);
		d.getDialogPane().getButtonTypes().add(done);
		Node doneButton = d.getDialogPane().lookupButton(done);
		doneButton.getStyleClass().addAll("mp-primary");
		d.showAndWait();
		onDataChanged.run();
		rebuild();
		if (addDate != null) {
			renderAddPanel();
		} else if (panel != null) {
			renderPanel();
		}
	}

	private void fillTagEditor(VBox box) {
		box.getChildren().clear();
		box.getChildren().add(label("Your meal tags", "mp-h2"));
		Label hint = label(
				"Renaming a tag renames it on every meal that uses it. Deleting one leaves those meals on your calendar, just untagged.",
				"mp-hint");
		hint.setWrapText(true);
		hint.setMinHeight(Region.USE_PREF_SIZE);
		box.getChildren().add(hint);
		for (String t : facade.getMealTags()) {
			HBox row = new HBox(8);
			row.setAlignment(Pos.CENTER_LEFT);
			row.getStyleClass().add("mp-tag-row");
			if (MealtonFacade.isBuiltInTag(t)) {
				Label name = label(t, "mp-pick-name");
				name.setMaxWidth(Double.MAX_VALUE);
				HBox.setHgrow(name, Priority.ALWAYS);
				row.getChildren().addAll(name, label("built in", "mp-badge-soft"));
				row.getStyleClass().add("mp-tag-row-fixed");
			} else {
				TextField field = new TextField(t);
				field.getStyleClass().addAll("mp-input", "mp-tag-field");
				HBox.setHgrow(field, Priority.ALWAYS);
				Runnable save = () -> {
					try {
						int n = facade.renameMealTag(t, field.getText());
						if (qaTag.equals(t)) {
							qaTag = field.getText().trim();
						}
						if (apTag.equals(t)) {
							apTag = field.getText().trim();
						}
						tagMessage = n == 0 ? "Renamed."
								: "Renamed \u2014 " + n + (n == 1 ? " meal follows" : " meals follow") + " it.";
					} catch (IllegalArgumentException e) {
						tagMessage = e.getMessage();
					}
					fillTagEditor(box);
				};
				field.setOnAction(e -> save.run());
				row.getChildren().addAll(field, button("Rename", "mp-outline-sm", save),
						button("Delete", "mp-danger-sm", () -> {
							try {
								int n = facade.deleteMealTag(t);
								if (qaTag.equals(t)) {
									qaTag = "";
								}
								if (apTag.equals(t)) {
									apTag = "";
								}
								tagMessage = n == 0 ? "Deleted."
										: "Deleted \u2014 " + n + (n == 1 ? " meal is" : " meals are")
												+ " now untagged. Undo brings them back.";
							} catch (IllegalArgumentException e) {
								tagMessage = e.getMessage();
							}
							fillTagEditor(box);
						}));
			}
			box.getChildren().add(row);
		}
		TextField fresh = new TextField();
		fresh.setPromptText("New tag, e.g. Pre-game");
		fresh.getStyleClass().add("mp-input");
		HBox.setHgrow(fresh, Priority.ALWAYS);
		Runnable add = () -> {
			try {
				String made = facade.addMealTag(fresh.getText());
				tagMessage = "Added \u201C" + made + "\u201D.";
			} catch (IllegalArgumentException e) {
				tagMessage = e.getMessage();
			}
			fillTagEditor(box);
		};
		fresh.setOnAction(e -> add.run());
		fresh.getStyleClass().add("mp-tag-field");
		HBox addRow = new HBox(8, fresh, button("+ Add tag", "mp-primary-sm", add));
		addRow.setAlignment(Pos.CENTER_LEFT);
		addRow.getStyleClass().add("mp-tag-new");
		box.getChildren().add(label("ADD A TAG", "mp-rv-kicker"));
		box.getChildren().add(addRow);
		if (!tagMessage.isEmpty()) {
			Label msg = label(tagMessage, "mp-toast");
			msg.setWrapText(true);
			box.getChildren().add(msg);
		}
	}

	// ------------------------------------------------------------------ quick add
	// (side card)

	private LocalDate qaDay() {
		if (qaDay == null || isPast(qaDay)) {
			qaDay = isPast(focus) ? today() : focus;
		}
		return qaDay;
	}

	private Node quickAddCard() {
		qaBox.getStyleClass().setAll("mp-card-big");
		fillQuickAdd();
		return qaBox;
	}

	private void fillQuickAdd() {
		qaBox.getChildren().clear();
		qaBox.getChildren().add(label("Quick add", "mp-h2"));

		ComboBox<LocalDate> day = new ComboBox<>();
		day.getStyleClass().add("mp-combo");
		day.setMaxWidth(Double.MAX_VALUE);
		for (int i = 0; i < 28; i++) {
			day.getItems().add(today().plusDays(i));
		}
		day.setConverter(new javafx.util.StringConverter<LocalDate>() {
			@Override
			public String toString(LocalDate x) {
				return x == null ? "" : shortDay(x) + ", " + monthDay(x) + (x.equals(today()) ? " (today)" : "");
			}

			@Override
			public LocalDate fromString(String s) {
				return null;
			}
		});
		day.setValue(qaDay());
		day.valueProperty().addListener((o, a, b) -> {
			if (b != null) {
				qaDay = b;
			}
		});
		qaBox.getChildren().add(labeled("Day", day));

		ComboBox<String> tag = new ComboBox<>();
		tag.getStyleClass().add("mp-combo");
		tag.setMaxWidth(Double.MAX_VALUE);
		List<String> tags = new ArrayList<>(facade.getMealTags());
		if (!qaTag.isEmpty() && !tags.contains(qaTag)) {
			tags.add(qaTag);
		}
		tags.add("No tag");
		tags.add("New tag…");
		tag.getItems().setAll(tags);
		tag.setValue(qaNewTag ? "New tag…" : qaTag.isEmpty() ? "No tag" : qaTag);
		tag.valueProperty().addListener((o, a, b) -> {
			if (b == null) {
				return;
			}
			qaErr = "";
			if (b.equals("New tag…")) {
				qaNewTag = true;
			} else {
				qaNewTag = false;
				qaTag = b.equals("No tag") ? "" : b;
			}
			fillQuickAdd();
		});
		qaBox.getChildren().add(new VBox(4, tagHeader("Tag", "mp-label"), tag));
		if (qaNewTag) {
			TextField name = new TextField(qaNewTagText);
			name.setPromptText("e.g. Pre-game");
			name.getStyleClass().add("mp-input");
			name.textProperty().addListener((o, a, b) -> qaNewTagText = b);
			HBox.setHgrow(name, Priority.ALWAYS);
			Runnable create = () -> {
				try {
					qaTag = facade.addMealTag(name.getText());
					qaNewTag = false;
					qaNewTagText = "";
					qaErr = "";
				} catch (IllegalArgumentException e) {
					qaErr = e.getMessage();
				}
				fillQuickAdd();
			};
			name.setOnAction(e -> create.run());
			HBox row = new HBox(6, name, button("Create", "mp-primary", create), button("Cancel", "mp-link", () -> {
				qaNewTag = false;
				qaErr = "";
				fillQuickAdd();
			}));
			row.setAlignment(Pos.CENTER_LEFT);
			qaBox.getChildren().add(row);
		}

		// what to eat
		HBox tabs = new HBox(3);
		tabs.getStyleClass().add("mp-limbar");
		String[][] src = { { "recipes", "Recipes" }, { "pantry", "From pantry" } };
		for (String[] t : src) {
			Button b = button(t[1], t[0].equals(qaSrc) ? "mp-lim-on" : "mp-lim", () -> {
				qaSrc = t[0];
				qaQuery = "";
				fillQuickAdd();
			});
			b.setMaxWidth(Double.MAX_VALUE);
			HBox.setHgrow(b, Priority.ALWAYS);
			tabs.getChildren().add(b);
		}
		TextField search = new TextField(qaQuery);
		search.setPromptText(
				qaSrc.equals("pantry") ? "Search what you can cook from your pantry" : "Search your recipes");
		search.getStyleClass().add("mp-input");
		VBox list = new VBox(6);
		search.textProperty().addListener((o, a, b) -> {
			qaQuery = b == null ? "" : b;
			fillQuickAddList(list);
		});
		ScrollPane listScroll = new ScrollPane(list);
		listScroll.setFitToWidth(true);
		listScroll.setPrefViewportHeight(248);
		listScroll.setMaxHeight(260);
		listScroll.getStyleClass().add("mp-scroll");
		qaBox.getChildren().add(new VBox(8, label("What to eat", "mp-label"), tabs, search, listScroll));
		fillQuickAddList(list);

		// servings
		Label servLabel = label(String.valueOf(qaServ), "mp-day-name");
		servLabel.setMinWidth(32);
		servLabel.setAlignment(Pos.CENTER);
		Button minus = button("−", "mp-step", () -> {
			qaServ = Math.max(1, qaServ - 1);
			servLabel.setText(String.valueOf(qaServ));
		});
		Button plus = button("+", "mp-step", () -> {
			qaServ = Math.min(20, qaServ + 1);
			servLabel.setText(String.valueOf(qaServ));
		});
		Region grow = new Region();
		HBox.setHgrow(grow, Priority.ALWAYS);
		HBox serv = new HBox(6, label("Servings", "mp-label"), grow, minus, servLabel, plus);
		serv.setAlignment(Pos.CENTER_LEFT);
		qaBox.getChildren().add(serv);

		// allergy warning or the add button
		List<String> bad = qaPick == null ? List.of() : ProfileCheck.conflicts(qaPick, facade.getProfile());
		if (!bad.isEmpty()) {
			Label warn = label("⚠ " + bad.get(0) + " — listed in your profile. Add anyway?", "mp-rv-alert");
			warn.setWrapText(true);
			warn.setMinHeight(Region.USE_PREF_SIZE);
			Button anyway = button("Add anyway", "mp-outline", this::quickAdd);
			Button another = button("Pick another", "mp-primary", () -> {
				qaPick = null;
				fillQuickAdd();
			});
			anyway.setMaxWidth(Double.MAX_VALUE);
			another.setMaxWidth(Double.MAX_VALUE);
			HBox.setHgrow(anyway, Priority.ALWAYS);
			HBox.setHgrow(another, Priority.ALWAYS);
			qaBox.getChildren().addAll(warn, new HBox(8, anyway, another));
		} else {
			String name = qaPick == null ? "a recipe"
					: qaPick.getTitle().length() > 22 ? qaPick.getTitle().substring(0, 21) + "…" : qaPick.getTitle();
			Button add = button("Add " + name + " to " + shortDay(qaDay()) + (qaTag.isEmpty() ? "" : " · " + qaTag),
					"mp-primary", this::quickAdd);
			add.setMaxWidth(Double.MAX_VALUE);
			add.setDisable(qaPick == null);
			qaBox.getChildren().add(add);
		}
		if (!qaErr.isEmpty()) {
			Label e = label(qaErr, "mp-error");
			e.setWrapText(true);
			qaBox.getChildren().add(e);
		}
	}

	private void fillQuickAddList(VBox list) {
		recipeRows(list, qaSrc, qaQuery, qaPick, r -> {
			qaPick = r;
			qaErr = "";
			fillQuickAdd();
		});
	}

	/** The pick-a-recipe rows used by Quick add and the Add a meal panel. */
	private void recipeRows(VBox list, String src, String query, Recipe selected,
			java.util.function.Consumer<Recipe> onPick) {
		list.getChildren().clear();
		String q = query.trim().toLowerCase(Locale.ROOT);
		List<Recipe> pool = new ArrayList<>();
		for (Recipe r : facade.getRecipes()) {
			if (q.isEmpty() || r.getTitle().toLowerCase(Locale.ROOT).contains(q)) {
				pool.add(r);
			}
		}
		boolean pantry = src.equals("pantry");
		if (pantry) {
			Map<Long, Integer> have = new java.util.HashMap<>();
			List<Recipe> keep = new ArrayList<>();
			for (Recipe r : pool) {
				int n = facade.checkRecipe(r.getId()).available().size();
				if (n > 0) {
					have.put(r.getId(), n);
					keep.add(r);
				}
			}
			keep.sort((a, b) -> Integer.compare(have.get(b.getId()), have.get(a.getId())));
			pool = keep;
		}
		UserProfile profile = facade.getProfile();
		for (Recipe r : pool) {
			boolean sel = selected != null && selected.getId() == r.getId();
			StackPane photo = photoBox(r, 44, 34);
			String meta = facade.tracksMacros() && !kcalOf(r).isEmpty() ? kcalOf(r) + " \u00B7 " : "";
			if (pantry) {
				var m = facade.checkRecipe(r.getId());
				meta += "you have " + m.available().size() + " of " + (m.available().size() + m.missing().size());
			} else {
				meta += r.getServings() + (r.getServings() == 1 ? " serving" : " servings");
			}
			Label name = label(r.getTitle(), "mp-pick-name");
			name.setWrapText(true);
			name.setMinHeight(Region.USE_PREF_SIZE);
			name.setMinWidth(0);
			VBox text = new VBox(1, name, label(meta, "mp-meal-meta"));
			text.setMinWidth(0);
			HBox.setHgrow(text, Priority.ALWAYS);
			HBox row = new HBox(10, photo, text);
			row.setAlignment(Pos.CENTER_LEFT);
			if (!ProfileCheck.conflicts(r, profile).isEmpty()) {
				row.getChildren().add(label("allergen", "mp-badge-bad"));
			}
			if (sel) {
				row.getChildren().add(label("\u2713", "mp-pick-check"));
			}
			row.getStyleClass().add(sel ? "mp-qa-row-on" : "mp-qa-row");
			row.setOnMouseClicked(e -> onPick.accept(r));
			list.getChildren().add(row);
		}
		if (pool.isEmpty()) {
			list.getChildren().add(label(
					q.isEmpty() ? "Nothing to show yet." : "Nothing matches \u201C" + query + "\u201D.", "mp-hint"));
		}
	}

	private String kcalOf(Recipe r) {
		RecipeNutrition n = facade.estimateNutrition(r);
		return n.counted().isEmpty() || n.perServing().calories() <= 0 ? ""
				: Math.round(n.perServing().calories()) + " kcal";
	}

	private void quickAdd() {
		if (qaNewTag) {
			qaErr = "Create the new tag or cancel it first.";
			fillQuickAdd();
			return;
		}
		if (qaPick == null) {
			qaErr = "Pick a recipe first.";
			fillQuickAdd();
			return;
		}
		LocalDate d = qaDay();
		Recipe r = qaPick;
		String tag = qaTag;
		int servings = qaServ;
		qaPick = null;
		qaServ = 1;
		qaErr = "";
		run(() -> {
			facade.addPlannedMeal(d, tag, r.getId(), servings);
			focus = d;
			say("Added " + r.getTitle() + " to " + shortDay(d) + (tag.isEmpty() ? "." : " · " + tag + "."));
		});
	}

	// ------------------------------------------------------------------ recipe
	// sections for the panel

	private Node recipeNutrition(Recipe r) {
		RecipeNutrition n = facade.estimateNutrition(r);
		VBox card = new VBox(10);
		card.getStyleClass().add("mp-rv-nut");
		boolean known = !n.counted().isEmpty() && n.perServing().calories() > 0;
		Region g = new Region();
		HBox.setHgrow(g, Priority.ALWAYS);
		HBox top = new HBox(8, new HBox(6, label("Nutrition", "mp-rv-h3"), label("· per serving", "mp-meal-meta")), g,
				label(known ? Math.round(n.perServing().calories()) + " kcal" : "–", "mp-rv-kcal"));
		top.setAlignment(Pos.BASELINE_LEFT);
		card.getChildren().add(top);
		if (!known) {
			Label l = label("Some ingredients have no nutrition data yet, so Mealton can’t total this recipe.",
					"mp-meal-meta");
			l.setWrapText(true);
			l.setMinHeight(Region.USE_PREF_SIZE);
			card.getChildren().add(l);
			return card;
		}
		UserProfile p = facade.getProfile();
		boolean track = facade.tracksMacros();
		if (track && p.calorieGoal() > 0) {
			card.getChildren().add(label(Math.round(n.perServing().calories() * 100.0 / p.calorieGoal())
					+ "% of your daily " + p.calorieGoal() + " kcal", "mp-meal-meta"));
		}
		card.getChildren().add(recipeMacro("Protein", n.perServing().protein(), p.proteinGoal(), track));
		card.getChildren().add(recipeMacro("Carbs", n.perServing().carbs(), p.carbsGoal(), track));
		card.getChildren().add(recipeMacro("Fat", n.perServing().fat(), p.fatGoal(), track));
		return card;
	}

	private Node recipeMacro(String name, double grams, int goal, boolean track) {
		Region g = new Region();
		HBox.setHgrow(g, Priority.ALWAYS);
		Label v = label(Math.round(grams) + " g", "mp-rv-val");
		v.setMinWidth(54);
		v.setAlignment(Pos.CENTER_RIGHT);
		HBox row = new HBox(10, label(name, "mp-rv-ing"), g, v);
		row.setAlignment(Pos.CENTER_LEFT);
		if (track && goal > 0) {
			javafx.scene.control.ProgressBar bar = new javafx.scene.control.ProgressBar(Math.min(1, grams / goal));
			bar.setPrefWidth(90);
			bar.getStyleClass().add("mp-progress");
			Label pct = label(Math.round(grams * 100.0 / goal) + "%", "mp-meal-meta");
			pct.setMinWidth(36);
			pct.setAlignment(Pos.CENTER_RIGHT);
			row.getChildren().addAll(bar, pct);
		}
		return row;
	}

	private Node recipeIngredients(Recipe r) {
		var match = facade.checkRecipe(r.getId());
		java.util.Set<String> have = new java.util.HashSet<>();
		for (var a : match.available()) {
			have.add(a.name());
		}
		VBox box = new VBox(0);
		HBox h = new HBox(8, label("Ingredients", "mp-rv-h3"),
				label("· you have " + have.size() + " of " + r.getIngredients().size(), "mp-meal-meta"));
		h.setAlignment(Pos.BASELINE_LEFT);
		h.setPadding(new Insets(0, 0, 6, 0));
		box.getChildren().add(h);
		for (var ing : r.getIngredients()) {
			boolean has = have.contains(ing.name());
			Region dot = new Region();
			dot.setMinSize(10, 10);
			dot.setPrefSize(10, 10);
			dot.setMaxSize(10, 10);
			dot.getStyleClass().add(has ? "mp-rv-dot-have" : "mp-rv-dot-need");
			Label text = label(com.mealton.util.Formats.quantityWithUnit(ing.quantity(), ing.unit()) + " " + ing.name(),
					"mp-rv-ing");
			text.setWrapText(true);
			text.setMaxWidth(Double.MAX_VALUE);
			HBox.setHgrow(text, Priority.ALWAYS);
			Label tag = label(has ? "Have it" : "Need to buy", has ? "mp-status-ok" : "mp-status-short");
			HBox row = new HBox(10, dot, text, tag);
			row.setAlignment(Pos.CENTER_LEFT);
			row.getStyleClass().add("mp-rv-row");
			box.getChildren().add(row);
		}
		return box;
	}

	private Node recipeMethod(Recipe r) {
		VBox steps = new VBox(10);
		steps.getChildren().add(label("Method", "mp-rv-h3"));
		int n = 1;
		for (String step : r.getSteps()) {
			Label num = label(String.valueOf(n++), "mp-rv-step-n");
			num.setMinSize(26, 26);
			num.setPrefSize(26, 26);
			num.setAlignment(Pos.CENTER);
			Label text = label(step, "mp-rv-step");
			text.setWrapText(true);
			text.setMinHeight(Region.USE_PREF_SIZE);
			HBox.setHgrow(text, Priority.ALWAYS);
			HBox row = new HBox(12, num, text);
			row.setAlignment(Pos.TOP_LEFT);
			steps.getChildren().add(row);
		}
		return steps;
	}

	// ------------------------------------------------------------------ meal side
	// panel

	private List<DragRef> everyMeal() {
		List<DragRef> out = new ArrayList<>();
		for (LocalDate d : facade.getCalendarDates()) {
			for (int i = 0; i < facade.getPlannedMeals(d).size(); i++) {
				out.add(new DragRef(d, i));
			}
		}
		return out;
	}

	private void openPanel(LocalDate date, int index) {
		panel = new DragRef(date, index);
		swapOpen = false;
		fullOpen = false;
		renderPanel();
	}

	private void closePanel() {
		panel = null;
		swapOpen = false;
		fullOpen = false;
		overlay.setVisible(false);
		overlay.getChildren().clear();
	}

	/**
	 * After a change: keep the panel on the same meal (found again by recipe and
	 * tag), or close it.
	 */
	private void afterPanelChange(LocalDate date, long recipeId, String tag) {
		onDataChanged.run();
		rebuild();
		List<PlannedMeal> day = facade.getPlannedMeals(date);
		for (int i = 0; i < day.size(); i++) {
			if (day.get(i).recipe().getId() == recipeId && day.get(i).tag().equals(tag)) {
				panel = new DragRef(date, i);
				renderPanel();
				return;
			}
		}
		closePanel();
	}

	private Button navButton(String text, boolean enabled, Runnable action) {
		Button b = button(text, "mp-rv-nav", action);
		b.setDisable(!enabled);
		return b;
	}

	private Node moveCombo(LocalDate date, int index, Recipe r, PlannedMeal m, Label status) {
		ComboBox<LocalDate> moveTo = new ComboBox<>();
		moveTo.getStyleClass().add("mp-combo");
		moveTo.setPrefWidth(200);
		moveTo.setMaxWidth(Double.MAX_VALUE);
		for (int i = 0; i < 28; i++) {
			LocalDate x = today().plusDays(i);
			if (!x.equals(date)) {
				moveTo.getItems().add(x);
			}
		}
		moveTo.setPromptText("Move to\u2026");
		moveTo.setConverter(new javafx.util.StringConverter<LocalDate>() {
			@Override
			public String toString(LocalDate x) {
				return x == null ? "" : shortDay(x) + ", " + monthDay(x);
			}

			@Override
			public LocalDate fromString(String t) {
				return null;
			}
		});
		moveTo.valueProperty().addListener((o, a, b) -> {
			if (b != null) {
				try {
					facade.movePlannedMeal(date, index, b, -1);
					say("Moved " + r.getTitle() + " to " + shortDay(b) + ".");
					focus = b;
					afterPanelChange(b, r.getId(), m.tag());
				} catch (IllegalArgumentException ex) {
					status.setText(ex.getMessage());
				}
			}
		});
		return moveTo;
	}

	private boolean fullOpen;

	private void renderPanel() {
		overlay.getChildren().clear();
		if (panel == null) {
			overlay.setVisible(false);
			return;
		}
		List<PlannedMeal> day = facade.getPlannedMeals(panel.date());
		if (panel.index() >= day.size()) {
			closePanel();
			return;
		}
		PlannedMeal m = day.get(panel.index());
		Recipe r = m.recipe();
		LocalDate date = panel.date();
		int index = panel.index();
		boolean past = isPast(date);
		List<DragRef> all = everyMeal();
		int pos = -1;
		for (int i = 0; i < all.size(); i++) {
			if (all.get(i).date().equals(date) && all.get(i).index() == index) {
				pos = i;
			}
		}
		final int at = pos;

		Region scrim = new Region();
		scrim.setStyle("-fx-background-color: rgba(31,42,34,0.25);");
		scrim.setOnMouseClicked(e -> closePanel());

		Label where = label("Meal " + (pos + 1) + " of " + all.size() + " in your plan", "mp-meal-meta");
		where.setAlignment(Pos.CENTER);
		where.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(where, Priority.ALWAYS);
		HBox head = new HBox(8, navButton("‹", at > 0, () -> {
			DragRef p = all.get(at - 1);
			panel = p;
			swapOpen = false;
			fullOpen = false;
			renderPanel();
		}), where, navButton("›", at >= 0 && at < all.size() - 1, () -> {
			DragRef p = all.get(at + 1);
			panel = p;
			swapOpen = false;
			fullOpen = false;
			renderPanel();
		}), button("×", "mp-rv-close", this::closePanel));
		head.setAlignment(Pos.CENTER);
		head.getStyleClass().add("mp-rv-head");

		VBox body = new VBox(14);
		body.setPadding(new Insets(18));

		var file = facade.getRecipePhoto(r.getId());
		double ph = file.isPresent() ? 200 : 110;
		StackPane photo = new StackPane();
		photo.setMinSize(404, ph);
		photo.setPrefSize(404, ph);
		photo.setMaxSize(404, ph);
		photo.getStyleClass().add("mp-photo");
		photo.getChildren().add(letterOf(r, ph));
		Rectangle clip = new Rectangle(404, ph);
		clip.setArcWidth(28);
		clip.setArcHeight(28);
		photo.setClip(clip);
		if (file.isPresent()) {
			ImageView v = PhotoFit.cover(file.get(), 404, ph);
			if (v != null) {
				if (past) {
					v.setOpacity(0.7);
				}
				photo.getChildren().add(v);
			}
		}
		body.getChildren().add(photo);

		String time = TIMES.getOrDefault(m.tag(), "");
		body.getChildren()
				.add(label((shortDay(date) + ", " + monthDay(date) + (m.tag().isEmpty() ? "" : " \u00B7 " + m.tag())
						+ (time.isEmpty() ? "" : " \u00B7 " + time)).toUpperCase(), "mp-rv-kicker"));
		Label title = label(r.getTitle(), "mp-rv-title");
		title.setWrapText(true);
		title.setMinHeight(Region.USE_PREF_SIZE);
		body.getChildren().add(title);
		FlowPane metaRow = new FlowPane(14, 4);
		if (r.getTotalMinutes() > 0) {
			metaRow.getChildren().add(label(r.getTotalMinutes() + " min", "mp-meal-meta"));
		}
		metaRow.getChildren().add(label(m.servings() + (m.servings() == 1 ? " serving" : " servings"), "mp-meal-meta"));
		if (r.getSourceName() != null && !r.getSourceName().isBlank()) {
			metaRow.getChildren().add(label(r.getSourceName(), "mp-meal-meta"));
		}
		body.getChildren().add(metaRow);
		for (String w : ProfileCheck.conflicts(r, facade.getProfile())) {
			Label l = label("⚠ " + w, "mp-warning");
			l.setWrapText(true);
			body.getChildren().add(l);
		}
		// main actions (as in the design: Open full recipe + Log it, then a row of
		// changes)
		Label status = label("", "mp-error");
		status.setWrapText(true);
		if (past) {
			body.getChildren().add(label("Past \u00B7 can\u2019t be edited", "mp-badge-soft"));
		}
		Button full = button(fullOpen ? "Hide full recipe" : "Open full recipe", "mp-primary", () -> {
			fullOpen = !fullOpen;
			renderPanel();
		});
		Button log = button("Log it", "mp-outline", () -> {
			closePanel();
			logRecipe(r);
		});
		full.setMaxWidth(Double.MAX_VALUE);
		log.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(full, Priority.ALWAYS);
		HBox.setHgrow(log, Priority.ALWAYS);
		HBox actions = new HBox(8, full, log);
		body.getChildren().add(actions);
		if (!past) {
			Button swapBtn = button(swapOpen ? "Hide the list" : "\u21C4  Swap recipe",
					swapOpen ? "mp-primary" : "mp-outline", () -> {
						swapOpen = !swapOpen;
						renderPanel();
					});
			Button elseBtn = button("\u21BB  Something else", "mp-outline", () -> {
				try {
					Recipe now = facade.swapForSuggestion(date, index);
					say("Swapped " + r.getTitle() + " for " + now.getTitle() + ".");
					afterPanelChange(date, now.getId(), m.tag());
				} catch (IllegalArgumentException ex) {
					status.setText(ex.getMessage());
				}
			});
			swapBtn.setMaxWidth(Double.MAX_VALUE);
			elseBtn.setMaxWidth(Double.MAX_VALUE);
			HBox.setHgrow(swapBtn, Priority.ALWAYS);
			HBox.setHgrow(elseBtn, Priority.ALWAYS);
			Node mover = moveCombo(date, index, r, m, status);
			HBox.setHgrow(mover, Priority.ALWAYS);
			Button removeBtn = button("Remove", "mp-danger", () -> {
				facade.removePlannedMeal(date, index);
				say("Removed " + r.getTitle() + ".");
				closePanel();
				onDataChanged.run();
				rebuild();
			});
			HBox moveRow = new HBox(8, mover, removeBtn);
			moveRow.setAlignment(Pos.CENTER_LEFT);
			VBox changes = new VBox(10, label("CHANGE THIS MEAL", "mp-rv-kicker"), new HBox(8, swapBtn, elseBtn),
					moveRow);
			changes.getStyleClass().add("mp-rv-sec");
			body.getChildren().add(changes);
		}

		if (!past && swapOpen) {
			VBox swapBox = new VBox(8);
			swapBox.getStyleClass().add("mp-rv-nut");
			swapBox.getChildren().add(label("Swap " + r.getTitle() + " for…", "mp-rv-h3"));
			ListView<Recipe> list = recipePicker(swapBox);
			Button use = button("Use this recipe", "mp-primary", () -> {
				Recipe pick = list.getSelectionModel().getSelectedItem();
				if (pick == null) {
					status.setText("Pick a recipe first.");
					return;
				}
				try {
					facade.replacePlannedMeal(date, index, pick.getId());
					say("Now " + pick.getTitle() + ".");
					swapOpen = false;
					afterPanelChange(date, pick.getId(), m.tag());
				} catch (IllegalArgumentException ex) {
					status.setText(ex.getMessage());
				}
			});
			swapBox.getChildren().add(use);
			body.getChildren().add(swapBox);
		}

		if (!past) {
			VBox tagSec = new VBox(10);
			tagSec.getStyleClass().add("mp-rv-sec");
			// tag
			tagSec.getChildren().add(tagHeader("TAG", "mp-rv-kicker"));
			FlowPane chips = new FlowPane(6, 6);
			List<String> tags = new ArrayList<>(facade.getMealTags());
			if (!m.tag().isEmpty() && !tags.contains(m.tag())) {
				tags.add(m.tag());
			}
			tags.add("");
			for (String t : tags) {
				boolean on = t.equals(m.tag());
				chips.getChildren().add(button(t.isEmpty() ? "No tag" : t, on ? "mp-chip-on" : "mp-chip-solid", () -> {
					if (on) {
						return;
					}
					try {
						facade.setPlannedMealTag(date, index, t);
						afterPanelChange(date, r.getId(), t);
					} catch (IllegalArgumentException ex) {
						status.setText(ex.getMessage());
					}
				}));
			}
			tagSec.getChildren().add(chips);

			// servings
			int[] servings = { m.servings() };
			Label servLabel = label(String.valueOf(m.servings()), "mp-day-name");
			servLabel.setMinWidth(32);
			servLabel.setAlignment(Pos.CENTER);
			Button minus = button("−", "mp-step", () -> {
				servings[0] = Math.max(1, servings[0] - 1);
				servLabel.setText(String.valueOf(servings[0]));
				facade.setPlannedMealServings(date, index, servings[0]);
				onDataChanged.run();
				rebuild();
			});
			Button plus = button("+", "mp-step", () -> {
				servings[0] = Math.min(20, servings[0] + 1);
				servLabel.setText(String.valueOf(servings[0]));
				facade.setPlannedMealServings(date, index, servings[0]);
				onDataChanged.run();
				rebuild();
			});
			Region grow = new Region();
			HBox.setHgrow(grow, Priority.ALWAYS);
			HBox serv = new HBox(6, label("Servings", "mp-label"), grow, minus, servLabel, plus);
			serv.setAlignment(Pos.CENTER_LEFT);
			tagSec.getChildren().add(serv);
			body.getChildren().add(tagSec);
		}
		// the other meals that day
		if (day.size() > 1) {
			VBox same = new VBox(6);
			same.getStyleClass().add("mp-rv-sec");
			same.getChildren().add(label("SAME DAY", "mp-rv-kicker"));
			for (int i = 0; i < day.size(); i++) {
				final int idx = i;
				PlannedMeal o = day.get(i);
				Label tl = label(o.tag().isEmpty() ? "NO TAG" : o.tag().toUpperCase(), "mp-meal-meta");
				tl.setMinWidth(92);
				Label nl = label(o.recipe().getTitle(), "mp-pick-name");
				HBox rowBox = new HBox(8, tl, nl);
				rowBox.setAlignment(Pos.CENTER_LEFT);
				rowBox.getStyleClass().add(i == index ? "mp-sameday-on" : "mp-sameday");
				rowBox.setStyle("-fx-cursor: hand;");
				rowBox.setOnMouseClicked(e -> {
					panel = new DragRef(date, idx);
					swapOpen = false;
					renderPanel();
				});
				same.getChildren().add(rowBox);
			}
			body.getChildren().add(same);
		}
		if (fullOpen) {
			body.getChildren().add(recipeNutrition(r));
			body.getChildren().add(recipeIngredients(r));
			if (!r.getSteps().isEmpty()) {
				body.getChildren().add(recipeMethod(r));
			}
		}
		body.getChildren().add(status);

		ScrollPane sp = new ScrollPane(body);
		sp.setFitToWidth(true);
		sp.getStyleClass().add("mp-scroll");
		VBox.setVgrow(sp, Priority.ALWAYS);
		VBox side = new VBox(head, sp);
		side.getStyleClass().add("mp-rv-panel");
		side.setMinWidth(440);
		side.setPrefWidth(440);
		side.setMaxWidth(440);
		StackPane.setAlignment(side, Pos.CENTER_RIGHT);
		overlay.getChildren().addAll(scrim, side);
		overlay.setVisible(true);
	}

	// ------------------------------------------------------------------ plan meals
	// / prep (temporary pop-ups)

	private void planMeals() {
		PlanMealsView wizard = new PlanMealsView(facade, this::backToCalendar, message -> {
			view = View.WEEK;
			say(message);
			backToCalendar();
		}, onDataChanged);
		getChildren().setAll(wizard);
	}

	private void backToCalendar() {
		getChildren().setAll(scroll, overlay);
		onDataChanged.run();
		rebuild();
	}

	private void showPrepDay() {
		showPrepDay(null);
	}

	private void showPrepDay(LocalDate preset) {
		closePanel();
		getChildren().setAll(new PrepDayView(facade, this::backToCalendar, message -> {
			say(message);
			backToCalendar();
		}, onDataChanged, preset));
	}
}