package com.mealton.gui;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

import com.mealton.facade.MealtonFacade;
import com.mealton.nutrition.RecipeNutrition;
import com.mealton.plan.CalendarService;
import com.mealton.plan.PlannedMeal;
import com.mealton.plan.RangePlanner;
import com.mealton.plan.RangePlanner.Draft;
import com.mealton.plan.RangePlanner.Step;
import com.mealton.profile.Diet;
import com.mealton.profile.UserProfile;
import com.mealton.recipe.Recipe;
import com.mealton.recipe.RecipeIngredient;
import com.mealton.recipe.RecipeMatch;

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

/**
 * The Plan meals page: choose dates and options, let Mealton draft the meals,
 * keep the ones you like, regenerate the rest, then apply the draft to the
 * calendar. Nothing reaches the calendar until "Apply to calendar".
 */
public class PlanMealsView extends StackPane {

	private final MealtonFacade facade;
	private final Runnable onBack;
	private final Consumer<String> onApplied;
	private final Runnable onDataChanged;

	// form
	private String preset = "this";
	private boolean settingDates;
	private final DatePicker start = new DatePicker();
	private final DatePicker end = new DatePicker();
	private final ComboBox<String> diet = new ComboBox<>();
	private final TextField calories = new TextField();
	private final CheckBox breakfast = new CheckBox("Breakfast");
	private final CheckBox lunch = new CheckBox("Lunch");
	private final CheckBox dinner = new CheckBox("Dinner");
	private final CheckBox pantryFirst = new CheckBox("Use pantry items first");
	private final CheckBox avoidRecent = new CheckBox("Avoid meals from last week");
	private final CheckBox moreOnline = new CheckBox("Also look for new recipes online");
	private boolean searching;
	private boolean suggestedOn = true;
	private final Set<String> picked = new LinkedHashSet<>();
	private final List<String> custom = new ArrayList<>();
	private final TextField cuisineDraft = new TextField();
	private String cuisineError = "";

	private final VBox presetBox = new VBox(8);
	private final VBox rangeInfo = new VBox(4);
	private final VBox cuisineBox = new VBox(8);
	private final Button generate = new Button();

	// results
	private final VBox results = new VBox(16);
	private Draft draft;
	private VBox formPane; // the options on the left
	private Button optionsToggle; // hides the options so the calendar can use the whole width
	private final Set<String> kept = new HashSet<>();
	private String toast = "";
	private String formError = "";

	public PlanMealsView(MealtonFacade facade, Runnable onBack, Consumer<String> onApplied, Runnable onDataChanged) {
		this.facade = facade;
		this.onBack = onBack;
		this.onApplied = onApplied;
		this.onDataChanged = onDataChanged;
		var css = PlanMealsView.class.getResource("/plan.css");
		if (css != null) {
			getStylesheets().add(css.toExternalForm());
		}
		getStyleClass().add("mp-page");

		Button back = button("← Back to meal plan", "mp-link", onBack);
		Label title = label("Plan meals", "mp-title");
		Label sub = label(
				"Pick the dates, and Mealton drafts meals for each day from your profile, allergies and pantry — then checks every meal before saving.",
				"mp-hint");
		sub.setWrapText(true);
		optionsToggle = button("Hide options", "mp-outline", () -> setOptionsHidden(formPane.isVisible()));
		optionsToggle.setVisible(false);
		optionsToggle.setManaged(false);
		VBox top = new VBox(6, back, title, sub, optionsToggle);

		VBox form = buildForm();
		formPane = form;
		form.setMinWidth(330);
		form.setPrefWidth(340);
		form.setMaxWidth(360);
		HBox.setHgrow(results, Priority.ALWAYS);
		HBox columns = new HBox(24, form, results);
		columns.setAlignment(Pos.TOP_LEFT);

		VBox page = new VBox(18, top, columns);
		page.setPadding(new Insets(24, 24, 32, 24));
		ScrollPane scroll = new ScrollPane(page);
		scroll.setFitToWidth(true);
		scroll.getStyleClass().add("mp-scroll");
		getChildren().add(scroll);
		overlay.setVisible(false);
		getChildren().add(overlay);

		applyPreset(facade.today().getDayOfWeek().getValue() >= 5 && facade.today().getDayOfWeek() != DayOfWeek.SUNDAY
				? "next"
				: "this");
		drawResults();
	}

	// ------------------------------------------------------------------ small
	// helpers

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

	private LocalDate today() {
		return facade.today();
	}

	private static String shortDay(LocalDate d) {
		return d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
	}

	private static String monthDay(LocalDate d) {
		return d.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + d.getDayOfMonth();
	}

	// ------------------------------------------------------------------ the form

	private VBox buildForm() {
		VBox card = new VBox(14);
		card.getStyleClass().add("mp-card-big");

		card.getChildren().add(label("Dates", "mp-h2"));
		card.getChildren().add(presetBox);
		start.setEditable(false);
		end.setEditable(false);
		start.getStyleClass().add("mp-date");
		end.getStyleClass().add("mp-date");
		start.valueProperty().addListener((o, a, b) -> dateChanged());
		end.valueProperty().addListener((o, a, b) -> dateChanged());
		HBox dates = new HBox(8, labeled("From", start), arrow(), labeled("To", end));
		dates.setAlignment(Pos.BOTTOM_LEFT);
		card.getChildren().addAll(dates, rangeInfo);

		card.getChildren().add(label("Options (optional)", "mp-h2"));
		diet.getItems().addAll("From profile (no restriction)", "Vegetarian", "Vegan", "High-protein");
		diet.setValue(diet.getItems().get(0));
		diet.getStyleClass().add("mp-combo");
		diet.setMaxWidth(Double.MAX_VALUE);
		card.getChildren().add(labeled("Diet", diet));

		UserProfile p = facade.getProfile();
		calories.setText(String.valueOf(p.calorieGoal()));
		calories.getStyleClass().add("mp-input");
		card.getChildren().add(labeled("Daily calories", calories));
		card.getChildren().add(
				label("Profile target: " + p.calorieGoal() + " kcal. Change to override for this plan.", "mp-hint"));

		breakfast.setSelected(true);
		lunch.setSelected(true);
		dinner.setSelected(true);
		card.getChildren().add(labeled("Meals to plan", new HBox(14, breakfast, lunch, dinner)));

		card.getChildren().add(cuisineBox);
		pantryFirst.setSelected(true);
		avoidRecent.setSelected(true);
		moreOnline.setSelected(true);
		card.getChildren().addAll(pantryFirst, avoidRecent, moreOnline);

		generate.getStyleClass().setAll("button", "mp-primary");
		generate.setMaxWidth(Double.MAX_VALUE);
		generate.setOnAction(e -> generate());
		card.getChildren().add(generate);
		return card;
	}

