package com.mealton.gui;

import java.io.File;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.mealton.facade.MealtonFacade;
import com.mealton.nutrition.Nutrients;
import com.mealton.nutrition.RecipeNutrition;
import com.mealton.pantry.PantryItem;
import com.mealton.plan.MealEntry;
import com.mealton.plan.MealSlot;
import com.mealton.profile.UserProfile;
import com.mealton.recipe.LeftoverFreshness;
import com.mealton.recipe.Recipe;
import com.mealton.recipe.RecipeImporter;
import com.mealton.recipe.RecipeIngredient;
import com.mealton.recipe.RecipeMatch;
import com.mealton.recipe.RecipeParser;
import com.mealton.recipe.RecipePhotos;
import com.mealton.recipe.RecipeTag;
import com.mealton.shopping.ShoppingItem;
import com.mealton.util.Formats;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;

/**
 * The "Recipes" page (Stage 1: RecipeBrowserView), laid out like the design:
 * header with search and "Rank by", pill tabs with counts, a Filters box (tag
 * chips, your own filters, "Suggested filters" switch), a grid of recipe cards,
 * and a detail view.
 * <ul>
 * <li><b>Can make now</b>: library recipes you can cook with what is in the
 * pantry (F07)</li>
 * <li><b>Almost there</b>: recipes missing only a few ingredients, with "Add
 * missing to list" and Undo (F08)</li>
 * <li><b>My Recipes</b>: recipes you made plus the ones you saved with the
 * heart (F13), with "+ New recipe"</li>
 * </ul>
 * The ranking is plain code; the AI only writes the one-line "Why this one?"
 * reason in the detail view.
 */
public class RecipeBrowserView extends BorderPane {
	private enum Tab {
		CAN, ALMOST, ALL, MINE
	}

	private final MealtonFacade facade;
	private final Runnable onDataChanged;

	private Tab tab = Tab.CAN;
	private final Set<RecipeTag> wantedTags = EnumSet.noneOf(RecipeTag.class);
	private final List<String> customFilters = new ArrayList<>();
	private boolean suggestedFilters = true;
	private boolean planOnly = false; // only recipes on this week's meal calendar
	private Map<Long, String> planned = new HashMap<>(); // recipe id -> "Mon dinner, Tue lunch" for this week
	private String filterDraft = "";
	private String filterError = "";
	private final Map<Long, List<Long>> addedToList = new HashMap<>(); // recipe id -> shopping item ids (for Undo)
	private Long openId = null; // recipe shown in the detail view, or null for the list
	private boolean creating = false; // the "+ New recipe" form is open
	private int detailServings = 0; // servings chosen in the detail view (0 = the recipe's own)
	private boolean wholeRecipe = false;
	private boolean useCups = false; // ingredient units: false = g / ml, true = cups
	private final Set<Integer> doneSteps = new HashSet<>(); // method steps ticked off in the detail view
	private Long editingId = null; // recipe being edited in the form, or null for a new one
	private String draftTitle = "";
	private int draftServings = 2;
	private String draftPrep = "";
	private String draftCook = "";
	private String draftTotal = "";
	private String draftRating = "";
	private String draftRatingCount = "";
	private String draftSourceName = "";
	private String draftSourceUrl = "";
	private boolean removeExistingPhoto = false;
	private Path pendingPhoto = null; // picture chosen in the New recipe form
	/**
	 * Macro bars are switched on when the "track macros" setting arrives on the
	 * Profile page.
	 */
	private static final boolean SHOW_MACRO_BARS = false;
	private String whyText = "";
	private String message = "";
	private boolean messageIsError = false;
	private static final int PAGE_SIZE = 30;
	private int page = 0; // 0-based page of the list being shown
	private String listSignature = ""; // changes when the tab, search or filters change (page goes back to 1)
	private boolean generating = false; // Claude is writing new recipes for All recipes
	private boolean triedAutoGenerate = false;
	private boolean importing = false;
	private boolean estimating = false;
	private Runnable undoAction = null; // what the underlined Undo link next to the message does
	private String undoMessage = ""; // the message the Undo belongs to (it disappears with it)

	private final Label subtitle = new Label();
	private final TextField search = new TextField();
	private final ComboBox<String> rank = new ComboBox<>();
	private final HBox tabBar = new HBox(6);
	private final VBox filterBox = new VBox(10);
	private final StackPane body = new StackPane();

	public RecipeBrowserView(MealtonFacade facade, Runnable onDataChanged) {
		this.facade = facade;
		this.onDataChanged = onDataChanged;
		var css = RecipeBrowserView.class.getResource("/recipes.css");
		if (css != null) {
			getStylesheets().add(css.toExternalForm());
		}
		getStyleClass().add("rc-page");
		setPadding(new Insets(32, 40, 20, 40));

		Label title = new Label("Recipes");
		title.getStyleClass().add("rc-title");
		subtitle.getStyleClass().add("rc-sub");
		VBox header = new VBox(2, title, subtitle);

		search.setPromptText("Search recipes");
		search.getStyleClass().add("rc-search");
		search.setPrefWidth(240);
		search.setPrefHeight(44);
		search.textProperty().addListener((obs, o, n) -> showList());
		rank.getItems().addAll("Use up expiring food first", "Best pantry match", "Best macro fit", "Highest rated");
		rank.setPrefHeight(44);
		rank.setValue("Use up expiring food first");
		rank.setOnAction(e -> showList());
		Label rankLabel = new Label("Rank by");
		rankLabel.getStyleClass().add("rc-text");
		HBox tools = new HBox(10, search, rankLabel, rank);
		tools.setAlignment(Pos.CENTER_LEFT);

		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox top = new HBox(header, spacer, tools);
		top.setAlignment(Pos.CENTER_LEFT);

		tabBar.setAlignment(Pos.CENTER_LEFT);
		tabBar.getStyleClass().add("rc-tabs");
		filterBox.getStyleClass().add("rc-filters");
		VBox head = new VBox(20, top, tabBar); // the filters live inside the scrolling list, so they scroll away with
												// it
		head.setPadding(new Insets(0, 0, 14, 0));
		setTop(head);
		setCenter(body);
		refresh();
	}

	/**
	 * Reloads the page (call this whenever the pantry, recipes or profile may have
	 * changed).
	 */
	public void refresh() {
		updateSubtitle();
		if (creating) {
			// keep what is being typed: the form is not rebuilt by a refresh
		} else if (openId != null) {
			showDetail();
		} else {
			showList();
		}
	}

	private void updateSubtitle() {
		int pantry = facade.getPantryItems().size();
		int expiring = facade.getExpiringItems().size();
		subtitle.setText("Based on " + pantry + (pantry == 1 ? " item" : " items") + " in your pantry · " + expiring
				+ " expiring soon");
	}

	/**
	 * Detail and New recipe pages keep the tabs (clicking one goes back to that
	 * list) but hide the filters.
	 */
	private void hideListChrome() {
		filterBox.getChildren().clear();
		filterBox.setVisible(false);
		filterBox.setManaged(false);
		showTabs();
	}

	private void showTabs() {
		buildTabs(load(Tab.CAN).size(), load(Tab.ALMOST).size(), load(Tab.ALL).size(), load(Tab.MINE).size());
	}

	private String shownKind = "";

	/**
	 * Shows a page in the body. Rebuilding the same page (after a click) keeps the
	 * scroll position.
	 */
	private void present(String kind, ScrollPane scroll) {
		double keepPixels = -1;
		if (kind.equals(shownKind) && !body.getChildren().isEmpty()
				&& body.getChildren().get(0) instanceof ScrollPane old && old.getContent() != null) {
			double range = old.getContent().getLayoutBounds().getHeight() - old.getViewportBounds().getHeight();
			keepPixels = range > 0 ? old.getVvalue() * range : 0;
		}
		shownKind = kind;
		body.getChildren().setAll(scroll);
		if (keepPixels > 0) {
			final double px = keepPixels;
			Platform.runLater(() -> Platform.runLater(() -> {
				double range = scroll.getContent().getLayoutBounds().getHeight()
						- scroll.getViewportBounds().getHeight();
				if (range > 0) {
					scroll.setVvalue(Math.min(1.0, px / range));
				}
			}));
		}
	}

	private static ScrollPane scroller(Node content) {
		ScrollPane scroll = new ScrollPane(content);
		scroll.setFitToWidth(true);
		scroll.getStyleClass().add("rc-scroll");
		return scroll;
	}

	// ------------------------------------------------------------------ list

	private static LocalDate weekStart() {
		return LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
	}

	private static String weekLabel() {
		LocalDate start = weekStart();
		LocalDate end = start.plusDays(6);
		DateTimeFormatter month = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
		return start.format(month) + "–"
				+ (start.getMonth() == end.getMonth() ? String.valueOf(end.getDayOfMonth()) : end.format(month));
	}

	/** Which recipes are on this week's meal calendar, and when. */
	private void refreshPlanned() {
		Map<Long, String> found = new HashMap<>();
		LocalDate start = weekStart();
		for (int i = 0; i < 7; i++) {
			LocalDate day = start.plusDays(i);
			for (MealEntry entry : facade.getCalendarMeals(day)) {
				String when = day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " "
						+ entry.slot().label().toLowerCase(Locale.ROOT);
				found.merge(entry.recipe().getId(), when, (a, b) -> a + ", " + b);
			}
		}
		planned = found;
	}

	private List<RecipeMatch> load(Tab which) {
		refreshPlanned();
		List<RecipeMatch> matches;
		String priority = "Best pantry match".equals(rank.getValue()) ? "best" : "expiring";
		switch (which) {
		case CAN -> matches = facade.canMakeNow(priority, EnumSet.copyOf(wantedTags));
		case ALMOST -> matches = facade.almostThere(EnumSet.copyOf(wantedTags));
		case ALL -> {
			List<RecipeMatch> all = new ArrayList<>();
			for (Recipe r : facade.getRecipes()) {
				try {
					all.add(facade.checkRecipe(r.getId()));
				} catch (IllegalArgumentException gone) {
					// deleted meanwhile
				}
			}
			matches = RecipeTag.filter(all, wantedTags);
		}
		default -> {
			List<RecipeMatch> all = new ArrayList<>();
			for (Recipe r : facade.getMyRecipes()) {
				try {
					all.add(facade.checkRecipe(r.getId()));
				} catch (IllegalArgumentException gone) {
					// deleted meanwhile
				}
			}
			matches = RecipeTag.filter(all, wantedTags);
		}
		}
		List<RecipeMatch> result = new ArrayList<>(matches);
		String q = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
		if (!q.isEmpty()) {
			result.removeIf(m -> !textMatches(m, q));
		}
		for (String f : customFilters) {
			result.removeIf(m -> !textMatches(m, f.toLowerCase(Locale.ROOT)));
		}
		if (planOnly) {
			result.removeIf(m -> !planned.containsKey(m.recipe().getId()));
		}
		if ("Highest rated".equals(rank.getValue())) {
			// your own stars first; recipes you have not rated use the public rating from
			// their website
			result.sort(Comparator.comparingDouble((RecipeMatch m) -> -ratingScore(m.recipe()))
					.thenComparing(m -> m.recipe().getTitle(), String.CASE_INSENSITIVE_ORDER));
		}
		if ("Best macro fit".equals(rank.getValue())) {
			result.sort(Comparator
					.comparingDouble((RecipeMatch m) -> -RecipeNutrition.of(m.recipe()).perServing().protein()));
		}
		return result;
	}

	private static double ratingScore(Recipe r) {
		return r.isRated() ? r.getRating() : r.getPublicRating();
	}

	/** "4.6 (1,203)" for the rating other people gave a recipe on its website. */
	private static String publicRatingText(Recipe r) {
		String text = Formats.number(Math.round(r.getPublicRating() * 10) / 10.0);
		return r.getPublicRatingCount() > 0
				? text + " (" + String.format(Locale.ROOT, "%,d", r.getPublicRatingCount()) + ")"
				: text;
	}

	private static boolean textMatches(RecipeMatch m, String q) {
		if (m.recipe().getTitle().toLowerCase(Locale.ROOT).contains(q)) {
			return true;
		}
		for (RecipeIngredient i : m.recipe().getIngredients()) {
			if (i.name().toLowerCase(Locale.ROOT).contains(q)) {
				return true;
			}
		}
		for (RecipeTag t : RecipeTag.tagsOf(m)) {
			if (t.label().toLowerCase(Locale.ROOT).contains(q)) {
				return true;
			}
		}
		for (String t : m.recipe().getAiTags()) {
			if (t.contains(q)) {
				return true;
			}
		}
		return false;
	}

