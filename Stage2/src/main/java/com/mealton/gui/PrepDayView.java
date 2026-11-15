package com.mealton.gui;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

import com.mealton.facade.MealtonFacade;
import com.mealton.plan.CalendarService;
import com.mealton.plan.PrepScheduler;
import com.mealton.plan.PrepScheduler.PrepMeal;
import com.mealton.plan.PrepScheduler.Row;
import com.mealton.plan.PrepScheduler.Schedule;
import com.mealton.plan.PrepScheduler.Task;
import com.mealton.plan.RangePlanner;
import com.mealton.util.Formats;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * The Schedule prep day page: pick a prep day and the dates to cook for, choose
 * how much time you have, and Mealton builds one batch-cooking session with a
 * timeline, a combined ingredient list and tick-off steps.
 */
public class PrepDayView extends StackPane {

	private static final int[] LIMITS = { 60, 90, 120, 180 };
	private static final String[] LIMIT_NAMES = { "1 h", "1.5 h", "2 h", "3 h" };

	private final MealtonFacade facade;
	private final Runnable onBack;
	private final Runnable onDataChanged;
	private final Consumer<String> onSaved;

	private final DatePicker prepDate = new DatePicker();
	private final ComboBox<String> prepTime = new ComboBox<>();
	private final DatePicker start = new DatePicker();
	private final DatePicker end = new DatePicker();
	private String preset = "next5";
	private int limit = 120;
	private boolean settingDates;

	private final VBox presetBox = new VBox();
	private final VBox rangeInfo = new VBox(4);
	private final HBox limitBox = new HBox(4);
	private final Label totalLabel = new Label();
	private final Label fitLabel = new Label();
	private final VBox content = new VBox(18);

	private Schedule schedule;
	private String error = "";
	private final Set<String> done = new HashSet<>();
	private List<Long> addedIds = new ArrayList<>();
	private String listNote = "";
	private final Memory memory;

	/**
	 * What the page remembers while the person leaves and comes back: the start
	 * time and what was added to the shopping list.
	 */
	public static final class Memory {
		String time = "10:00";
		List<Long> addedIds = new ArrayList<>();
		String note = "";
		String signature = "";
		int limit = 120;
	}

	public PrepDayView(MealtonFacade facade, Runnable onBack, Consumer<String> onSaved, Runnable onDataChanged,
			LocalDate presetDate) {
		this(facade, onBack, onSaved, onDataChanged, presetDate, new Memory());
	}

	public PrepDayView(MealtonFacade facade, Runnable onBack, Consumer<String> onSaved, Runnable onDataChanged,
			LocalDate presetDate, Memory memory) {
		this.memory = memory;
		this.addedIds = memory.addedIds;
		this.limit = memory.limit;
		this.listNote = memory.note;
		this.facade = facade;
		this.onSaved = onSaved;
		this.onBack = onBack;
		this.onDataChanged = onDataChanged;
		var css = PrepDayView.class.getResource("/plan.css");
		if (css != null) {
			getStylesheets().add(css.toExternalForm());
		}
		getStyleClass().add("mp-page");

		Label sub = label(
				"Batch-cook the meals for the dates you choose in one session. Tasks that can run at the same time are shown side by side.",
				"mp-hint");
		sub.setWrapText(true);
		VBox top = new VBox(6, button("← Back to meal plan", "mp-link", onBack), label("Schedule prep day", "mp-title"),
				sub);

		prepDate.setEditable(false);
		start.setEditable(false);
		end.setEditable(false);
		for (int h = 6; h <= 20; h++) {
			prepTime.getItems().add(String.format("%02d:00", h));
			prepTime.getItems().add(String.format("%02d:30", h));
		}
		prepTime.setValue(prepTime.getItems().contains(memory.time) ? memory.time : "10:00");
		prepTime.getStyleClass().add("mp-combo");
		prepDate.valueProperty().addListener((o, a, b) -> rebuildIfReady());
		prepTime.valueProperty().addListener((o, a, b) -> {
			if (b != null) {
				memory.time = b;
			}
			rebuildIfReady();
		});
		start.valueProperty().addListener((o, a, b) -> customDates());
		end.valueProperty().addListener((o, a, b) -> customDates());

		VBox controls = new VBox(16, existingBox, controlsCard());
		VBox page = new VBox(18, top, controls, content);
		page.setPadding(new Insets(28, 32, 32, 32));
		ScrollPane scroll = new ScrollPane(page);
		scroll.setFitToWidth(true);
		scroll.getStyleClass().add("mp-scroll");
		getChildren().add(scroll);

		prepDate.setValue(facade.today());
		applyPreset("next5");
		var existing = presetDate == null ? null : facade.getPrepDay(presetDate);
		if (existing != null) {
			editPrep(existing);
		} else {
			drawExisting();
		}
	}