	private static Label arrow() {
		Label l = label("\u2192", "mp-meal-meta");
		l.setMinWidth(Region.USE_PREF_SIZE);
		l.setPadding(new Insets(0, 0, 10, 0));
		return l;
	}

	private static Node labeled(String text, Node control) {
		Label l = new Label(text);
		l.getStyleClass().add("mp-label");
		return new VBox(4, l, control);
	}

	private void drawPresets() {
		presetBox.getChildren().clear();
		FlowPane pills = new FlowPane(8, 8);
		String[][] items = { { "this", "This week" }, { "next", "Next week" }, { "two", "Next 2 weeks" },
				{ "rest", "Rest of this week" }, { "custom", "Custom" } };
		for (String[] it : items) {
			Button b = button(it[1], it[0].equals(preset) ? "mp-pill-on" : "mp-pill", () -> applyPreset(it[0]));
			pills.getChildren().add(b);
		}
		presetBox.getChildren().add(pills);
	}

	private void applyPreset(String id) {
		preset = id;
		LocalDate t = today();
		LocalDate monday = CalendarService.weekStart(t);
		settingDates = true;
		switch (id) {
		case "this":
			start.setValue(monday);
			end.setValue(monday.plusDays(6));
			break;
		case "next":
			start.setValue(monday.plusWeeks(1));
			end.setValue(monday.plusWeeks(1).plusDays(6));
			break;
		case "two":
			start.setValue(monday.plusWeeks(1));
			end.setValue(monday.plusWeeks(1).plusDays(13));
			break;
		case "rest":
			start.setValue(t);
			end.setValue(monday.plusDays(6));
			break;
		default:
			break;
		}
		settingDates = false;
		refreshForm();
	}

	private void dateChanged() {
		if (settingDates) {
			return;
		}
		preset = "custom";
		refreshForm();
	}

	private void refreshForm() {
		drawPresets();
		rangeInfo.getChildren().clear();
		String err = RangePlanner.rangeError(start.getValue(), end.getValue());
		int n = RangePlanner.days(start.getValue(), end.getValue());
		int past = RangePlanner.pastDays(start.getValue(), end.getValue(), today());
		int planDays = err.isEmpty() ? n - past : 0;
		if (!err.isEmpty()) {
			rangeInfo.getChildren().add(label(err, "mp-error"));
		} else {
			rangeInfo.getChildren().add(label(monthDay(start.getValue()) + " – " + monthDay(end.getValue()) + " · " + n
					+ (n == 1 ? " day" : " days"), "mp-meal-meta"));
			if (past > 0) {
				rangeInfo.getChildren()
						.add(label(past + (past == 1 ? " day is" : " days are")
								+ " already past — Mealton keeps what you ate and plans the other " + planDays + ".",
								"mp-hint"));
			}
		}
		generate.setDisable(!err.isEmpty() || planDays < 1);
		generate.setText(!err.isEmpty() ? "Fix the dates to continue"
				: "Generate plan for " + planDays + (planDays == 1 ? " day" : " days"));
		drawCuisines();
	}

	private void drawCuisines() {
		cuisineBox.getChildren().clear();
		Label title = label(
				"Cuisines "
						+ (activeCuisines().isEmpty() ? "· any cuisine" : "· " + activeCuisines().size() + " picked"),
				"mp-label");
		Region grow = new Region();
		HBox.setHgrow(grow, Priority.ALWAYS);
		Label swl = label("Suggested", "mp-label");
		StackPane sw = switchNode(suggestedOn, () -> {
			suggestedOn = !suggestedOn;
			drawCuisines();
		});
		HBox head = new HBox(10, title, grow, swl, sw);
		head.setAlignment(Pos.CENTER_LEFT);

		FlowPane chips = new FlowPane(8, 8);
		if (suggestedOn) {
			for (String c : RangePlanner.SUGGESTED_CUISINES) {
				boolean on = picked.contains(c);
				chips.getChildren().add(button(c, on ? "mp-chip-on" : "mp-chip-dash", () -> toggle(c)));
			}
		}
		for (String c : custom) {
			boolean on = picked.contains(c);
			Button name = button(c, on ? "mp-chip-on" : "mp-chip-solid", () -> toggle(c));
			Button x = button("×", "mp-chip-x", () -> {
				custom.remove(c);
				picked.remove(c);
				drawCuisines();
			});
			HBox pair = new HBox(0, name, x);
			chips.getChildren().add(pair);
		}
		cuisineDraft.setPromptText("Add your own, e.g. Korean");
		cuisineDraft.getStyleClass().add("mp-input");
		cuisineDraft.setOnAction(e -> addCuisine());
		HBox.setHgrow(cuisineDraft, Priority.ALWAYS);
		HBox add = new HBox(8, cuisineDraft, button("+ Add", "mp-outline", this::addCuisine));
		cuisineBox.getChildren().addAll(head, chips, add);
		if (!cuisineError.isEmpty()) {
			cuisineBox.getChildren().add(label(cuisineError, "mp-error"));
		}
		List<String> act = new ArrayList<>(activeCuisines());
		cuisineBox.getChildren()
				.add(label((suggestedOn ? "Dashed chips are Mealton’s suggestions; solid ones are yours. "
						: "Suggestions are hidden. ")
						+ (act.isEmpty() ? "Pick none and Mealton mixes it up."
								: "Mealton will lean toward " + String.join(", ", act) + "."),
						"mp-hint"));
	}