	private void showList() {
		creating = false;
		openId = null;
		List<RecipeMatch> can = load(Tab.CAN);
		List<RecipeMatch> almost = load(Tab.ALMOST);
		List<RecipeMatch> mine = load(Tab.MINE);
		List<RecipeMatch> every = load(Tab.ALL);

		buildTabs(can.size(), almost.size(), every.size(), mine.size());
		buildFilters();

		List<RecipeMatch> shown = tab == Tab.CAN ? can : tab == Tab.ALMOST ? almost : tab == Tab.ALL ? every : mine;
		String signature = tab + "|" + search.getText() + "|" + wantedTags + "|" + customFilters + "|" + planOnly + "|"
				+ rank.getValue();
		if (!signature.equals(listSignature)) {
			listSignature = signature;
			page = 0;
		}
		int pageCount = Math.max(1, (shown.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		page = Math.max(0, Math.min(page, pageCount - 1));
		FlowPane grid = new FlowPane(18, 18);
		grid.setPadding(new Insets(0, 0, 16, 0));
		for (RecipeMatch m : shown.subList(page * PAGE_SIZE, Math.min(shown.size(), (page + 1) * PAGE_SIZE))) {
			grid.getChildren().add(card(m));
		}
		VBox content = new VBox(20);
		content.setPadding(new Insets(0, 4, 0, 0));
		content.getChildren().add(filterBox);
		if (tab == Tab.MINE) {
			Label hint = new Label("Write your own recipe. Mealton tags it automatically.");
			hint.getStyleClass().add("rc-text");
			hint.setWrapText(true);
			hint.setMinHeight(Region.USE_PREF_SIZE);
			Region sp = new Region();
			HBox.setHgrow(sp, Priority.ALWAYS);
			Button add = new Button("+ New recipe");
			add.getStyleClass().add("primary");
			add.setOnAction(e -> startNewRecipe());
			HBox banner = new HBox(10, hint, sp, add);
			banner.setAlignment(Pos.CENTER_LEFT);
			banner.getStyleClass().add("rc-banner");
			content.getChildren().add(banner);
		}
		if (!message.isEmpty()) {
			content.getChildren().add(messageLabel());
		}
		if (generating && tab == Tab.ALL) {
			Label busy = new Label("Claude is writing new recipes… this can take a minute.");
			busy.getStyleClass().add("rc-ok");
			content.getChildren().add(busy);
		}
		if (shown.isEmpty()) {
			Label empty = new Label(emptyText());
			empty.setWrapText(true);
			empty.setMinHeight(Region.USE_PREF_SIZE);
			empty.getStyleClass().add("rc-empty");
			content.getChildren().add(empty);
		} else {
			content.getChildren().add(grid);
			if (pageCount > 1) {
				content.getChildren().add(pager(pageCount, shown.size()));
			}
		}
		Label foot = new Label(tab == Tab.ALL && facade.canGenerateRecipes()
				? "New recipes on this tab are written by Claude. Press Refresh for a different set. Save the ones you like with the heart."
				: "Recipes are worked out from your pantry. Add your own photo to your own recipes.");
		foot.getStyleClass().add("rc-meta");
		foot.setWrapText(true);
		foot.setMinHeight(Region.USE_PREF_SIZE);
		content.getChildren().add(foot);
		ScrollPane scroll = scroller(content);
		// like the design's auto-fill grid: cards at least 260 wide share the row
		// equally
		scroll.viewportBoundsProperty().addListener((obs, o, bounds) -> sizeCards(grid, bounds.getWidth() - 8));
		sizeCards(grid, Math.max(300, body.getWidth() - 8));
		present("list" + tab, scroll);
		maybeAutoGenerate();
	}

	/** Page switch under the grid: ‹ 1 2 3 › */
	private Node pager(int pageCount, int total) {
		HBox bar = new HBox(3);
		bar.getStyleClass().add("rc-seg-bar");
		Button prev = new Button("‹");
		prev.getStyleClass().add("rc-seg");
		prev.setDisable(page == 0);
		prev.setOnAction(e -> {
			page--;
			showList();
		});
		bar.getChildren().add(prev);
		for (int i = 0; i < pageCount; i++) {
			final int target = i;
			Button b = new Button(String.valueOf(i + 1));
			b.getStyleClass().add("rc-seg");
			if (i == page) {
				b.getStyleClass().add("rc-seg-on");
			}
			b.setOnAction(e -> {
				page = target;
				showList();
			});
			bar.getChildren().add(b);
		}
		Button next = new Button("›");
		next.getStyleClass().add("rc-seg");
		next.setDisable(page >= pageCount - 1);
		next.setOnAction(e -> {
			page++;
			showList();
		});
		bar.getChildren().add(next);
		Label info = new Label(
				"Showing " + (page * PAGE_SIZE + 1) + "–" + Math.min(total, (page + 1) * PAGE_SIZE) + " of " + total);
		info.getStyleClass().add("rc-meta");
		HBox row = new HBox(14, bar, info);
		row.setAlignment(Pos.CENTER_LEFT);
		row.setPadding(new Insets(0, 0, 8, 0));
		return row;
	}

	/**
	 * The first time All recipes is opened with Claude on, Claude writes a set of
	 * recipes.
	 */
	private void maybeAutoGenerate() {
		if (tab == Tab.ALL && !triedAutoGenerate && !generating && facade.canGenerateRecipes()) {
			triedAutoGenerate = true;
			boolean anyGenerated = facade.getRecipes().stream().anyMatch(Recipe::isDiscovered);
			if (!anyGenerated) {
				generateRecipes();
			}
		}
	}

	/**
	 * Claude writes up to 30 new recipes in the background; they replace the
	 * generated ones you did not keep.
	 */
	private void generateRecipes() {
		if (generating) {
			return;
		}
		generating = true;
		List<String> hintParts = new ArrayList<>(customFilters);
		wantedTags.forEach(t -> hintParts.add(t.label()));
		String hint = String.join(", ", hintParts);
		List<String> avoid = facade.recipeTitles();
		Task<List<Recipe>> task = new Task<>() {
			@Override
			protected List<Recipe> call() {
				return facade.generateRecipeDrafts(PAGE_SIZE, hint, avoid);
			}
		};
		task.setOnSucceeded(e -> {
			generating = false;
			int added = facade.swapInDiscoveredRecipes(task.getValue());
			page = 0;
			setMessageOnly(added == 0 ? "Claude did not return any new recipes this time, so the current ones stay."
					: "Claude wrote " + added + " new recipes.", added == 0);
			refresh();
		});
		task.setOnFailed(e -> {
			generating = false;
			setMessageOnly("Could not generate recipes: " + task.getException().getMessage(), true);
			refresh();
		});
		Thread thread = new Thread(task, "recipe-discover");
		thread.setDaemon(true);
		thread.start();
		if (openId == null && !creating) {
			showList();
		}
	}

	private static void sizeCards(FlowPane grid, double width) {
		double gap = grid.getHgap();
		int cols = Math.max(1, (int) Math.floor((width + gap) / (260 + gap)));
		double w = Math.max(240, (width - gap * (cols - 1)) / cols - 1);
		for (Node c : grid.getChildren()) {
			if (c instanceof Region r) {
				r.setPrefWidth(w);
				r.setMinWidth(w);
				r.setMaxWidth(w);
			}
		}
	}

	private int activeFilterCount() {
		return wantedTags.size() + customFilters.size() + (planOnly ? 1 : 0);
	}

	private String emptyText() {
		if (!search.getText().isBlank() || activeFilterCount() > 0) {
			return "No recipes in this tab match your search or filters. Use Clear filters to see more.";
		}
		return switch (tab) {
		case CAN -> facade.getPantryItems().isEmpty()
				? "Your pantry is empty. Add food on the 'Pantry & list' page and recipes you can make will show up here."
				: "Nothing can be cooked with your pantry right now. Check 'Almost there' for recipes that need a few more ingredients.";
		case ALMOST -> "No recipes are within " + MealtonFacade.ALMOST_THERE_LIMIT
				+ " ingredients of being cookable yet. Add more food to your pantry to see some here.";
		case ALL -> "There are no recipes yet.";
		case MINE ->
			"My Recipes is empty. Save recipes you like with the heart, or press '+ New recipe' to write your own.";
		};
	}

	private void buildTabs(int can, int almost, int all, int mine) {
		tabBar.getChildren().setAll(tabButton("Can make now", can, Tab.CAN),
				tabButton("Almost there", almost, Tab.ALMOST), tabButton("All recipes", all, Tab.ALL),
				tabButton("My Recipes", mine, Tab.MINE));
		Region fill = new Region();
		HBox.setHgrow(fill, Priority.ALWAYS);
		Button reload = new Button("↻ Refresh");
		reload.getStyleClass().add("rc-pill-btn");
		reload.setOnAction(e -> {
			message = "";
			addedToList.clear();
			if (tab == Tab.ALL && facade.canGenerateRecipes()) {
				generateRecipes();
				return;
			}
			if (tab == Tab.ALL) {
				setMessageOnly("Claude is off, so All recipes only shows the built-in recipes. "
						+ "Add an API key to .env to have Claude write new ones.", false);
			}
			refresh();
		});
		tabBar.getChildren().addAll(fill, reload);
	}

	private Button tabButton(String text, int count, Tab which) {
		boolean on = tab == which;
		Button b = new Button(text);
		b.getStyleClass().add("rc-tab");
		if (on) {
			b.getStyleClass().add("rc-tab-on");
		}
		Label badge = new Label(String.valueOf(count));
		badge.getStyleClass().add(on ? "rc-count-on" : "rc-count");
		b.setGraphic(badge);
		b.setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
		b.setGraphicTextGap(8);
		b.setOnAction(e -> {
			tab = which;
			message = "";
			creating = false;
			openId = null;
			showList();
		});
		return b;
	}

	/** A pill chip: solid green when on, dashed outline when off. */
	private Button chip(String text, boolean on, Runnable action) {
		Button chip = new Button(text.replace('-', ' ')); // filters read "high protein", not "high-protein"
		chip.getStyleClass().add(on ? "rc-chip-on" : "rc-chip");
		chip.setOnAction(e -> action.run());
		return chip;
	}

	private void buildFilters() {
		filterBox.setVisible(true);
		filterBox.setManaged(true);
		Label heading = new Label("Filters");
		heading.getStyleClass().add("rc-h3");
		Label headCount = new Label(String.valueOf(activeFilterCount()));
		headCount.getStyleClass().add("rc-meta");
		HBox headLeft = new HBox(6, heading, headCount);
		headLeft.setAlignment(Pos.CENTER_LEFT);
		Region sp = new Region();
		HBox.setHgrow(sp, Priority.ALWAYS);
		Button clear = new Button("Clear filters");
		clear.getStyleClass().add("rc-link-bold");
		clear.setOnAction(e -> {
			wantedTags.clear();
			customFilters.clear();
			planOnly = false;
			filterError = "";
			showList();
		});

		// the "Suggested filters" switch
		Region knob = new Region();
		knob.getStyleClass().add("rc-knob");
		knob.setMinSize(20, 20);
		knob.setMaxSize(20, 20);
		StackPane track = new StackPane(knob);
		track.getStyleClass().add(suggestedFilters ? "rc-switch-on" : "rc-switch");
		track.setMinSize(44, 26);
		track.setMaxSize(44, 26);
		StackPane.setAlignment(knob, suggestedFilters ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
		StackPane.setMargin(knob, new Insets(0, 3, 0, 3));
		Label swLabel = new Label("Suggested filters");
		swLabel.getStyleClass().add("rc-h3");
		HBox sw = new HBox(10, swLabel, track);
		sw.setAlignment(Pos.CENTER_LEFT);
		sw.setStyle("-fx-cursor: hand;");
		sw.setOnMouseClicked(e -> {
			suggestedFilters = !suggestedFilters;
			if (!suggestedFilters) {
				wantedTags.clear();
			}
			showList();
		});
		HBox headRow = new HBox(8, headLeft, sp, clear, sw);
		headRow.setAlignment(Pos.CENTER_LEFT);

		FlowPane chips = new FlowPane(8, 8);
		chips.setAlignment(Pos.CENTER_LEFT);
		if (!planned.isEmpty() || planOnly) {
			Button plan = new Button("Meal plan · " + weekLabel());
			plan.getStyleClass().add(planOnly ? "rc-plan-on" : "rc-plan-off");
			plan.setOnAction(e -> {
				planOnly = !planOnly;
				showList();
			});
			Region divider = new Region();
			divider.getStyleClass().add("rc-divider");
			divider.setMinSize(1, 24);
			divider.setPrefSize(1, 24);
			chips.getChildren().addAll(plan, divider);
		}
		List<String> aiChips = suggestedFilters ? facade.aiFilterSuggestions(12) : List.of();
		if (!aiChips.isEmpty()) {
			// filters written by Claude: the tags it gave the recipes it wrote; click to
			// turn one on or off
			for (String word : aiChips) {
				boolean on = customFilters.stream().anyMatch(f -> f.equalsIgnoreCase(word));
				chips.getChildren().add(chip(capitalise(word), on, () -> {
					if (!customFilters.removeIf(f -> f.equalsIgnoreCase(word))) {
						customFilters.add(word);
					}
					showList();
				}));
			}
		} else if (suggestedFilters) {
			for (RecipeTag t : RecipeTag.values()) {
				chips.getChildren().add(chip(t.label(), wantedTags.contains(t), () -> {
					if (!wantedTags.remove(t)) {
						wantedTags.add(t);
					}
					showList();
				}));
			}
		}
		for (String f : customFilters) {
			if (aiChips.stream().anyMatch(a -> a.equalsIgnoreCase(f))) {
				continue; // already shown as one of Claude's chips
			}
			// your own filter: a solid chip; click it to remove it
			chips.getChildren().add(chip(f, true, () -> {
				customFilters.remove(f);
				showList();
			}));
		}
		TextField draft = new TextField(filterDraft);
		draft.setPromptText("New filter, e.g. spicy");
		draft.getStyleClass().add("rc-pill-input");
		draft.setPrefWidth(170);
		draft.textProperty().addListener((obs, o, n) -> filterDraft = n);
		Button add = new Button("+ Add");
		add.getStyleClass().add("rc-pill-btn");
		Runnable doAdd = () -> {
			String f = draft.getText() == null ? "" : draft.getText().trim();
			if (f.isEmpty()) {
				filterError = "Type a word to filter by first.";
			} else if (customFilters.stream().anyMatch(x -> x.equalsIgnoreCase(f))) {
				filterError = "You already have that filter.";
			} else {
				customFilters.add(f);
				filterDraft = "";
				filterError = "";
			}
			showList();
		};
		add.setOnAction(e -> doAdd.run());
		draft.setOnAction(e -> doAdd.run());
		HBox addRow = new HBox(6, draft, add);
		addRow.setAlignment(Pos.CENTER_LEFT);
		chips.getChildren().add(addRow);

		filterBox.getChildren().setAll(headRow, chips);
		if (!filterError.isEmpty()) {
			Label err = new Label(filterError);
			err.getStyleClass().add("rc-err");
			filterBox.getChildren().add(err);
		}
		Label hint = new Label(suggestedFilters
				? "Dashed chips are Mealton's suggestions; solid ones are yours. Your filters match recipe names, tags and ingredients."
				: "Suggested filters are hidden. Your filters match recipe names, tags and ingredients.");
		hint.getStyleClass().add("rc-meta");
		hint.setWrapText(true);
		hint.setMinHeight(Region.USE_PREF_SIZE);
		filterBox.getChildren().add(hint);
	}

	/** First expiring pantry item that this recipe uses, or null. */
	private PantryItem expiringUsedBy(Recipe r) {
		PantryItem best = null;
		for (PantryItem p : facade.getExpiringItems()) {
			String pn = p.getName().toLowerCase(Locale.ROOT);
			for (RecipeIngredient i : r.getIngredients()) {
				String in = i.name().toLowerCase(Locale.ROOT);
				if (in.equals(pn) || in.contains(pn) || pn.contains(in)) {
					if (best == null || p.daysLeft() < best.daysLeft()) {
						best = p;
					}
				}
			}
		}
		return best;
	}

	private static String capitalise(String s) {
		return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
	}

	private static String daysText(int days) {
		return days <= 0 ? "expires today" : days == 1 ? "1 day left" : days + " days left";
	}

	/**
	 * A plain one-line reason shown on each card (the AI reason is on the detail
	 * page).
	 */
	private String cardWhy(RecipeMatch m, PantryItem expiring) {
		if (expiring != null) {
			return "Uses your " + expiring.getName().toLowerCase(Locale.ROOT) + " before it spoils.";
		}
		int total = m.available().size() + m.missing().size();
		return m.isCookable() ? "You have everything you need."
				: "You have " + m.available().size() + " of " + total + " ingredients.";
	}

	private static final String GREEN = "#4F6B52";

	private Node leafIcon() {
		javafx.scene.shape.SVGPath leaf = new javafx.scene.shape.SVGPath();
		leaf.setContent("M11 20A7 7 0 0 1 9.8 6.1C15.5 5 17 4.48 19 2c1 2 2 4.18 2 8 0 5.5-4.78 10-10 10Z");
		leaf.setFill(javafx.scene.paint.Color.TRANSPARENT);
		leaf.setStroke(javafx.scene.paint.Color.web(GREEN));
		leaf.setStrokeWidth(2);
		leaf.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
		leaf.setScaleX(0.58);
		leaf.setScaleY(0.58);
		javafx.scene.Group g = new javafx.scene.Group(leaf);
		StackPane box = new StackPane(g);
		box.setMinSize(14, 14);
		box.setMaxSize(14, 14);
		return box;
	}

	/**
	 * Puts the recipe's own photo (if it has one) into a box, cropped to fill it;
	 * hides the placeholder letter.
	 */
	private void addPhoto(StackPane box, Recipe recipe, Node placeholder, double height) {
		Optional<Path> file = facade.getRecipePhoto(recipe.getId());
		if (file.isEmpty()) {
			return;
		}
		Image img = new Image(file.get().toUri().toString(), 900, 0, true, true);
		ImageView view = new ImageView(img);
		view.setPreserveRatio(false);
		view.setFitHeight(height);
		view.setManaged(false); // the picture must never make the card wider; it is sized to the box below
		Runnable fit = () -> {
			double w = box.getWidth();
			if (w <= 0 || img.getWidth() <= 0) {
				return;
			}
			double scale = Math.max(w / img.getWidth(), height / img.getHeight());
			double vw = w / scale;
			double vh = height / scale;
			view.setViewport(
					new javafx.geometry.Rectangle2D((img.getWidth() - vw) / 2, (img.getHeight() - vh) / 2, vw, vh));
			view.setFitWidth(w);
			view.relocate(0, 0);
		};
		box.widthProperty().addListener((obs, o, n) -> fit.run());
		img.progressProperty().addListener((obs, o, n) -> fit.run());
		fit.run();
		Rectangle clip = new Rectangle();
		clip.setArcWidth(32);
		clip.setArcHeight(32);
		clip.widthProperty().bind(box.widthProperty());
		clip.heightProperty().bind(box.heightProperty().add(24));
		box.setClip(clip);
		placeholder.setVisible(false);
		placeholder.setManaged(false);
		box.getChildren().add(0, view);
	}

	private Node card(RecipeMatch m) {
		Recipe r = m.recipe();
		long id = r.getId();
		PantryItem expiring = expiringUsedBy(r);

		// top band: first letter, expiring badge, heart
		String letter = r.getTitle().isEmpty() ? "?" : r.getTitle().substring(0, 1).toUpperCase(Locale.ROOT);
		Label big = new Label(letter);
		big.getStyleClass().add("rc-card-letter");
		StackPane top = new StackPane(big);
		top.getStyleClass().add("rc-card-top");
		top.setMinHeight(170);
		top.setPrefHeight(170);
		top.setMaxHeight(170);
		addPhoto(top, r, big, 170);
		if (expiring != null) {
			Label badge = new Label(capitalise(expiring.getName()) + " · " + daysText(expiring.daysLeft()));
			badge.getStyleClass().add("rc-expiry");
			StackPane.setAlignment(badge, Pos.TOP_LEFT);
			StackPane.setMargin(badge, new Insets(10, 0, 0, 10));
			top.getChildren().add(badge);
		}
		boolean saved = r.isInMyRecipes();
		Button heart = new Button(saved ? "♥" : "♡");
		heart.getStyleClass().add("rc-heart-card");
		if (r.isLibrary()) {
			heart.setOnAction(e -> attempt(() -> {
				if (saved) {
					facade.removeFromMyRecipes(id);
				} else {
					facade.saveToMyRecipes(id);
				}
				refresh();
			}));
		} else {
			heart.setDisable(true);
		}
		StackPane.setAlignment(heart, Pos.TOP_RIGHT);
		StackPane.setMargin(heart, new Insets(8, 8, 0, 0));
		top.getChildren().add(heart);

		// title and stars
		Label name = new Label(r.getTitle());
		name.getStyleClass().add("rc-card-title");
		name.setWrapText(true);
		name.setMinHeight(Region.USE_PREF_SIZE);
		name.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(name, Priority.ALWAYS);
		HBox titleRow = new HBox(8, name);
		if (r.isRated() || r.getPublicRating() > 0) {
			// your own stars stay blank until you rate it; the public rating sits next to
			// them
			Label stars = new Label(starText(r.isRated() ? r.getRating() : 0));
			stars.getStyleClass().add("rc-stars");
			stars.setMinWidth(Region.USE_PREF_SIZE); // the stars keep their full width; the title wraps instead
			titleRow.getChildren().add(stars);
		}

		// meta: kcal, ingredients you have
		RecipeNutrition n = RecipeNutrition.of(r);
		int total = m.available().size() + m.missing().size();
		Label kcal = new Label(n.counted().isEmpty() || n.total().calories() <= 0 ? r.getServings() + " servings"
				: Math.round(n.perServing().calories()) + " kcal");
		Label have = new Label(m.available().size() + "/" + total + " ingredients");
		kcal.getStyleClass().add("rc-meta13");
		have.getStyleClass().add("rc-meta13");
		FlowPane meta = new FlowPane(12, 4);
		if (totalMinutes(r) > 0) {
			Label time = new Label("⏱ " + totalTimeText(r));
			time.getStyleClass().add("rc-meta13");
			meta.getChildren().add(time);
		}
		if (r.getPublicRating() > 0) {
			Label pub = new Label("★ " + publicRatingText(r) + " public");
			pub.getStyleClass().add("rc-meta13");
			meta.getChildren().add(pub);
		}
		meta.getChildren().addAll(kcal, have);
		Label planLine = null;
		if (planned.containsKey(id)) {
			planLine = new Label("On your plan · " + planned.get(id));
			planLine.getStyleClass().add("rc-plan-chip");
			planLine.setWrapText(true);
			planLine.setMinHeight(Region.USE_PREF_SIZE);
		}

		// match bar
		double frac = total == 0 ? 0 : (double) m.available().size() / total;
		Region fill = new Region();
		fill.getStyleClass().add("rc-bar-fill");
		fill.setMinHeight(6);
		fill.setPrefHeight(6);
		Pane track = new Pane(fill);
		track.getStyleClass().add("rc-bar-track");
		track.setMinHeight(6);
		track.setPrefHeight(6);
		track.setMaxHeight(6);
		fill.prefWidthProperty().bind(track.widthProperty().multiply(frac));
		fill.maxWidthProperty().bind(track.widthProperty().multiply(frac));

		Label why = new Label(cardWhy(m, expiring));
		why.getStyleClass().add("rc-why-line");
		why.setWrapText(true);
		why.setMinHeight(Region.USE_PREF_SIZE);
		HBox.setHgrow(why, Priority.ALWAYS);
		HBox whyRow = new HBox(6, leafIcon(), why);
		whyRow.setAlignment(Pos.TOP_LEFT);

		VBox info = new VBox(10, titleRow, meta);
		if (planLine != null) {
			info.getChildren().add(planLine);
		}
		info.getChildren().addAll(track, whyRow);

		// missing box + add-to-list + undo
		if (!m.missing().isEmpty()) {
			VBox missing = new VBox(6);
			missing.getStyleClass().add("rc-missing-box");
			Label ml = new Label("MISSING");
			ml.getStyleClass().add("rc-missing-title");
			missing.getChildren().add(ml);
			for (RecipeIngredient i : m.missing()) {
				Region dot = new Region();
				dot.getStyleClass().add("rc-dot");
				dot.setMinSize(6, 6);
				dot.setMaxSize(6, 6);
				Label c = new Label(i.name());
				c.getStyleClass().add("rc-meta13");
				HBox row = new HBox(8, dot, c);
				row.setAlignment(Pos.CENTER_LEFT);
				missing.getChildren().add(row);
			}
			info.getChildren().add(missing);
			List<Long> added = addedToList.get(id);
			if (added == null) {
				Button add = new Button("Add missing to list");
				add.getStyleClass().add("rc-solid");
				add.setMaxWidth(Double.MAX_VALUE);
				add.setOnAction(e -> attempt(() -> addMissing(id)));
				info.getChildren().add(add);
			} else {
				Label done = new Label(
						added.isEmpty() ? "Already on your list" : "Added " + added.size() + " to your list");
				done.getStyleClass().add("rc-added-text");
				Region gap = new Region();
				HBox.setHgrow(gap, Priority.ALWAYS);
				Button undo = new Button("Undo");
				undo.getStyleClass().add("rc-undo");
				undo.setOnAction(e -> attempt(() -> {
					if (!added.isEmpty()) {
						facade.deleteShoppingItems(added);
						onDataChanged.run();
					}
					addedToList.remove(id);
					refresh();
				}));
				HBox row = new HBox(8, done, gap, undo);
				row.setAlignment(Pos.CENTER_LEFT);
				row.getStyleClass().add("rc-added-bar");
				info.getChildren().add(row);
			}
		}

		FlowPane tags = new FlowPane(6, 6);
		for (RecipeTag t : RecipeTag.tagsOf(m)) {
			Label tl = new Label(t.label().replace('-', ' '));
			tl.getStyleClass().add("rc-tag");
			tags.getChildren().add(tl);
		}
		if (!tags.getChildren().isEmpty()) {
			info.getChildren().add(tags);
		}
		if (!m.warnings().isEmpty()) {
			Label warn = new Label("⚠ " + m.warnings().get(0));
			warn.getStyleClass().add("rc-need");
			warn.setWrapText(true);
			warn.setMinHeight(Region.USE_PREF_SIZE);
			info.getChildren().add(warn);
		}
		Region grow = new Region();
		VBox.setVgrow(grow, Priority.ALWAYS);
		Label foot = new Label(r.getSourceName() != null ? "From " + r.getSourceName()
				: r.isLibrary() ? "Mealton recipe" : "Your recipe");
		foot.getStyleClass().add("rc-meta");
		foot.setWrapText(false);
		Region footGap = new Region();
		HBox.setHgrow(footGap, Priority.ALWAYS);
		HBox footBox = new HBox(8, foot, footGap);
		footBox.setAlignment(Pos.CENTER_LEFT);
		if (r.getSourceUrl() != null) {
			Button original = new Button("View original ↗");
			original.getStyleClass().add("rc-link");
			original.setOnAction(e -> openLink(r.getSourceUrl()));
			footBox.getChildren().add(original);
		} else if (!r.isLibrary()) {
			Button edit = new Button("✎ Edit");
			edit.getStyleClass().add("rc-link");
			edit.setOnAction(e -> startEditing(r));
			footBox.getChildren().add(edit);
		}
		footBox.getStyleClass().add("rc-card-foot");
		info.getChildren().addAll(grow, footBox);
		info.setPadding(new Insets(16));
		VBox.setVgrow(info, Priority.ALWAYS);

		VBox card = new VBox(top, info);
		card.getStyleClass().add("rc-card");
		card.setOnMouseClicked(e -> {
			message = "";
			whyText = "";
			detailServings = 0;
			doneSteps.clear();
			openId = id;
			showDetail();
		});
		return card;
	}

	/**
	 * Puts the missing ingredients on the shopping list and remembers which rows
	 * were new, for Undo.
	 */
	private void addMissing(long id) {
		Set<Long> before = facade.getShoppingItems().stream().map(ShoppingItem::getId).collect(Collectors.toSet());
		facade.addMissingToShoppingList(id);
		List<Long> fresh = facade.getShoppingItems().stream().map(ShoppingItem::getId).filter(x -> !before.contains(x))
				.collect(Collectors.toList());
		addedToList.put(id, fresh);
		onDataChanged.run();
		refresh();
	}

	/**
	 * Opens a web link in the computer's browser (in the background, so the window
	 * never freezes).
	 */
	private void openLink(String url) {
		Thread t = new Thread(() -> {
			try {
				java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
			} catch (Exception | Error e) {
				Platform.runLater(() -> setMessage("The link could not be opened: " + url, true));
			}
		}, "open-link");
		t.setDaemon(true);
		t.start();
	}

	private static String starText(int rating) {
		return "★".repeat(rating) + "☆".repeat(5 - rating);
	}

	// ---------------------------------------------------------------- detail

	private static final Pattern TIME = Pattern.compile(
			"(\\d+(?:\\s*[-–]\\s*\\d+)?)\\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?)", Pattern.CASE_INSENSITIVE);

	/**
	 * Minutes mentioned in a step ("2 minutes", "12-15 min", "1 hour"), or 0. A
	 * range counts its larger number.
	 */
	private static int minutesIn(String step) {
		Matcher m = TIME.matcher(step);
		int total = 0;
		while (m.find()) {
			String[] parts = m.group(1).split("\\s*[-–]\\s*");
			int value = Integer.parseInt(parts[parts.length - 1].trim());
			String unit = m.group(2).toLowerCase(Locale.ROOT);
			if (unit.startsWith("h")) {
				total += value * 60;
			} else if (unit.startsWith("s")) {
				total += Math.max(1, value / 60);
			} else {
				total += value;
			}
		}
		return total;
	}

	private static final Pattern WAITING = Pattern
			.compile("(?i)\\b(chill|refrigerat|rest|marinat|rise|proof|freez|soak)");

	/**
	 * Estimated total time in minutes: prep + cook, plus the waiting steps
	 * (chilling, resting, marinating...) whose time is written in the method. With
	 * no prep or cook time it adds up every time mentioned in the steps.
	 */
	private static int totalMinutes(Recipe r) {
		if (r.getTotalOverride() > 0) {
			return r.getTotalOverride();
		}
		int active = r.getPrepMinutes() + r.getCookMinutes();
		int stepsTotal = 0;
		int waiting = 0;
		for (String step : r.getSteps()) {
			int m = minutesIn(step);
			stepsTotal += m;
			if (WAITING.matcher(step).find()) {
				waiting += m;
			}
		}
		return active > 0 ? active + waiting : stepsTotal;
	}

	/**
	 * "about 2 h 25 min", marked as an estimate when waiting steps or step times
	 * were added in.
	 */
	private static String totalTimeText(Recipe r) {
		int total = totalMinutes(r);
		boolean exact = r.getTotalOverride() > 0 || r.getPrepMinutes() + r.getCookMinutes() == total;
		return (exact ? "" : "about ") + timeText(total);
	}

	/**
	 * "Leftovers keep about 3 days" with Mealton's reason. Recipes you made or
	 * imported can be changed here (type a number, or Reset to go back to the
	 * suggestion); built-in recipes only show the estimate.
	 */
	private Node freshnessRow(Recipe recipe) {
		LeftoverFreshness.Suggestion sug = recipe.suggestedFresh();
		Label lead = new Label("Leftovers keep");
		lead.getStyleClass().add("rc-sub");
		boolean editable = !recipe.isLibrary() || (recipe.isDiscovered() && recipe.isSaved());
		if (!editable) {
			Label est = new Label("about " + sug.days() + " days in the fridge (estimate: " + sug.reason() + ")");
			est.getStyleClass().add("rc-sub");
			est.setWrapText(true);
			est.setMinHeight(Region.USE_PREF_SIZE);
			return new HBox(6, lead, est);
		}
		TextField days = new TextField(String.valueOf(recipe.freshDays()));
		days.setPrefWidth(64);
		days.setMaxWidth(64);
		days.getStyleClass().add("rc-fresh-field");
		Label unit = new Label("days");
		unit.getStyleClass().add("rc-sub");
		Label hint = new Label(recipe.getFreshOverride() > 0
				? "you set this \u00B7 suggested " + sug.days() + " (" + sug.reason() + ")"
				: "suggested (" + sug.reason() + ")");
		hint.getStyleClass().add("rc-meta");
		Runnable commit = () -> {
			String typed = days.getText() == null ? "" : days.getText().trim();
			try {
				int n = Integer.parseInt(typed);
				if (n < 1 || n > 60) {
					throw new NumberFormatException();
				}
				if (n != recipe.freshDays()) {
					recipe.setFreshOverride(n == sug.days() ? 0 : n);
					setMessage("Leftovers of '" + recipe.getTitle() + "' now keep " + n + " days.", false);
					onDataChanged.run();
				}
			} catch (NumberFormatException bad) {
				setMessage("Enter a whole number of days from 1 to 60.", true);
			}
		};
		days.setOnAction(e -> commit.run());
		days.focusedProperty().addListener((o, was, is) -> {
			if (!is) {
				commit.run();
			}
		});
		Button reset = new Button("Use suggestion");
		reset.getStyleClass().add("rc-pill-btn");
		reset.setVisible(recipe.getFreshOverride() > 0);
		reset.setManaged(recipe.getFreshOverride() > 0);
		reset.setOnAction(e -> {
			recipe.setFreshOverride(0);
			setMessage("Leftovers of '" + recipe.getTitle() + "' use the suggested " + sug.days() + " days.", false);
			onDataChanged.run();
		});
		HBox row = new HBox(8, lead, days, unit, hint, reset);
		row.setAlignment(Pos.CENTER_LEFT);
		return row;
	}

	private static String timeText(int minutes) {
		return minutes >= 60 ? (minutes / 60) + " h" + (minutes % 60 == 0 ? "" : " " + (minutes % 60) + " min")
				: minutes + " min";
	}

	/**
	 * The "← Back to recipes" button at the top of the detail and New recipe pages.
	 */
	private Button backButton(Runnable action) {
		Button back = new Button("← Back to recipes");
		back.getStyleClass().add("rc-pill-btn");
		back.setOnAction(e -> action.run());
		return back;
	}

	private void showDetail() {
		creating = false;
		Recipe recipe;
		RecipeMatch match;
		try {
			recipe = facade.getRecipe(openId);
			match = facade.checkRecipe(openId);
		} catch (IllegalArgumentException gone) {
			openId = null;
			showList();
			return;
		}
		hideListChrome();
		long id = recipe.getId();
		int servings = detailServings > 0 ? detailServings : recipe.getServings();
		Recipe scaled = servings == recipe.getServings() ? recipe : facade.scaledRecipe(id, servings);
		int total = match.available().size() + match.missing().size();

		// tags
		FlowPane tags = new FlowPane(6, 6);
		for (RecipeTag t : RecipeTag.tagsOf(match)) {
			Label tl = new Label(t.label().replace('-', ' '));
			tl.getStyleClass().add("rc-tag");
			tags.getChildren().add(tl);
		}
		Label auto = new Label("tags worked out by Mealton");
		auto.getStyleClass().add("rc-meta");
		tags.getChildren().add(auto);

		Label title = new Label(recipe.getTitle());
		title.getStyleClass().add("rc-title");
		title.setWrapText(true);
		title.setMinHeight(Region.USE_PREF_SIZE);

		StringBuilder metaText = new StringBuilder(recipe.getServings() + " servings");
		if (recipe.getPrepMinutes() > 0) {
			metaText.append(" · Prep ").append(timeText(recipe.getPrepMinutes()));
		}
		if (recipe.getCookMinutes() > 0) {
			metaText.append(" · Cook ").append(timeText(recipe.getCookMinutes()));
		}
		if (totalMinutes(recipe) > 0) {
			metaText.append(" · Total ").append(totalTimeText(recipe));
		}
		metaText.append(" · ").append(match.isCookable() ? "all " + total + " ingredients in your pantry"
				: match.available().size() + " of " + total + " ingredients in your pantry");
		Label meta = new Label(metaText.toString());
		meta.getStyleClass().add("rc-sub");
		meta.setWrapText(true);
		meta.setMinHeight(Region.USE_PREF_SIZE);

		// rating
		HBox stars = new HBox(0);
		stars.setAlignment(Pos.CENTER_LEFT);
		for (int i = 1; i <= 5; i++) {
			final int value = i;
			Button star = new Button("★");
			star.getStyleClass().add("rc-star-btn");
			if (recipe.isRated() && i <= recipe.getRating()) {
				star.getStyleClass().add("rc-star-on");
			}
			star.setOnAction(e -> attempt(() -> {
				facade.rateRecipe(id, value);
				setMessage("Rated '" + recipe.getTitle() + "' " + value + "/5.", false);
			}));
			stars.getChildren().add(star);
		}
		Label ratingText = new Label(
				recipe.isRated() ? "Your rating · " + recipe.getRating() + "/5" : "Your rating · not rated");
		ratingText.getStyleClass().add("rc-meta");
		stars.getChildren().add(ratingText);
		if (recipe.getPublicRating() > 0) {
			Label pub = new Label("   ·   Public rating ★ " + publicRatingText(recipe)
					+ (recipe.getSourceName() == null ? "" : " on " + recipe.getSourceName()));
			pub.getStyleClass().add("rc-meta");
			stars.getChildren().add(pub);
		}

		// action buttons
		Button log = new Button("✓  I made this · Log it");
		log.getStyleClass().add("primary");
		log.setOnAction(e -> attempt(() -> {
			java.time.LocalTime now = java.time.LocalTime.now();
			String wanted = now.getHour() < 11 ? "Breakfast" : now.getHour() < 16 ? "Lunch" : "Dinner";
			String meal = facade.getMealTags().contains(wanted) ? wanted : "";
			com.mealton.diary.MealLogService.Result result = facade.logRecipeMeal(id, recipe.getServings(), 1, meal,
					null, "", true);
			LogFeedback.Outcome outcome = LogFeedback.afterLogging(this, facade, result);
			onDataChanged.run();
			if (outcome.undone()) {
				setMessage("Undid the log of '" + recipe.getTitle() + "'.", false);
			} else {
				setMessage(
						"Logged 1 serving of '" + recipe.getTitle() + "'"
								+ (outcome.addedToList() > 0 ? " and added " + outcome.addedToList()
										+ (outcome.addedToList() == 1 ? " item" : " items") + " to your shopping list."
										: "."),
						false);
			}
		}));
		Button calendar = new Button("Add to calendar");
		calendar.setOnAction(e -> attempt(() -> addToCalendar(recipe)));
		FlowPane actions = new FlowPane(10, 10, log, calendar);
		if (recipe.isLibrary()) {
			boolean inMine = recipe.isInMyRecipes();
			Button heart = new Button(inMine ? "♥ Saved" : "♡ Save");
			heart.getStyleClass().add("rc-heart");
			if (inMine) {
				heart.getStyleClass().add("rc-heart-on");
			}
			heart.setOnAction(e -> attempt(() -> {
				if (inMine) {
					facade.removeFromMyRecipes(id);
					setMessage("Removed '" + recipe.getTitle() + "' from My Recipes.", false);
				} else {
					facade.saveToMyRecipes(id);
					setMessage("Saved '" + recipe.getTitle() + "' to My Recipes.", false);
				}
			}));
			actions.getChildren().add(heart);
		} else {
			Button edit = new Button("Edit recipe");
			edit.setOnAction(e -> startEditing(recipe));
			actions.getChildren().add(edit);
		}
		if (!match.missing().isEmpty()) {
			Button missing = new Button("Add missing to shopping list");
			missing.setOnAction(e -> attempt(() -> {
				List<RecipeIngredient> added = facade.addMissingToShoppingList(id);
				onDataChanged.run();
				setMessage(added.isEmpty() ? "Nothing to add: the pantry and shopping list already cover it."
						: "Added to the shopping list: "
								+ added.stream().map(RecipeIngredient::toString).collect(Collectors.joining(", "))
								+ ".",
						false);
			}));
			actions.getChildren().add(missing);
		}

		// every action button gets the same size; only "I made this" is filled
		for (Node n : actions.getChildren()) {
			if (n instanceof Button b && !b.getStyleClass().contains("rc-heart")) {
				b.getStyleClass().remove("primary");
				b.getStyleClass().add(b == log ? "rc-action-primary" : "rc-action");
			}
		}

		Label why = new Label(whyText);
		why.getStyleClass().add("rc-why");
		why.setWrapText(true);
		why.setMinHeight(Region.USE_PREF_SIZE);
		why.setVisible(!whyText.isEmpty());
		why.setManaged(!whyText.isEmpty());
		Button whyButton = new Button("Why this recipe? (AI)");
		whyButton.getStyleClass().add("rc-link-bold");
		whyButton.setOnAction(e -> askWhy(id, whyButton, why));

		VBox heroText = new VBox(12, tags, title, meta, freshnessRow(recipe));
		if (recipe.getSourceName() != null || recipe.getSourceUrl() != null) {
			Label from = new Label(
					"Recipe from " + (recipe.getSourceName() == null ? "the web" : recipe.getSourceName()));
			from.getStyleClass().add("rc-text");
			HBox sourceRow = new HBox(10, from);
			sourceRow.setAlignment(Pos.CENTER_LEFT);
			if (recipe.getSourceUrl() != null) {
				Button original = new Button("View original ↗");
				original.getStyleClass().add("rc-link-bold");
				original.setOnAction(e -> openLink(recipe.getSourceUrl()));
				sourceRow.getChildren().add(original);
			}
			sourceRow.getStyleClass().add("rc-source");
			heroText.getChildren().add(sourceRow);
		}
		heroText.getChildren().addAll(stars, actions);
		if (!message.isEmpty()) {
			heroText.getChildren().add(messageLabel());
		}
		HBox.setHgrow(heroText, Priority.ALWAYS);

		// picture (own recipes) or the placeholder letter
		String letter = recipe.getTitle().isEmpty() ? "?" : recipe.getTitle().substring(0, 1).toUpperCase(Locale.ROOT);
		Label big = new Label(letter);
		big.getStyleClass().add("rc-card-letter");
		StackPane picture = new StackPane(big);
		picture.getStyleClass().add("rc-hero-pic");
		picture.setMinSize(260, 220);
		picture.setPrefSize(260, 220);
		picture.setMaxSize(260, 220);
		addPhoto(picture, recipe, big, 220);
		HBox hero = new HBox(22, picture, heroText);
		hero.getStyleClass().add("rc-panel");
		hero.setAlignment(Pos.CENTER_LEFT);

		// ingredients with servings stepper
		Set<String> have = new HashSet<>();
		for (RecipeIngredient i : match.available()) {
			have.add(i.name().toLowerCase(Locale.ROOT));
		}
		Label ingTitle = new Label("Ingredients");
		ingTitle.getStyleClass().add("rc-h2");
		Region sp = new Region();
		HBox.setHgrow(sp, Priority.ALWAYS);
		Label servLabel = new Label("Servings");
		servLabel.getStyleClass().add("rc-text");
		Button minus = new Button("−");
		minus.getStyleClass().add("rc-step-btn");
		minus.setDisable(servings <= 1);
		minus.setOnAction(e -> {
			detailServings = servings - 1;
			showDetail();
		});
		Label count = new Label(String.valueOf(servings));
		count.getStyleClass().add("rc-h2");
		count.setMinWidth(34);
		count.setAlignment(Pos.CENTER);
		Button plus = new Button("+");
		plus.getStyleClass().add("rc-step-btn");
		plus.setDisable(servings >= 100);
		plus.setOnAction(e -> {
			detailServings = servings + 1;
			showDetail();
		});
		Button metric = new Button("g / ml");
		Button cups = new Button("cups");
		metric.getStyleClass().add("rc-seg");
		cups.getStyleClass().add("rc-seg");
		(useCups ? cups : metric).getStyleClass().add("rc-seg-on");
		metric.setOnAction(e -> {
			useCups = false;
			showDetail();
		});
		cups.setOnAction(e -> {
			useCups = true;
			showDetail();
		});
		HBox unitBar = new HBox(3, metric, cups);
		unitBar.getStyleClass().add("rc-seg-bar");
		HBox ingHead = new HBox(8, ingTitle, sp, servLabel, minus, count, plus, unitBar);
		ingHead.setAlignment(Pos.CENTER_LEFT);

		VBox ingredientBox = new VBox(0, ingHead);
		ingredientBox.getStyleClass().add("rc-panel");
		for (RecipeIngredient i : scaled.getIngredients()) {
			boolean haveIt = have.contains(i.name().toLowerCase(Locale.ROOT));
			String[] shown = displayQty(i, useCups);
			Label qty = new Label(shown[0]);
			qty.getStyleClass().addAll("rc-h2", "rc-text");
			qty.setMinWidth(90);
			Label nm = new Label(i.name());
			nm.getStyleClass().add("rc-text");
			VBox nameBox = new VBox(2, nm);
			if (!shown[1].isEmpty()) {
				Label unitNote = new Label(shown[1]);
				unitNote.getStyleClass().add("rc-need-note");
				nameBox.getChildren().add(unitNote);
			}
			HBox.setHgrow(nameBox, Priority.ALWAYS);
			nameBox.setMaxWidth(Double.MAX_VALUE);
			Label pill = new Label(haveIt ? "In pantry" : "Missing");
			pill.getStyleClass().add(haveIt ? "rc-pill-have" : "rc-pill-need");
			HBox row = new HBox(12, qty, nameBox, pill);
			row.setAlignment(Pos.CENTER_LEFT);
			row.setPadding(new Insets(10, 0, 10, 0));
			ingredientBox.getChildren().add(row);
		}
		if (servings != recipe.getServings()) {
			Label note = new Label("Scaled copy of the recipe. The saved recipe is unchanged.");
			note.getStyleClass().add("rc-meta");
			ingredientBox.getChildren().add(note);
		}

		// method: numbered steps with the ingredients and times each step mentions;
		// click a number to tick it off
		VBox methodBox = new VBox(14);
		methodBox.getStyleClass().add("rc-panel");
		Label methodTitle = new Label("Method");
		methodTitle.getStyleClass().add("rc-h2");
		Region msp = new Region();
		HBox.setHgrow(msp, Priority.ALWAYS);
		Label progress = new Label(doneSteps.size() + " of " + recipe.getSteps().size() + " done");
		progress.getStyleClass().add("rc-meta");
		HBox methodHead = new HBox(8, methodTitle, msp, progress);
		methodHead.setAlignment(Pos.CENTER_LEFT);
		methodBox.getChildren().add(methodHead);
		if (recipe.getSteps().isEmpty()) {
			Label none = new Label("No steps were written for this recipe.");
			none.getStyleClass().add("rc-meta");
			methodBox.getChildren().add(none);
		} else {
			int index = 0;
			for (String step : recipe.getSteps()) {
				final int stepIndex = index++;
				boolean done = doneSteps.contains(stepIndex);
				Button num = new Button(done ? "✓" : String.valueOf(stepIndex + 1));
				num.getStyleClass().add(done ? "rc-step-num-done" : "rc-step-num");
				num.setMinSize(30, 30);
				num.setMaxSize(30, 30);
				num.setOnAction(e -> {
					if (!doneSteps.remove(stepIndex)) {
						doneSteps.add(stepIndex);
					}
					showDetail();
				});
				Label stepTitleLabel = new Label("Step " + (stepIndex + 1) + " of " + recipe.getSteps().size());
				stepTitleLabel.getStyleClass().add("rc-step-kicker");
				Label text = new Label(step);
				text.getStyleClass().add(done ? "rc-step-done" : "rc-step-text");
				text.setWrapText(true);
				text.setMinHeight(Region.USE_PREF_SIZE);
				VBox stepBody = new VBox(4, stepTitleLabel, text);

				FlowPane chips = new FlowPane(6, 6);
				Integer chosenTime = recipe.getStepTime(stepIndex);
				int minutes = chosenTime != null ? chosenTime : minutesIn(step);
				if (minutes > 0) {
					Label t = new Label("⏱ " + timeText(minutes));
					t.getStyleClass().add("rc-time-chip");
					chips.getChildren().add(t);
				}
				List<String> custom = recipe.getStepIngredients(stepIndex); // chosen in Edit recipe, or null =
																			// automatic
				List<String> names = custom != null ? custom : autoStepIngredients(step, scaled);
				for (String chipName : names) {
					RecipeIngredient ing = scaled.getIngredients().stream()
							.filter(x -> x.name().equalsIgnoreCase(chipName)).findFirst().orElse(null);
					if (ing != null) {
						Label c = new Label(displayQty(ing, useCups)[0] + " " + ing.name());
						c.getStyleClass().add("rc-use-chip");
						chips.getChildren().add(c);
					}
				}
				if (!chips.getChildren().isEmpty()) {
					stepBody.getChildren().add(chips);
				}
				HBox.setHgrow(stepBody, Priority.ALWAYS);
				HBox row = new HBox(14, num, stepBody);
				row.setAlignment(Pos.TOP_LEFT);
				row.getStyleClass().add("rc-step-row");
				methodBox.getChildren().add(row);
			}
		}

		VBox left = new VBox(16, ingredientBox, methodBox);
		HBox.setHgrow(left, Priority.ALWAYS);
		VBox right = nutritionBox(scaled, servings, recipe);
		right.setMinWidth(300);
		right.setPrefWidth(320);
		right.setMaxWidth(340);
		right.setMaxHeight(Region.USE_PREF_SIZE);
		HBox columns = new HBox(16, left, right);
		columns.setAlignment(Pos.TOP_LEFT);

		VBox whyBox = new VBox(8, whyButton, why);
		whyBox.setAlignment(Pos.CENTER_LEFT);
		Button back = backButton(() -> {
			openId = null;
			message = "";
			showList();
		});
		VBox page = new VBox(16, back, hero, columns, whyBox);
		page.setPadding(new Insets(0, 8, 20, 4));
		present("detail" + openId, scroller(page));
	}

	// grams in one cup, for the g / ml <-> cups toggle (like the design's density
	// table)
	private static final Map<String, Double> GRAMS_PER_CUP = Map.ofEntries(Map.entry("flour", 125.0),
			Map.entry("all-purpose flour", 125.0), Map.entry("bread flour", 127.0),
			Map.entry("whole wheat flour", 120.0), Map.entry("almond flour", 96.0), Map.entry("coconut flour", 112.0),
			Map.entry("sugar", 200.0), Map.entry("brown sugar", 213.0), Map.entry("powdered sugar", 120.0),
			Map.entry("icing sugar", 120.0), Map.entry("confectioners sugar", 120.0), Map.entry("butter", 227.0),
			Map.entry("rice", 185.0), Map.entry("oats", 90.0), Map.entry("cheese", 113.0), Map.entry("feta", 150.0),
			Map.entry("spinach", 30.0), Map.entry("yogurt", 245.0), Map.entry("honey", 340.0), Map.entry("cocoa", 85.0),
			Map.entry("parmesan", 100.0), Map.entry("breadcrumbs", 110.0), Map.entry("peanut butter", 258.0),
			Map.entry("salt", 288.0), Map.entry("potato", 150.0), Map.entry("broccoli", 90.0),
			Map.entry("cornstarch", 128.0), Map.entry("almonds", 140.0), Map.entry("walnuts", 120.0),
			Map.entry("raisins", 150.0), Map.entry("chocolate chips", 170.0), Map.entry("quinoa", 170.0),
			Map.entry("lentils", 190.0), Map.entry("sour cream", 230.0), Map.entry("cream cheese", 225.0));
	private static final List<String> LIQUIDS = List.of("milk", "water", "oil", "cream", "juice", "stock", "broth",
			"vinegar", "wine", "sauce", "syrup", "soup");

	private static boolean isLiquid(String name) {
		String n = name.toLowerCase(Locale.ROOT);
		return LIQUIDS.stream().anyMatch(n::contains);
	}

	private static Double densityOf(String name) {
		String n = name.toLowerCase(Locale.ROOT);
		// the most specific match wins, so "almond flour" is not treated as plain
		// "flour"
		String best = null;
		for (String key : GRAMS_PER_CUP.keySet()) {
			if (n.contains(key) && (best == null || key.length() > best.length())) {
				best = key;
			}
		}
		return best == null ? null : GRAMS_PER_CUP.get(best);
	}

	private static String cupFraction(double cups) {
		double quarter = Math.round(cups * 4) / 4.0;
		int whole = (int) Math.floor(quarter);
		double rest = quarter - whole;
		String fr = rest == 0.25 ? "¼" : rest == 0.5 ? "½" : rest == 0.75 ? "¾" : "";
		if (whole == 0 && fr.isEmpty()) {
			return "0";
		}
		return (whole == 0 ? "" : String.valueOf(whole)) + fr;
	}

	private static String ml(double ml) {
		return Formats.number(Math.round(ml)) + " ml";
	}

	/**
	 * {amount text, small note}: the amount in g / ml or in cups. Counts, tsp, tbsp
	 * and pinches stay as they are.
	 */
	private static String[] displayQty(RecipeIngredient i, boolean cups) {
		String unit = i.unit() == null ? "" : i.unit().toLowerCase(Locale.ROOT);
		double q = i.quantity();
		String plain = Formats.quantityWithUnit(q, i.unit());
		Double density = densityOf(i.name());
		boolean liquid = isLiquid(i.name());
		// bring weights and volumes to grams or millilitres first
		double grams = -1;
		double millis = -1;
		switch (unit) {
		case "g" -> grams = q;
		case "kg" -> grams = q * 1000;
		case "oz" -> grams = q * 28.35;
		case "lb" -> grams = q * 453.6;
		case "ml" -> millis = q;
		case "l" -> millis = q * 1000;
		case "cup" -> millis = q * 240;
		default -> {
			return new String[] { plain, "" };
		}
		}
		if (!cups) {
			if (grams >= 0) {
				return new String[] { Formats.number(Math.round(grams)) + " g", "" };
			}
			if (!unit.equals("cup")) {
				return new String[] { ml(millis), "" };
			}
			if (!liquid && density != null) {
				return new String[] { Formats.number(Math.round(q * density)) + " g", "" };
			}
			return new String[] { ml(millis), "" };
		}
		if (grams >= 0) {
			if (liquid) {
				millis = grams;
			} else if (density != null) {
				return new String[] { cupFraction(grams / density) + (grams / density > 1.25 ? " cups" : " cup"), "" };
			} else {
				return new String[] { Formats.number(Math.round(grams)) + " g", "No density data, shown in grams" };
			}
		}
		double tbsp = millis / 15;
		if (tbsp < 4) {
			return new String[] { Formats.number(Math.round(tbsp * 10) / 10.0) + " tbsp", "" };
		}
		double c = millis / 240;
		return new String[] { cupFraction(c) + (c > 1.25 ? " cups" : " cup"), "" };
	}

	private File chooseImage(Node owner) {
		FileChooser chooser = new FileChooser();
		chooser.setTitle("Choose a picture");
		chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Pictures",
				RecipePhotos.EXTENSIONS.stream().map(e -> "*." + e).collect(Collectors.toList())));
		return chooser.showOpenDialog(owner.getScene() == null ? null : owner.getScene().getWindow());
	}

	private VBox nutritionBox(Recipe scaled, int servings, Recipe original) {
		RecipeNutrition nutrition = RecipeNutrition.of(scaled);
		VBox box = new VBox(12);
		box.getStyleClass().add("rc-panel");

		Label title = new Label("Nutrition");
		title.getStyleClass().add("rc-h2");
		Button per = new Button("Per serving");
		Button whole = new Button("Whole recipe");
		per.getStyleClass().add("rc-seg");
		whole.getStyleClass().add("rc-seg");
		(wholeRecipe ? whole : per).getStyleClass().add("rc-seg-on");
		per.setOnAction(e -> {
			wholeRecipe = false;
			showDetail();
		});
		whole.setOnAction(e -> {
			wholeRecipe = true;
			showDetail();
		});
		per.setMaxWidth(Double.MAX_VALUE);
		whole.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(per, Priority.ALWAYS);
		HBox.setHgrow(whole, Priority.ALWAYS);
		HBox seg = new HBox(3, per, whole);
		seg.getStyleClass().add("rc-seg-bar");
		box.getChildren().addAll(title, seg); // title above the switch, so neither gets squeezed

		if (nutrition.counted().isEmpty()) {
			Label none = new Label(estimating ? "Claude is estimating the nutrition…"
					: "No nutrition estimate: none of the ingredients are in the food table.");
			none.getStyleClass().add("rc-meta");
			none.setWrapText(true);
			none.setMinHeight(Region.USE_PREF_SIZE);
			box.getChildren().add(none);
			addEstimateButton(box, nutrition, original);
			return box;
		}
		Nutrients n = wholeRecipe ? nutrition.total() : nutrition.perServing();
		Label kcal = new Label(String.valueOf(Math.round(n.calories())));
		kcal.getStyleClass().add("rc-kcal");
		Label basis = new Label(wholeRecipe ? "kcal for " + servings + " servings" : "kcal per serving");
		basis.getStyleClass().add("rc-sub");
		box.getChildren().add(new VBox(0, kcal, basis));

		// Three tiles. Bars only appear once "track macros" exists: then every tile
		// gets one, never just some.
		UserProfile p = facade.getProfile();
		HBox tiles = new HBox(10, macroTile("Protein", n.protein(), SHOW_MACRO_BARS ? p.proteinGoal() : 0),
				macroTile("Carbs", n.carbs(), SHOW_MACRO_BARS ? p.carbsGoal() : 0),
				macroTile("Fat", n.fat(), SHOW_MACRO_BARS ? p.fatGoal() : 0));
		box.getChildren().add(tiles);

		String note = (SHOW_MACRO_BARS ? "Bars show the share of your daily targets. " : "")
				+ (nutrition.aiEstimated() ? "Claude estimated these from the ingredient list."
						: "These are estimates from the built-in food table.");
		if (!nutrition.notCounted().isEmpty()) {
			note += " Not counted: " + String.join(", ", nutrition.notCounted()) + ".";
		}
		Label noteLabel = new Label(note);
		noteLabel.getStyleClass().add("rc-meta");
		noteLabel.setWrapText(true);
		noteLabel.setMinHeight(Region.USE_PREF_SIZE);
		box.getChildren().add(noteLabel);
		addEstimateButton(box, nutrition, original);
		return box;
	}

	/**
	 * "Estimate with Claude" for your own recipes whose ingredients the food table
	 * does not fully know.
	 */
	private void addEstimateButton(VBox box, RecipeNutrition nutrition, Recipe original) {
		if (original.isLibrary() || nutrition.isComplete() || nutrition.aiEstimated() || !facade.hasAgent()
				|| estimating) {
			return;
		}
		Button ask = new Button("Estimate with Claude");
		ask.getStyleClass().add("rc-pill-btn");
		ask.setOnAction(e -> estimateInBackground(original.getId(), true));
		box.getChildren().add(ask);
	}

	/**
	 * Claude estimates the nutrition of one of your own recipes in the background,
	 * then the page refreshes.
	 */
	private void estimateInBackground(long id, boolean announce) {
		if (estimating) {
			return;
		}
		estimating = true;
		Task<Boolean> task = new Task<>() {
			@Override
			protected Boolean call() {
				return facade.estimateNutrition(id);
			}
		};
		task.setOnSucceeded(e -> {
			estimating = false;
			if (announce || task.getValue()) {
				setMessageOnly(task.getValue() ? "Claude added a nutrition estimate."
						: "Claude could not estimate this one right now.", !task.getValue());
			}
			refresh();
		});
		task.setOnFailed(e -> {
			estimating = false;
			refresh();
		});
		Thread thread = new Thread(task, "recipe-nutrition");
		thread.setDaemon(true);
		thread.start();
		refresh();
	}

	private Node macroTile(String name, double grams, int goal) {
		Label value = new Label(Math.round(grams) + " g");
		value.getStyleClass().add("rc-tile-value");
		Label label = new Label(name);
		label.getStyleClass().add("rc-meta");
		VBox tile = new VBox(2, value, label);
		if (goal > 0) {
			Region fill = new Region();
			fill.getStyleClass().add("rc-bar-fill");
			Pane track = new Pane(fill);
			track.getStyleClass().add("rc-bar-track");
			track.setMinHeight(8);
			track.setPrefHeight(8);
			fill.setMinHeight(8);
			fill.setPrefHeight(8);
			double frac = Math.min(1.0, grams / goal);
			fill.prefWidthProperty().bind(track.widthProperty().multiply(frac));
			fill.maxWidthProperty().bind(track.widthProperty().multiply(frac));
			tile.getChildren().add(track);
		}
		tile.getStyleClass().add("rc-tile");
		HBox.setHgrow(tile, Priority.ALWAYS);
		tile.setMaxWidth(Double.MAX_VALUE);
		tile.setPrefWidth(90);
		return tile;
	}

	/**
	 * Asks for a date and a meal in a dialog styled like the rest of the app, then
	 * puts the recipe on the calendar.
	 */
	private void addToCalendar(Recipe recipe) {
		Dialog<ButtonType> dialog = new Dialog<>();
		dialog.setTitle("Add to calendar");
		dialog.setHeaderText(null);
		dialog.setGraphic(null);
		dialog.getDialogPane().getStylesheets().addAll(getStylesheets());
		var main = RecipeBrowserView.class.getResource("/mealton.css");
		if (main != null) {
			dialog.getDialogPane().getStylesheets().add(0, main.toExternalForm());
		}
		dialog.getDialogPane().getStyleClass().add("rc-dialog");

		Label heading = new Label("Add to calendar");
		heading.getStyleClass().add("rc-dialog-title");
		Label sub = new Label(recipe.getTitle());
		sub.getStyleClass().add("rc-sub");
		sub.setWrapText(true);
		sub.setMinHeight(Region.USE_PREF_SIZE);

		Label dateLabel = new Label("Day");
		dateLabel.getStyleClass().add("rc-h3");
		DatePicker date = new DatePicker(LocalDate.now());
		date.setMaxWidth(Double.MAX_VALUE);
		date.setEditable(false);
		date.getStyleClass().add("rc-date");

		Label mealLabel = new Label("Meal tag");
		mealLabel.getStyleClass().add("rc-h3");
		MealSlot[] chosen = { MealSlot.DINNER };
		HBox mealRow = new HBox(8);
		Runnable[] redraw = new Runnable[1];
		redraw[0] = () -> {
			mealRow.getChildren().clear();
			for (MealSlot slot : MealSlot.values()) {
				Button tag = new Button(slot.label());
				tag.getStyleClass().add(chosen[0] == slot ? "rc-tag-on" : "rc-tag-off");
				tag.setOnAction(e -> {
					chosen[0] = slot;
					redraw[0].run();
				});
				mealRow.getChildren().add(tag);
			}
		};
		redraw[0].run();

		VBox content = new VBox(10, heading, sub, dateLabel, date, mealLabel, mealRow);
		content.setPadding(new Insets(6, 10, 16, 10));
		content.setPrefWidth(360);
		dialog.getDialogPane().setContent(content);
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
		Button ok = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
		ok.setText("Add to calendar");
		ok.getStyleClass().add("primary");
		((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Cancel");

		Optional<ButtonType> answer = dialog.showAndWait();
		if (answer.isPresent() && answer.get() == ButtonType.OK) {
			if (date.getValue() == null) {
				throw new IllegalArgumentException("Choose a date first.");
			}
			facade.setCalendarMeal(date.getValue(), chosen[0].name().toLowerCase(Locale.ROOT), recipe.getId());
			onDataChanged.run();
			setMessage("Added '" + recipe.getTitle() + "' to " + chosen[0].label().toLowerCase(Locale.ROOT) + " on "
					+ date.getValue() + ".", false);
		}
	}

	/**
	 * Asks the AI for the one-line reason in the background, so the window never
	 * freezes.
	 */
	private void askWhy(long id, Button button, Label why) {
		button.setDisable(true);
		why.setText("Asking the AI...");
		why.setVisible(true);
		why.setManaged(true);
		Task<String> task = new Task<>() {
			@Override
			protected String call() {
				return facade.explainRecipe(id);
			}
		};
		task.setOnSucceeded(e -> {
			whyText = task.getValue();
			if (openId != null && openId == id) {
				why.setText(whyText);
				button.setDisable(false);
			}
		});
		task.setOnFailed(e -> {
			if (openId != null && openId == id) {
				why.setText("Could not get a reason right now.");
				button.setDisable(false);
			}
		});
		Thread thread = new Thread(task, "recipe-why");
		thread.setDaemon(true);
		thread.start();
	}

	/**
	 * The ingredients a step seems to use: its full name appears in the text, or at
	 * least its last word (flour, butter).
	 */
	private static List<String> autoStepIngredients(String step, Recipe recipe) {
		return autoNames(step, recipe.getIngredients().stream().map(RecipeIngredient::name).toList());
	}

	private static List<String> autoNames(String step, List<String> ingredientNames) {
		String lower = step == null ? "" : step.toLowerCase(Locale.ROOT);
		List<String> found = new ArrayList<>();
		for (String ingName : ingredientNames) {
			String name = ingName.toLowerCase(Locale.ROOT);
			String[] words = name.split("\\s+");
			String last = words[words.length - 1];
			boolean lastWord = last.length() >= 4
					&& Pattern.compile("\\b" + Pattern.quote(last) + "s?\\b").matcher(lower).find();
			if (lower.contains(name) || lastWord) {
				found.add(ingName);
			}
		}
		return found;
	}

	/** Shows a message with an underlined Undo link beside it. */
	private void setMessageWithUndo(String text, Runnable undo) {
		setMessageOnly(text, false);
		undoAction = undo;
		undoMessage = text;
		refresh();
	}

	private static Node effectLine(String mark, String text) {
		Label m = new Label(mark);
		m.getStyleClass().add("rc-del-mark");
		m.setMinWidth(18);
		Label t = new Label(text);
		t.getStyleClass().add("rc-text");
		t.setWrapText(true);
		t.setMinHeight(Region.USE_PREF_SIZE);
		t.setPrefWidth(340);
		t.setMaxWidth(340);
		HBox line = new HBox(8, m, t);
		line.setAlignment(Pos.TOP_LEFT);
		return line;
	}

	private void deleteOwn(Recipe recipe) {
		long id = recipe.getId();
		boolean hasPhoto = facade.getRecipePhoto(id).isPresent();
		Dialog<ButtonType> dialog = new Dialog<>();
		dialog.setTitle("Delete recipe");
		dialog.setHeaderText(null);
		dialog.setGraphic(null);
		dialog.getDialogPane().getStylesheets().addAll(getStylesheets());
		var main = RecipeBrowserView.class.getResource("/mealton.css");
		if (main != null) {
			dialog.getDialogPane().getStylesheets().add(0, main.toExternalForm());
		}
		dialog.getDialogPane().getStyleClass().add("rc-dialog");

		Label heading = new Label("Delete this recipe?");
		heading.getStyleClass().add("rc-dialog-title");

		// section 1: which recipe (with its picture or first letter)
		StackPane thumb = new StackPane();
		thumb.getStyleClass().add("rc-del-thumb");
		thumb.setMinSize(56, 56);
		thumb.setPrefSize(56, 56);
		thumb.setMaxSize(56, 56);
		Optional<Path> photoFile = facade.getRecipePhoto(id);
		if (photoFile.isPresent()) {
			Image img = new Image(photoFile.get().toUri().toString(), 160, 160, true, true);
			ImageView view = new ImageView(img);
			double side = Math.min(img.getWidth(), img.getHeight());
			if (side > 0) {
				view.setViewport(new javafx.geometry.Rectangle2D((img.getWidth() - side) / 2,
						(img.getHeight() - side) / 2, side, side));
			}
			view.setFitWidth(56);
			view.setFitHeight(56);
			Rectangle round = new Rectangle(56, 56);
			round.setArcWidth(16);
			round.setArcHeight(16);
			view.setClip(round);
			thumb.getChildren().add(view);
		} else {
			Label letter = new Label(
					recipe.getTitle().isEmpty() ? "?" : recipe.getTitle().substring(0, 1).toUpperCase(Locale.ROOT));
			letter.getStyleClass().add("rc-card-letter");
			letter.setStyle("-fx-font-size: 26px;");
			thumb.getChildren().add(letter);
		}
		Label name = new Label(recipe.getTitle());
		name.getStyleClass().add("rc-h2");
		name.setWrapText(true);
		name.setMinHeight(Region.USE_PREF_SIZE);
		name.setPrefWidth(270);
		name.setMaxWidth(270);
		Label where = new Label(recipe.getServings() + " servings · in My Recipes");
		where.getStyleClass().add("rc-meta");
		HBox recipeCard = new HBox(14, thumb, new VBox(3, name, where));
		recipeCard.setAlignment(Pos.CENTER_LEFT);
		recipeCard.getStyleClass().add("rc-del-card");

		// section 2: what will happen
		VBox effects = new VBox(8, effectLine("•", "Removed from My Recipes and from your meal calendar."),
				effectLine("•", hasPhoto ? "Its photo is deleted too." : "It has no photo."),
				effectLine("↶", "You can undo this right after."));
		effects.getStyleClass().add("rc-del-list");

		VBox content = new VBox(14, heading, recipeCard, effects);
		content.setPadding(new Insets(6, 10, 14, 10));
		content.setPrefWidth(400);
		content.setMinHeight(Region.USE_PREF_SIZE);
		dialog.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
		dialog.setResizable(false);
		dialog.getDialogPane().setContent(content);
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
		Button ok = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
		ok.setText("Delete recipe");
		ok.getStyleClass().add("rc-danger");
		((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Keep it");

		Optional<ButtonType> answer = dialog.showAndWait();
		if (answer.isPresent() && answer.get() == ButtonType.OK) {
			Optional<Path> backup = facade.backupRecipePhoto(id);
			facade.deleteRecipe(id);
			creating = false;
			editingId = null;
			resetDrafts();
			openId = null;
			tab = Tab.MINE;
			onDataChanged.run();
			setMessageWithUndo("Deleted '" + recipe.getTitle() + "'.", () -> {
				facade.restoreDeletedRecipe(recipe);
				backup.ifPresent(b -> facade.setRecipePhoto(id, b));
				onDataChanged.run();
				setMessage("Restored '" + recipe.getTitle() + "'.", false);
			});
		}
	}

	// ------------------------------------------------------------- new recipe

	private static final List<String> UNITS = List.of("", "g", "kg", "ml", "l", "tsp", "tbsp", "cup", "oz", "lb",
			"pinch");

	/** One editable ingredient row of the recipe form. */
	/**
	 * One step in the New / Edit recipe form: its text, the ingredient tags and the
	 * timer tag the cook can fix.
	 */
	private static final class StepEdit {
		final TextArea area = new TextArea();
		final FlowPane flow = new FlowPane(6, 6);
		final TextField time = new TextField();
		List<String> chips = null; // null = automatic
		Runnable render = () -> {
		};
	}

	private static final class IngredientRow {
		final TextField amount = new TextField();
		final ComboBox<String> unit = new ComboBox<>();
		final TextField name = new TextField();
		final HBox box = new HBox(8);

		IngredientRow(Runnable onRemove, RecipeIngredient prefill) {
			amount.setPromptText("Amount");
			amount.setPrefWidth(90);
			unit.getItems().addAll(UNITS);
			unit.setValue("");
			unit.setPromptText("Unit");
			unit.setPrefWidth(100);
			unit.setCellFactory(v -> new javafx.scene.control.ListCell<>() {
				@Override
				protected void updateItem(String u, boolean empty) {
					super.updateItem(u, empty);
					setText(empty || u == null ? null : u.isEmpty() ? "(count)" : u);
				}
			});
			unit.setButtonCell(new javafx.scene.control.ListCell<>() {
				@Override
				protected void updateItem(String u, boolean empty) {
					super.updateItem(u, empty);
					setText(empty || u == null ? "Unit" : u.isEmpty() ? "(count)" : u);
				}
			});
			name.setPromptText("Ingredient, e.g. flour");
			HBox.setHgrow(name, Priority.ALWAYS);
			if (prefill != null) {
				amount.setText(Formats.number(prefill.quantity()));
				String u = prefill.unit() == null ? "" : prefill.unit();
				if (!unit.getItems().contains(u)) {
					unit.getItems().add(u);
				}
				unit.setValue(u);
				name.setText(prefill.name());
			}
			Button remove = new Button("×");
			remove.getStyleClass().add("rc-x-btn");
			remove.setOnAction(e -> onRemove.run());
			box.getChildren().addAll(amount, unit, name, remove);
			box.setAlignment(Pos.CENTER_LEFT);
		}

		boolean isBlank() {
			return amount.getText().isBlank() && name.getText().isBlank();
		}
	}

	private void resetDrafts() {
		draftTitle = "";
		draftServings = 2;
		draftPrep = "";
		draftCook = "";
		draftTotal = "";
		draftRating = "";
		draftRatingCount = "";
		draftSourceName = "";
		draftSourceUrl = "";
		pendingPhoto = null;
		removeExistingPhoto = false;
	}

	/** Opens the form for a new recipe. */
	private void startNewRecipe() {
		message = "";
		editingId = null;
		resetDrafts();
		creating = true;
		openId = null;
		showRecipeForm();
	}

	/** Opens the form filled in with one of your own recipes. */
	private void startEditing(Recipe r) {
		message = "";
		resetDrafts();
		editingId = r.getId();
		draftTitle = r.getTitle();
		draftServings = r.getServings();
		draftPrep = r.getPrepMinutes() > 0 ? String.valueOf(r.getPrepMinutes()) : "";
		draftCook = r.getCookMinutes() > 0 ? String.valueOf(r.getCookMinutes()) : "";
		draftTotal = r.getTotalOverride() > 0 ? String.valueOf(r.getTotalOverride()) : "";
		draftRating = r.getPublicRating() > 0 ? Formats.number(r.getPublicRating()) : "";
		draftRatingCount = r.getPublicRatingCount() > 0 ? String.valueOf(r.getPublicRatingCount()) : "";
		draftSourceName = r.getSourceName() == null ? "" : r.getSourceName();
		draftSourceUrl = r.getSourceUrl() == null ? "" : r.getSourceUrl();
		creating = true;
		openId = null;
		showRecipeForm();
	}

	private TextField labelledField(VBox into, String label, String prompt, String value,
			java.util.function.Consumer<String> onChange) {
		Label l = new Label(label);
		l.getStyleClass().add("rc-h3");
		TextField f = new TextField(value);
		f.setPromptText(prompt);
		f.getStyleClass().add("rc-field");
		f.textProperty().addListener((obs, o, n) -> onChange.accept(n));
		into.getChildren().addAll(l, f);
		return f;
	}

	private void showRecipeForm() {
		hideListChrome();
		Recipe editing = null;
		if (editingId != null) {
			try {
				editing = facade.getRecipe(editingId);
			} catch (IllegalArgumentException gone) {
				editingId = null;
			}
		}
		final Recipe editedRecipe = editing;

		Label title = new Label(editing == null ? "New recipe" : "Edit recipe");
		title.getStyleClass().add("rc-title");
		Label sub = new Label(
				editing == null ? "Write it once, then log it, plan it and shop for it. Mealton tags it for you."
						: "Changes apply everywhere the recipe is used, including your meal calendar.");
		sub.getStyleClass().add("rc-sub");
		sub.setWrapText(true);
		sub.setMinHeight(Region.USE_PREF_SIZE);

		// --- photo
		Label photoTitle = new Label("Photo");
		photoTitle.getStyleClass().add("rc-h2");
		Label photoHint = new Label();
		photoHint.getStyleClass().add("rc-meta");
		photoHint.setWrapText(true);
		photoHint.setMinHeight(Region.USE_PREF_SIZE);
		StackPane preview = new StackPane();
		preview.getStyleClass().add("rc-drop");
		preview.setMinSize(220, 150);
		preview.setPrefSize(220, 150);
		preview.setMaxSize(220, 150);
		Button choose = new Button("Choose a picture…");
		Button removePhoto = new Button("Remove");
		Optional<Path> existing = editing == null || removeExistingPhoto ? Optional.empty()
				: facade.getRecipePhoto(editing.getId());
		Runnable updatePhoto = () -> {
			preview.getChildren().clear();
			Path shown = pendingPhoto != null ? pendingPhoto
					: (editedRecipe == null || removeExistingPhoto ? null
							: facade.getRecipePhoto(editedRecipe.getId()).orElse(null));
			if (shown != null) {
				Image img = new Image(shown.toUri().toString(), 440, 0, true, true);
				ImageView view = new ImageView(img);
				view.setPreserveRatio(false);
				view.setFitWidth(220);
				view.setFitHeight(150);
				if (img.getWidth() > 0) {
					double scale = Math.max(220 / img.getWidth(), 150 / img.getHeight());
					view.setViewport(new javafx.geometry.Rectangle2D((img.getWidth() - 220 / scale) / 2,
							(img.getHeight() - 150 / scale) / 2, 220 / scale, 150 / scale));
				}
				preview.getChildren().add(view);
				photoHint.setText(pendingPhoto != null ? pendingPhoto.getFileName().toString() : "Current photo");
			} else {
				Label icon = new Label("＋\nAdd a photo");
				icon.getStyleClass().add("rc-drop-text");
				icon.setAlignment(Pos.CENTER);
				icon.setStyle("-fx-text-alignment: center;");
				preview.getChildren().add(icon);
				photoHint.setText("Add a picture of the finished dish (png, jpg, gif or bmp, up to 10 MB).");
			}
			choose.setText(shown == null ? "Choose a picture…" : "Change picture…");
			removePhoto.setVisible(shown != null);
			removePhoto.setManaged(shown != null);
		};
		updatePhoto.run();
		VBox photoSide = new VBox(10, photoHint, new HBox(8, choose, removePhoto));
		photoSide.setAlignment(Pos.CENTER_LEFT);
		HBox photoRow = new HBox(18, preview, photoSide);
		photoRow.setAlignment(Pos.CENTER_LEFT);
		VBox photoBox = new VBox(12, photoTitle, photoRow);
		photoBox.getStyleClass().add("rc-panel");

		// --- basics
		Label basicsTitle = new Label("Basics");
		basicsTitle.getStyleClass().add("rc-h2");
		VBox basicsBox = new VBox(8, basicsTitle);
		basicsBox.getStyleClass().add("rc-panel");
		TextField titleField = labelledField(basicsBox, "Title", "Recipe title, e.g. Fluffy pancakes", draftTitle,
				v -> draftTitle = v);
		Label servLabel = new Label("Servings");
		servLabel.getStyleClass().add("rc-h3");
		Button minus = new Button("−");
		minus.getStyleClass().add("rc-step-btn");
		minus.setStyle("-fx-text-fill: #2f4a34; -fx-font-size: 20px; -fx-font-weight: bold;");
		Label count = new Label(String.valueOf(draftServings));
		count.getStyleClass().add("rc-h2");
		count.setMinWidth(34);
		count.setAlignment(Pos.CENTER);
		Button plus = new Button("+");
		plus.getStyleClass().add("rc-step-btn");
		plus.setStyle("-fx-text-fill: #2f4a34; -fx-font-size: 20px; -fx-font-weight: bold;");
		minus.setDisable(draftServings <= 1);
		minus.setOnAction(e -> {
			draftServings = Math.max(1, draftServings - 1);
			count.setText(String.valueOf(draftServings));
			minus.setDisable(draftServings <= 1);
		});
		plus.setOnAction(e -> {
			draftServings = Math.min(100, draftServings + 1);
			count.setText(String.valueOf(draftServings));
			minus.setDisable(draftServings <= 1);
		});
		HBox servRow = new HBox(8, minus, count, plus);
		servRow.setAlignment(Pos.CENTER_LEFT);
		VBox prepBox = new VBox(6);
		TextField prepField = labelledField(prepBox, "Prep (minutes)", "e.g. 10", draftPrep, v -> draftPrep = v);
		VBox cookBox = new VBox(6);
		TextField cookField = labelledField(cookBox, "Cook (minutes)", "e.g. 15", draftCook, v -> draftCook = v);
		VBox totalBox = new VBox(6);
		TextField totalField = labelledField(totalBox, "Total (minutes)",
				editing == null ? "auto" : "auto: about " + Math.max(0, totalMinutes(editing)), draftTotal,
				v -> draftTotal = v);
		VBox ratingBox = new VBox(6);
		TextField ratingField = labelledField(ratingBox, "Public rating (0–5)", "e.g. 4.8", draftRating,
				v -> draftRating = v);
		VBox ratingCountBox = new VBox(6);
		TextField ratingCountField = labelledField(ratingCountBox, "Number of ratings", "e.g. 1203", draftRatingCount,
				v -> draftRatingCount = v);
		HBox ratingRow = new HBox(16, ratingBox, ratingCountBox);
		Label totalHint = new Label(
				"Leave Total empty and Mealton estimates it, including chilling and resting steps.");
		totalHint.getStyleClass().add("rc-meta");
		totalHint.setWrapText(true);
		totalHint.setMinHeight(Region.USE_PREF_SIZE);
		HBox times = new HBox(16, new VBox(6, servLabel, servRow), prepBox, cookBox, totalBox);
		times.setAlignment(Pos.BOTTOM_LEFT);
		basicsBox.getChildren().addAll(times, totalHint, ratingRow);
		Label sourceHint = new Label(
				"Where is it from? Optional. A link lets you open the original recipe from Mealton.");
		sourceHint.getStyleClass().add("rc-meta");
		basicsBox.getChildren().add(sourceHint);
		TextField sourceNameField = labelledField(basicsBox, "Recipe from", "e.g. The Mediterranean Dish",
				draftSourceName, v -> draftSourceName = v);
		TextField sourceUrlField = labelledField(basicsBox, "Link to the original", "https://…", draftSourceUrl,
				v -> draftSourceUrl = v);

		// --- ingredients
		Label ingTitle = new Label("Ingredients");
		ingTitle.getStyleClass().add("rc-h2");
		Label ingHint = new Label("Amount, unit and name. Leave the unit on (count) for things like 2 eggs.");
		ingHint.getStyleClass().add("rc-meta");
		VBox ingRows = new VBox(8);
		List<IngredientRow> rows = new ArrayList<>();
		java.util.function.Consumer<RecipeIngredient> addRow = prefill -> {
			IngredientRow[] self = new IngredientRow[1];
			self[0] = new IngredientRow(() -> {
				if (rows.size() > 1) {
					rows.remove(self[0]);
					ingRows.getChildren().remove(self[0].box);
				}
			}, prefill);
			rows.add(self[0]);
			ingRows.getChildren().add(self[0].box);
		};
		if (editing != null) {
			for (RecipeIngredient i : editing.getIngredients()) {
				addRow.accept(i);
			}
		} else {
			for (int i = 0; i < 3; i++) {
				addRow.accept(null);
			}
		}
		Button addIng = new Button("+ Add ingredient");
		addIng.getStyleClass().add("rc-pill-btn");
		addIng.setOnAction(e -> addRow.accept(null));
		VBox ingBox = new VBox(10, ingTitle, ingHint, ingRows, addIng);
		ingBox.getStyleClass().add("rc-panel");

		// --- steps
		Label stepTitle = new Label("Method");
		stepTitle.getStyleClass().add("rc-h2");
		Label stepHint = new Label(
				"One step per box. Mention times like \"2 minutes\" and Mealton shows them as timers on the recipe.");
		stepHint.getStyleClass().add("rc-meta");
		stepHint.setWrapText(true);
		stepHint.setMinHeight(Region.USE_PREF_SIZE);
		VBox stepRows = new VBox(10);
		List<TextArea> stepAreas = new ArrayList<>();
		Map<TextArea, StepEdit> stepEdits = new java.util.IdentityHashMap<>();
		java.util.function.Supplier<List<String>> ingredientNames = () -> {
			List<String> names = new ArrayList<>();
			for (IngredientRow r : rows) {
				String n = r.name.getText() == null ? "" : r.name.getText().trim();
				if (!n.isEmpty()) {
					names.add(n);
				}
			}
			return names;
		};
		java.util.function.Consumer<StepEdit> renderChips = edit -> {
			edit.flow.getChildren().clear();
			List<String> names = edit.chips != null ? edit.chips
					: autoNames(edit.area.getText(), ingredientNames.get());
			Label lead = new Label("Ingredient tags:");
			lead.getStyleClass().add("rc-meta");
			edit.flow.getChildren().add(lead);
			for (String n : names) {
				Label c = new Label(n);
				Button x = new Button("×");
				x.getStyleClass().add("rc-chip-x");
				x.setOnAction(e -> {
					List<String> left = new ArrayList<>(names);
					left.removeIf(v -> v.equalsIgnoreCase(n));
					edit.chips = left;
					edit.render.run();
				});
				HBox chip = new HBox(2, c, x);
				chip.setAlignment(Pos.CENTER_LEFT);
				chip.getStyleClass().add("rc-use-chip-box");
				edit.flow.getChildren().add(chip);
			}
			Button add = new Button("+ ingredient");
			add.getStyleClass().add("rc-chip-add");
			add.setOnAction(e -> {
				ContextMenu menu = new ContextMenu();
				for (String option : ingredientNames.get()) {
					if (names.stream().noneMatch(v -> v.equalsIgnoreCase(option))) {
						MenuItem item = new MenuItem(option);
						item.setOnAction(ev -> {
							List<String> more = new ArrayList<>(names);
							more.add(option);
							edit.chips = more;
							edit.render.run();
						});
						menu.getItems().add(item);
					}
				}
				if (menu.getItems().isEmpty()) {
					MenuItem none = new MenuItem("Every ingredient is already tagged");
					none.setDisable(true);
					menu.getItems().add(none);
				}
				menu.show(add, javafx.geometry.Side.BOTTOM, 0, 4);
			});
			edit.flow.getChildren().add(add);
			if (edit.chips != null) {
				Button reset = new Button("Reset");
				reset.getStyleClass().add("rc-link-undo");
				reset.setOnAction(e -> {
					edit.chips = null;
					edit.render.run();
				});
				edit.flow.getChildren().add(reset);
			}
		};
		java.util.function.Consumer<StepEdit> addStepEdit = edit -> {
			edit.area.setPromptText("Step " + (stepAreas.size() + 1) + ", e.g. Whisk the eggs and milk together");
			edit.area.setPrefRowCount(2);
			edit.area.setWrapText(true);
			HBox.setHgrow(edit.area, Priority.ALWAYS);
			Button remove = new Button("×");
			remove.getStyleClass().add("rc-x-btn");
			edit.render = () -> renderChips.accept(edit);
			edit.area.textProperty().addListener((obs, o, n) -> {
				if (edit.chips == null) {
					edit.render.run();
				}
				int autoNow = minutesIn(n == null ? "" : n);
				edit.time.setPromptText(autoNow > 0 ? "auto: " + autoNow : "auto");
			});
			int auto = minutesIn(edit.area.getText());
			edit.time.setPromptText(auto > 0 ? "auto: " + auto : "auto");
			edit.time.setPrefWidth(90);
			edit.time.getStyleClass().add("rc-field");
			Label timeLabel = new Label("⏱ Timer tag (minutes)");
			timeLabel.getStyleClass().add("rc-meta");
			Label timeHint = new Label("empty = automatic, 0 = no tag");
			timeHint.getStyleClass().add("rc-meta");
			HBox timeRow = new HBox(8, timeLabel, edit.time, timeHint);
			timeRow.setAlignment(Pos.CENTER_LEFT);
			HBox top = new HBox(8, edit.area, remove);
			top.setAlignment(Pos.TOP_LEFT);
			VBox row = new VBox(6, top, edit.flow, timeRow);
			remove.setOnAction(e -> {
				if (stepAreas.size() > 1) {
					stepAreas.remove(edit.area);
					stepEdits.remove(edit.area);
					stepRows.getChildren().remove(row);
				}
			});
			stepAreas.add(edit.area);
			stepEdits.put(edit.area, edit);
			stepRows.getChildren().add(row);
			edit.render.run();
		};
		java.util.function.Consumer<String> addStep = text -> {
			StepEdit edit = new StepEdit();
			edit.area.setText(text == null ? "" : text);
			addStepEdit.accept(edit);
		};
		if (editing != null && !editing.getSteps().isEmpty()) {
			for (int i = 0; i < editing.getSteps().size(); i++) {
				StepEdit edit = new StepEdit();
				edit.area.setText(editing.getSteps().get(i));
				List<String> chosen = editing.getStepIngredients(i);
				edit.chips = chosen == null ? null : new ArrayList<>(chosen);
				Integer time = editing.getStepTime(i);
				edit.time.setText(time == null ? "" : String.valueOf(time));
				addStepEdit.accept(edit);
			}
		} else {
			for (int i = 0; i < 3; i++) {
				addStep.accept(null);
			}
		}
		Button addStepBtn = new Button("+ Add step");
		addStepBtn.getStyleClass().add("rc-pill-btn");
		addStepBtn.setOnAction(e -> addStep.accept(null));
		VBox stepBox = new VBox(10, stepTitle, stepHint, stepRows, addStepBtn);
		stepBox.getStyleClass().add("rc-panel");

		// --- actions
		Label status = new Label(message);
		status.getStyleClass().add(messageIsError ? "rc-err" : "rc-ok");
		status.setWrapText(true);
		status.setMinHeight(Region.USE_PREF_SIZE);

		VBox importBox = null;
		if (editing == null) {
			Label impTitle = new Label("Import from a link");
			impTitle.getStyleClass().add("rc-h2");
			Label impHint = new Label(
					"Paste the web address of a recipe. Mealton reads the page and fills in everything below, "
							+ "including the photo. Claude tidies the ingredients when it is switched on. You can change anything before saving.");
			impHint.getStyleClass().add("rc-meta");
			impHint.setWrapText(true);
			impHint.setMinHeight(Region.USE_PREF_SIZE);
			TextField linkField = new TextField();
			linkField.setPromptText("https://…");
			linkField.getStyleClass().add("rc-field");
			HBox.setHgrow(linkField, Priority.ALWAYS);
			Button go = new Button("Import");
			go.getStyleClass().add("primary");
			Runnable runImport = () -> {
				if (importing) {
					return;
				}
				String url = linkField.getText() == null ? "" : linkField.getText().trim();
				importing = true;
				go.setDisable(true);
				status.getStyleClass().setAll("label", "rc-ok");
				status.setText("Reading that page…");
				Task<RecipeImporter.Imported> task = new Task<>() {
					@Override
					protected RecipeImporter.Imported call() throws Exception {
						return facade.importRecipeFromLink(url);
					}
				};
				task.setOnSucceeded(ev -> {
					importing = false;
					go.setDisable(false);
					Recipe got = task.getValue().recipe();
					titleField.setText(got.getTitle());
					draftServings = got.getServings();
					count.setText(String.valueOf(draftServings));
					minus.setDisable(draftServings <= 1);
					prepField.setText(got.getPrepMinutes() > 0 ? String.valueOf(got.getPrepMinutes()) : "");
					cookField.setText(got.getCookMinutes() > 0 ? String.valueOf(got.getCookMinutes()) : "");
					ratingField.setText(got.getPublicRating() > 0 ? Formats.number(got.getPublicRating()) : "");
					ratingCountField
							.setText(got.getPublicRatingCount() > 0 ? String.valueOf(got.getPublicRatingCount()) : "");
					sourceNameField.setText(got.getSourceName() == null ? "" : got.getSourceName());
					sourceUrlField.setText(got.getSourceUrl() == null ? "" : got.getSourceUrl());
					rows.clear();
					ingRows.getChildren().clear();
					for (RecipeIngredient i : got.getIngredients()) {
						addRow.accept(i);
					}
					stepAreas.clear();
					stepRows.getChildren().clear();
					for (String st : got.getSteps()) {
						addStep.accept(st);
					}
					if (stepAreas.isEmpty()) {
						addStep.accept(null);
					}
					String photoNote = "";
					if (task.getValue().imageFile() != null) {
						pendingPhoto = task.getValue().imageFile();
						removeExistingPhoto = false;
						updatePhoto.run();
						photoNote = " The photo was added too.";
					}
					status.getStyleClass().setAll("label", "rc-ok");
					status.setText("Imported '" + got.getTitle() + "'." + photoNote
							+ " Check the details, then press Save recipe. Claude fills in missing nutrition after saving.");
				});
				task.setOnFailed(ev -> {
					importing = false;
					go.setDisable(false);
					status.getStyleClass().setAll("label", "rc-err");
					status.setText(task.getException() == null ? "Could not import that page."
							: task.getException().getMessage());
				});
				Thread thread = new Thread(task, "recipe-import");
				thread.setDaemon(true);
				thread.start();
			};
			go.setOnAction(e -> runImport.run());
			linkField.setOnAction(e -> runImport.run());
			HBox impRow = new HBox(8, linkField, go);
			impRow.setAlignment(Pos.CENTER_LEFT);
			importBox = new VBox(10, impTitle, impHint, impRow);
			importBox.getStyleClass().add("rc-panel");
		}

		choose.setOnAction(e -> {
			File picked = chooseImage(choose);
			if (picked != null) {
				String name = picked.getName().toLowerCase(Locale.ROOT);
				String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
				if (!RecipePhotos.EXTENSIONS.contains(ext)) {
					status.getStyleClass().setAll("label", "rc-err");
					status.setText("Choose a picture file (png, jpg, gif or bmp).");
				} else {
					pendingPhoto = picked.toPath();
					removeExistingPhoto = false;
					status.setText("");
					updatePhoto.run();
				}
			}
		});
		removePhoto.setOnAction(e -> {
			pendingPhoto = null;
			removeExistingPhoto = true;
			updatePhoto.run();
		});

		Button save = new Button(editing == null ? "Save recipe" : "Save changes");
		save.getStyleClass().add("primary");
		save.setOnAction(e -> {
			try {
				List<String> parts = new ArrayList<>();
				for (IngredientRow r : rows) {
					if (r.isBlank()) {
						continue;
					}
					if (r.name.getText().isBlank()) {
						throw new IllegalArgumentException("Every ingredient needs a name.");
					}
					String amount = r.amount.getText().isBlank() ? "1" : r.amount.getText().trim();
					String unit = r.unit.getValue() == null ? "" : r.unit.getValue();
					parts.add(amount + " " + (unit.isEmpty() ? "" : unit + " ") + r.name.getText().trim());
				}
				if (parts.isEmpty()) {
					throw new IllegalArgumentException("Add at least one ingredient.");
				}
				List<String> steps = new ArrayList<>();
				List<StepEdit> keptEdits = new ArrayList<>();
				for (TextArea a : stepAreas) {
					if (!a.getText().isBlank()) {
						steps.add(a.getText().trim().replaceAll("\\s*\\R\\s*", " "));
						keptEdits.add(stepEdits.get(a));
					}
				}
				Recipe made = new Recipe(draftTitle, draftServings,
						RecipeParser.parseIngredients(String.join("; ", parts)), steps);
				made.setTimes(minutesFrom(draftPrep, "Prep time"), minutesFrom(draftCook, "Cook time"));
				made.setSource(draftSourceName, draftSourceUrl);
				made.setTotalOverride(minutesFrom(draftTotal, "Total time"));
				String ratingTyped = draftRating == null ? "" : draftRating.trim().replace(',', '.');
				if (!ratingTyped.isEmpty()) {
					double average;
					try {
						average = Double.parseDouble(ratingTyped);
					} catch (NumberFormatException bad) {
						throw new IllegalArgumentException("The public rating must be a number from 0 to 5.");
					}
					int ratingCount = 0;
					try {
						ratingCount = draftRatingCount == null || draftRatingCount.isBlank() ? 0
								: Integer.parseInt(draftRatingCount.trim().replace(",", ""));
					} catch (NumberFormatException bad) {
						throw new IllegalArgumentException("The number of ratings must be a whole number.");
					}
					made.setPublicRating(average, ratingCount);
				}
				for (int i = 0; i < keptEdits.size(); i++) {
					StepEdit edit = keptEdits.get(i);
					if (edit.chips != null) {
						made.setStepIngredients(i, edit.chips);
					}
					String typed = edit.time.getText() == null ? "" : edit.time.getText().trim();
					if (!typed.isEmpty()) {
						made.setStepTime(i, minutesFrom(typed, "Step " + (i + 1) + " timer"));
					}
				}
				Recipe saved = editedRecipe == null ? facade.saveRecipe(made)
						: facade.updateRecipe(editedRecipe.getId(), made);
				String extra = "";
				try {
					if (removeExistingPhoto && pendingPhoto == null) {
						facade.removeRecipePhoto(saved.getId());
					}
					if (pendingPhoto != null) {
						facade.setRecipePhoto(saved.getId(), pendingPhoto);
					}
				} catch (IllegalArgumentException photoProblem) {
					extra = " (" + photoProblem.getMessage() + ")";
				}
				creating = false;
				tab = Tab.MINE;
				openId = saved.getId();
				detailServings = 0;
				whyText = "";
				doneSteps.clear();
				String verb = editedRecipe == null ? "Saved '" : "Updated '";
				editingId = null;
				resetDrafts();
				setMessageOnly(verb + saved.getTitle() + "'" + (editedRecipe == null ? " to My Recipes." : ".") + extra,
						false);
				onDataChanged.run();
				showDetail();
				if (facade.hasAgent()) {
					estimateInBackground(saved.getId(), false); // fills gaps in the food table, quietly
				}
			} catch (IllegalArgumentException ex) {
				status.getStyleClass().setAll("label", "rc-err");
				status.setText(ex.getMessage());
			}
		});
		Button cancel = new Button("Cancel");
		cancel.setOnAction(e -> {
			creating = false;
			editingId = null;
			message = "";
			resetDrafts();
			showList();
		});
		HBox buttons = new HBox(10, save, cancel);
		buttons.setAlignment(Pos.CENTER_LEFT);
		if (editedRecipe != null) {
			Region grow = new Region();
			HBox.setHgrow(grow, Priority.ALWAYS);
			Button delete = new Button("Delete recipe");
			delete.getStyleClass().add("rc-delete-btn");
			delete.setOnAction(e -> attempt(() -> deleteOwn(editedRecipe)));
			buttons.getChildren().addAll(grow, delete);
		}

		Button backToList = backButton(() -> {
			creating = false;
			editingId = null;
			message = "";
			resetDrafts();
			showList();
		});
		VBox form = new VBox(16, backToList, new VBox(4, title, sub));
		if (importBox != null) {
			form.getChildren().add(importBox);
		}
		form.getChildren().addAll(photoBox, basicsBox, ingBox, stepBox, buttons, status);
		form.setMaxWidth(820);
		form.setPadding(new Insets(0, 8, 24, 4));
		present("create", scroller(form));
	}

	private static int minutesFrom(String text, String what) {
		String t = text == null ? "" : text.trim();
		if (t.isEmpty()) {
			return 0;
		}
		try {
			return Integer.parseInt(t);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(what + " must be a whole number of minutes.");
		}
	}

	// --------------------------------------------------------------- helpers

	private Node messageLabel() {
		Label l = new Label(message);
		l.getStyleClass().add(messageIsError ? "rc-err" : "rc-ok");
		l.setWrapText(true);
		l.setMinHeight(Region.USE_PREF_SIZE);
		if (undoAction == null || !message.equals(undoMessage)) {
			return l;
		}
		Button undo = new Button("Undo");
		undo.getStyleClass().add("rc-link-undo");
		undo.setOnAction(e -> {
			Runnable run = undoAction;
			undoAction = null;
			attempt(run);
		});
		HBox row = new HBox(10, l, undo);
		row.setAlignment(Pos.CENTER_LEFT);
		return row;
	}

	private void setMessageOnly(String text, boolean error) {
		message = text;
		messageIsError = error;
	}

	private void setMessage(String text, boolean error) {
		setMessageOnly(text, error);
		refresh();
	}

	/** Runs an action and shows a red message if it fails with a normal error. */
	private void attempt(Runnable action) {
		try {
			action.run();
		} catch (IllegalArgumentException e) {
			setMessage(e.getMessage(), true);
		}
	}
}