	// ------------------------------------------------------------------ prep days
	// already on the calendar

	private final VBox existingBox = new VBox(10);
	/**
	 * The date of the calendar prep day being edited (null when planning a new
	 * one).
	 */
	private LocalDate editing;
	private String existingNote = "";

	private void editPrep(CalendarService.PrepEvent e) {
		editing = e.date();
		settingDates = true;
		prepDate.setValue(e.date());
		prepTime.setValue(e.start().toString());
		// show the days that follow the prep day, so the dates always make sense
		preset = "custom";
		start.setValue(e.date().isBefore(facade.today()) ? facade.today() : e.date());
		end.setValue(start.getValue().plusDays(4));
		settingDates = false;
		drawExisting();
		rebuildIfReady();
	}

	private void drawExisting() {
		existingBox.getChildren().clear();
		List<CalendarService.PrepEvent> all = new ArrayList<>(facade.getPrepDays());
		all.sort((a, b) -> a.date().compareTo(b.date()));
		if (all.isEmpty()) {
			return;
		}
		VBox card = new VBox(10);
		card.getStyleClass().add("mp-card-big");
		card.getChildren().add(label("Prep days on your calendar", "mp-h2"));
		for (CalendarService.PrepEvent e : all) {
			boolean on = e.date().equals(editing);
			Label when = label(
					nice(e.date()) + " \u00B7 " + e.start() + " \u2013 " + e.end() + " \u00B7 " + duration(e.minutes()),
					"mp-pick-name");
			Label meta = label(
					e.meals() + (e.meals() == 1 ? " portion" : " portions") + (on ? " \u00B7 editing below" : ""),
					"mp-meal-meta");
			Region grow = new Region();
			HBox.setHgrow(grow, Priority.ALWAYS);
			HBox row = new HBox(10, new VBox(2, when, meta), grow,
					button(on ? "Editing" : "Edit", "mp-outline-sm", () -> editPrep(e)),
					button("Delete", "mp-danger-sm", () -> {
						facade.removePrepDay(e.date());
						onDataChanged.run();
						if (e.date().equals(editing)) {
							editing = null;
						}
						existingNote = "Took the prep day off " + nice(e.date())
								+ ". Undo on the calendar puts it back.";
						drawExisting();
						rebuildIfReady();
					}));
			row.setAlignment(Pos.CENTER_LEFT);
			row.getStyleClass().add(on ? "mp-sameday-on" : "mp-sameday");
			card.getChildren().add(row);
		}
		if (!existingNote.isEmpty()) {
			card.getChildren().add(label(existingNote, "mp-toast"));
		}
		existingBox.getChildren().add(card);
	}

	// ------------------------------------------------------------------ helpers

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

	private static Label arrow() {
		Label l = label("\u2192", "mp-meal-meta");
		l.setMinWidth(Region.USE_PREF_SIZE);
		l.setPadding(new Insets(0, 0, 10, 0));
		return l;
	}

	private static Node labeled(String text, Node control) {
		return new VBox(4, label(text, "mp-label"), control);
	}

	private static String nice(LocalDate d) {
		return d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + ", "
				+ d.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + d.getDayOfMonth();
	}

	private static String duration(int m) {
		return (m >= 60 ? m / 60 + " h " : "") + (m % 60 != 0 ? m % 60 + " min" : (m >= 60 ? "" : "0 min"));
	}

	private LocalTime startTime() {
		return LocalTime.parse(prepTime.getValue());
	}

	private String clock(int minute) {
		return startTime().plusMinutes(minute).format(java.time.format.DateTimeFormatter.ofPattern("H:mm"));
	}

	// ------------------------------------------------------------------ controls