	private Set<String> activeCuisines() {
		Set<String> out = new LinkedHashSet<>();
		for (String c : picked) {
			if ((suggestedOn && RangePlanner.SUGGESTED_CUISINES.contains(c)) || custom.contains(c)) {
				out.add(c);
			}
		}
		return out;
	}

	private void toggle(String c) {
		if (!picked.remove(c)) {
			picked.add(c);
		}
		drawCuisines();
	}

	private void addCuisine() {
		String n = cuisineDraft.getText() == null ? "" : cuisineDraft.getText().trim();
		if (n.isEmpty()) {
			cuisineError = "Type a cuisine first.";
		} else {
			boolean dup = false;
			List<String> all = new ArrayList<>(RangePlanner.SUGGESTED_CUISINES);
			all.addAll(custom);
			for (String c : all) {
				dup |= c.equalsIgnoreCase(n);
			}
			if (dup) {
				cuisineError = "“" + n + "” is already on the list.";
			} else {
				custom.add(n);
				picked.add(n);
				cuisineDraft.clear();
				cuisineError = "";
			}
		}
		drawCuisines();
	}

	private StackPane switchNode(boolean on, Runnable toggle) {
		Region knob = new Region();
		knob.getStyleClass().add("mp-knob");
		knob.setMinSize(20, 20);
		knob.setPrefSize(20, 20);
		knob.setMaxSize(20, 20);
		StackPane track = new StackPane(knob);
		track.getStyleClass().add(on ? "mp-switch-on" : "mp-switch-off");
		track.setMinSize(46, 26);
		track.setPrefSize(46, 26);
		track.setMaxSize(46, 26);
		StackPane.setAlignment(knob, on ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
		StackPane.setMargin(knob, new Insets(0, 3, 0, 3));
		track.setStyle("-fx-cursor: hand;");
		track.setOnMouseClicked(e -> toggle.run());
		return track;
	}

	// ------------------------------------------------------------------ generating

	private void generate() {
		if (searching) {
			return;
		}
		formError = "";
		toast = "";
		try {
			List<String> tags = new ArrayList<>();
			if (breakfast.isSelected()) {
				tags.add("Breakfast");
			}
			if (lunch.isSelected()) {
				tags.add("Lunch");
			}
			if (dinner.isSelected()) {
				tags.add("Dinner");
			}
			Integer kcal = null;
			String text = calories.getText() == null ? "" : calories.getText().trim().replace(",", "");
			if (!text.isEmpty()) {
				try {
					int v = Integer.parseInt(text);
					if (v < 800 || v > 6000) {
						throw new IllegalArgumentException("Daily calories should be between 800 and 6000.");
					}
					kcal = v == facade.getProfile().calorieGoal() ? null : v;
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException("Daily calories must be a whole number.");
				}
			}
			String d = diet.getValue();
			Diet override = d.startsWith("Vegetarian") ? Diet.VEGETARIAN : d.startsWith("Vegan") ? Diet.VEGAN : null;
			RangePlanner.Options o = new RangePlanner.Options(start.getValue(), end.getValue(), tags, override,
					d.startsWith("High"), kcal, new LinkedHashSet<>(activeCuisines()), pantryFirst.isSelected(),
					avoidRecent.isSelected());
			if (moreOnline.isSelected()) {
				List<String> hints = new ArrayList<>(o.cuisines());
				if (hints.size() > 3) {
					hints = hints.subList(0, 3);
				}
				if (tags.contains("Breakfast")) {
					hints.add("breakfast");
				}
				if (hints.isEmpty()) {
					hints.add("");
				}
				final List<String> ask = hints;
				searching = true;
				generate.setDisable(true);
				draft = null;
				formError = "";
				drawSearching();
				javafx.concurrent.Task<List<com.mealton.recipe.Recipe>> task = new javafx.concurrent.Task<>() {
					@Override
					protected List<com.mealton.recipe.Recipe> call() {
						return facade.findRecipesForPlan(ask, 12);
					}
				};
				task.setOnSucceeded(ev -> {
					int added = facade.swapInDiscoveredRecipes(task.getValue());
					finishDraft(o, added);
				});
				task.setOnFailed(ev -> finishDraft(o, 0));
				Thread t = new Thread(task, "plan-recipes");
				t.setDaemon(true);
				t.start();
				return;
			}
			draft = facade.draftPlan(o);
			kept.clear();
			setOptionsHidden(true); // give the calendar the whole width
		} catch (IllegalArgumentException e) {
			formError = e.getMessage();
			draft = null;
			setOptionsHidden(false);
		}
		drawResults();
	}

	private void drawSearching() {
		results.getChildren().clear();
		VBox box = new VBox(8);
		box.getStyleClass().add("mp-card-big");
		box.getChildren().add(label("Finding recipes\u2026", "mp-h2"));
		Label text = label(
				"Mealton is checking TheMealDB and Spoonacular for new recipes to choose from, then it will build your plan.",
				"mp-hint");
		text.setWrapText(true);
		box.getChildren().add(text);
		results.getChildren().add(box);
	}

	/**
	 * The online search is over (it may have found nothing): build the draft from
	 * every recipe now loaded.
	 */
	private void finishDraft(RangePlanner.Options o, int added) {
		searching = false;
		generate.setDisable(false);
		try {
			draft = facade.draftPlan(o);
			kept.clear();
			if (added > 0) {
				toast = "Added " + added + " new recipe" + (added == 1 ? "" : "s") + " from online to choose from.";
			}
			setOptionsHidden(true);
		} catch (IllegalArgumentException e) {
			formError = e.getMessage();
			draft = null;
			setOptionsHidden(false);
		}
		drawResults();
	}

	/**
	 * Hides or shows the options on the left; hidden, the week calendar gets the
	 * whole width.
	 */
	private void setOptionsHidden(boolean hidden) {
		boolean hide = hidden && draft != null;
		formPane.setVisible(!hide);
		formPane.setManaged(!hide);
		optionsToggle.setText(hide ? "Show options" : "Hide options");
		optionsToggle.setVisible(draft != null);
		optionsToggle.setManaged(draft != null);
	}

	// ------------------------------------------------------------------ results

	private void drawResults() {
		results.getChildren().clear();
		if (draft == null) {
			VBox empty = new VBox(8);
			empty.getStyleClass().add("mp-card-big");
			empty.getChildren()
					.add(label(formError.isEmpty() ? "Your plan will show up here" : "Couldn’t make a plan", "mp-h2"));
			Label text = label(formError.isEmpty()
					? "Choose your dates and options, then press Generate. You can keep the meals you like and regenerate the rest before anything is saved."
					: formError, formError.isEmpty() ? "mp-hint" : "mp-error");
			text.setWrapText(true);
			empty.getChildren().add(text);
			results.getChildren().add(empty);
			return;
		}
		results.getChildren().add(stepsCard());
		results.getChildren().add(readyCard());
	}

	private Node stepsCard() {
		VBox card = new VBox(10);
		card.getStyleClass().add("mp-card-big");
		card.getChildren().add(label("What Mealton did", "mp-h2"));
		for (Step s : draft.steps()) {
			Label mark = label(s.ok() ? "✓" : "!", s.ok() ? "mp-dot-ok" : "mp-dot-warn");
			mark.setMinSize(26, 26);
			mark.setAlignment(Pos.CENTER);
			Label t = label(s.title(), "mp-pick-name");
			Label detail = label(s.detail(), "mp-meal-meta");
			detail.setWrapText(true);
			detail.setMinHeight(Region.USE_PREF_SIZE);
			Label tool = label(s.tool(), "mp-code");
			HBox line = new HBox(10, t, tool);
			line.setAlignment(Pos.CENTER_LEFT);
			HBox row = new HBox(12, mark, new VBox(2, line, detail));
			row.setAlignment(Pos.TOP_LEFT);
			card.getChildren().add(row);
		}
		return card;
	}

	private Node readyCard() {
		VBox card = new VBox(14);
		card.getStyleClass().add("mp-card-big");
		LocalDate a = draft.options().start();
		LocalDate b = draft.options().end();
		card.getChildren().add(label("Your plan is ready · " + monthDay(a) + " – " + monthDay(b), "mp-h2"));
		if (!toast.isEmpty()) {
			card.getChildren().add(label(toast, "mp-toast"));
		}
		HBox tiles = new HBox(12, tile(String.valueOf(draft.mealCount()), "meals"),
				tile(draft.avgKcal() == 0 ? "–" : String.format("%,d", draft.avgKcal()), "avg kcal / day"),
				tile(draft.expiringUsed() + " / " + draft.expiringTotal(), "expiring items used"),
				tile(String.valueOf(draft.toBuy()), "items to buy"));
		card.getChildren().add(tiles);

		Label prev = label("Preview", "mp-pick-name");
		Label help = label("Tap a meal to see the full recipe. Lock the ones you like, then regenerate the rest. "
				+ (kept.isEmpty() ? "" : kept.size() + " kept."), "mp-meal-meta");
		help.setWrapText(true);
		boolean all = kept.size() == draft.mealCount();
		Button keepAll = button(all ? "Unlock all" : "Keep all", "mp-outline", () -> {
			kept.clear();
			if (!all) {
				for (LocalDate d : draft.meals().keySet()) {
					for (int i = 0; i < draft.meals().get(d).size(); i++) {
						kept.add(RangePlanner.key(d, i));
					}
				}
			}
			drawResults();
		});
		int missing = facade.missingDraftMeals(draft);
		Button regen = button(
				missing > 0 && all ? "Fill empty meals" : kept.isEmpty() ? "Regenerate all" : "Regenerate the rest",
				"mp-outline", () -> {
					var keptNow = keptMeals();
					int before = draft.mealCount();
					facade.regenerateDraft(draft, kept);
					afterDraftEdit(keptNow);
					int added = draft.mealCount() - before;
					toast = (kept.isEmpty() ? "Made a fresh set of meals."
							: "Kept " + kept.size() + " and replaced the rest.")
							+ (added > 0 ? " Filled " + added + " empty meal" + (added == 1 ? "" : "s") + "." : "");
					drawResults();
				});
		regen.setDisable(all && missing == 0);
		keepAll.setMinWidth(Region.USE_PREF_SIZE);
		regen.setMinWidth(Region.USE_PREF_SIZE);
		VBox pt = new VBox(2, prev, help);
		HBox.setHgrow(pt, Priority.ALWAYS);
		pt.setMinWidth(0);
		Button dup = button(duplicateMode ? "\u29C9 Duplicate: on" : "\u29C9 Duplicate",
				duplicateMode ? "mp-primary" : "mp-outline", () -> {
					duplicateMode = !duplicateMode;
					toast = duplicateMode ? "Duplicate is on: dragging a meal now copies it."
							: "Duplicate is off: dragging moves meals again.";
					drawResults();
				});
		Tooltip(dup,
				"When on, dragging a meal makes a copy (so a recipe can repeat in the week). Holding Ctrl while dragging does the same.");
		dup.setMinWidth(Region.USE_PREF_SIZE);
		HBox bar = new HBox(10, pt, dup, keepAll, regen);
		bar.setAlignment(Pos.CENTER_LEFT);
		Label dragHint = label(
				"Drag meals to shuffle them: drop on another meal to swap, on its top or bottom edge to slot in, or on a day to move it there. Turn on Duplicate (or hold Ctrl) to copy a meal instead, so a recipe can repeat; keep the copies and regenerate the rest for fresh ones. Nothing is saved until you apply.",
				"mp-hint");
		dragHint.setWrapText(true);
		dragHint.setMinHeight(Region.USE_PREF_SIZE);
		card.getChildren().addAll(bar, dragHint, previewGrid());

		Button apply = button("Apply to calendar", "mp-primary", this::apply);
		String sig = listSignature();
		boolean onList = sig.equals(listAddedFor);
		Button list;
		if (onList) {
			list = button("\u2713  On your shopping list", "mp-done", () -> {
			});
			list.setDisable(true);
		} else if (draft.toBuy() == 0) {
			list = button("\u2713  Nothing to buy", "mp-done", () -> {
			});
			list.setDisable(true);
		} else {
			list = button("Add " + draft.toBuy() + " items to shopping list", "mp-outline", () -> {
				int n = facade.addDraftToShoppingList(draft);
				listAddedFor = sig;
				toast = n == 0 ? "Those are already on your shopping list."
						: n + (n == 1 ? " ingredient" : " ingredients") + " added to your shopping list.";
				onDataChanged.run();
				drawResults();
			});
		}
		HBox actions = new HBox(10, apply, list);
		card.getChildren().add(actions);
		return card;
	}

	/**
	 * Which recipes the draft uses now; the list button locks once these have been
	 * added.
	 */
	private String listAddedFor = "";

	private String listSignature() {
		List<Long> ids = new ArrayList<>();
		for (var r : draft.distinctRecipes()) {
			ids.add(r.getId());
		}
		java.util.Collections.sort(ids);
		return ids.toString();
	}

	private Node tile(String big, String small) {
		VBox t = new VBox(2, label(big, "mp-kcal"), label(small, "mp-meal-meta"));
		t.getStyleClass().add("mp-tile");
		HBox.setHgrow(t, Priority.ALWAYS);
		return t;
	}

	private Node previewGrid() {
		VBox weeks = new VBox(12);
		LocalDate first = CalendarService.weekStart(draft.options().start());
		LocalDate last = draft.options().end();
		for (LocalDate monday = first; !monday.isAfter(last); monday = monday.plusWeeks(1)) {
			GridPane grid = new GridPane();
			grid.setMinWidth(0);
			grid.setHgap(6);
			grid.setVgap(8);
			for (int i = 0; i < 7; i++) {
				javafx.scene.layout.ColumnConstraints c = new javafx.scene.layout.ColumnConstraints();
				c.setPercentWidth(100.0 / 7);
				c.setHgrow(Priority.ALWAYS);
				c.setMinWidth(0);
				grid.getColumnConstraints().add(c);
				LocalDate d = monday.plusDays(i);
				VBox column = new VBox(4,
						label(DayOfWeek.SUNDAY.plus(i).getDisplayName(TextStyle.SHORT, Locale.ENGLISH), "mp-col-name"),
						previewDay(d));
				column.setMinWidth(0);
				grid.add(column, i, 0);
			}
			weeks.getChildren().add(grid);
		}
		return weeks;
	}

	private Node previewDay(LocalDate d) {
		VBox col = new VBox(6);
		col.setPadding(new Insets(4));
		col.setMinWidth(0);
		List<PlannedMeal> meals = draft.meals().get(d);
		boolean outside = meals == null;
		col.getStyleClass().add(outside ? "mp-cell-out" : "mp-cell");
		col.getChildren().add(label(monthDay(d), outside ? "mp-meal-meta" : "mp-col-date"));
		if (outside) {
			if (!d.isBefore(draft.options().start()) && !d.isAfter(draft.options().end()) && d.isBefore(today())) {
				col.getChildren().add(label("Past", "mp-meal-meta"));
			}
			return col;
		}
		for (int i = 0; i < meals.size(); i++) {
			col.getChildren().add(previewMeal(d, i, meals.get(i)));
		}
		col.setMinHeight(90);
		col.setOnDragOver(e -> {
			if (dragging != null) {
				e.acceptTransferModes(
						duplicateMode ? new TransferMode[] { TransferMode.COPY } : TransferMode.COPY_OR_MOVE);
			}
			e.consume();
		});
		col.setOnDragEntered(e -> {
			if (dragging != null && !col.getStyleClass().contains("mp-col-drop")) {
				col.getStyleClass().add("mp-col-drop");
			}
		});
		col.setOnDragExited(e -> col.getStyleClass().remove("mp-col-drop"));
		col.setOnDragDropped(e -> {
			if (dragging != null) {
				Ref from = dragging;
				dragging = null;
				boolean copy = duplicateMode || e.getTransferMode() == TransferMode.COPY;
				e.setDropCompleted(true);
				moveDraftMeal(from, d, -1, copy);
			}
			e.consume();
		});
		return col;
	}

	// ---- drag and drop inside the draft (nothing reaches the calendar until
	// Apply)

	private Ref dragging;
	/** When on, dragging a meal copies it (Ctrl while dragging does the same). */
	private boolean duplicateMode;

	private static String zone(DragEvent e, Node card) {
		double f = e.getY() / Math.max(1, card.getBoundsInLocal().getHeight());
		return f < 0.3 ? "before" : f > 0.7 ? "after" : "swap";
	}

	private static void clearHint(Node card) {
		card.getStyleClass().removeAll("mp-drop-before", "mp-drop-after", "mp-drop-swap");
	}

	/**
	 * The kept flags follow the meals: remember which meal objects are kept, then
	 * rebuild the keys.
	 */
	private void afterDraftEdit(java.util.IdentityHashMap<PlannedMeal, Boolean> keptMeals) {
		kept.clear();
		for (LocalDate day : draft.meals().keySet()) {
			List<PlannedMeal> list = draft.meals().get(day);
			for (int i = 0; i < list.size(); i++) {
				if (keptMeals.containsKey(list.get(i))) {
					kept.add(RangePlanner.key(day, i));
				}
			}
		}
	}

	private java.util.IdentityHashMap<PlannedMeal, Boolean> keptMeals() {
		java.util.IdentityHashMap<PlannedMeal, Boolean> out = new java.util.IdentityHashMap<>();
		for (LocalDate day : draft.meals().keySet()) {
			List<PlannedMeal> list = draft.meals().get(day);
			for (int i = 0; i < list.size(); i++) {
				if (kept.contains(RangePlanner.key(day, i))) {
					out.put(list.get(i), Boolean.TRUE);
				}
			}
		}
		return out;
	}

	private void moveDraftMeal(Ref from, LocalDate to, int index, boolean copy) {
		try {
			PlannedMeal m = draft.meals().get(from.date()).get(from.index());
			var keptNow = keptMeals();
			if (copy) {
				PlannedMeal made = facade.copyDraftMeal(draft, from.date(), from.index(), to, index, false);
				if (keptNow.containsKey(m)) {
					keptNow.put(made, Boolean.TRUE); // a copy of a kept meal is kept too
				}
				afterDraftEdit(keptNow);
				toast = "Copied " + m.recipe().getTitle() + " to " + shortDay(to) + ", " + monthDay(to) + ".";
				drawResults();
				return;
			}
			facade.moveDraftMeal(draft, from.date(), from.index(), to, index);
			afterDraftEdit(keptNow);
			toast = "Moved " + m.recipe().getTitle() + " to " + shortDay(to) + ", " + monthDay(to)
					+ " \u2014 it keeps its "
					+ (m.tag().isEmpty() ? "no-tag setting" : m.tag().toLowerCase(Locale.ROOT) + " tag") + ".";
		} catch (IllegalArgumentException | IndexOutOfBoundsException ex) {
			toast = ex.getMessage() == null ? "That meal is gone." : ex.getMessage();
		}
		drawResults();
	}

	private void dropOnMeal(Ref from, LocalDate date, int index, String zone, boolean copy) {
		if (from.date().equals(date) && from.index() == index && !copy) {
			drawResults();
			return;
		}
		if (!zone.equals("swap")) {
			moveDraftMeal(from, date, zone.equals("before") ? index : index + 1, copy);
			return;
		}
		if (copy) {
			// dropping a copy on top of a meal puts the recipe in that meal's place
			try {
				PlannedMeal a = draft.meals().get(from.date()).get(from.index());
				var keptNow = keptMeals();
				PlannedMeal made = facade.copyDraftMeal(draft, from.date(), from.index(), date, index, true);
				if (keptNow.containsKey(a)) {
					keptNow.put(made, Boolean.TRUE);
				}
				afterDraftEdit(keptNow);
				toast = "Copied " + a.recipe().getTitle() + " onto " + shortDay(date) + ", " + monthDay(date) + ".";
			} catch (IllegalArgumentException | IndexOutOfBoundsException ex) {
				toast = ex.getMessage() == null ? "That meal is gone." : ex.getMessage();
			}
			drawResults();
			return;
		}
		try {
			PlannedMeal a = draft.meals().get(from.date()).get(from.index());
			PlannedMeal b = draft.meals().get(date).get(index);
			boolean aKept = kept.contains(RangePlanner.key(from.date(), from.index()));
			boolean bKept = kept.contains(RangePlanner.key(date, index));
			facade.swapDraftMeals(draft, from.date(), from.index(), date, index);
			kept.remove(RangePlanner.key(from.date(), from.index()));
			kept.remove(RangePlanner.key(date, index));
			if (bKept) {
				kept.add(RangePlanner.key(from.date(), from.index()));
			}
			if (aKept) {
				kept.add(RangePlanner.key(date, index));
			}
			toast = "Swapped " + a.recipe().getTitle() + " (" + shortDay(from.date()) + ") with "
					+ b.recipe().getTitle() + " (" + shortDay(date) + ").";
		} catch (IllegalArgumentException | IndexOutOfBoundsException ex) {
			toast = ex.getMessage() == null ? "That meal is gone." : ex.getMessage();
		}
		drawResults();
	}

	private String kcalOf(PlannedMeal m) {
		RecipeNutrition n = facade.estimateNutrition(m.recipe());
		return n.counted().isEmpty() || n.perServing().calories() <= 0 ? ""
				: Math.round(n.perServing().calories()) + " kcal";
	}

	private Node previewMeal(LocalDate d, int i, PlannedMeal m) {
		String key = RangePlanner.key(d, i);
		boolean isKept = kept.contains(key);
		Label tag = label(m.tag().toUpperCase(Locale.ROOT), "mp-pv-tag");
		tag.setMinWidth(Region.USE_PREF_SIZE);
		Label name = label(m.recipe().getTitle(), "mp-pv-name");
		name.setWrapText(true);
		name.setMinHeight(Region.USE_PREF_SIZE);
		Label kcal = label(kcalOf(m), "mp-pv-kcal");
		VBox text = new VBox(2, tag, name, kcal);
		text.setStyle("-fx-cursor: hand;");
		text.setOnMouseClicked(e -> preview(d, i));
		Button lock = button(isKept ? "●" : "○", isKept ? "mp-mini-on" : "mp-mini", () -> {
			if (!kept.remove(key)) {
				kept.add(key);
			}
			drawResults();
		});
		Tooltip(lock, isKept ? "Kept — click to unlock" : "Keep this meal");
		Button roll = button("↻", "mp-mini", () -> {
			facade.rerollDraftMeal(draft, d, i);
			drawResults();
		});
		Tooltip(roll, "Regenerate this meal");
		roll.setDisable(isKept);
		Button remove = button("\u2715", "mp-mini", () -> {
			var keptNow = keptMeals();
			facade.removeDraftMeal(draft, d, i);
			afterDraftEdit(keptNow);
			toast = "Removed " + m.recipe().getTitle() + ". Press Regenerate to fill the empty meal with a new recipe.";
			drawResults();
		});
		Tooltip(remove, "Remove this meal from the plan");
		HBox tools = new HBox(4, lock, roll, remove);
		text.setMinWidth(0);
		tools.setMinWidth(0);
		name.setMinWidth(0);
		kcal.setMinWidth(0);
		VBox card = new VBox(4, text, tools);
		card.setMinWidth(0);
		card.getStyleClass().add(isKept ? "mp-pv-card-kept" : "mp-pv-card");
		card.setOnDragDetected(e -> {
			Dragboard db = card.startDragAndDrop(TransferMode.COPY_OR_MOVE);
			ClipboardContent c = new ClipboardContent();
			c.putString(m.recipe().getTitle());
			db.setContent(c);
			dragging = new Ref(d, i);
			card.setOpacity(0.5);
			e.consume();
		});
		card.setOnDragDone(e -> {
			dragging = null;
			card.setOpacity(1);
		});
		card.setOnDragOver(e -> {
			if (dragging != null) {
				e.acceptTransferModes(
						duplicateMode ? new TransferMode[] { TransferMode.COPY } : TransferMode.COPY_OR_MOVE);
				clearHint(card);
				card.getStyleClass().add("mp-drop-" + zone(e, card));
			}
			e.consume();
		});
		card.setOnDragExited(e -> clearHint(card));
		card.setOnDragDropped(e -> {
			if (dragging != null) {
				Ref from = dragging;
				String z = zone(e, card);
				boolean copy = duplicateMode || e.getTransferMode() == TransferMode.COPY;
				dragging = null;
				clearHint(card);
				e.setDropCompleted(true);
				dropOnMeal(from, d, i, z, copy);
			}
			e.consume();
		});
		return card;
	}

	private static void Tooltip(Button b, String text) {
		b.setTooltip(new javafx.scene.control.Tooltip(text));
	}

	private void apply() {
		try {
			int n = facade.applyDraft(draft);
			onDataChanged.run();
			onApplied.accept("Added " + n + " meals from " + monthDay(draft.options().start()) + " to "
					+ monthDay(draft.options().end()) + ". Undo takes them back.");
		} catch (IllegalArgumentException e) {
			toast = e.getMessage();
			drawResults();
		}
	}

	// ------------------------------------------------------------------ one meal
	// (side panel)

	private record Ref(LocalDate date, int index) {
	}

	private final StackPane overlay = new StackPane();
	private Ref viewing;

	private List<Ref> everyMeal() {
		List<Ref> out = new ArrayList<>();
		for (LocalDate d : draft.meals().keySet()) {
			for (int k = 0; k < draft.meals().get(d).size(); k++) {
				out.add(new Ref(d, k));
			}
		}
		return out;
	}

	private void preview(LocalDate d, int i) {
		viewing = new Ref(d, i);
		renderPreview();
	}

	private void closePreview() {
		viewing = null;
		overlay.setVisible(false);
		overlay.getChildren().clear();
		drawResults();
	}

	private Button navButton(String text, boolean enabled, Runnable action) {
		Button b = button(text, "mp-rv-nav", action);
		b.setDisable(!enabled);
		return b;
	}

	private void renderPreview() {
		overlay.getChildren().clear();
		if (draft == null || viewing == null) {
			overlay.setVisible(false);
			return;
		}
		List<Ref> all = everyMeal();
		int pos = all.indexOf(viewing);
		List<PlannedMeal> day = draft.meals().get(viewing.date());
		if (pos < 0 || day == null || viewing.index() >= day.size()) {
			closePreview();
			return;
		}
		PlannedMeal m = day.get(viewing.index());
		Recipe r = m.recipe();
		String key = RangePlanner.key(viewing.date(), viewing.index());
		boolean isKept = kept.contains(key);

		Region scrim = new Region();
		scrim.setStyle("-fx-background-color: rgba(31,42,34,0.25);");
		scrim.setOnMouseClicked(e -> closePreview());

		Label when = label(shortDay(viewing.date()) + ", " + monthDay(viewing.date()) + " \u00B7 " + m.tag()
				+ " \u00B7 " + (pos + 1) + " of " + all.size(), "mp-meal-meta");
		when.setAlignment(Pos.CENTER);
		when.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(when, Priority.ALWAYS);
		HBox head = new HBox(8, navButton("\u2039", pos > 0, () -> {
			viewing = all.get(pos - 1);
			renderPreview();
		}), when, navButton("\u203A", pos < all.size() - 1, () -> {
			viewing = all.get(pos + 1);
			renderPreview();
		}), button("\u00D7", "mp-rv-close", this::closePreview));
		head.setAlignment(Pos.CENTER);
		head.getStyleClass().add("mp-rv-head");

		VBox body = new VBox(16);
		body.setPadding(new Insets(18));
		body.getChildren().add(previewPhoto(r));

		Label title = label(r.getTitle(), "mp-rv-title");
		title.setWrapText(true);
		title.setMinHeight(Region.USE_PREF_SIZE);
		FlowPane meta = new FlowPane(14, 4);
		if (r.getTotalMinutes() > 0) {
			meta.getChildren().add(label(r.getTotalMinutes() + " min", "mp-meal-meta"));
		}
		meta.getChildren().add(label(m.servings() + (m.servings() == 1 ? " serving" : " servings"), "mp-meal-meta"));
		if (r.getSourceName() != null && !r.getSourceName().isBlank()) {
			meta.getChildren().add(label(r.getSourceName(), "mp-meal-meta"));
		}
		VBox titleBox = new VBox(6, title, meta);
		if (isKept) {
			titleBox.getChildren().add(label("\u25CF Kept in your plan", "mp-rv-kept"));
		}
		body.getChildren().add(titleBox);

		Button keep = button(isKept ? "Kept in your plan \u00B7 unlock" : "Keep in plan",
				isKept ? "mp-primary" : "mp-outline", () -> {
					if (!kept.remove(key)) {
						kept.add(key);
					}
					drawResults();
					renderPreview();
				});
		Button other = button("\u21BB  Show me something else", "mp-outline", () -> {
			facade.rerollDraftMeal(draft, viewing.date(), viewing.index());
			drawResults();
			renderPreview();
		});
		other.setDisable(isKept);
		FlowPane actions = new FlowPane(8, 8, keep, other);
		if (r.isLibrary()) {
			// save the meal to My Recipes (the heart on the Recipes page)
			boolean saved = facade.getMyRecipes().stream().anyMatch(x -> x.getId() == r.getId());
			Button save = button(saved ? "\u2665  Saved to My Recipes" : "\u2661  Save recipe",
					saved ? "mp-primary" : "mp-outline", () -> {
						if (facade.getMyRecipes().stream().anyMatch(x -> x.getId() == r.getId())) {
							facade.removeFromMyRecipes(r.getId());
						} else {
							facade.saveToMyRecipes(r.getId());
						}
						renderPreview();
					});
			actions.getChildren().add(save);
		}
		if (r.getSourceUrl() != null && !r.getSourceUrl().isBlank()) {
			actions.getChildren().add(button("View original \u2197", "mp-link", () -> openLink(r.getSourceUrl())));
		}
		body.getChildren().add(actions);

		body.getChildren().add(nutritionCard(r));

		RecipeMatch match = facade.checkRecipe(r.getId());
		Set<String> have = new HashSet<>();
		for (RecipeIngredient a : match.available()) {
			have.add(a.name());
		}
		int total = r.getIngredients().size();
		Label why = label(
				"Mealton picked this because you already have " + have.size() + " of its " + total + " ingredients"
						+ (draft.options().pantryFirst() ? " and it uses what\u2019s in your pantry first" : "")
						+ ". It fits the " + facade.getProfile().calorieGoal() + " kcal daily target.",
				"mp-rv-why");
		why.setWrapText(true);
		why.setMinHeight(Region.USE_PREF_SIZE);
		body.getChildren().add(why);

		VBox ings = new VBox(0);
		ings.getChildren().add(rvHeading("Ingredients", "you have " + have.size() + " of " + total));
		for (RecipeIngredient ing : r.getIngredients()) {
			boolean has = have.contains(ing.name());
			Region dot = new Region();
			dot.setMinSize(10, 10);
			dot.setPrefSize(10, 10);
			dot.setMaxSize(10, 10);
			dot.getStyleClass().add(has ? "mp-rv-dot-have" : "mp-rv-dot-need");
			Label text = label(com.mealton.util.Formats.quantityWithUnit(ing.quantity(), ing.unit()) + " " + ing.name(),
					"mp-rv-ing");
			text.setWrapText(true);
			HBox.setHgrow(text, Priority.ALWAYS);
			text.setMaxWidth(Double.MAX_VALUE);
			Label tag = label(has ? "Have it" : "Need to buy", has ? "mp-status-ok" : "mp-status-short");
			HBox row = new HBox(10, dot, text, tag);
			row.setAlignment(Pos.CENTER_LEFT);
			row.getStyleClass().add("mp-rv-row");
			ings.getChildren().add(row);
		}
		body.getChildren().add(ings);

		if (!r.getSteps().isEmpty()) {
			VBox steps = new VBox(10);
			steps.getChildren().add(rvHeading("Method", ""));
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
			body.getChildren().add(steps);
		}

		ScrollPane sp = new ScrollPane(body);
		sp.setFitToWidth(true);
		sp.getStyleClass().add("mp-scroll");
		VBox.setVgrow(sp, Priority.ALWAYS);
		VBox panel = new VBox(head, sp);
		panel.getStyleClass().add("mp-rv-panel");
		panel.setMinWidth(460);
		panel.setPrefWidth(460);
		panel.setMaxWidth(460);
		StackPane.setAlignment(panel, Pos.CENTER_RIGHT);
		overlay.getChildren().addAll(scrim, panel);
		overlay.setVisible(true);
	}

	private Node rvHeading(String text, String small) {
		HBox h = new HBox(8, label(text, "mp-rv-h3"));
		if (!small.isEmpty()) {
			h.getChildren().add(label("\u00B7 " + small, "mp-meal-meta"));
		}
		h.setAlignment(Pos.BASELINE_LEFT);
		h.setPadding(new Insets(0, 0, 6, 0));
		return h;
	}

	private Node previewPhoto(Recipe r) {
		StackPane box = new StackPane();
		double w = 424;
		var file = facade.getRecipePhoto(r.getId());
		double h = file.isPresent() ? 210 : 120;
		box.setMinSize(w, h);
		box.setPrefSize(w, h);
		box.setMaxSize(w, h);
		box.getStyleClass().add("mp-photo");
		box.getChildren().add(MealPlanView.letterOf(r, h));
		javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(w, h);
		clip.setArcWidth(28);
		clip.setArcHeight(28);
		box.setClip(clip);
		if (file.isPresent()) {
			javafx.scene.image.ImageView v = PhotoFit.cover(file.get(), w, h);
			if (v != null) {
				box.getChildren().add(v);
			}
		}
		return box;
	}

	private Node nutritionCard(Recipe r) {
		RecipeNutrition n = facade.estimateNutrition(r);
		VBox card = new VBox(10);
		card.getStyleClass().add("mp-rv-nut");
		boolean known = !n.counted().isEmpty() && n.perServing().calories() > 0;
		Label kcal = label(known ? Math.round(n.perServing().calories()) + " kcal" : "\u2013", "mp-rv-kcal");
		Region g = new Region();
		HBox.setHgrow(g, Priority.ALWAYS);
		HBox top = new HBox(8, new HBox(6, label("Nutrition", "mp-rv-h3"), label("\u00B7 per serving", "mp-meal-meta")),
				g, kcal);
		top.setAlignment(Pos.BASELINE_LEFT);
		card.getChildren().add(top);
		if (!known) {
			card.getChildren()
					.add(label("Some ingredients have no nutrition data yet, so Mealton can\u2019t total this recipe.",
							"mp-meal-meta"));
			return card;
		}
		boolean track = facade.tracksMacros();
		UserProfile p = facade.getProfile();
		if (track && p.calorieGoal() > 0) {
			card.getChildren().add(label(Math.round(n.perServing().calories() * 100.0 / p.calorieGoal())
					+ "% of your daily " + p.calorieGoal() + " kcal", "mp-meal-meta"));
		}
		card.getChildren().add(macroRow("Protein", n.perServing().protein(), p.proteinGoal(), track));
		card.getChildren().add(macroRow("Carbs", n.perServing().carbs(), p.carbsGoal(), track));
		card.getChildren().add(macroRow("Fat", n.perServing().fat(), p.fatGoal(), track));
		if (!n.counted().isEmpty() && n.perServing().calories() > 0) {
			card.getChildren()
					.add(label("Estimated from the ingredients Mealton has nutrition data for.", "mp-meal-meta"));
		}
		return card;
	}

	private Node macroRow(String name, double grams, int goal, boolean track) {
		Label l = label(name, "mp-rv-ing");
		l.setMinWidth(70);
		Label v = label(Math.round(grams) + " g", "mp-rv-val");
		v.setMinWidth(54);
		v.setAlignment(Pos.CENTER_RIGHT);
		Region g = new Region();
		HBox.setHgrow(g, Priority.ALWAYS);
		HBox row = new HBox(10, l, g, v);
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

	private void openLink(String url) {
		try {
			java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
		} catch (Exception e) {
			toast = "Couldn\u2019t open the link: " + url;
			drawResults();
		}
	}
}