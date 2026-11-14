package com.mealton.gui;

import java.time.LocalDate;
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
import java.util.stream.Collectors;

import com.mealton.facade.MealtonFacade;
import com.mealton.nutrition.Nutrients;
import com.mealton.nutrition.RecipeNutrition;
import com.mealton.pantry.PantryItem;
import com.mealton.plan.MealSlot;
import com.mealton.profile.UserProfile;
import com.mealton.recipe.Recipe;
import com.mealton.recipe.RecipeIngredient;
import com.mealton.recipe.RecipeMatch;
import com.mealton.recipe.RecipeParser;
import com.mealton.recipe.RecipeTag;
import com.mealton.shopping.ShoppingItem;
import com.mealton.util.Formats;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

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
		CAN, ALMOST, MINE
	}

	private final MealtonFacade facade;
	private final Runnable onDataChanged;

	private Tab tab = Tab.CAN;
	private final Set<RecipeTag> wantedTags = EnumSet.noneOf(RecipeTag.class);
	private final List<String> customFilters = new ArrayList<>();
	private boolean suggestedFilters = true;
	private String filterDraft = "";
	private String filterError = "";
	private final Map<Long, List<Long>> addedToList = new HashMap<>(); // recipe id -> shopping item ids (for Undo)
	private Long openId = null; // recipe shown in the detail view, or null for the list
	private boolean creating = false; // the "+ New recipe" form is open
	private int detailServings = 0; // servings chosen in the detail view (0 = the recipe's own)
	private boolean wholeRecipe = false;
	private String whyText = "";
	private String message = "";
	private boolean messageIsError = false;

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
		setPadding(new Insets(28, 32, 20, 32));

		Label title = new Label("Recipes");
		title.getStyleClass().add("rc-title");
		subtitle.getStyleClass().add("rc-sub");
		VBox header = new VBox(2, title, subtitle);

		search.setPromptText("Search recipes");
		search.getStyleClass().add("rc-search");
		search.setPrefWidth(260);
		search.textProperty().addListener((obs, o, n) -> {
			if (openId == null && !creating) {
				showList();
			}
		});
		rank.getItems().addAll("Use up expiring food first", "Best pantry match", "Best macro fit");
		rank.setValue("Use up expiring food first");
		rank.setOnAction(e -> {
			if (openId == null && !creating) {
				showList();
			}
		});
		Label rankLabel = new Label("Rank by");
		rankLabel.getStyleClass().add("rc-text");
		HBox tools = new HBox(10, search, rankLabel, rank);
		tools.setAlignment(Pos.CENTER_LEFT);

		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox top = new HBox(header, spacer, tools);
		top.setAlignment(Pos.CENTER_LEFT);

		tabBar.setAlignment(Pos.CENTER_LEFT);
		filterBox.getStyleClass().add("rc-panel");
		VBox head = new VBox(14, top, tabBar, filterBox);
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
			showCreateForm();
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

	private void hideListChrome() {
		tabBar.getChildren().clear();
		filterBox.getChildren().clear();
		filterBox.setVisible(false);
		filterBox.setManaged(false);
	}

	private static ScrollPane scroller(Node content) {
		ScrollPane scroll = new ScrollPane(content);
		scroll.setFitToWidth(true);
		scroll.getStyleClass().add("rc-scroll");
		return scroll;
	}

	// ------------------------------------------------------------------ list

	private List<RecipeMatch> load(Tab which) {
		List<RecipeMatch> matches;
		String priority = "Best pantry match".equals(rank.getValue()) ? "best" : "expiring";
		switch (which) {
		case CAN -> matches = facade.canMakeNow(priority, EnumSet.copyOf(wantedTags));
		case ALMOST -> matches = facade.almostThere(EnumSet.copyOf(wantedTags));
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
		if ("Best macro fit".equals(rank.getValue())) {
			result.sort(Comparator
					.comparingDouble((RecipeMatch m) -> -RecipeNutrition.of(m.recipe()).perServing().protein()));
		}
		return result;
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
		return false;
	}

	private void showList() {
		creating = false;
		openId = null;
		List<RecipeMatch> can = load(Tab.CAN);
		List<RecipeMatch> almost = load(Tab.ALMOST);
		List<RecipeMatch> mine = load(Tab.MINE);

		buildTabs(can.size(), almost.size(), mine.size());
		buildFilters();

		List<RecipeMatch> shown = tab == Tab.CAN ? can : tab == Tab.ALMOST ? almost : mine;
		FlowPane grid = new FlowPane(16, 16);
		grid.setPadding(new Insets(4, 4, 16, 4));
		for (RecipeMatch m : shown) {
			grid.getChildren().add(card(m));
		}
		VBox content = new VBox(12);
		content.setPadding(new Insets(0, 4, 0, 0));
		if (tab == Tab.MINE) {
			Label hint = new Label("Write your own recipe. Mealton tags it automatically.");
			hint.getStyleClass().add("rc-text");
			Region sp = new Region();
			HBox.setHgrow(sp, Priority.ALWAYS);
			Button add = new Button("+ New recipe");
			add.getStyleClass().add("primary");
			add.setOnAction(e -> {
				message = "";
				creating = true;
				showCreateForm();
			});
			HBox banner = new HBox(10, hint, sp, add);
			banner.setAlignment(Pos.CENTER_LEFT);
			banner.getStyleClass().add("rc-banner");
			content.getChildren().add(banner);
		}
		if (!message.isEmpty()) {
			content.getChildren().add(messageLabel());
		}
		if (shown.isEmpty()) {
			Label empty = new Label(emptyText());
			empty.setWrapText(true);
			empty.setMinHeight(Region.USE_PREF_SIZE);
			empty.getStyleClass().add("rc-empty");
			content.getChildren().add(empty);
		} else {
			content.getChildren().add(grid);
		}
		Label foot = new Label("Pictures are not stored yet: each recipe shows its first letter instead.");
		foot.getStyleClass().add("rc-meta");
		content.getChildren().add(foot);
		body.getChildren().setAll(scroller(content));
	}

	private int activeFilterCount() {
		return wantedTags.size() + customFilters.size();
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
		case MINE ->
			"My Recipes is empty. Save recipes you like with the heart, or press '+ New recipe' to write your own.";
		};
	}

	private void buildTabs(int can, int almost, int mine) {
		tabBar.getChildren().setAll(tabButton("Can make now", can, Tab.CAN),
				tabButton("Almost there", almost, Tab.ALMOST), tabButton("My Recipes", mine, Tab.MINE));
	}

	private Button tabButton(String text, int count, Tab which) {
		Button b = new Button(text + "  " + count);
		b.getStyleClass().add("rc-tab");
		if (tab == which) {
			b.getStyleClass().add("rc-tab-on");
		}
		b.setOnAction(e -> {
			tab = which;
			message = "";
			showList();
		});
		return b;
	}

	private void buildFilters() {
		filterBox.setVisible(true);
		filterBox.setManaged(true);
		Label heading = new Label("Filters  " + activeFilterCount());
		heading.getStyleClass().add("rc-h2");
		Region sp = new Region();
		HBox.setHgrow(sp, Priority.ALWAYS);
		Button clear = new Button("Clear filters");
		clear.getStyleClass().add("rc-link");
		clear.setOnAction(e -> {
			wantedTags.clear();
			customFilters.clear();
			filterError = "";
			showList();
		});
		Button suggested = new Button(suggestedFilters ? "Suggested filters: on" : "Suggested filters: off");
		suggested.getStyleClass().add("rc-chip");
		if (suggestedFilters) {
			suggested.getStyleClass().add("rc-chip-on");
		}
		suggested.setOnAction(e -> {
			suggestedFilters = !suggestedFilters;
			if (!suggestedFilters) {
				wantedTags.clear();
			}
			showList();
		});
		HBox headRow = new HBox(10, heading, sp, clear, suggested);
		headRow.setAlignment(Pos.CENTER_LEFT);

		FlowPane chips = new FlowPane(8, 8);
		if (suggestedFilters) {
			for (RecipeTag t : RecipeTag.values()) {
				Button chip = new Button(t.label());
				chip.getStyleClass().add("rc-chip");
				if (wantedTags.contains(t)) {
					chip.getStyleClass().add("rc-chip-on");
				} else {
					chip.getStyleClass().add("rc-chip-dash");
				}
				chip.setOnAction(e -> {
					if (!wantedTags.remove(t)) {
						wantedTags.add(t);
					}
					showList();
				});
				chips.getChildren().add(chip);
			}
		}
		for (String f : customFilters) {
			Button chip = new Button(f + "  ×");
			chip.getStyleClass().addAll("rc-chip", "rc-chip-custom");
			chip.setOnAction(e -> {
				customFilters.remove(f);
				showList();
			});
			chips.getChildren().add(chip);
		}
		TextField draft = new TextField(filterDraft);
		draft.setPromptText("New filter, e.g. spicy");
		draft.getStyleClass().add("rc-search");
		draft.setPrefWidth(190);
		draft.textProperty().addListener((obs, o, n) -> filterDraft = n);
		Button add = new Button("+ Add");
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
		HBox addRow = new HBox(8, draft, add);
		addRow.setAlignment(Pos.CENTER_LEFT);
		chips.getChildren().add(addRow);

		filterBox.getChildren().setAll(headRow, chips);
		if (!filterError.isEmpty()) {
			Label err = new Label(filterError);
			err.getStyleClass().add("rc-err");
			filterBox.getChildren().add(err);
		}
		Label hint = new Label("Your filters match recipe names, tags and ingredients. Selected tags must all apply.");
		hint.getStyleClass().add("rc-meta");
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
		top.setMinHeight(96);
		top.setPrefHeight(96);
		if (expiring != null) {
			Label badge = new Label(capitalise(expiring.getName()) + " · " + daysText(expiring.daysLeft()));
			badge.getStyleClass().add("rc-expiry");
			StackPane.setAlignment(badge, Pos.BOTTOM_LEFT);
			StackPane.setMargin(badge, new Insets(0, 0, 8, 8));
			top.getChildren().add(badge);
		}
		boolean saved = r.isInMyRecipes();
		Button heart = new Button(saved ? "♥" : "♡");
		heart.getStyleClass().add("rc-heart-card");
		if (saved) {
			heart.getStyleClass().add("rc-heart-card-on");
		}
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
		HBox.setHgrow(name, Priority.ALWAYS);
		name.setMaxWidth(Double.MAX_VALUE);
		HBox titleRow = new HBox(6, name);
		if (r.isRated()) {
			Label stars = new Label(starText(r.getRating()));
			stars.getStyleClass().add("rc-stars");
			titleRow.getChildren().add(stars);
		}

		// meta: kcal, ingredients you have
		RecipeNutrition n = RecipeNutrition.of(r);
		int total = m.available().size() + m.missing().size();
		Label kcal = new Label(n.counted().isEmpty() ? r.getServings() + " servings"
				: Math.round(n.perServing().calories()) + " kcal");
		Label have = new Label(m.available().size() + "/" + total + " in pantry");
		kcal.getStyleClass().add("rc-meta");
		have.getStyleClass().add("rc-meta");
		HBox meta = new HBox(12, kcal, have);

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
		fill.prefWidthProperty().bind(track.widthProperty().multiply(frac));
		fill.maxWidthProperty().bind(track.widthProperty().multiply(frac));

		Label why = new Label(cardWhy(m, expiring));
		why.getStyleClass().add("rc-why-line");
		why.setWrapText(true);
		why.setMinHeight(Region.USE_PREF_SIZE);

		VBox info = new VBox(8, titleRow, meta, track, why);

		// missing + add-to-list + undo
		if (!m.missing().isEmpty()) {
			FlowPane missing = new FlowPane(5, 5);
			Label ml = new Label("Missing");
			ml.getStyleClass().add("rc-meta");
			missing.getChildren().add(ml);
			for (RecipeIngredient i : m.missing()) {
				Label c = new Label(i.name());
				c.getStyleClass().add("rc-miss-chip");
				missing.getChildren().add(c);
			}
			info.getChildren().add(missing);
			List<Long> added = addedToList.get(id);
			if (added == null) {
				Button add = new Button("Add missing to list");
				add.setOnAction(e -> attempt(() -> addMissing(id)));
				info.getChildren().add(add);
			} else {
				Label done = new Label(
						added.isEmpty() ? "Already on your list" : "Added " + added.size() + " to your list");
				done.getStyleClass().add("rc-ok");
				Button undo = new Button("Undo");
				undo.getStyleClass().add("rc-link");
				undo.setOnAction(e -> attempt(() -> {
					if (!added.isEmpty()) {
						facade.deleteShoppingItems(added);
						onDataChanged.run();
					}
					addedToList.remove(id);
					refresh();
				}));
				HBox row = new HBox(8, done, undo);
				row.setAlignment(Pos.CENTER_LEFT);
				info.getChildren().add(row);
			}
		}

		FlowPane tags = new FlowPane(5, 5);
		for (RecipeTag t : RecipeTag.tagsOf(m)) {
			Label tl = new Label(t.label());
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
		Label foot = new Label(r.isLibrary() ? "Library recipe" : "Your recipe");
		foot.getStyleClass().add("rc-meta");
		info.getChildren().add(foot);
		info.setPadding(new Insets(12));

		VBox card = new VBox(top, info);
		card.getStyleClass().add("rc-card");
		card.setPrefWidth(270);
		card.setMinWidth(270);
		card.setMaxWidth(270);
		card.setOnMouseClicked(e -> {
			message = "";
			whyText = "";
			detailServings = 0;
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

	private static String starText(int rating) {
		return "★".repeat(rating) + "☆".repeat(5 - rating);
	}

	// ---------------------------------------------------------------- detail

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

		Button back = new Button("← All recipes");
		back.getStyleClass().add("rc-link");
		back.setOnAction(e -> {
			openId = null;
			message = "";
			showList();
		});

		// tags
		FlowPane tags = new FlowPane(6, 6);
		for (RecipeTag t : RecipeTag.tagsOf(match)) {
			Label tl = new Label(t.label());
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

		Label meta = new Label(recipe.getServings() + " servings · "
				+ (match.isCookable() ? "all " + total + " ingredients in your pantry"
						: match.available().size() + " of " + total + " ingredients in your pantry"));
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

		// action buttons
		Button log = new Button("✓  I made this · Log it");
		log.getStyleClass().add("primary");
		log.setOnAction(e -> attempt(() -> {
			facade.logRecipeServings(id, 1);
			onDataChanged.run();
			setMessage("Logged 1 serving of '" + recipe.getTitle() + "' in today's food diary.", false);
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
			Button delete = new Button("Delete recipe");
			delete.setOnAction(e -> attempt(() -> deleteOwn(recipe)));
			actions.getChildren().add(delete);
		}
		Button whyButton = new Button("Why this one? (AI)");
		actions.getChildren().add(whyButton);
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

		Label why = new Label(whyText);
		why.getStyleClass().add("rc-why");
		why.setWrapText(true);
		why.setMinHeight(Region.USE_PREF_SIZE);
		why.setVisible(!whyText.isEmpty());
		why.setManaged(!whyText.isEmpty());
		whyButton.setOnAction(e -> askWhy(id, whyButton, why));

		VBox hero = new VBox(12, tags, title, meta, stars, actions, why);
		hero.getStyleClass().add("rc-panel");
		if (!message.isEmpty()) {
			hero.getChildren().add(messageLabel());
		}

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
		HBox ingHead = new HBox(8, ingTitle, sp, servLabel, minus, count, plus);
		ingHead.setAlignment(Pos.CENTER_LEFT);

		VBox ingredientBox = new VBox(0, ingHead);
		ingredientBox.getStyleClass().add("rc-panel");
		for (RecipeIngredient i : scaled.getIngredients()) {
			boolean haveIt = have.contains(i.name().toLowerCase(Locale.ROOT));
			Label qty = new Label(Formats.quantityWithUnit(i.quantity(), i.unit()));
			qty.getStyleClass().addAll("rc-h2", "rc-text");
			qty.setMinWidth(90);
			Label nm = new Label(i.name());
			nm.getStyleClass().add("rc-text");
			HBox.setHgrow(nm, Priority.ALWAYS);
			nm.setMaxWidth(Double.MAX_VALUE);
			Label pill = new Label(haveIt ? "In pantry" : "Missing");
			pill.getStyleClass().add(haveIt ? "rc-pill-have" : "rc-pill-need");
			HBox row = new HBox(12, qty, nm, pill);
			row.setAlignment(Pos.CENTER_LEFT);
			row.setPadding(new Insets(10, 0, 10, 0));
			ingredientBox.getChildren().add(row);
		}
		if (servings != recipe.getServings()) {
			Label note = new Label("Scaled copy of the recipe. The saved recipe is unchanged.");
			note.getStyleClass().add("rc-meta");
			ingredientBox.getChildren().add(note);
		}

		// method
		VBox methodBox = new VBox(12);
		methodBox.getStyleClass().add("rc-panel");
		Label methodTitle = new Label("Method");
		methodTitle.getStyleClass().add("rc-h2");
		methodBox.getChildren().add(methodTitle);
		if (recipe.getSteps().isEmpty()) {
			Label none = new Label("No steps were written for this recipe.");
			none.getStyleClass().add("rc-meta");
			methodBox.getChildren().add(none);
		} else {
			int number = 1;
			for (String step : recipe.getSteps()) {
				Label num = new Label(String.valueOf(number++));
				num.getStyleClass().add("rc-step-num");
				num.setMinSize(28, 28);
				num.setMaxSize(28, 28);
				Label text = new Label(step);
				text.getStyleClass().add("rc-text");
				text.setWrapText(true);
				text.setMinHeight(Region.USE_PREF_SIZE);
				HBox.setHgrow(text, Priority.ALWAYS);
				HBox row = new HBox(14, num, text);
				row.setAlignment(Pos.TOP_LEFT);
				methodBox.getChildren().add(row);
			}
		}

		VBox left = new VBox(16, ingredientBox, methodBox);
		HBox.setHgrow(left, Priority.ALWAYS);
		VBox right = nutritionBox(scaled, servings);
		right.setMinWidth(300);
		right.setPrefWidth(320);
		right.setMaxWidth(340);
		right.setMaxHeight(Region.USE_PREF_SIZE);
		HBox columns = new HBox(16, left, right);
		columns.setAlignment(Pos.TOP_LEFT);

		VBox page = new VBox(16, back, hero, columns);
		page.setPadding(new Insets(0, 8, 20, 4));
		body.getChildren().setAll(scroller(page));
	}

	private VBox nutritionBox(Recipe scaled, int servings) {
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
		HBox seg = new HBox(3, per, whole);
		seg.getStyleClass().add("rc-seg-bar");
		Region sp = new Region();
		HBox.setHgrow(sp, Priority.ALWAYS);
		HBox head = new HBox(8, title, sp, seg);
		head.setAlignment(Pos.CENTER_LEFT);
		box.getChildren().add(head);

		if (nutrition.counted().isEmpty()) {
			Label none = new Label("No nutrition estimate: none of the ingredients are in the food table.");
			none.getStyleClass().add("rc-meta");
			none.setWrapText(true);
			none.setMinHeight(Region.USE_PREF_SIZE);
			box.getChildren().add(none);
			return box;
		}
		Nutrients n = wholeRecipe ? nutrition.total() : nutrition.perServing();
		Label kcal = new Label(String.valueOf(Math.round(n.calories())));
		kcal.getStyleClass().add("rc-kcal");
		Label basis = new Label(wholeRecipe ? "kcal for " + servings + " servings" : "kcal per serving");
		basis.getStyleClass().add("rc-sub");
		box.getChildren().add(new VBox(0, kcal, basis));

		UserProfile p = facade.getProfile();
		box.getChildren().add(macroRow("Protein", n.protein(), p.proteinGoal()));
		box.getChildren().add(macroRow("Carbs", n.carbs(), p.carbsGoal()));
		box.getChildren().add(macroRow("Fat", n.fat(), p.fatGoal()));

		boolean anyGoal = p.proteinGoal() > 0 || p.carbsGoal() > 0 || p.fatGoal() > 0;
		String note = (anyGoal ? "Bars show the share of your daily targets. "
				: "Set daily targets on the Profile page to see bars. ")
				+ "These are estimates from the built-in food table.";
		if (!nutrition.notCounted().isEmpty()) {
			note += " Not counted: " + String.join(", ", nutrition.notCounted()) + ".";
		}
		Label noteLabel = new Label(note);
		noteLabel.getStyleClass().add("rc-meta");
		noteLabel.setWrapText(true);
		noteLabel.setMinHeight(Region.USE_PREF_SIZE);
		box.getChildren().add(noteLabel);
		return box;
	}

	private Node macroRow(String name, double grams, int goal) {
		Label nm = new Label(name);
		nm.getStyleClass().addAll("rc-h2", "rc-text");
		Region sp = new Region();
		HBox.setHgrow(sp, Priority.ALWAYS);
		Label val = new Label(Math.round(grams) + " g");
		val.getStyleClass().add("rc-meta");
		HBox row = new HBox(8, nm, sp, val);
		VBox v = new VBox(5, row);
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
			v.getChildren().add(track);
		}
		return v;
	}

	/** Asks for a date and a meal, then puts the recipe on the meal calendar. */
	private void addToCalendar(Recipe recipe) {
		Dialog<ButtonType> dialog = new Dialog<>();
		dialog.setTitle("Add to calendar");
		dialog.setHeaderText("Add '" + recipe.getTitle() + "' to your meal calendar");
		DatePicker date = new DatePicker(LocalDate.now());
		ComboBox<MealSlot> slot = new ComboBox<>();
		slot.getItems().addAll(MealSlot.values());
		slot.setValue(MealSlot.DINNER);
		VBox form = new VBox(10, new Label("Date"), date, new Label("Meal"), slot);
		form.setPadding(new Insets(10));
		dialog.getDialogPane().setContent(form);
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
		Optional<ButtonType> answer = dialog.showAndWait();
		if (answer.isPresent() && answer.get() == ButtonType.OK) {
			if (date.getValue() == null) {
				throw new IllegalArgumentException("Choose a date first.");
			}
			facade.setCalendarMeal(date.getValue(), slot.getValue().name().toLowerCase(Locale.ROOT), recipe.getId());
			onDataChanged.run();
			setMessage("Added '" + recipe.getTitle() + "' to " + slot.getValue().label().toLowerCase(Locale.ROOT)
					+ " on " + date.getValue() + ".", false);
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

	private void deleteOwn(Recipe recipe) {
		Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete '" + recipe.getTitle() + "' for good?",
				ButtonType.YES, ButtonType.NO);
		Optional<ButtonType> answer = confirm.showAndWait();
		if (answer.isPresent() && answer.get() == ButtonType.YES) {
			facade.deleteRecipe(recipe.getId());
			openId = null;
			tab = Tab.MINE;
			setMessage("Deleted '" + recipe.getTitle() + "'.", false);
		}
	}

	// ------------------------------------------------------------- new recipe

	private void showCreateForm() {
		hideListChrome();

		Button back = new Button("← All recipes");
		back.getStyleClass().add("rc-link");
		back.setOnAction(e -> {
			creating = false;
			message = "";
			showList();
		});
		Label title = new Label("New recipe");
		title.getStyleClass().add("rc-title");

		TextField titleField = new TextField();
		titleField.setPromptText("Title, e.g. Pancakes");
		TextField servingsField = new TextField("2");
		servingsField.setPrefColumnCount(4);
		TextArea ingredients = new TextArea();
		ingredients.setPromptText("Ingredients on one line, separated by semicolons:  200 g flour; 2 egg; 0.3 l milk");
		ingredients.setPrefRowCount(3);
		ingredients.setWrapText(true);
		TextField steps = new TextField();
		steps.setPromptText("Steps separated by | (optional), e.g. Mix|Fry");
		Label status = new Label(message);
		status.getStyleClass().add(messageIsError ? "rc-err" : "rc-ok");
		status.setWrapText(true);
		status.setMinHeight(Region.USE_PREF_SIZE);

		Button save = new Button("Save recipe");
		save.getStyleClass().add("primary");
		save.setOnAction(e -> {
			try {
				int count;
				try {
					count = Integer.parseInt(servingsField.getText().trim());
				} catch (NumberFormatException bad) {
					throw new IllegalArgumentException("Servings must be a whole number.");
				}
				Recipe made = new Recipe(titleField.getText(), count,
						RecipeParser.parseIngredients(ingredients.getText()), RecipeParser.parseSteps(steps.getText()));
				Recipe saved = facade.saveRecipe(made);
				creating = false;
				tab = Tab.MINE;
				openId = saved.getId();
				detailServings = 0;
				whyText = "";
				setMessageOnly("Saved '" + saved.getTitle() + "' to My Recipes.", false);
				onDataChanged.run();
				showDetail();
			} catch (IllegalArgumentException ex) {
				status.getStyleClass().setAll("label", "rc-err");
				status.setText(ex.getMessage());
			}
		});
		Button cancel = new Button("Cancel");
		cancel.setOnAction(e -> {
			creating = false;
			message = "";
			showList();
		});

		Label servLabel = new Label("Servings");
		servLabel.getStyleClass().add("rc-text");
		HBox titleRow = new HBox(8, titleField, servLabel, servingsField);
		titleRow.setAlignment(Pos.CENTER_LEFT);
		HBox.setHgrow(titleField, Priority.ALWAYS);
		VBox form = new VBox(10, titleRow, ingredients, steps, new HBox(8, save, cancel), status);
		form.getStyleClass().add("rc-panel");
		form.setMaxWidth(720);

		VBox page = new VBox(14, back, title, form);
		body.getChildren().setAll(scroller(page));
	}

	// --------------------------------------------------------------- helpers

	private Label messageLabel() {
		Label l = new Label(message);
		l.getStyleClass().add(messageIsError ? "rc-err" : "rc-ok");
		l.setWrapText(true);
		l.setMinHeight(Region.USE_PREF_SIZE);
		return l;
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