	private Node controlsCard() {
		VBox card = new VBox(16);
		card.getStyleClass().add("mp-card-big");

		HBox when = new HBox(8, prepDate, prepTime);
		prepDate.setPrefWidth(170);
		VBox prepCol = new VBox(8, label("Prep day", "mp-label"), when);
		prepCol.setMinWidth(300);
		HBox dates = new HBox(8, labeled("From", start), arrow(), labeled("To", end));
		dates.setAlignment(Pos.BOTTOM_LEFT);
		VBox rangeCol = new VBox(10, label("Cook meals for", "mp-label"), presetBox, dates, rangeInfo);
		HBox.setHgrow(rangeCol, Priority.ALWAYS);
		HBox row1 = new HBox(28, prepCol, rangeCol);
		row1.setAlignment(Pos.TOP_LEFT);

		for (int i = 0; i < LIMITS.length; i++) {
			final int v = LIMITS[i];
			limitBox.getChildren().add(button(LIMIT_NAMES[i], "mp-lim", () -> {
				limit = v;
				memory.limit = v;
				drawLimits();
				rebuildIfReady();
			}));
		}
		customLimit.setPromptText("Custom");
		customLimit.setPrefWidth(84);
		customLimit.getStyleClass().add("mp-input");
		customLimit.setTooltip(new Tooltip("Type your own time, e.g. 150, 2h 30 or 2:30 (15 min to 12 h)"));
		Runnable applyCustom = () -> {
			int m = parseMinutes(customLimit.getText());
			if (m < 0) {
				customLimit.setStyle("-fx-border-color: #c0392b;");
				return;
			}
			customLimit.setStyle("");
			limit = m;
			memory.limit = m;
			drawLimits();
			rebuildIfReady();
		};
		customLimit.setOnAction(e -> applyCustom.run());
		customLimit.focusedProperty().addListener((o, was, now) -> {
			if (!now && !customLimit.getText().isBlank()) {
				applyCustom.run();
			}
		});
		limitBox.getChildren().add(customLimit);
		limitBox.setAlignment(Pos.CENTER_LEFT);
		limitBox.getStyleClass().add("mp-limbar");
		limitBox.setMaxWidth(Region.USE_PREF_SIZE);
		VBox limitCol = new VBox(8, label("Time available", "mp-label"), limitBox);
		Region grow = new Region();
		HBox.setHgrow(grow, Priority.ALWAYS);
		totalLabel.getStyleClass().add("mp-kcal");
		HBox totalLine = new HBox(8, totalLabel, fitLabel);
		totalLine.setAlignment(Pos.BASELINE_RIGHT);
		VBox totalCol = new VBox(2, label("Total time", "mp-meal-meta"), totalLine);
		totalCol.setAlignment(Pos.CENTER_RIGHT);
		HBox row2 = new HBox(16, limitCol, grow, totalCol);
		row2.setAlignment(Pos.BOTTOM_LEFT);
		card.getChildren().addAll(row1, row2);
		drawLimits();
		return card;
	}

	private final TextField customLimit = new TextField();

	private void drawLimits() {
		boolean preset = false;
		for (int i = 0; i < LIMITS.length; i++) {
			Button b = (Button) limitBox.getChildren().get(i);
			b.getStyleClass().setAll("button", LIMITS[i] == limit ? "mp-lim-on" : "mp-lim");
			preset |= LIMITS[i] == limit;
		}
		if (preset) {
			if (!customLimit.isFocused()) {
				customLimit.clear();
			}
		} else {
			customLimit.setText(duration(limit));
		}
	}

	private String limitName() {
		for (int i = 0; i < LIMITS.length; i++) {
			if (LIMITS[i] == limit) {
				return LIMIT_NAMES[i];
			}
		}
		return duration(limit);
	}

