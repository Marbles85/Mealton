package com.mealton.gui;

import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.mealton.diary.MealLogService;
import com.mealton.diary.PortionNotes;
import com.mealton.facade.MealtonFacade;
import com.mealton.nutrition.DailyProgress;
import com.mealton.nutrition.LoggedFood;
import com.mealton.nutrition.Nutrients;
import com.mealton.pantry.PantryItem;
import com.mealton.plan.MealEntry;
import com.mealton.profile.UserProfile;
import com.mealton.recipe.Recipe;
import com.mealton.util.Formats;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;

/**
 * The Log food page (features F04, F06 and F11 with the AI): tell Mealton what
 * you cooked or ate. Your portion counts toward today's macros, what you used
 * is taken out of the pantry, leftovers can be saved, things that are now
 * running low can go on the shopping list, and everything can be undone. The
 * History tab shows what you logged, day by day.
 * <p>
 * When "Track your macros" is off (Profile page) every macro figure is hidden
 * and meals still update the pantry and the log.
 */
public class FoodDiaryView extends VBox {
	private enum Tab {
		LOG, HISTORY
	}

	private enum Source {
		RECIPE, PANTRY
	}

	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "mealton-log-food");
		t.setDaemon(true);
		return t;
	});
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH);

	private final MealtonFacade facade;

	// ---- what is chosen ----
	private Tab tab = Tab.LOG;
	private Source source = Source.RECIPE;
	private Recipe recipe;
	private int batch = 1;
	private double portion = 1;
	private String meal = "Dinner";
	private boolean saveLeftovers = true;
	private final Map<Integer, Double> overrides = new HashMap<>();
	private List<String> understood;
	private boolean noMatch;
	private PantryItem item;
	private double amount = 1;
	private Nutrients macroOverride;
	private boolean macroEditing;
	private boolean aiAck;
	private MealtonFacade.PantryMacros pantryPreview;
	private String pantryPreviewError = "";
	private String previewKey = "";
	private boolean working; // an AI or log job is running
	private String problem = "";
	private MealLogService.Result lastResult;
	private String lastTitle = "";
	private String lastPantry = "";
	private String listAdded = "";
	private boolean fromRecipeNotice;
	private boolean fromPantryNotice;
	private Runnable onViewShoppingList;

	// ---- history ----
	private String histRange = "7";
	private LocalDate histDate; // the day shown in the Day view, or any day of the week shown in the Week view
	private Node slideTarget; // the part of the page that slides when you change day
	private LocalDate logDate; // the day Log a meal logs to (set from History), or null for today
	private int slideDir; // -1 = came from the left, 1 = came from the right, 0 = no animation
	private LoggedFood removed;
	private String toast = "";

	// ---- controls that keep their text while the page redraws ----
	private final TextField recipeSearch = new TextField();
	private final TextField pantrySearch = new TextField();
	private final TextArea notesArea = new TextArea();
	private final TextField histSearch = new TextField();
	private final ComboBox<String> mealBox = new ComboBox<>();

	// ---- containers ----
	private final Label intro = new Label();
	private final HBox pageTabs = new HBox(4);
	private final VBox logTab = new VBox(16);
	private final VBox historyTab = new VBox(16);
	private final VBox bannerBox = new VBox();
	private final VBox sourceCard = new VBox(14);
	private final VBox choicesBox = new VBox(10);
	private final VBox amountCard = new VBox(14);
	private final VBox notesCard = new VBox(12);
	private final VBox sideBox = new VBox(16);
	private final VBox histBody = new VBox(16);
	private final HBox histPills = new HBox(8);

	public FoodDiaryView(MealtonFacade facade) {
		this.facade = facade;
		var css = FoodDiaryView.class.getResource("/logfood.css");
		if (css != null) {
			getStylesheets().add(css.toExternalForm());
		}
		getStyleClass().add("lf-page");

		Label title = new Label("Log food");
		title.getStyleClass().add("lf-title");
		intro.getStyleClass().add("lf-sub");
		intro.setWrapText(true);
		VBox head = new VBox(2, title, intro);
		HBox.setHgrow(head, Priority.ALWAYS);
		pageTabs.getStyleClass().add("lf-seg-bar");
		pageTabs.setMaxHeight(Region.USE_PREF_SIZE);
		HBox top = new HBox(16, head, pageTabs);
		top.setAlignment(Pos.BOTTOM_LEFT);

		recipeSearch.setPromptText("Search all recipes, including yours");
		recipeSearch.getStyleClass().add("lf-input");
		recipeSearch.textProperty().addListener((o, a, b) -> drawChoices());
		pantrySearch.setPromptText("Search your pantry, e.g. orange");
		pantrySearch.getStyleClass().add("lf-input");
		pantrySearch.textProperty().addListener((o, a, b) -> drawChoices());
		histSearch.setPromptText("Search meals, e.g. oats");
		histSearch.getStyleClass().add("lf-input");
		histSearch.setPrefWidth(260);
		histSearch.textProperty().addListener((o, a, b) -> drawHistory());
		mealBox.getStyleClass().add("lf-combo");
		refreshTags();
		mealBox.setOnAction(e -> {
			String picked = mealBox.getValue();
			if (tagsLoading || picked == null) {
				return;
			}
			if (picked.equals(NEW_TAG)) {
				Optional<String> name = LogFeedback.askNewTag(this, facade);
				if (name.isPresent()) {
					meal = name.get();
				}
				refreshTags();
				drawSide();
				return;
			}
			meal = picked;
		});
		notesArea.setPromptText("e.g. I only used one egg, no feta, half the spinach");
		notesArea.setPrefRowCount(2);
		notesArea.setWrapText(true);
		notesArea.getStyleClass().add("lf-area");

		buildLogTab();
		HBox histControls = new HBox(12, histPills, grow(), histSearch);
		histControls.setAlignment(Pos.CENTER_LEFT);
		historyTab.getChildren().addAll(histControls, histBody);

		StackPane body = new StackPane(logTab, historyTab);
		VBox page = new VBox(20, top, body);
		page.setPadding(new Insets(32, 40, 60, 40));
		ScrollPane scroll = new ScrollPane(page);
		scroll.setFitToWidth(true);
		scroll.getStyleClass().add("lf-scroll");
		VBox.setVgrow(scroll, Priority.ALWAYS);
		getChildren().add(scroll);

		refresh();
	}

	// ====================================================================
	// public API
	// ====================================================================

	/** Reloads everything (call when the page is shown or data changed). */
	private static final String NEW_TAG = "+ New tag\u2026";
	private boolean tagsLoading;

	/**
	 * Fills the meal drop-down from the user's tags (the same ones as the plan and
	 * calendar).
	 */
	private void refreshTags() {
		tagsLoading = true;
		try {
			List<String> tags = new ArrayList<>(facade.getMealTags());
			if (meal != null && !meal.isBlank() && !tags.contains(meal)) {
				meal = tags.isEmpty() ? "" : tags.get(tags.size() > 2 ? 2 : 0);
			}
			tags.add(NEW_TAG);
			mealBox.getItems().setAll(tags);
			mealBox.setValue(meal);
		} finally {
			tagsLoading = false;
		}
	}

	public void refresh() {
		refreshTags();
		if (recipe != null) {
			try {
				recipe = facade.getRecipe(recipe.getId());
			} catch (RuntimeException e) {
				recipe = null;
			}
		}
		if (item != null && facade.getPantryItems().stream().noneMatch(p -> p.getId() == item.getId())) {
			item = null;
			pantryPreview = null;
		}
		drawAll();
	}

	/**
	 * What the "View shopping list" link in the banner does (the app opens the
	 * Pantry page).
	 */
	public void setOnViewShoppingList(Runnable action) {
		this.onViewShoppingList = action;
	}

	/** Opens the page with this recipe chosen (for "I made this" buttons). */
	public void selectRecipe(long recipeId) {
		tab = Tab.LOG;
		source = Source.RECIPE;
		chooseRecipe(facade.getRecipe(recipeId));
		fromRecipeNotice = true;
		drawAll();
	}

	/** Opens the page with this pantry item chosen (for "Log it" buttons). */
	public void selectPantryItem(long itemId) {
		tab = Tab.LOG;
		source = Source.PANTRY;
		for (PantryItem p : facade.getPantryItems()) {
			if (p.getId() == itemId) {
				chooseItem(p);
				fromPantryNotice = true;
				drawAll();
				return;
			}
		}
	}

	// ====================================================================
	// building blocks
	// ====================================================================

	private void buildLogTab() {
		VBox left = new VBox(16, bannerBox, sourceCard, amountCard, notesCard);
		HBox.setHgrow(left, Priority.ALWAYS);
		left.setMaxWidth(Double.MAX_VALUE);
		sideBox.setPrefWidth(360);
		sideBox.setMinWidth(320);
		sideBox.setMaxWidth(380);
		for (VBox card : new VBox[] { sourceCard, amountCard, notesCard }) {
			card.getStyleClass().add("lf-card");
		}
		HBox columns = new HBox(24, left, sideBox);
		columns.setAlignment(Pos.TOP_LEFT);
		logTab.getChildren().add(columns);
	}

	private static Label label(String text, String styleClass) {
		Label l = new Label(text);
		l.getStyleClass().add(styleClass);
		l.setWrapText(true);
		l.setMinHeight(Region.USE_PREF_SIZE);
		return l;
	}

	private static Button pill(String text, boolean on, Runnable action) {
		Button b = new Button(text);
		b.getStyleClass().addAll("lf-pill");
		if (on) {
			b.getStyleClass().add("lf-pill-on");
		}
		b.setOnAction(e -> action.run());
		return b;
	}

	private static Button seg(String text, boolean on, Runnable action) {
		Button b = new Button(text);
		b.getStyleClass().add("lf-seg");
		if (on) {
			b.getStyleClass().add("lf-seg-on");
		}
		b.setOnAction(e -> action.run());
		return b;
	}

	private static Button stepButton(String text, Runnable action) {
		Button b = new Button(text);
		b.getStyleClass().add("lf-step");
		b.setOnAction(e -> action.run());
		return b;
	}

	private static Region grow() {
		Region r = new Region();
		HBox.setHgrow(r, Priority.ALWAYS);
		return r;
	}

	/**
	 * True when logging to a day that has already passed: the pantry is not
	 * changed.
	 */
	private boolean pastLog() {
		return logDate != null && logDate.isBefore(facade.today());
	}

	private boolean tracking() {
		return facade.tracksMacros();
	}

	private static String fmt(double n) {
		return String.format(Locale.US, "%,d", Math.round(n));
	}

	private static String portionLabel(double n) {
		long whole = (long) Math.floor(n + 1e-9);
		double frac = n - whole;
		String f = Math.abs(frac - 0.5) < 1e-9 ? "\u00BD"
				: Math.abs(frac - 0.25) < 1e-9 ? "\u00BC" : Math.abs(frac - 0.75) < 1e-9 ? "\u00BE" : "";
		return whole == 0 && !f.isEmpty() ? f : whole + f;
	}

	private static String servingsText(double n) {
		String num = n == 0.5 ? "½" : n % 1 == 0.5 ? (long) Math.floor(n) + "½" : Formats.number(n);
		return num + (Math.abs(n - 1) < 1e-9 ? " serving" : " servings");
	}

	// ====================================================================
	// drawing everything
	// ====================================================================

	private void drawAll() {
		intro.setText(tab == Tab.HISTORY ? "Everything you’ve logged, day by day."
				: tracking()
						? "Tell Mealton what you cooked and ate. It counts your portion toward today’s macros "
								+ "and takes what you used out of your pantry."
						: "Tell Mealton what you cooked and ate. It takes what you used out of your pantry and adds "
								+ "it to your food log.");
		pageTabs.getChildren().setAll(seg("Log a meal", tab == Tab.LOG, () -> {
			if (tab != Tab.LOG) {
				logDate = null;
			}
			tab = Tab.LOG;
			toast = "";
			drawAll();
		}), seg("History", tab == Tab.HISTORY, () -> {
			tab = Tab.HISTORY;
			drawAll();
		}));
		logTab.setVisible(tab == Tab.LOG);
		logTab.setManaged(tab == Tab.LOG);
		historyTab.setVisible(tab == Tab.HISTORY);
		historyTab.setManaged(tab == Tab.HISTORY);
		if (tab == Tab.HISTORY) {
			drawHistory();
			return;
		}
		drawBanner();
		drawSource();
		drawAmount();
		drawNotes();
		drawSide();
	}

	// ---------------------------------------------------------------- banner

	private void drawBanner() {
		bannerBox.getChildren().clear();
		if (lastResult == null) {
			return;
		}
		Label t = label(lastTitle, "lf-banner-title");
		VBox text = new VBox(3, t);
		if (!lastPantry.isEmpty()) {
			text.getChildren().add(label(lastPantry, "lf-text"));
		}
		if (!listAdded.isEmpty()) {
			text.getChildren().add(label(listAdded, "lf-text"));
		}
		HBox.setHgrow(text, Priority.ALWAYS);
		Button undo = new Button("Undo");
		undo.getStyleClass().add("lf-link");
		undo.setOnAction(e -> {
			facade.undoMealLog(lastResult.undo());
			lastResult = null;
			drawAll();
		});
		HBox row = new HBox(12, text, undo);
		row.setAlignment(Pos.CENTER_LEFT);
		row.getStyleClass().add("lf-banner");
		bannerBox.getChildren().add(row);
	}

	// ---------------------------------------------------------------- 1. what

	private void drawSource() {
		Label h = label(source == Source.RECIPE ? "What did you make?" : "What did you eat?", "lf-h2");
		HBox tabs = new HBox(4, seg("A recipe", source == Source.RECIPE, () -> {
			source = Source.RECIPE;
			problem = "";
			drawAll();
		}), seg("From pantry", source == Source.PANTRY, () -> {
			source = Source.PANTRY;
			problem = "";
			drawAll();
		}));
		tabs.getStyleClass().add("lf-seg-bar");
		tabs.setMaxHeight(Region.USE_PREF_SIZE);
		HBox head = new HBox(12, h, grow(), tabs);
		head.setAlignment(Pos.CENTER_LEFT);

		HBox options = new HBox(16);
		options.setAlignment(Pos.CENTER_LEFT);
		if (source == Source.RECIPE) {
			Label made = label("You made", "lf-label");
			made.setWrapText(false);
			Label count = label(servingsText(batch), "lf-label");
			count.setWrapText(false);
			count.setMinWidth(86);
			count.setAlignment(Pos.CENTER);
			options.getChildren().addAll(made, stepButton("−", () -> setBatch(batch - 1)), count,
					stepButton("+", () -> setBatch(batch + 1)));
		}
		Label logAs = label("Log as", "lf-label");
		logAs.setWrapText(false);
		Label today = label(logDate == null ? "\u00B7 today" : "\u00B7 " + logDate.format(DAY), "lf-muted");
		today.setWrapText(false);
		today.setMinWidth(Region.USE_PREF_SIZE);
		logAs.setMinWidth(Region.USE_PREF_SIZE);
		for (Node n : options.getChildren()) {
			if (n instanceof Label l) {
				l.setMinWidth(Region.USE_PREF_SIZE);
			}
		}
		options.getChildren().addAll(grow(), logAs, mealBox, today);

		Label notice = label(
				source == Source.RECIPE ? "Picked from the recipe you were viewing." : "Picked from your pantry.",
				"lf-note");
		boolean showNotice = source == Source.RECIPE ? fromRecipeNotice && recipe != null
				: fromPantryNotice && item != null;
		sourceCard.getChildren().setAll(head);
		if (showNotice) {
			sourceCard.getChildren().add(notice);
		}
		sourceCard.getChildren().addAll(source == Source.RECIPE ? recipeSearch : pantrySearch, choicesBox, options);
		if (logDate != null) {
			boolean past = logDate.isBefore(facade.today());
			Label when = label("Logging for " + dayTitle(logDate, facade.today())
					+ (past ? ". It\u2019s a past day, so your pantry stays as it is."
							: ". Your pantry is updated like normal."),
					"lf-note");
			Button back = new Button("Back to today");
			back.getStyleClass().add("lf-link");
			back.setOnAction(e -> {
				logDate = null;
				drawAll();
			});
			HBox whenRow = new HBox(10, when, back);
			whenRow.setAlignment(Pos.CENTER_LEFT);
			HBox.setHgrow(when, Priority.ALWAYS);
			sourceCard.getChildren().add(whenRow);
		}
		drawChoices();
	}

	private void setBatch(int value) {
		if (recipe == null || value < 1 || value > 48) {
			return;
		}
		batch = value;
		if (portion > batch) {
			portion = batch;
		}
		overrides.clear();
		understood = null;
		noMatch = false;
		drawAll();
	}

	private void drawChoices() {
		choicesBox.getChildren().clear();
		if (source == Source.RECIPE) {
			String q = recipeSearch.getText() == null ? "" : recipeSearch.getText().trim().toLowerCase(Locale.ROOT);
			if (!q.isEmpty()) {
				Map<Recipe, String> found = new LinkedHashMap<>();
				for (Recipe r : facade.getRecipes()) {
					if (r.getTitle().toLowerCase(Locale.ROOT).contains(q) && found.size() < 12) {
						found.put(r, r.getServings() + (r.getServings() == 1 ? " serving" : " servings"));
					}
				}
				choicesBox.getChildren().add(label(found.isEmpty() ? "No recipes match \u201C" + q + "\u201D"
						: found.size() + (found.size() == 1 ? " match" : " matches"), "lf-muted"));
				if (!found.isEmpty()) {
					choicesBox.getChildren().add(recipeCards(found));
				}
				return;
			}
			choicesBox.getChildren().add(label("Planned today", "lf-label"));
			Map<Recipe, String> planned = new LinkedHashMap<>();
			for (MealEntry e : facade.getCalendarMeals(facade.today())) {
				final long id = e.recipe().getId();
				if (planned.keySet().stream().noneMatch(r -> r.getId() == id)) {
					planned.put(e.recipe(), e.slot().label() + " \u00B7 on today\u2019s plan");
				}
			}
			choicesBox.getChildren()
					.add(planned.isEmpty() ? label("Nothing planned for today.", "lf-muted") : recipeCards(planned));
			choicesBox.getChildren().add(label("Recent & saved", "lf-label"));
			Map<Recipe, String> recent = recentRecipes();
			choicesBox.getChildren()
					.add(recent.isEmpty() ? label(
							"Cook something, or save a recipe, and it shows up here. Search to find any recipe.",
							"lf-muted") : recipeCards(recent));
			return;
		}
		String q = pantrySearch.getText() == null ? "" : pantrySearch.getText().trim().toLowerCase(Locale.ROOT);
		Map<String, List<PantryItem>> groups = new TreeMap<>((a, b) -> {
			boolean la = a.equals(MealLogService.LEFTOVER_CATEGORY);
			boolean lb = b.equals(MealLogService.LEFTOVER_CATEGORY);
			return la == lb ? a.compareTo(b) : la ? -1 : 1;
		});
		for (PantryItem p : facade.getPantryItems()) {
			if (q.isEmpty() || p.getName().toLowerCase(Locale.ROOT).contains(q)) {
				groups.computeIfAbsent(p.getCategory(), k -> new ArrayList<>()).add(p);
			}
		}
		if (groups.isEmpty()) {
			choicesBox.getChildren().add(label(q.isEmpty() ? "Your pantry is empty. Add food on the Pantry page."
					: "Nothing in your pantry matches \u201C" + q + "\u201D.", "lf-muted"));
			return;
		}
		LocalDate today = facade.today();
		for (Map.Entry<String, List<PantryItem>> g : groups.entrySet()) {
			choicesBox.getChildren().add(
					label(g.getKey().equals(MealLogService.LEFTOVER_CATEGORY) ? "Recipes \u00B7 leftovers" : g.getKey(),
							"lf-label"));
			FlowPane flow = new FlowPane(10, 10);
			for (PantryItem p : g.getValue()) {
				int days = p.daysLeft(today);
				String meta = (p.getUnit().equalsIgnoreCase("servings") && round2(p.getQuantity()) == 1 ? "1 serving"
						: Formats.quantityWithUnit(round2(p.getQuantity()), p.getUnit()))
						+ " left"
						+ (days >= 100000 ? ""
								: days < 0 ? " \u00B7 expired"
										: days <= 3 ? " \u00B7 " + days + (days == 1 ? " day" : " days")
												: " \u00B7 good " + days + " days");
				VBox card = choiceCard(p.getName(), meta, item != null && item.getId() == p.getId());
				card.setOnMouseClicked(e -> chooseItem(p));
				flow.getChildren().add(card);
			}
			choicesBox.getChildren().add(flow);
		}
	}

	private Node recipeCards(Map<Recipe, String> list) {
		FlowPane flow = new FlowPane(10, 10);
		for (Map.Entry<Recipe, String> entry : list.entrySet()) {
			Recipe r = entry.getKey();
			VBox card = choiceCard(r.getTitle(), entry.getValue(), recipe != null && recipe.getId() == r.getId());
			card.getChildren().add(0, photo(r, 166, 84));
			card.setOnMouseClicked(e -> chooseRecipe(r));
			flow.getChildren().add(card);
		}
		return flow;
	}

	/**
	 * The recipe's photo cropped to fill the box, or its first letter on a green
	 * tile.
	 */
	private Node photo(Recipe r, double w, double h) {
		StackPane box = new StackPane();
		box.getStyleClass().add("lf-photo");
		box.setMinSize(w, h);
		box.setPrefSize(w, h);
		box.setMaxSize(w, h);
		Label letter = new Label(r.getTitle().isEmpty() ? "?" : r.getTitle().substring(0, 1).toUpperCase());
		letter.getStyleClass().add("lf-photo-letter");
		box.getChildren().add(letter);
		try {
			Optional<Path> file = facade.getRecipePhoto(r.getId());
			if (file.isPresent()) {
				Image img = new Image(file.get().toUri().toString(), 600, 0, true, true);
				if (!img.isError() && img.getWidth() > 0) {
					ImageView view = new ImageView(img);
					view.setPreserveRatio(false);
					view.setManaged(false);
					double scale = Math.max(w / img.getWidth(), h / img.getHeight());
					double vw = w / scale;
					double vh = h / scale;
					view.setViewport(new javafx.geometry.Rectangle2D((img.getWidth() - vw) / 2,
							(img.getHeight() - vh) / 2, vw, vh));
					view.setFitWidth(w);
					view.setFitHeight(h);
					view.relocate(0, 0);
					box.getChildren().setAll(view);
				}
			}
		} catch (RuntimeException e) {
			// no photo: the letter stays
		}
		Rectangle clip = new Rectangle(w, h);
		clip.setArcWidth(16);
		clip.setArcHeight(16);
		box.setClip(clip);
		return box;
	}

	private static VBox choiceCard(String name, String meta, boolean on) {
		Label n = new Label(name);
		n.getStyleClass().add("lf-choice-name");
		n.setWrapText(true);
		n.setMinHeight(Region.USE_PREF_SIZE);
		Label m = new Label(meta);
		m.getStyleClass().add("lf-muted");
		VBox card = new VBox(2, n, m);
		card.getStyleClass().add("lf-choice");
		if (on) {
			card.getStyleClass().add("lf-choice-on");
		}
		card.setPrefWidth(190);
		card.setMaxWidth(190);
		return card;
	}

	private Map<Recipe, String> recentRecipes() {
		Map<Recipe, String> result = new LinkedHashMap<>();
		LocalDate today = facade.today();
		List<LoggedFood> logged = facade.getLoggedFoodBetween(today.minusDays(30), today);
		for (Recipe r : facade.getRecipes()) {
			String why = null;
			for (int i = logged.size() - 1; i >= 0; i--) {
				LoggedFood f = logged.get(i);
				if ("recipe".equals(f.source()) && f.description().startsWith(r.getTitle())) {
					long days = today.toEpochDay() - f.date().toEpochDay();
					why = days <= 0 ? "Cooked today" : days == 1 ? "Cooked yesterday" : "Cooked " + days + " days ago";
					break;
				}
			}
			if (why != null || r.isSaved() || r.isInMyRecipes()) {
				String saved = r.isSaved() ? "\u2665 Saved" : r.isInMyRecipes() ? "My recipe" : "";
				result.put(r, saved.isEmpty() ? why : why == null ? saved : saved + " \u00B7 " + why);
			}
			if (result.size() >= 9) {
				break;
			}
		}
		return result;
	}

	private void chooseRecipe(Recipe r) {
		fromRecipeNotice = false;
		recipe = r;
		batch = r.getServings();
		portion = 1;
		overrides.clear();
		understood = null;
		noMatch = false;
		notesArea.clear();
		problem = "";
		drawAll();
	}

	private void chooseItem(PantryItem p) {
		fromPantryNotice = false;
		item = p;
		amount = defaultAmount(p);
		macroOverride = null;
		macroEditing = false;
		aiAck = false;
		problem = "";
		pantryPreview = null;
		pantryPreviewError = "";
		refreshPantryPreview();
		drawAll();
	}

	private static double defaultAmount(PantryItem p) {
		String u = p.getUnit().toLowerCase(Locale.ROOT);
		double def = u.isEmpty() || u.startsWith("pc") || u.equals("serving") || u.equals("servings") ? 1
				: u.equals("g") || u.equals("ml") ? 100 : 1;
		return Math.min(def, p.getQuantity());
	}

	// ---------------------------------------------------------------- 2. how much

	private void drawAmount() {
		Label h = label("How much did you eat?", "lf-h2");
		amountCard.getChildren().setAll(h);
		if (source == Source.RECIPE) {
			if (recipe == null) {
				amountCard.getChildren().add(label("Choose a recipe above.", "lf-muted"));
				return;
			}
			FlowPane pills = new FlowPane(8, 8);
			for (double o : new double[] { 0.5, 1, 1.5, 2 }) {
				if (o <= batch + 1e-9) {
					pills.getChildren().add(
							pill(portionLabel(o) + (o == 1 ? " serving" : ""), Math.abs(portion - o) < 1e-9, () -> {
								portion = o;
								drawAll();
							}));
				}
			}
			Label shown = label(portionLabel(portion) + " of " + batch, "lf-label");
			shown.setWrapText(false);
			shown.setMinWidth(70);
			shown.setAlignment(Pos.CENTER);
			HBox stepper = new HBox(6, stepButton("\u2212", () -> setPortion(portion - 0.25)), shown,
					stepButton("+", () -> setPortion(portion + 0.25)));
			stepper.setAlignment(Pos.CENTER_LEFT);
			HBox row = new HBox(14, pills, stepper);
			row.setAlignment(Pos.CENTER_LEFT);
			amountCard.getChildren().add(row);
			double leftover = batch - portion;
			if (leftover > 1e-9 && !pastLog()) {
				CheckBox keep = new CheckBox("Save " + servingsText(leftover) + " to your pantry under Recipes "
						+ "(good for " + recipe.freshDays() + " days)");
				keep.setSelected(saveLeftovers);
				keep.setWrapText(true);
				keep.setOnAction(e -> saveLeftovers = keep.isSelected());
				amountCard.getChildren().add(keep);
			}
			return;
		}
		if (item == null) {
			amountCard.getChildren().add(label("Choose a food from your pantry above.", "lf-muted"));
			return;
		}
		String unit = item.getUnit();
		FlowPane pills = new FlowPane(8, 8);
		double[] steps = unit.equalsIgnoreCase("g") || unit.equalsIgnoreCase("ml") ? new double[] { 30, 100, 170 }
				: unit.isEmpty() || unit.toLowerCase(Locale.ROOT).startsWith("pc") ? new double[] { 1, 2, 3 }
						: new double[] { 0.5, 1, 2 };
		for (double s : steps) {
			if (s <= item.getQuantity() + 1e-9) {
				String shownLabel = unit.equalsIgnoreCase("g") || unit.equalsIgnoreCase("ml")
						? Formats.number(s) + " " + unit
						: (s == 0.5 ? "\u00BD" : Formats.number(s))
								+ (unit.toLowerCase(Locale.ROOT).startsWith("serving")
										? (s == 1 ? " serving" : " servings")
										: "");
				pills.getChildren().add(pill(shownLabel, Math.abs(amount - s) < 1e-9, () -> setAmount(s)));
			}
		}
		TextField exact = new TextField(Formats.number(amount));
		exact.getStyleClass().add("lf-input");
		exact.setPrefWidth(110);
		Runnable commit = () -> {
			try {
				setAmount(Double.parseDouble(exact.getText().trim().replace(',', '.')));
			} catch (NumberFormatException e) {
				exact.setText(Formats.number(amount));
			}
		};
		exact.setOnAction(e -> commit.run());
		exact.focusedProperty().addListener((o, was, is) -> {
			if (!is) {
				commit.run();
			}
		});
		Label exactLabel = label("Exact amount", "lf-label");
		exactLabel.setWrapText(false);
		exactLabel.setMinWidth(Region.USE_PREF_SIZE);
		Label unitLabel = label(unit.isEmpty() ? "pcs" : unit, "lf-muted");
		unitLabel.setWrapText(false);
		unitLabel.setMinWidth(Region.USE_PREF_SIZE);
		HBox row = new HBox(10, pills, grow(), exactLabel, exact, unitLabel);
		row.setAlignment(Pos.CENTER_LEFT);
		double after = Math.max(0, item.getQuantity() - amount);
		boolean tooMuch = amount > item.getQuantity() + 1e-9;
		if (pastLog()) {
			amountCard.getChildren().add(row);
			amountCard.getChildren().add(label("This is a past day, so your pantry stays as it is.", "lf-note"));
			return;
		}
		amountCard.getChildren().addAll(row,
				label(tooMuch
						? "You only have " + Formats.quantityWithUnit(round2(item.getQuantity()), unit)
								+ " \u2014 Mealton will take it all."
						: "Taken out of your pantry: " + item.getName() + " "
								+ Formats.quantityWithUnit(round2(item.getQuantity()), unit) + " \u2192 "
								+ Formats.quantityWithUnit(round2(after), unit),
						tooMuch ? "lf-note-warn" : "lf-note"));
	}

	private void setPortion(double value) {
		if (recipe == null || value < 0.25 || value > batch + 1e-9) {
			return;
		}
		portion = value;
		drawAll();
	}

	private void setAmount(double value) {
		if (item == null || value <= 0) {
			return;
		}
		amount = value;
		macroOverride = null;
		macroEditing = false;
		aiAck = false;
		refreshPantryPreview();
		drawAll();
	}

	private static double round2(double v) {
		return Math.round(v * 100) / 100.0;
	}

	// ---------------------------------------------------------------- 3. anything
	// different

	private void drawNotes() {
		notesCard.getChildren().clear();
		boolean show = source == Source.RECIPE && recipe != null && !pastLog();
		notesCard.setVisible(show);
		notesCard.setManaged(show);
		if (!show) {
			return;
		}
		Label h = label("Anything different?", "lf-h2");
		Label help = label("Describe changes in your own words — Mealton adjusts the amounts below.", "lf-text");
		Button apply = new Button(working ? "Reading…" : "Apply with Mealton");
		apply.getStyleClass().add("lf-ai-btn");
		apply.setDisable(working);
		apply.setOnAction(e -> applyNotes());
		FlowPane actions = new FlowPane(8, 8);
		actions.setAlignment(Pos.CENTER_LEFT);
		actions.getChildren().add(apply);
		List<com.mealton.recipe.RecipeIngredient> ings = recipe.getIngredients();
		String[] examples = new String[3];
		examples[0] = ings.size() > 0 ? "I only used one " + ings.get(0).name().toLowerCase(Locale.ROOT) : null;
		examples[1] = ings.size() > 1 ? "No " + ings.get(1).name().toLowerCase(Locale.ROOT) : null;
		examples[2] = ings.size() > 2 ? "Half the " + ings.get(2).name().toLowerCase(Locale.ROOT) : null;
		for (String ex : examples) {
			if (ex != null) {
				Button chip = new Button(ex);
				chip.getStyleClass().add("lf-chip");
				chip.setOnAction(e -> notesArea.setText(ex));
				actions.getChildren().add(chip);
			}
		}
		notesCard.getChildren().addAll(h, help, notesArea, actions);
		if (understood != null && !understood.isEmpty()) {
			VBox box = new VBox(4, label("Mealton understood:", "lf-label"));
			for (String u : understood) {
				box.getChildren().add(label(u, "lf-text"));
			}
			box.getStyleClass().add("lf-understood");
			notesCard.getChildren().add(box);
		}
		if (noMatch) {
			notesCard.getChildren().add(
					label("Mealton couldn’t match that to an ingredient. Edit the amounts below directly.", "lf-note"));
		}
		notesCard.getChildren().add(ingredientTable());
	}

	private void applyNotes() {
		if (recipe == null || working) {
			return;
		}
		String text = notesArea.getText() == null ? "" : notesArea.getText().trim();
		if (text.isEmpty()) {
			return;
		}
		working = true;
		drawNotes();
		final long id = recipe.getId();
		final int b = batch;
		Task<PortionNotes.Result> task = new Task<>() {
			@Override
			protected PortionNotes.Result call() {
				return facade.readMealNotes(id, b, text);
			}
		};
		task.setOnSucceeded(e -> {
			working = false;
			PortionNotes.Result r = task.getValue();
			overrides.putAll(r.amounts());
			understood = r.said().isEmpty() ? null : r.said();
			noMatch = r.isEmpty();
			drawAll();
		});
		task.setOnFailed(e -> {
			working = false;
			noMatch = true;
			drawAll();
		});
		WORKER.submit(task);
	}

	private Node ingredientTable() {
		VBox table = new VBox(0);
		Label caption = label("Taken out of your pantry", "lf-label");
		GridPane head = new GridPane();
		head.setHgap(10);
		setColumns(head);
		String[] titles = { "Ingredient", "You have", "You used", "Left after" };
		for (int i = 0; i < titles.length; i++) {
			head.add(label(titles[i], "lf-table-head"), i, 0);
		}
		table.getChildren().addAll(caption, head);
		List<MealLogService.IngredientLine> lines;
		try {
			lines = facade.previewMeal(recipe.getId(), batch, overrides);
		} catch (RuntimeException e) {
			return label(e.getMessage() == null ? "" : e.getMessage(), "lf-error");
		}
		for (MealLogService.IngredientLine l : lines) {
			GridPane row = new GridPane();
			row.setHgap(10);
			row.getStyleClass().add("lf-row");
			if (l.changed()) {
				row.getStyleClass().add("lf-row-changed");
			}
			setColumns(row);
			HBox name = new HBox(6, label(l.name(), "lf-text"));
			name.setAlignment(Pos.CENTER_LEFT);
			if (l.changed()) {
				name.getChildren().add(label("changed", "lf-changed"));
			}
			String unit = l.unit();
			TextField used = new TextField(Formats.number(l.used()));
			used.getStyleClass().add("lf-input");
			used.setPrefWidth(84);
			Runnable commit = () -> {
				try {
					double v = Math.max(0, Double.parseDouble(used.getText().trim().replace(',', '.')));
					if (Math.abs(v - l.planned()) < 1e-9) {
						overrides.remove(l.index());
					} else {
						overrides.put(l.index(), v);
					}
					drawAll();
				} catch (NumberFormatException e) {
					used.setText(Formats.number(l.used()));
				}
			};
			used.setOnAction(e -> commit.run());
			used.focusedProperty().addListener((o, was, is) -> {
				if (!is && !used.getText().trim().equals(Formats.number(l.used()))) {
					commit.run();
				}
			});
			HBox usedBox = new HBox(6, used, label(unit, "lf-muted"));
			usedBox.setAlignment(Pos.CENTER_LEFT);
			String have = !l.inPantry() ? "Not in pantry"
					: l.have() < 0 ? "In pantry" : Formats.quantityWithUnit(round2(l.have()), unit);
			String after;
			boolean warn = false;
			if (!l.inPantry()) {
				after = "Nothing to deduct";
			} else if (l.have() < 0) {
				after = "—";
			} else if (l.used() > l.have() + 1e-9) {
				after = "0 · used it all";
				warn = true;
			} else {
				after = Formats.quantityWithUnit(round2(l.after()), unit);
				warn = l.after() <= 1e-9;
			}
			Label afterLabel = label(after, warn ? "lf-warn" : "lf-text");
			row.add(name, 0, 0);
			row.add(label(have, "lf-text"), 1, 0);
			row.add(usedBox, 2, 0);
			row.add(afterLabel, 3, 0);
			table.getChildren().add(row);
		}
		return table;
	}

	private static void setColumns(GridPane g) {
		double[] widths = { 34, 22, 26, 18 };
		for (double w : widths) {
			javafx.scene.layout.ColumnConstraints c = new javafx.scene.layout.ColumnConstraints();
			c.setPercentWidth(w);
			g.getColumnConstraints().add(c);
		}
	}

	// ====================================================================
	// right column: macros, AI estimate, logged today
	// ====================================================================

	/** What this meal adds up to right now, or null when unknown. */
	private Nutrients mealNutrients() {
		if (source == Source.RECIPE) {
			if (recipe == null) {
				return null;
			}
			try {
				return facade.previewMealNutrients(recipe.getId(), batch, portion, overrides);
			} catch (RuntimeException e) {
				return null;
			}
		}
		if (macroOverride != null) {
			return macroOverride;
		}
		return pantryPreview == null ? null : pantryPreview.nutrients();
	}

	private void refreshPantryPreview() {
		if (item == null || amount <= 0) {
			pantryPreview = null;
			return;
		}
		final long id = item.getId();
		final double amt = amount;
		final String key = id + ":" + amt;
		previewKey = key;
		pantryPreview = null;
		pantryPreviewError = "";
		Task<MealtonFacade.PantryMacros> task = new Task<>() {
			@Override
			protected MealtonFacade.PantryMacros call() {
				return facade.pantryMacros(id, Math.min(amt, item == null ? amt : item.getQuantity()));
			}
		};
		task.setOnSucceeded(e -> {
			if (key.equals(previewKey)) {
				pantryPreview = task.getValue();
				drawSide();
			}
		});
		task.setOnFailed(e -> {
			if (key.equals(previewKey)) {
				pantryPreview = null;
				pantryPreviewError = task.getException() == null ? "" : task.getException().getMessage();
				drawSide();
			}
		});
		WORKER.submit(task);
	}

	private void drawSide() {
		sideBox.getChildren().clear();
		sideBox.getChildren().add(macroCard());
		sideBox.getChildren().add(loggedTodayCard());
	}

	private boolean showNutritionPopup() {
		return tracking() && source == Source.PANTRY && item != null && pantryPreview != null
				&& (!aiAck && macroOverride == null || macroEditing);
	}

	private static boolean countUnit(String unit) {
		String u = unit == null ? "" : unit.toLowerCase(Locale.ROOT);
		return u.isEmpty() || u.startsWith("pc") || u.equals("piece") || u.equals("pieces");
	}

	private static String singular(String name) {
		String n = name.toLowerCase(Locale.ROOT);
		if (n.endsWith("ies") && n.length() > 4) {
			return n.substring(0, n.length() - 3) + "y";
		}
		if (n.endsWith("oes")) {
			return n.substring(0, n.length() - 2);
		}
		return n.endsWith("s") && !n.endsWith("ss") ? n.substring(0, n.length() - 1) : n;
	}

	private static String plural(String name) {
		String n = name.toLowerCase(Locale.ROOT);
		if (n.endsWith("s")) {
			return n;
		}
		if (n.endsWith("y") && n.length() > 2 && "aeiou".indexOf(n.charAt(n.length() - 2)) < 0) {
			return n.substring(0, n.length() - 1) + "ies";
		}
		return n.endsWith("ch") || n.endsWith("sh") || n.endsWith("x") || n.endsWith("o") ? n + "es" : n + "s";
	}

	/** "2 oranges", "1 egg", "100 g of cheese", "1 serving of leftovers". */
	private static String foodPhrase(double amount, PantryItem item) {
		double a = round2(amount);
		if (countUnit(item.getUnit())) {
			return Formats.number(a) + " " + (a == 1 ? singular(item.getName()) : plural(item.getName()));
		}
		String unit = item.getUnit();
		if (unit.equalsIgnoreCase("servings") && a == 1) {
			unit = "serving";
		}
		return Formats.quantityWithUnit(a, unit) + " of " + item.getName().toLowerCase(Locale.ROOT);
	}

	private Node aiEstimateCard() {
		Nutrients n = macroOverride != null ? macroOverride
				: pantryPreview == null ? Nutrients.ZERO : pantryPreview.nutrients();
		boolean ai = pantryPreview != null && pantryPreview.aiEstimated();
		boolean leftover = MealLogService.LEFTOVER_CATEGORY.equals(item.getCategory());
		SVGPath leaf = new SVGPath();
		leaf.setContent("M11 20A7 7 0 0 1 9.8 6.1C15.5 5 17 4.48 19 2c1 2 2 4.18 2 8 0 5.5-4.78 10-10 10Z");
		leaf.setFill(null);
		leaf.setStroke(javafx.scene.paint.Color.web("#c5d5b8"));
		leaf.setStrokeWidth(2);
		leaf.setScaleX(0.8);
		leaf.setScaleY(0.8);
		Label title = label("Macros estimated by Mealton AI", "lf-ai-title");
		if (!ai) {
			title.setText("Nutrition from Mealton");
		}
		HBox titleRow = new HBox(8, leaf, title);
		titleRow.setAlignment(Pos.CENTER_LEFT);
		String phrase = foodPhrase(amount, item);
		String numbers = fmt(n.calories()) + " kcal \u00B7 " + Formats.number(round1(n.protein()))
				+ " g protein \u00B7 " + Formats.number(round1(n.carbs())) + " g carbs \u00B7 "
				+ Formats.number(round1(n.fat())) + " g fat.";
		Label text = label(ai
				? "Mealton doesn\u2019t have a recipe for this, so it estimated the nutrition for " + phrase + ": "
						+ numbers
				: leftover ? "These are the saved leftovers from your recipe. Nutrition for " + phrase + ": " + numbers
						: "Mealton looked up the nutrition for " + phrase + ": " + numbers
								+ " Change it if your food is different.",
				"lf-ai-text");
		VBox card = new VBox(10, titleRow, text);
		card.getStyleClass().add("lf-ai-card");
		if (macroEditing) {
			String[] names = { "Calories", "Protein g", "Carbs g", "Fat g" };
			double[] values = { n.calories(), n.protein(), n.carbs(), n.fat() };
			TextField[] fields = new TextField[4];
			HBox grid = new HBox(6);
			for (int i = 0; i < 4; i++) {
				fields[i] = new TextField(Formats.number(round1(values[i])));
				fields[i].getStyleClass().add("lf-ai-input");
				fields[i].setMinWidth(0);
				Label l = label(names[i], "lf-ai-small");
				l.setWrapText(false);
				VBox cell = new VBox(4, l, fields[i]);
				HBox.setHgrow(cell, Priority.ALWAYS);
				cell.setMinWidth(0);
				grid.getChildren().add(cell);
			}
			Button save = new Button("Save");
			save.getStyleClass().add("lf-ai-light");
			save.setOnAction(e -> {
				try {
					macroOverride = new Nutrients(num(fields[0]), num(fields[1]), num(fields[2]), num(fields[3]));
					macroEditing = false;
					aiAck = true;
					problem = "";
					drawSide();
				} catch (IllegalArgumentException ex) {
					problem = "Enter whole or decimal numbers, 0 or more.";
					drawSide();
				}
			});
			Button reset = new Button(ai ? "Use AI estimate" : "Use Mealton\u2019s numbers");
			reset.getStyleClass().add("lf-ai-outline");
			reset.setOnAction(e -> {
				macroOverride = null;
				macroEditing = false;
				aiAck = false;
				drawSide();
			});
			card.getChildren().addAll(grid, new HBox(8, save, reset));
		} else {
			Button ok = new Button("Looks right");
			ok.getStyleClass().add("lf-ai-light");
			ok.setOnAction(e -> {
				aiAck = true;
				drawSide();
			});
			Button change = new Button("Change");
			change.getStyleClass().add("lf-ai-outline");
			change.setOnAction(e -> {
				macroEditing = true;
				drawSide();
			});
			card.getChildren().add(new HBox(8, ok, change));
		}
		// a little arrow pointing up at the Log button, like the mockup's speech bubble
		javafx.scene.shape.Polygon tail = new javafx.scene.shape.Polygon(0, 9, 9, 0, 18, 9);
		tail.setFill(javafx.scene.paint.Color.web("#1f2a22"));
		VBox wrap = new VBox(-1, new HBox(tail), card);
		HBox.setMargin(tail, new Insets(0, 0, 0, 26));
		return wrap;
	}

	private static double num(TextField f) {
		try {
			double v = Double.parseDouble(f.getText().trim().replace(',', '.'));
			if (v < 0 || Double.isNaN(v)) {
				throw new IllegalArgumentException("negative");
			}
			return v;
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("number");
		}
	}

	private static double round1(double v) {
		return Math.round(v * 10) / 10.0;
	}

	private Node macroCard() {
		VBox card = new VBox(12);
		card.getStyleClass().add("lf-card");
		Nutrients meal = mealNutrients();
		boolean track = tracking();
		if (!track) {
			card.getChildren().addAll(label("Log this meal", "lf-h2"), label(
					"Macro tracking is off, so this only updates your pantry and food log. Turn it on in Profile.",
					"lf-note"));
		} else {
			LocalDate today = logDate == null ? facade.today() : logDate;
			DailyProgress progress = facade.getProgress(today);
			UserProfile p = facade.getProfile();
			Nutrients eaten = progress.total();
			Nutrients m = meal == null ? Nutrients.ZERO : meal;
			HBox head = new HBox(8,
					label(logDate == null ? "Today’s macros" : "Macros for " + dayTitle(logDate, facade.today()),
							"lf-h2"),
					grow(), label(today.format(DAY), "lf-muted"));
			head.setAlignment(Pos.CENTER_LEFT);
			Label big = label(fmt(eaten.calories() + m.calories()), "lf-big");
			big.setWrapText(false);
			Label of = label(p.calorieGoal() > 0 ? "/ " + fmt(p.calorieGoal()) + " kcal" : "kcal (no target set)",
					"lf-muted");
			of.setWrapText(false);
			Label plus = label("+" + fmt(m.calories()) + " this meal", "lf-plus");
			plus.setWrapText(false);
			HBox bigRow = new HBox(8, big, of, grow(), plus);
			bigRow.setAlignment(Pos.BASELINE_LEFT);
			card.getChildren().addAll(head, bigRow);
			card.getChildren().add(bar("Calories", eaten.calories(), m.calories(), p.calorieGoal(), " kcal"));
			card.getChildren().add(bar("Protein", eaten.protein(), m.protein(), p.proteinGoal(), " g"));
			card.getChildren().add(bar("Carbs", eaten.carbs(), m.carbs(), p.carbsGoal(), " g"));
			card.getChildren().add(bar("Fat", eaten.fat(), m.fat(), p.fatGoal(), " g"));
			HBox legend = new HBox(14, label("\u25CF Already eaten", "lf-legend-eaten"),
					label("\u25CF This meal", "lf-legend-meal"));
			card.getChildren().add(legend);
			if (meal == null) {
				card.getChildren()
						.add(label(source == Source.RECIPE && recipe != null
								? "Mealton will estimate this recipe’s nutrition when you log it."
								: pantryPreviewError.isEmpty() ? "" : pantryPreviewError, "lf-muted"));
			}
		}
		if (!problem.isEmpty()) {
			card.getChildren().add(label(problem, "lf-error"));
		}
		String what = "Log this meal";
		if (source == Source.RECIPE && recipe != null) {
			what = "Log " + servingsText(portion);
		} else if (source == Source.PANTRY && item != null && amount > 0) {
			what = "Log " + foodPhrase(amount, item).replace(" of ", " ");
		}
		Nutrients mn = mealNutrients();
		if (tracking() && mn != null) {
			what += " \u00B7 " + fmt(mn.calories()) + " kcal";
		}
		Button log = new Button(working ? "Working\u2026" : what);
		log.getStyleClass().add("lf-log-btn");
		log.setMaxWidth(Double.MAX_VALUE);
		boolean ready = source == Source.RECIPE ? recipe != null
				: item != null && amount > 0 && (pantryPreview != null || macroOverride != null);
		log.setDisable(working || !ready);
		log.setOnAction(e -> logMeal());
		card.getChildren().add(log);
		if (showNutritionPopup()) {
			card.getChildren().add(aiEstimateCard());
		}
		if (track) {
			if (source == Source.PANTRY && item != null && pantryPreview != null) {
				boolean ai = pantryPreview.aiEstimated();
				String src = macroOverride != null ? "Nutrition edited by you."
						: ai ? "Nutrition estimated by Mealton AI."
								: MealLogService.LEFTOVER_CATEGORY.equals(item.getCategory())
										? "Nutrition from your saved recipe."
										: "Nutrition from Mealton\u2019s food table.";
				Label srcLabel = label(src, "lf-muted");
				if ((aiAck || macroOverride != null) && !macroEditing) {
					Button changeLink = new Button("Change");
					changeLink.getStyleClass().add("lf-link");
					changeLink.setOnAction(e -> {
						macroEditing = true;
						drawSide();
					});
					card.getChildren().add(new HBox(6, srcLabel, changeLink));
				} else {
					card.getChildren().add(srcLabel);
				}
			} else {
				card.getChildren().add(label("Nutrition is estimated from the amounts you used.", "lf-muted"));
			}
		}
		return card;
	}

	private Node bar(String name, double eaten, double meal, double target, String unit) {
		return bar(name, eaten, meal, target, unit, " left after this meal");
	}

	private Node bar(String name, double eaten, double meal, double target, String unit, String leftSuffix) {
		HBox top = new HBox(6, label(name, "lf-label"), grow(),
				label(fmt(eaten + meal) + (target > 0 ? " / " + fmt(target) : "") + unit
						+ (meal > 0 ? "  (+" + fmt(meal) + ")" : ""), "lf-muted"));
		VBox box = new VBox(4, top);
		if (target > 0) {
			double before = Math.min(1, eaten / target);
			double add = Math.max(0, Math.min(1 - before, meal / target));
			HBox track = new HBox();
			track.getStyleClass().add("lf-bar-track");
			track.setPrefHeight(9);
			track.setMinHeight(9);
			track.setMaxHeight(9);
			Region b = new Region();
			b.getStyleClass().add("lf-bar-eaten");
			Region a = new Region();
			a.getStyleClass().add("lf-bar-meal");
			b.prefWidthProperty().bind(track.widthProperty().multiply(before));
			a.prefWidthProperty().bind(track.widthProperty().multiply(add));
			b.setMaxHeight(9);
			a.setMaxHeight(9);
			track.getChildren().addAll(b, a);
			box.getChildren().add(track);
			double left = target - eaten - meal;
			box.getChildren()
					.add(label(left >= 0 ? fmt(left) + unit + leftSuffix : fmt(-left) + unit + " over your target",
							left >= 0 ? "lf-muted" : "lf-over"));
		}
		return box;
	}

	private Node loggedTodayCard() {
		VBox card = new VBox(8,
				label(logDate == null ? "Logged today"
						: (logDate.equals(facade.today().minusDays(1)) || logDate.equals(facade.today().plusDays(1))
								? "Logged " + dayTitle(logDate, facade.today()).toLowerCase(Locale.ROOT)
								: "Logged on " + dayTitle(logDate, facade.today())),
						"lf-h2"));
		card.getStyleClass().add("lf-card");
		List<LoggedFood> today = logDate == null ? facade.getTodaysFood()
				: facade.getLoggedFoodBetween(logDate, logDate);
		if (today.isEmpty()) {
			card.getChildren().add(label("Nothing logged yet today.", "lf-muted"));
		}
		List<LoggedFood> newestFirst = new ArrayList<>(today);
		java.util.Collections.reverse(newestFirst);
		boolean first = true;
		for (LoggedFood f : newestFirst) {
			VBox text = new VBox(2);
			if (!f.meal().isEmpty()) {
				text.getChildren().add(label(f.meal(), "lf-muted"));
			}
			text.getChildren().add(label(f.description(), "lf-text"));
			if (!f.note().isEmpty()) {
				text.getChildren().add(label("“" + f.note() + "”", "lf-muted"));
			}
			HBox.setHgrow(text, Priority.ALWAYS);
			HBox row = new HBox(8, text);
			if (tracking()) {
				Label k = label(fmt(f.nutrients().calories()) + " kcal", "lf-label");
				k.setWrapText(false);
				row.getChildren().add(k);
			}
			row.setAlignment(Pos.TOP_LEFT);
			if (first && lastResult != null) {
				row.getStyleClass().add("lf-row-new");
			}
			first = false;
			card.getChildren().add(row);
		}
		return card;
	}

	// ====================================================================
	// logging
	// ====================================================================

	private void logMeal() {
		if (working) {
			return;
		}
		problem = "";
		working = true;
		drawSide();
		final Source src = source;
		final long recipeId = recipe == null ? -1 : recipe.getId();
		final int b = batch;
		final double por = portion;
		final String mealName = meal;
		final Map<Integer, Double> over = new HashMap<>(overrides);
		final String note = notesArea.getText() == null ? "" : notesArea.getText().trim();
		final boolean keep = saveLeftovers;
		final long itemId = item == null ? -1 : item.getId();
		final double amt = amount;
		final Nutrients custom = macroOverride;
		final LocalDate when = logDate == null ? facade.today() : logDate;
		Task<MealLogService.Result> task = new Task<>() {
			@Override
			protected MealLogService.Result call() {
				return src == Source.RECIPE ? facade.logRecipeMeal(recipeId, b, por, mealName, over, note, keep, when)
						: facade.logPantryMeal(itemId, amt, mealName, custom, when);
			}
		};
		task.setOnSucceeded(e -> {
			working = false;
			finishLogging(task.getValue());
		});
		task.setOnFailed(e -> {
			working = false;
			Throwable t = task.getException();
			problem = t == null || t.getMessage() == null ? "That could not be logged." : t.getMessage();
			drawAll();
		});
		WORKER.submit(task);
	}

	private void finishLogging(MealLogService.Result r) {
		lastResult = r;
		LoggedFood entry = r.entry();
		lastTitle = "Logged " + entry.description()
				+ (tracking() ? " · " + fmt(entry.nutrients().calories()) + " kcal added to today." : ".");
		List<String> changes = r.pantryChanges();
		lastPantry = changes.isEmpty() ? ""
				: "Pantry updated: " + String.join(", ", changes.subList(0, Math.min(4, changes.size())))
						+ (changes.size() > 4 ? ", +" + (changes.size() - 4) + " more" : "") + "."
						+ (r.leftoverText().isEmpty() ? "" : " " + r.leftoverText());
		if (changes.isEmpty() && !r.leftoverText().isEmpty()) {
			lastPantry = r.leftoverText();
		}
		listAdded = "";
		overrides.clear();
		understood = null;
		noMatch = false;
		notesArea.clear();
		portion = 1;
		macroOverride = null;
		macroEditing = false;
		aiAck = false;
		if (source == Source.PANTRY) {
			item = null;
			pantryPreview = null;
		}
		drawAll();
		if (!r.low().isEmpty()) {
			Platform.runLater(() -> askLowStock(r.low()));
		}
	}

	private void askLowStock(List<MealLogService.LowItem> low) {
		int n = LogFeedback.askLowStock(this, facade, low);
		if (n > 0) {
			listAdded = n + (n == 1 ? " item" : " items") + " added to your shopping list.";
			if (onViewShoppingList != null) {
				listAdded += " View shopping list";
			}
			drawBanner();
		}
	}

	// ====================================================================
	// history
	// ====================================================================

	private LocalDate rangeStart(LocalDate today) {
		if ("week".equals(histRange)) {
			return today.with(DayOfWeek.MONDAY);
		}
		return today.minusDays("30".equals(histRange) ? 29 : 6);
	}

	private void drawTrends(LocalDate today) {
		LocalDate from = rangeStart(today);
		boolean track = tracking();
		UserProfile p = facade.getProfile();

		List<LoggedFood> all = facade.getLoggedFoodBetween(from, today);
		Map<LocalDate, Nutrients> totals = new LinkedHashMap<>();
		Map<String, Integer> counts = new HashMap<>();
		for (LoggedFood f : all) {
			totals.merge(f.date(), f.nutrients(), Nutrients::plus);
			counts.merge(f.description().replaceAll(" \\(.*servings?\\)$", ""), 1, Integer::sum);
		}
		// stats
		List<Node> stats = new ArrayList<>();
		if (track) {
			List<LocalDate> fullDays = new ArrayList<>();
			for (LocalDate d : totals.keySet()) {
				if (!d.equals(today)) {
					fullDays.add(d);
				}
			}
			double avg = 0;
			int near = 0;
			for (LocalDate d : fullDays) {
				double k = totals.get(d).calories();
				avg += k;
				if (p.calorieGoal() > 0 && Math.abs(k - p.calorieGoal()) <= p.calorieGoal() * 0.1) {
					near++;
				}
			}
			stats.add(stat(fullDays.isEmpty() ? "—" : fmt(avg / fullDays.size()), "avg kcal / day (full days)"));
			stats.add(stat(p.calorieGoal() > 0 ? near + " / " + fullDays.size() : "—", "days within 10% of target"));
		}
		stats.add(stat(String.valueOf(all.size()), "meals logged"));
		String fav = counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("—");
		stats.add(stat(fav, "logged most"));
		HBox statRow = new HBox(12);
		for (Node s : stats) {
			HBox.setHgrow(s, Priority.ALWAYS);
			((Region) s).setMaxWidth(Double.MAX_VALUE);
			statRow.getChildren().add(s);
		}
		histBody.getChildren().add(statRow);

		if (track) {
			histBody.getChildren().add(chart(from, today, totals, p.calorieGoal()));
		}
	}

	private void drawHistory() {
		histBody.getChildren().clear();
		LocalDate today = facade.today();
		if (histDate == null) {
			histDate = today;
		}
		histPills.getChildren().setAll(pill("This week", "week".equals(histRange), () -> setRange("week")),
				pill("Last 7 days", "7".equals(histRange), () -> setRange("7")),
				pill("Last 30 days", "30".equals(histRange), () -> setRange("30")));
		slideTarget = null;
		drawTrends(today);
		if (!toast.isEmpty()) {
			histBody.getChildren().add(toastRow());
		}
		Node page = dayPage(today);
		histBody.getChildren().add(page);
		addSwipe(page);
		if (slideDir != 0) {
			animateIn(slideTarget != null ? slideTarget : page);
			slideDir = 0;
		}
	}

	private void animateIn(Node page) {
		{
			page.setOpacity(0);
			page.setTranslateX(slideDir * 70);
			javafx.animation.TranslateTransition move = new javafx.animation.TranslateTransition(
					javafx.util.Duration.millis(260), page);
			move.setToX(0);
			move.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
			javafx.animation.FadeTransition fade = new javafx.animation.FadeTransition(javafx.util.Duration.millis(260),
					page);
			fade.setToValue(1);
			new javafx.animation.ParallelTransition(move, fade).play();
		}
	}

	/**
	 * Moves the Day view by days or the Week view by weeks, sliding the page in.
	 */
	private void shift(int steps) {
		histDate = histDate.plusDays(steps);
		slideDir = steps > 0 ? 1 : -1;
		drawHistory();
	}

	private void goTo(LocalDate date, String view) {
		slideDir = date.isAfter(histDate) ? 1 : date.isBefore(histDate) ? -1 : 0;
		histDate = date;
		drawHistory();
	}

	/**
	 * Drag sideways with the mouse (or swipe on a touch screen) to go to the next
	 * or earlier page.
	 */
	private void addSwipe(Node page) {
		double[] startX = { 0 };
		page.setOnMousePressed(e -> startX[0] = e.getSceneX());
		page.setOnMouseReleased(e -> {
			double dx = e.getSceneX() - startX[0];
			if (Math.abs(dx) > 120) {
				shift(dx < 0 ? 1 : -1);
			}
		});
		page.setOnSwipeLeft(e -> shift(1));
		page.setOnSwipeRight(e -> shift(-1));
	}

	private Button arrow(String text, String tip, Runnable action) {
		Button b = new Button(text);
		b.getStyleClass().add("lf-arrow");
		b.setTooltip(new javafx.scene.control.Tooltip(tip));
		b.setOnAction(e -> action.run());
		return b;
	}

	private Node toastRow() {
		Label t = label(toast, "lf-toast-text");
		HBox.setHgrow(t, Priority.ALWAYS);
		HBox row = new HBox(12, t);
		row.setAlignment(Pos.CENTER_LEFT);
		if (removed != null) {
			Button undo = new Button("Undo");
			undo.getStyleClass().add("lf-toast-undo");
			undo.setOnAction(e -> {
				facade.restoreLoggedEntry(removed);
				toast = "Put '" + removed.description() + "' back.";
				removed = null;
				drawHistory();
			});
			row.getChildren().add(undo);
		}
		row.getStyleClass().add("lf-toast");
		return row;
	}

	/**
	 * One day: the same macros breakdown as Log food, with arrows to look around,
	 * then that day's entries.
	 */
	private Node dayPage(LocalDate today) {
		LocalDate date = histDate;
		boolean track = tracking();
		UserProfile p = facade.getProfile();
		List<LoggedFood> entries = facade.getLoggedFoodBetween(date, date);
		Nutrients total = Nutrients.ZERO;
		for (LoggedFood f : entries) {
			total = total.plus(f.nutrients());
		}
		VBox page = new VBox(16);
		VBox content = new VBox(16);
		slideTarget = content;
		page.getChildren().add(content);

		Label title = label(dayTitle(date, today), "lf-h2");
		title.setWrapText(false);
		Label sub = label(
				date.format(DAY) + " \u00B7 " + entries.size() + (entries.size() == 1 ? " entry" : " entries"),
				"lf-muted");
		sub.setWrapText(false);
		HBox head = new HBox(10, arrow("\u2039", "Previous day", () -> shift(-1)), new VBox(2, title, sub), grow());
		head.setAlignment(Pos.CENTER_LEFT);
		if (!date.equals(today)) {
			Button now = new Button("Today");
			now.getStyleClass().add("lf-again");
			now.setOnAction(e -> goTo(today, "day"));
			head.getChildren().add(now);
		}
		Button logHere = new Button(date.equals(today) ? "Log food" : "Log food for " + dayTitle(date, today));
		logHere.getStyleClass().add("lf-again");
		logHere.setOnAction(e -> {
			logDate = date.equals(today) ? null : date;
			tab = Tab.LOG;
			toast = "";
			drawAll();
		});
		head.getChildren().addAll(logHere, arrow("\u203A", "Next day", () -> shift(1)));

		VBox card = new VBox(12, head);
		card.getStyleClass().add("lf-card");
		if (track) {
			Label big = label(fmt(total.calories()), "lf-big");
			big.setWrapText(false);
			Label of = label(p.calorieGoal() > 0 ? "/ " + fmt(p.calorieGoal()) + " kcal" : "kcal (no target set)",
					"lf-muted");
			of.setWrapText(false);
			HBox bigRow = new HBox(8, big, of);
			bigRow.setAlignment(Pos.BASELINE_LEFT);
			card.getChildren().add(bigRow);
			card.getChildren().add(bar("Calories", total.calories(), 0, p.calorieGoal(), " kcal", " left"));
			card.getChildren().add(bar("Protein", total.protein(), 0, p.proteinGoal(), " g", " left"));
			card.getChildren().add(bar("Carbs", total.carbs(), 0, p.carbsGoal(), " g", " left"));
			card.getChildren().add(bar("Fat", total.fat(), 0, p.fatGoal(), " g", " left"));
		}
		content.getChildren().add(card);

		String q = histSearch.getText() == null ? "" : histSearch.getText().trim().toLowerCase(Locale.ROOT);
		List<LoggedFood> shown = new ArrayList<>();
		for (LoggedFood f : entries) {
			if (q.isEmpty()
					|| (f.description() + " " + f.meal() + " " + f.note()).toLowerCase(Locale.ROOT).contains(q)) {
				shown.add(f);
			}
		}
		if (shown.isEmpty()) {
			content.getChildren().add(label(
					entries.isEmpty() ? "Nothing logged on this day." : "Nothing here matches \u201C" + q + "\u201D.",
					"lf-muted"));
		} else {
			content.getChildren().add(daySection(date, shown, total, today, track, false));
		}
		return page;
	}

	private void setRange(String range) {
		histRange = range;
		drawHistory();
	}

	private static Node stat(String value, String key) {
		Label v = new Label(value);
		v.getStyleClass().add("lf-stat-v");
		v.setWrapText(true);
		v.setMinHeight(Region.USE_PREF_SIZE);
		VBox box = new VBox(2, v, label(key, "lf-muted"));
		box.getStyleClass().add("lf-stat");
		return box;
	}

	private Node chart(LocalDate from, LocalDate to, Map<LocalDate, Nutrients> totals, int target) {
		HBox bars = new HBox(4);
		bars.setAlignment(Pos.BOTTOM_CENTER);
		HBox labels = new HBox(4);
		int span = (int) (to.toEpochDay() - from.toEpochDay()) + 1;
		for (int i = 0; i < span; i++) {
			LocalDate d = from.plusDays(i);
			double k = totals.containsKey(d) ? totals.get(d).calories() : 0;
			double height = target > 0 ? Math.min(118, Math.round(k / target * 96)) : Math.min(118, k / 20);
			Region bar = new Region();
			bar.setPrefHeight(Math.max(k > 0 ? 3 : 0, height));
			bar.setMinHeight(bar.getPrefHeight());
			bar.setMaxHeight(bar.getPrefHeight());
			bar.getStyleClass().add(d.equals(histDate) ? "lf-chart-bar-on"
					: target > 0 && Math.abs(k - target) <= target * 0.1 ? "lf-chart-bar-near" : "lf-chart-bar");
			javafx.scene.control.Tooltip.install(bar, new javafx.scene.control.Tooltip(
					d.format(DAY) + ": " + (k > 0 ? fmt(k) + " kcal" : "nothing logged")));
			VBox col = new VBox(bar);
			col.setAlignment(Pos.BOTTOM_CENTER);
			HBox.setHgrow(col, Priority.ALWAYS);
			col.setMaxWidth(Double.MAX_VALUE);
			bar.setMaxWidth(40);
			bar.setCursor(javafx.scene.Cursor.HAND);
			final LocalDate clicked = d;
			col.setCursor(javafx.scene.Cursor.HAND);
			col.setOnMouseClicked(e -> {
				if (e.isStillSincePress() && !clicked.equals(histDate)) {
					goTo(clicked, "day");
				}
			});
			bars.getChildren().add(col);
			String text = span > 10 ? (d.getDayOfMonth() % 5 == 0 ? String.valueOf(d.getDayOfMonth()) : "")
					: d.format(DAY).substring(0, 3);
			Label lab = label(text, "lf-muted");
			lab.setWrapText(false);
			lab.setAlignment(Pos.CENTER);
			lab.setMaxWidth(Double.MAX_VALUE);
			HBox.setHgrow(lab, Priority.ALWAYS);
			labels.getChildren().add(lab);
		}
		StackPane plot = new StackPane(bars);
		plot.setPrefHeight(124);
		plot.setMinHeight(124);
		if (target > 0) {
			Region line = new Region();
			line.getStyleClass().add("lf-target-line");
			line.setMaxHeight(1);
			line.setPrefHeight(1);
			StackPane.setAlignment(line, Pos.BOTTOM_CENTER);
			line.setTranslateY(-96);
			plot.getChildren().add(line);
		}
		HBox head = new HBox(8, label("Calories per day", "lf-label"), grow(),
				label(target > 0 ? "dashed line = your " + fmt(target) + " kcal target \u00B7 click a day to open it"
						: "click a day to open it", "lf-muted"));
		VBox card = new VBox(8, head, plot, labels);
		card.getStyleClass().add("lf-card");
		return card;
	}

	private static String dayTitle(LocalDate date, LocalDate today) {
		return date.equals(today) ? "Today"
				: date.equals(today.plusDays(1)) ? "Tomorrow"
						: date.equals(today.minusDays(1)) ? "Yesterday" : date.format(DAY);
	}

	private Node macroMini(String key, double eaten, double target, String color) {
		Label k = label(key, "lf-muted");
		k.setWrapText(false);
		Region fill = new Region();
		fill.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 3;");
		double frac = target > 0 ? Math.min(1, eaten / target) : 0;
		fill.setPrefSize(44 * frac, 6);
		fill.setMinSize(44 * frac, 6);
		fill.setMaxSize(44 * frac, 6);
		StackPane track = new StackPane(fill);
		StackPane.setAlignment(fill, Pos.CENTER_LEFT);
		track.setStyle("-fx-background-color: #e3e8e1; -fx-background-radius: 3;");
		track.setPrefSize(44, 6);
		track.setMinSize(44, 6);
		track.setMaxSize(44, 6);
		Label v = label(fmt(eaten) + " g", "lf-label");
		v.setWrapText(false);
		HBox box = new HBox(5, k, track, v);
		box.setAlignment(Pos.CENTER_LEFT);
		return box;
	}

	private Node daySection(LocalDate date, List<LoggedFood> entries, Nutrients total, LocalDate today, boolean track) {
		return daySection(date, entries, total, today, track, true);
	}

	private Node daySection(LocalDate date, List<LoggedFood> entries, Nutrients total, LocalDate today, boolean track,
			boolean header) {
		UserProfile p = facade.getProfile();
		Label title = label(dayTitle(date, today), "lf-day-h");
		title.setWrapText(false);
		String sub = (date.equals(today) || date.equals(today.minusDays(1)) || date.equals(today.plusDays(1))
				? "\u00B7 " + date.format(DAY) + " "
				: "") + "\u00B7 " + entries.size() + (entries.size() == 1 ? " entry" : " entries");
		HBox head = new HBox(8, title, label(sub, "lf-muted"), grow());
		head.setAlignment(Pos.CENTER_LEFT);
		head.getStyleClass().add("lf-day-head");
		if (track && total != null) {
			boolean near = p.calorieGoal() > 0 && Math.abs(total.calories() - p.calorieGoal()) <= p.calorieGoal() * 0.1;
			Label k = label(fmt(total.calories()) + (p.calorieGoal() > 0 ? " / " + fmt(p.calorieGoal()) : "") + " kcal",
					"lf-label");
			k.setWrapText(false);
			k.setStyle("-fx-font-size: 14px; -fx-text-fill: " + (p.calorieGoal() <= 0 || near ? "#2f5a3a" : "#b45309")
					+ ";");
			head.getChildren().add(k);
		}
		VBox card = new VBox(0);
		if (header) {
			card.getChildren().add(head);
		}
		card.getStyleClass().add("lf-card-flat");
		if (header && track && total != null) {
			HBox bars = new HBox(24, bar("Calories", total.calories(), 0, p.calorieGoal(), " kcal", " left"),
					bar("Protein", total.protein(), 0, p.proteinGoal(), " g", " left"),
					bar("Carbs", total.carbs(), 0, p.carbsGoal(), " g", " left"),
					bar("Fat", total.fat(), 0, p.fatGoal(), " g", " left"));
			for (Node b : bars.getChildren()) {
				HBox.setHgrow(b, Priority.ALWAYS);
				((Region) b).setMaxWidth(Double.MAX_VALUE);
				((Region) b).setPrefWidth(0);
			}
			bars.setPadding(new Insets(12, 16, 12, 16));
			card.getChildren().add(bars);
		}
		for (LoggedFood f : entries) {
			Label time = label(f.time(), "lf-muted");
			time.setWrapText(false);
			time.setMinWidth(46);
			HBox tags = new HBox(8);
			tags.setAlignment(Pos.CENTER_LEFT);
			if (!f.meal().isEmpty()) {
				Label m = label(f.meal().toUpperCase(Locale.ROOT), "lf-log-meal-plain");
				m.setWrapText(false);
				tags.getChildren().add(m);
			}
			if ("pantry".equals(f.source())) {
				Label src = label("From pantry", "lf-log-src-amber");
				src.setWrapText(false);
				tags.getChildren().add(src);
			}
			VBox text = new VBox(2);
			if (!tags.getChildren().isEmpty()) {
				text.getChildren().add(tags);
			}
			text.getChildren().add(label(f.description(), "lf-text-strong"));
			if (!f.note().isEmpty()) {
				text.getChildren().add(label("\u201C" + f.note() + "\u201D", "lf-note-italic"));
			}
			HBox.setHgrow(text, Priority.ALWAYS);
			HBox row = new HBox(12, time, text);
			row.setAlignment(Pos.CENTER_LEFT);
			row.getStyleClass().add("lf-row");
			if (track) {
				Label k = label(fmt(f.nutrients().calories()) + " kcal", "lf-text");
				k.setWrapText(false);
				row.getChildren().add(k);
			}
			Button again = new Button("Log again");
			again.getStyleClass().add("lf-again");
			again.setOnAction(e -> logAgainPrefill(f));
			SVGPath bin = new SVGPath();
			bin.setContent("M4 7h16 M9 7V4h6v3 M6 7l1 13h10l1-13 M10 11v6 M14 11v6");
			bin.setFill(null);
			bin.setStroke(javafx.scene.paint.Color.web("#5b6660"));
			bin.setStrokeWidth(1.8);
			Button del = new Button();
			del.setGraphic(bin);
			del.getStyleClass().add("lf-del");
			del.setTooltip(
					new javafx.scene.control.Tooltip("Delete " + f.description() + " from " + dayTitle(date, today)));
			del.setOnAction(e -> {
				facade.deleteLoggedFood(f.id());
				removed = f;
				toast = "Deleted " + f.description() + " from " + dayTitle(date, today) + ".";
				drawHistory();
			});
			Button edit = new Button("Edit");
			edit.getStyleClass().add("lf-again");
			edit.setOnAction(e -> {
				Optional<LoggedFood> changed = LogFeedback.editEntry(this, facade, f, facade.today());
				if (changed.isPresent()) {
					facade.deleteLoggedFood(f.id());
					try {
						facade.restoreLoggedEntry(changed.get());
						toast = "Updated " + f.description() + ".";
						removed = null;
					} catch (RuntimeException ex) {
						facade.restoreLoggedEntry(f);
						toast = "That change could not be saved.";
						removed = null;
					}
					drawHistory();
				}
			});
			row.getChildren().addAll(again, edit, del);
			card.getChildren().add(row);
		}
		return card;
	}

	/**
	 * Opens the Log a meal tab with this entry's recipe or pantry food and meal
	 * already chosen.
	 */
	private void logAgainPrefill(LoggedFood f) {
		String base = f.description().replaceAll(" \\(.*\\)$", "");
		boolean found = false;
		if ("pantry".equals(f.source())) {
			String name = base.replaceAll(
					"^[0-9.,/\u00BD\u00BC\u00BE]+\\s*(g|kg|ml|l|oz|lb|tsp|tbsp|cups?|pcs?|servings?)?\\s*(of\\s+)?",
					"");
			for (PantryItem pi : facade.getPantryItems()) {
				if (pi.getName().equalsIgnoreCase(name)
						|| base.toLowerCase(Locale.ROOT).contains(pi.getName().toLowerCase(Locale.ROOT))) {
					source = Source.PANTRY;
					tab = Tab.LOG;
					meal = f.meal().isEmpty() ? meal : f.meal();
					chooseItem(pi);
					found = true;
					break;
				}
			}
			if (!found) {
				source = Source.PANTRY;
				tab = Tab.LOG;
				pantrySearch.setText(name);
				found = true;
			}
		} else {
			for (Recipe r : facade.getRecipes()) {
				if (f.description().startsWith(r.getTitle())) {
					source = Source.RECIPE;
					tab = Tab.LOG;
					meal = f.meal().isEmpty() ? meal : f.meal();
					chooseRecipe(r);
					found = true;
					break;
				}
			}
			if (!found) {
				source = Source.RECIPE;
				tab = Tab.LOG;
				recipeSearch.setText(base);
				found = true;
			}
		}
		toast = "";
		refreshTags();
		drawAll();
	}
}