	/**
	 * Accepts 150, 2:30, 2h, 2h 30, 2.5h, 90m. Returns -1 when it is not a time
	 * between 15 minutes and 12 hours.
	 */
	static int parseMinutes(String text) {
		if (text == null) {
			return -1;
		}
		String t = text.trim().toLowerCase().replace("hours", "h").replace("hour", "h").replace("hrs", "h")
				.replace("hr", "h").replace("minutes", "m").replace("min", "m");
		try {
			double minutes;
			if (t.matches("\\d+:\\d{1,2}")) {
				String[] p = t.split(":");
				minutes = Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
			} else if (t.matches("\\d+(\\.\\d+)?\\s*h\\s*\\d*\\s*m?")) {
				String[] p = t.split("h");
				minutes = Double.parseDouble(p[0].trim()) * 60;
				if (p.length > 1 && !p[1].replace("m", "").trim().isEmpty()) {
					minutes += Integer.parseInt(p[1].replace("m", "").trim());
				}
			} else {
				minutes = Double.parseDouble(t.replace("m", "").trim());
			}
			int m = (int) Math.round(minutes);
			return m >= 15 && m <= 720 ? m : -1;
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private void drawPresets() {
		FlowPane pills = new FlowPane(8, 8);
		String[][] items = { { "next5", "Next 5 days" }, { "next", "Next week" }, { "rest", "Rest of this week" },
				{ "custom", "Custom" } };
		for (String[] it : items) {
			pills.getChildren()
					.add(button(it[1], it[0].equals(preset) ? "mp-pill-on" : "mp-pill", () -> applyPreset(it[0])));
		}
		presetBox.getChildren().setAll(pills);
	}

	private void applyPreset(String id) {
		preset = id;
		LocalDate t = facade.today();
		LocalDate monday = com.mealton.plan.CalendarService.weekStart(t);
		settingDates = true;
		switch (id) {
		case "next5":
			start.setValue(t.plusDays(1));
			end.setValue(t.plusDays(5));
			break;
		case "next":
			start.setValue(monday.plusWeeks(1));
			end.setValue(monday.plusWeeks(1).plusDays(6));
			break;
		case "rest":
			start.setValue(t);
			end.setValue(monday.plusDays(6));
			break;
		default:
			break;
		}
		if (!id.equals("custom") && start.getValue() != null && prepDate.getValue() != null
				&& prepDate.getValue().isAfter(start.getValue())) {
			prepDate.setValue(start.getValue().minusDays(1).isBefore(t) ? t : start.getValue().minusDays(1));
		}
		settingDates = false;
		rebuildIfReady();
	}

	private void customDates() {
		if (!settingDates) {
			preset = "custom";
			rebuildIfReady();
		}
	}

	private void rebuildIfReady() {
		if (settingDates || prepTime.getValue() == null) {
			return;
		}
		drawPresets();
		build();
	}

	// ------------------------------------------------------------------ building

	private void build() {
		String err = PrepScheduler.error(prepDate.getValue(), start.getValue(), end.getValue());
		rangeInfo.getChildren().clear();
		schedule = null;
		error = err;
		if (!err.isEmpty()) {
			rangeInfo.getChildren().add(label(err, "mp-error"));
		} else {
			int n = RangePlanner.days(start.getValue(), end.getValue());
			rangeInfo.getChildren().add(label(
					nice(start.getValue()) + " – " + nice(end.getValue()) + " · " + n + (n == 1 ? " day" : " days"),
					"mp-meal-meta"));
			int freeze = Math.max(0,
					(int) java.time.temporal.ChronoUnit.DAYS.between(prepDate.getValue(), end.getValue())
							- PrepScheduler.FRIDGE_DAYS);
			if (freeze > 0) {
				rangeInfo.getChildren()
						.add(label(
								"Cooked meals keep about " + PrepScheduler.FRIDGE_DAYS
										+ " days in the fridge — Mealton will freeze portions for the last "
										+ Math.min(freeze, n) + (Math.min(freeze, n) == 1 ? " day." : " days."),
								"mp-hint"));
			}
			try {
				schedule = facade.buildPrepSchedule(new PrepScheduler.Options(prepDate.getValue(), startTime(),
						start.getValue(), end.getValue(), limit));
			} catch (IllegalArgumentException e) {
				error = e.getMessage();
			}
		}
		// "added to the shopping list" only stays while it is still true: same missing
		// items, still on the list
		String sig = schedule == null ? ""
				: schedule.shortRows().stream().map(r -> r.name() + "|" + r.unit()).sorted()
						.collect(java.util.stream.Collectors.joining(","));
		var onList = new java.util.HashSet<Long>();
		facade.getShoppingItems().forEach(i -> onList.add(i.getId()));
		addedIds = new ArrayList<>(addedIds);
		addedIds.removeIf(id -> !onList.contains(id));
		memory.addedIds = addedIds;
		if (!sig.equals(memory.signature) || addedIds.isEmpty()) {
			addedIds.clear();
			listNote = "";
		}
		memory.signature = sig;
		draw();
	}

	private void draw() {
		memory.note = listNote;
		content.getChildren().clear();
		if (schedule == null) {
			totalLabel.setText("–");
			fitLabel.setText("");
			if (!error.isEmpty() && !error.startsWith("Pick") && !error.startsWith("The end")
					&& !error.startsWith("Prep covers") && !error.startsWith("Your prep")) {
				VBox card = new VBox(8, label("Nothing to prep yet", "mp-h2"), wrap(label(error, "mp-hint")));
				card.getStyleClass().add("mp-card-big");
				content.getChildren().add(card);
			}
			return;
		}
		totalLabel.setText(duration(schedule.totalMinutes()));
		fitLabel.setText(schedule.fits() ? "of " + duration(limit) + " · fits" : "over " + duration(limit));
		fitLabel.getStyleClass().setAll(schedule.fits() ? "mp-fit-ok" : "mp-fit-bad");
		if (!schedule.leftOut().isEmpty()) {
			String names = String.join(", ", schedule.leftOut());
			Label note = wrap(label("To fit " + limitName() + ", Mealton left out " + names + " — cook "
					+ (schedule.leftOut().size() == 1 ? "it" : "those") + " fresh on the day.", "mp-notice"));
			content.getChildren().add(note);
		}
		content.getChildren().addAll(mealsCard(), ingredientsCard(), timelineCard(), stepsCard());
	}

	private static int indexOf(int v) {
		for (int i = 0; i < LIMITS.length; i++) {
			if (LIMITS[i] == v) {
				return i;
			}
		}
		return 2;
	}

	private static Label wrap(Label l) {
		l.setWrapText(true);
		l.setMinHeight(Region.USE_PREF_SIZE);
		return l;
	}

	// ------------------------------------------------------------------ meals

	private Node mealsCard() {
		VBox card = new VBox(12);
		card.getStyleClass().add("mp-card-big");
		long recipes = schedule.meals().stream().filter(m -> !m.fresh()).count();
		int n = RangePlanner.days(start.getValue(), end.getValue());
		HBox head = new HBox(label("Meals you’re prepping", "mp-h2"));
		Region g = new Region();
		HBox.setHgrow(g, Priority.ALWAYS);
		head.getChildren().addAll(g, label(
				schedule.portions() + " portions across " + recipes + " recipes · " + n + (n == 1 ? " day" : " days"),
				"mp-meal-meta"));
		head.setAlignment(Pos.CENTER_LEFT);
		FlowPane grid = new FlowPane(12, 12);
		for (PrepMeal m : schedule.meals()) {
			grid.getChildren().add(mealCard(m));
		}
		card.getChildren().addAll(head, grid);
		return card;
	}

	private Node photo(com.mealton.recipe.Recipe r, boolean faded) {
		StackPane box = new StackPane();
		box.setMinSize(72, 72);
		box.setPrefSize(72, 72);
		box.setMaxSize(72, 72);
		box.getStyleClass().add("mp-photo");
		box.getChildren().add(MealPlanView.letterOf(r, 72));
		javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(72, 72);
		clip.setArcWidth(16);
		clip.setArcHeight(16);
		box.setClip(clip);
		var file = facade.getRecipePhoto(r.getId());
		if (file.isPresent()) {
			javafx.scene.image.ImageView v = PhotoFit.cover(file.get(), 72, 72);
			if (v != null) {
				box.getChildren().add(v);
			}
		}
		box.setOpacity(faded ? 0.6 : 1);
		return box;
	}

	private Node mealCard(PrepMeal m) {
		VBox c = new VBox(4);
		c.getChildren().add(wrap(label(m.recipe().getTitle(), "mp-pick-name")));
		String slot = m.tag().isEmpty() ? "meal" : m.tag().toLowerCase(Locale.ROOT);
		c.getChildren().add(
				label(m.portions() + (m.portions() == 1 ? " portion" : " portions") + " · " + slot, "mp-meal-meta"));
		List<String> days = new ArrayList<>();
		for (LocalDate d : m.dates()) {
			days.add(d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
		}
		c.getChildren().add(wrap(label(String.join(", ", days), "mp-hint")));
		int fridge = m.dates().size() - m.frozen();
		String store;
		if (m.fresh()) {
			store = "Cook fresh on the day — doesn’t fit in " + limitName();
		} else if (m.frozen() > 0 && "Breakfast".equalsIgnoreCase(m.tag())) {
			store = "Fridge " + fridge + " · assemble " + m.frozen() + " fresh";
		} else if (m.frozen() > 0) {
			store = "Fridge " + fridge + " · freeze " + m.frozen();
		} else {
			store = "Fridge · keeps " + PrepScheduler.FRIDGE_DAYS + " days";
		}
		c.getChildren()
				.add(label(store, m.fresh() ? "mp-store-warn" : m.frozen() > 0 ? "mp-store-freeze" : "mp-store-ok"));
		HBox.setHgrow(c, Priority.ALWAYS);
		HBox card = new HBox(12, photo(m.recipe(), m.fresh()), c);
		card.setPrefWidth(300);
		card.setPadding(new Insets(10));
		card.getStyleClass().add(m.fresh() ? "mp-prep-fresh" : "mp-prep-card");
		return card;
	}

	// ------------------------------------------------------------------
	// ingredients

	private String amount(double q, String unit) {
		return Formats.quantityWithUnit(Math.ceil(q * 100) / 100, unit);
	}

	private Node ingredientsCard() {
		VBox card = new VBox(12);
		card.getStyleClass().add("mp-card-big");
		VBox titles = new VBox(2, label("Ingredients", "mp-h2"),
				label("Everything for the session, combined across recipes and checked against your pantry.",
						"mp-meal-meta"));
		HBox.setHgrow(titles, Priority.ALWAYS);
		HBox head = new HBox(12, titles);
		head.setAlignment(Pos.CENTER_LEFT);
		List<Row> shorts = schedule.shortRows();
		if (!shorts.isEmpty()) {
			if (addedIds.isEmpty()) {
				head.getChildren().add(button("Add " + shorts.size() + " missing "
						+ (shorts.size() == 1 ? "item" : "items") + " to shopping list", "mp-primary", () -> {
							addedIds = new ArrayList<>(facade.addPrepShortToShoppingList(schedule));
							memory.addedIds = addedIds;
							listNote = addedIds.isEmpty() ? "Those are already on your shopping list."
									: "Added " + addedIds.size() + (addedIds.size() == 1 ? " item" : " items")
											+ " to your list.";
							onDataChanged.run();
							draw();
						}));
				if (!listNote.isEmpty()) {
					head.getChildren().add(label(listNote, "mp-toast"));
				}
			} else {
				Button undo = button("Undo", "mp-link", () -> {
					facade.deleteShoppingItems(addedIds);
					addedIds = new ArrayList<>();
					memory.addedIds = addedIds;
					listNote = "";
					onDataChanged.run();
					draw();
				});
				HBox ok = new HBox(10, label(listNote, "mp-toast"), undo);
				ok.setAlignment(Pos.CENTER_LEFT);
				head.getChildren().add(ok);
			}
		}
		card.getChildren().add(head);
		GridPane t = new GridPane();
		t.setHgap(18);
		t.setVgap(8);
		String[] cols = { "INGREDIENT", "YOU NEED", "IN PANTRY", "FOR", "STATUS" };
		for (int i = 0; i < cols.length; i++) {
			t.add(label(cols[i], "mp-th"), i, 0);
		}
		javafx.scene.layout.ColumnConstraints c0 = new javafx.scene.layout.ColumnConstraints();
		c0.setPercentWidth(24);
		javafx.scene.layout.ColumnConstraints c1 = new javafx.scene.layout.ColumnConstraints();
		c1.setPercentWidth(14);
		javafx.scene.layout.ColumnConstraints c2 = new javafx.scene.layout.ColumnConstraints();
		c2.setPercentWidth(14);
		javafx.scene.layout.ColumnConstraints c3 = new javafx.scene.layout.ColumnConstraints();
		c3.setPercentWidth(30);
		javafx.scene.layout.ColumnConstraints c4 = new javafx.scene.layout.ColumnConstraints();
		c4.setPercentWidth(18);
		t.getColumnConstraints().addAll(c0, c1, c2, c3, c4);
		int r = 1;
		for (Row row : schedule.ingredients()) {
			t.add(wrap(label(row.name().isEmpty() ? row.name()
					: Character.toUpperCase(row.name().charAt(0)) + row.name().substring(1), "mp-pick-name")), 0, r);
			t.add(label(amount(row.need(), row.unit()), "mp-td"), 1, r);
			t.add(label(row.have() > 0 ? amount(row.have(), row.unit()) : "—", "mp-td"), 2, r);
			t.add(wrap(label(String.join(", ", row.forRecipes()), "mp-hint")), 3, r);
			String status = row.shortBy() <= 1e-9 ? "Have it"
					: row.have() <= 0 ? "Buy " + amount(row.shortBy(), row.unit())
							: "Short " + amount(row.shortBy(), row.unit());
			t.add(label(status, row.shortBy() <= 1e-9 ? "mp-status-ok" : "mp-status-short"), 4, r);
			r++;
		}
		card.getChildren().add(t);
		return card;
	}

	// ------------------------------------------------------------------ timeline

	private Node timelineCard() {
		VBox card = new VBox(12);
		card.getStyleClass().add("mp-card-big");
		card.getChildren().add(label("Timeline", "mp-h2"));
		int span = Math.max(limit, schedule.totalMinutes());
		span = (span + 29) / 30 * 30;
		final int total = span;
		VBox lanes = new VBox(10);
		// ticks
		HBox ticks = new HBox();
		Pane tickPane = new Pane();
		tickPane.setMinHeight(18);
		tickPane.setPrefHeight(18);
		for (int m = 0; m <= total; m += 30) {
			final int at = m;
			Label l = label(clock(m), "mp-meal-meta");
			tickPane.getChildren().add(l);
			l.layoutXProperty().bind(tickPane.widthProperty().multiply((double) at / total));
			if (at > 0) {
				l.translateXProperty().bind(l.widthProperty().multiply(at == total ? -1.0 : -0.5));
			}
		}
		Label pad = new Label();
		pad.setMinWidth(100);
		HBox.setHgrow(tickPane, Priority.ALWAYS);
		ticks.getChildren().addAll(pad, tickPane);
		lanes.getChildren().add(ticks);
		for (String lane : PrepScheduler.LANES) {
			Pane track = new Pane();
			track.getStyleClass().add("mp-lane");
			track.setMinHeight(58);
			track.setPrefHeight(58);
			for (Task t : schedule.tasks()) {
				if (!t.lane().equals(lane)) {
					continue;
				}
				VBox block = new VBox(1, label(t.name(), "mp-block-name"),
						label(t.minutes() + " min", "mp-block-time"));
				block.getStyleClass().addAll("mp-block", "mp-block-" + lane.toLowerCase(Locale.ROOT));
				block.setPadding(new Insets(5, 8, 5, 8));
				javafx.scene.control.Tooltip.install(block,
						new javafx.scene.control.Tooltip(t.name() + "\n" + clock(t.startMinute()) + " \u00B7 "
								+ t.minutes() + " min \u00B7 " + lane
								+ (t.serves().isEmpty() ? "" : "\nFor: " + String.join(", ", t.serves()))));
				block.layoutXProperty().bind(track.widthProperty().multiply((double) t.startMinute() / total).add(1));
				block.prefWidthProperty()
						.bind(track.widthProperty().multiply((double) t.minutes() / total).subtract(2));
				block.maxWidthProperty().bind(block.prefWidthProperty());
				block.setLayoutY(5);
				block.setPrefHeight(48);
				block.setMaxHeight(48);
				block.setClip(new javafx.scene.shape.Rectangle());
				javafx.scene.shape.Rectangle clip = (javafx.scene.shape.Rectangle) block.getClip();
				clip.widthProperty().bind(block.widthProperty());
				clip.heightProperty().bind(block.heightProperty());
				track.getChildren().add(block);
			}
			Label name = label(lane, "mp-pick-name");
			name.setMinWidth(100);
			HBox.setHgrow(track, Priority.ALWAYS);
			HBox row = new HBox(name, track);
			row.setAlignment(Pos.CENTER_LEFT);
			lanes.getChildren().add(row);
		}
		card.getChildren().add(lanes);
		return card;
	}

	// ------------------------------------------------------------------ steps

	private Node stepsCard() {
		VBox card = new VBox(10);
		card.getStyleClass().add("mp-card-big");
		card.getChildren().add(label("Step by step", "mp-h2"));
		List<Task> ordered = schedule.ordered();
		int doneCount = 0;
		for (Task t : ordered) {
			if (done.contains(t.name())) {
				doneCount++;
			}
		}
		javafx.scene.control.ProgressBar bar = new javafx.scene.control.ProgressBar(
				(double) doneCount / Math.max(1, ordered.size()));
		bar.setPrefWidth(240);
		bar.getStyleClass().add("mp-progress");
		Region g = new Region();
		HBox.setHgrow(g, Priority.ALWAYS);
		HBox prog = new HBox(12,
				label("Tick steps off as you go. " + doneCount + " of " + ordered.size() + " done.", "mp-meal-meta"), g,
				bar);
		prog.setAlignment(Pos.CENTER_LEFT);
		card.getChildren().add(prog);
		int num = 1;
		for (Task t : ordered) {
			card.getChildren().add(stepRow(num++, t, ordered));
		}
		var onCalendar = facade.getPrepDay(prepDate.getValue());
		boolean moving = editing != null && !editing.equals(prepDate.getValue());
		Button calendar;
		if (editing != null) {
			calendar = button(moving ? "Move prep day to " + nice(prepDate.getValue()) : "Save changes", "mp-primary",
					() -> {
						try {
							LocalDate was = editing;
							facade.movePrepDay(was, schedule);
							editing = null;
							onDataChanged.run();
							onSaved.accept((was.equals(prepDate.getValue()) ? "Updated the prep day on "
									: "Moved the prep day from " + nice(was) + " to ") + nice(prepDate.getValue())
									+ " at " + prepTime.getValue() + ". Undo puts it back.");
						} catch (IllegalArgumentException e) {
							listNote = e.getMessage();
							draw();
						}
					});
		} else if (onCalendar != null) {
			calendar = button("Update this prep day", "mp-primary", () -> {
				facade.savePrepDay(schedule);
				onDataChanged.run();
				onSaved.accept("Updated the prep day on " + nice(prepDate.getValue()) + " at " + prepTime.getValue()
						+ ". Undo puts it back.");
			});
		} else {
			calendar = button("Add to calendar", "mp-primary", () -> {
				try {
					facade.savePrepDay(schedule);
					onDataChanged.run();
					onSaved.accept("Added a prep day on " + nice(prepDate.getValue()) + " at " + prepTime.getValue()
							+ " \u00B7 " + duration(schedule.totalMinutes()) + ". Undo takes it off.");
				} catch (IllegalArgumentException e) {
					listNote = e.getMessage();
					draw();
				}
			});
		}
		HBox bottomRow = new HBox(10, calendar);
		LocalDate removable = editing != null ? editing : onCalendar != null ? prepDate.getValue() : null;
		if (removable != null) {
			bottomRow.getChildren().add(button("Delete this prep day", "mp-danger", () -> {
				facade.removePrepDay(removable);
				onDataChanged.run();
				onSaved.accept("Took the prep day off " + nice(removable) + ". Undo puts it back.");
			}));
		}
		card.getChildren().add(bottomRow);
		return card;
	}

	private Node stepRow(int num, Task t, List<Task> all) {
		boolean isDone = done.contains(t.name());
		VBox time = new VBox(4, label(clock(t.startMinute()), "mp-pick-name"),
				label(t.minutes() + " min", "mp-meal-meta"));
		time.setAlignment(Pos.TOP_CENTER);
		time.setMinWidth(64);
		Label title = label(t.name(), "mp-step-title");
		if (isDone) {
			title.setStyle("-fx-strikethrough: true;");
		}
		HBox head = new HBox(8, label("Step " + num, "mp-meal-meta"), title,
				label(t.lane(), "mp-lane-" + t.lane().toLowerCase(Locale.ROOT)));
		head.setAlignment(Pos.CENTER_LEFT);
		VBox how = new VBox(3);
		for (String h : t.how()) {
			how.getChildren().add(wrap(label("• " + h, "mp-meal-meta")));
		}
		VBox body = new VBox(8, head, how);
		FlowPane chips = new FlowPane(6, 6);
		for (String s : t.serves().size() > 3 ? List.of("All recipes") : t.serves()) {
			chips.getChildren().add(label(s, "mp-serve"));
		}
		List<String> overlap = new ArrayList<>();
		for (Task u : all) {
			if (u != t && !u.lane().equals(t.lane()) && u.startMinute() < t.endMinute()
					&& t.startMinute() < u.endMinute()) {
				overlap.add(u.name().toLowerCase(Locale.ROOT));
			}
		}
		if (!overlap.isEmpty()) {
			chips.getChildren()
					.add(label("While this runs: " + String.join(", ", overlap.subList(0, Math.min(2, overlap.size()))),
							"mp-hint"));
		}
		body.getChildren().add(chips);
		if (t.tip() != null) {
			body.getChildren().add(wrap(label("Tip: " + t.tip(), "mp-tip")));
		}
		HBox.setHgrow(body, Priority.ALWAYS);
		CheckBox box = new CheckBox("Done");
		box.setSelected(isDone);
		box.setOnAction(e -> {
			if (box.isSelected()) {
				done.add(t.name());
			} else {
				done.remove(t.name());
			}
			draw();
		});
		HBox row = new HBox(14, time, body, box);
		row.setAlignment(Pos.TOP_LEFT);
		row.getStyleClass().add(isDone ? "mp-pstep-done" : "mp-pstep");
		return row;
	}

	/** Called when the page is shown again so a stale schedule is rebuilt. */
	public void refresh() {
		build();
	}
}