package com.mealton.gui;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.mealton.facade.MealtonFacade;
import com.mealton.nutrition.Nutrients;
import com.mealton.nutrition.RecipeNutrition;
import com.mealton.recipe.Recipe;
import com.mealton.recipe.RecipeIngredient;
import com.mealton.recipe.RecipeMatch;
import com.mealton.recipe.RecipeParser;
import com.mealton.recipe.RecipeTag;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * The "Recipes" page (Stage 1: RecipeBrowserView), laid out like the design: a
 * header, a search box and "Rank by" box, three pill tabs with counts, tag
 * filter chips, a grid of recipe cards, and a detail view for one recipe.
 * <ul>
 * <li><b>Can make now</b>: library recipes you can cook with what is in the
 * pantry (F07)</li>
 * <li><b>Almost there</b>: recipes missing only a few ingredients, with a
 * button to put them on the shopping list (F08)</li>
 * <li><b>My Recipes</b>: recipes you made plus the ones you saved with the
 * heart (F13), with "+ New recipe"</li>
 * </ul>
 * The ranking is plain code; the AI only writes the one-line "Why this one?"
 * reason.
 */
public class RecipeBrowserView extends BorderPane {
	private enum Tab {
		CAN, ALMOST, MINE
	}

	private final MealtonFacade facade;
	private final Runnable onDataChanged;

	private Tab tab = Tab.CAN;
	private final Set<RecipeTag> wantedTags = EnumSet.noneOf(RecipeTag.class);
	private Long openId = null; // recipe shown in the detail view, or null for the list
	private boolean creating = false; // the "+ New recipe" form is open
	private int detailServings = 0; // servings chosen in the detail view (0 = the recipe's own)
	private String whyText = "";
	private String message = "";
	private boolean messageIsError = false;

	private final Label subtitle = new Label();
	private final TextField search = new TextField();
	private final ComboBox<String> rank = new ComboBox<>();
	private final HBox tabBar = new HBox(6);
	private final FlowPane chipBar = new FlowPane(8, 6);
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

		search.setPromptText("Search recipes or ingredients");
		search.getStyleClass().add("rc-search");
		search.setPrefWidth(280);
		search.textProperty().addListener((obs, o, n) -> {
			if (openId == null && !creating) {
				showList();
			}
		});
		rank.getItems().addAll("Best match", "Use expiring first");
		rank.setValue("Best match");
		rank.setOnAction(e -> {
			if (openId == null && !creating) {
				showList();
			}
		});
		HBox tools = new HBox(10, search, new Label("Rank by"), rank);
		tools.setAlignment(Pos.CENTER_LEFT);

		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox top = new HBox(header, spacer, tools);
		top.setAlignment(Pos.CENTER_LEFT);

		tabBar.setAlignment(Pos.CENTER_LEFT);
		VBox head = new VBox(14, top, tabBar, chipBar);
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

	// ------------------------------------------------------------------ list

	private String priority() {
		return "Use expiring first".equals(rank.getValue()) ? "expiring" : "best";
	}

	private List<RecipeMatch> load(Tab which) {
		List<RecipeMatch> matches;
		switch (which) {
		case CAN -> matches = facade.canMakeNow(priority(), EnumSet.copyOf(wantedTags));
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
		String q = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) {
			return matches;
		}
		return matches.stream()
				.filter(m -> m.recipe().getTitle().toLowerCase(Locale.ROOT).contains(q) || m.recipe().getIngredients()
						.stream().anyMatch(i -> i.name().toLowerCase(Locale.ROOT).contains(q)))
				.collect(Collectors.toList());
	}

	private void showList() {
		creating = false;
		openId = null;
		List<RecipeMatch> can = load(Tab.CAN);
		List<RecipeMatch> almost = load(Tab.ALMOST);
		List<RecipeMatch> mine = load(Tab.MINE);

		buildTabs(can.size(), almost.size(), mine.size());
		buildChips();

		List<RecipeMatch> shown = tab == Tab.CAN ? can : tab == Tab.ALMOST ? almost : mine;
		FlowPane grid = new FlowPane(16, 16);
		grid.setPadding(new Insets(4, 4, 16, 4));
		for (RecipeMatch m : shown) {
			grid.getChildren().add(card(m));
		}
		VBox content = new VBox(10);
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
		ScrollPane scroll = new ScrollPane(content);
		scroll.setFitToWidth(true);
		scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
		body.getChildren().setAll(scroll);
	}

	private String emptyText() {
		if (!search.getText().isBlank()) {
			return "No recipes match your search here.";
		}
		if (!wantedTags.isEmpty()) {
			return "No recipes here have all of the selected tags. Clear the filters to see more.";
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
		if (tab == Tab.MINE) {
			Region spacer = new Region();
			HBox.setHgrow(spacer, Priority.ALWAYS);
			Button add = new Button("+ New recipe");
			add.getStyleClass().add("primary");
			add.setOnAction(e -> {
				message = "";
				creating = true;
				showCreateForm();
			});
			tabBar.getChildren().addAll(spacer, add);
		}
	}

	private Button tabButton(String text, int count, Tab which) {
		Button b = new Button(text + " " + count);
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

	private void buildChips() {
		chipBar.getChildren().clear();
		for (RecipeTag t : RecipeTag.values()) {
			Button chip = new Button(t.label());
			chip.getStyleClass().add("rc-chip");
			if (wantedTags.contains(t)) {
				chip.getStyleClass().add("rc-chip-on");
			}
			chip.setOnAction(e -> {
				if (!wantedTags.remove(t)) {
					wantedTags.add(t);
				}
				showList();
			});
			chipBar.getChildren().add(chip);
		}
		if (!wantedTags.isEmpty()) {
			Button clear = new Button("Clear filters");
			clear.getStyleClass().add("rc-link");
			clear.setOnAction(e -> {
				wantedTags.clear();
				showList();
			});
			chipBar.getChildren().add(clear);
		}
	}

	private Node card(RecipeMatch m) {
		Recipe r = m.recipe();
		String letter = r.getTitle().isEmpty() ? "?" : r.getTitle().substring(0, 1).toUpperCase(Locale.ROOT);
		Label big = new Label(letter);
		big.getStyleClass().add("rc-card-letter");
		StackPane top = new StackPane(big);
		top.getStyleClass().add("rc-card-top");
		top.setMinHeight(88);
		top.setPrefHeight(88);

		Label name = new Label(r.getTitle());
		name.getStyleClass().add("rc-card-title");
		name.setWrapText(true);
		name.setMinHeight(Region.USE_PREF_SIZE);

		RecipeNutrition n = RecipeNutrition.of(r);
		String meta = n.counted().isEmpty() ? r.getServings() + " servings"
				: Math.round(n.perServing().calories()) + " kcal · " + Math.round(n.perServing().protein())
						+ " g protein";
		Label metaLabel = new Label(meta);
		metaLabel.getStyleClass().add("rc-meta");

		Label state = new Label(m.isCookable() ? "Ready to cook" : m.missing().size() + " missing");
		state.getStyleClass().add(m.isCookable() ? "rc-ready" : "rc-missing");
		HBox badges = new HBox(6, state);
		badges.setAlignment(Pos.CENTER_LEFT);
		if (!r.isLibrary()) {
			Label mineBadge = new Label("Mine");
			mineBadge.getStyleClass().add("rc-mine");
			badges.getChildren().add(mineBadge);
		} else if (r.isInMyRecipes()) {
			Label saved = new Label("♥ Saved");
			saved.getStyleClass().add("rc-mine");
			badges.getChildren().add(saved);
		}
		if (!m.warnings().isEmpty()) {
			Label warn = new Label("⚠ warning");
			warn.getStyleClass().add("rc-missing");
			badges.getChildren().add(warn);
		}

		FlowPane tags = new FlowPane(5, 5);
		for (RecipeTag t : RecipeTag.tagsOf(m)) {
			Label tl = new Label(t.label());
			tl.getStyleClass().add("rc-tag");
			tags.getChildren().add(tl);
		}
		VBox info = new VBox(6, name, metaLabel, badges, tags);
		if (r.isRated()) {
			Label stars = new Label(starText(r.getRating()));
			stars.getStyleClass().add("rc-stars");
			info.getChildren().add(stars);
		}
		info.setPadding(new Insets(12));

		VBox card = new VBox(top, info);
		card.getStyleClass().add("rc-card");
		card.setPrefWidth(250);
		card.setMinWidth(250);
		card.setMaxWidth(250);
		card.setOnMouseClicked(e -> {
			message = "";
			whyText = "";
			detailServings = 0;
			openId = r.getId();
			showDetail();
		});
		return card;
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
		tabBar.getChildren().clear();
		chipBar.getChildren().clear();
		long id = recipe.getId();
		int servings = detailServings > 0 ? detailServings : recipe.getServings();
		Recipe scaled = servings == recipe.getServings() ? recipe : facade.scaledRecipe(id, servings);

		Button back = new Button("← Back to recipes");
		back.getStyleClass().add("rc-link");
		back.setOnAction(e -> {
			openId = null;
			message = "";
			showList();
		});

		Label title = new Label(recipe.getTitle());
		title.getStyleClass().add("rc-title");
		title.setWrapText(true);
		title.setMinHeight(Region.USE_PREF_SIZE);

		// rating stars
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
		Label ratingText = new Label(recipe.isRated() ? recipe.getRating() + "/5" : "not rated");
		ratingText.getStyleClass().add("rc-meta");
		stars.getChildren().add(ratingText);

		HBox titleRow = new HBox(12, title);
		titleRow.setAlignment(Pos.CENTER_LEFT);
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		titleRow.getChildren().add(spacer);
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
			titleRow.getChildren().add(heart);
		} else {
			Button delete = new Button("Delete recipe");
			delete.setOnAction(e -> attempt(() -> deleteOwn(recipe)));
			titleRow.getChildren().add(delete);
		}

		// tags and status
		FlowPane badges = new FlowPane(6, 6);
		Label state = new Label(match.isCookable() ? "Ready to cook" : match.missing().size() + " missing");
		state.getStyleClass().add(match.isCookable() ? "rc-ready" : "rc-missing");
		badges.getChildren().add(state);
		for (RecipeTag t : RecipeTag.tagsOf(match)) {
			Label tl = new Label(t.label());
			tl.getStyleClass().add("rc-tag");
			badges.getChildren().add(tl);
		}
		if (match.expiringUsed() > 0) {
			Label exp = new Label("uses " + match.expiringUsed() + " expiring");
			exp.getStyleClass().add("rc-missing");
			badges.getChildren().add(exp);
		}

		// servings
		Spinner<Integer> spinner = new Spinner<>(1, 100, servings);
		spinner.setPrefWidth(90);
		spinner.valueProperty().addListener((obs, o, n) -> {
			if (n != null && n != servings) {
				detailServings = n;
				showDetail();
			}
		});
		HBox servingsRow = new HBox(8, new Label("Servings"), spinner);
		servingsRow.setAlignment(Pos.CENTER_LEFT);
		Label servingsNote = new Label(
				servings == recipe.getServings() ? "" : "Scaled copy of the recipe (the saved recipe is unchanged).");
		servingsNote.getStyleClass().add("rc-meta");
		servingsRow.getChildren().add(servingsNote);

		// nutrition
		RecipeNutrition nutrition = RecipeNutrition.of(scaled);
		VBox nutritionBox = new VBox(8);
		nutritionBox.getStyleClass().add("rc-panel");
		Label nutTitle = new Label("Nutrition (estimate)");
		nutTitle.getStyleClass().add("rc-h2");
		nutritionBox.getChildren().add(nutTitle);
		if (nutrition.counted().isEmpty()) {
			Label none = new Label("No nutrition estimate: none of the ingredients are in the food table.");
			none.getStyleClass().add("rc-meta");
			none.setWrapText(true);
			none.setMinHeight(Region.USE_PREF_SIZE);
			nutritionBox.getChildren().add(none);
		} else {
			nutritionBox.getChildren().addAll(nutrientRow("Per serving", nutrition.perServing()),
					nutrientRow("Whole recipe", nutrition.total()));
			if (!nutrition.notCounted().isEmpty()) {
				Label miss = new Label("Not counted: " + String.join(", ", nutrition.notCounted()));
				miss.getStyleClass().add("rc-meta");
				miss.setWrapText(true);
				miss.setMinHeight(Region.USE_PREF_SIZE);
				nutritionBox.getChildren().add(miss);
			}
		}

		// ingredients
		Set<String> have = new HashSet<>();
		for (RecipeIngredient i : match.available()) {
			have.add(i.name().toLowerCase(Locale.ROOT));
		}
		VBox ingredientBox = new VBox(6);
		ingredientBox.getStyleClass().add("rc-panel");
		Label ingTitle = new Label("Ingredients");
		ingTitle.getStyleClass().add("rc-h2");
		ingredientBox.getChildren().add(ingTitle);
		for (RecipeIngredient i : scaled.getIngredients()) {
			boolean haveIt = have.contains(i.name().toLowerCase(Locale.ROOT));
			Label l = new Label((haveIt ? "✓  " : "•  ") + i + (haveIt ? "" : "   (need)"));
			l.getStyleClass().add(haveIt ? "rc-have" : "rc-need");
			l.setWrapText(true);
			l.setMinHeight(Region.USE_PREF_SIZE);
			ingredientBox.getChildren().add(l);
		}

		// steps
		VBox stepBox = new VBox(6);
		stepBox.getStyleClass().add("rc-panel");
		Label stepTitle = new Label("Steps");
		stepTitle.getStyleClass().add("rc-h2");
		stepBox.getChildren().add(stepTitle);
		if (recipe.getSteps().isEmpty()) {
			Label none = new Label("No steps were written for this recipe.");
			none.getStyleClass().add("rc-meta");
			stepBox.getChildren().add(none);
		} else {
			int number = 1;
			for (String step : recipe.getSteps()) {
				Label l = new Label(number++ + ".  " + step);
				l.setWrapText(true);
				l.setMinHeight(Region.USE_PREF_SIZE);
				stepBox.getChildren().add(l);
			}
		}

		// actions
		Button log = new Button("I made this · Log 1 serving");
		log.getStyleClass().add("primary");
		log.setOnAction(e -> attempt(() -> {
			facade.logRecipeServings(id, 1);
			onDataChanged.run();
			setMessage("Logged 1 serving of '" + recipe.getTitle() + "' in today's food diary.", false);
		}));
		Button whyButton = new Button("Why this one? (AI)");
		Button missing = new Button("Add missing to shopping list");
		missing.setDisable(match.missing().isEmpty());
		missing.setOnAction(e -> attempt(() -> {
			List<RecipeIngredient> added = facade.addMissingToShoppingList(id);
			onDataChanged.run();
			setMessage(added.isEmpty() ? "Nothing to add: the pantry and shopping list already cover it."
					: "Added to the shopping list: "
							+ added.stream().map(RecipeIngredient::toString).collect(Collectors.joining(", ")) + ".",
					false);
		}));
		FlowPane actions = new FlowPane(8, 8, log, whyButton, missing);

		Label why = new Label(whyText);
		why.getStyleClass().add("rc-why");
		why.setWrapText(true);
		why.setMinHeight(Region.USE_PREF_SIZE);
		why.setVisible(!whyText.isEmpty());
		why.setManaged(!whyText.isEmpty());
		whyButton.setOnAction(e -> askWhy(id, whyButton, why));

		VBox page = new VBox(14, back, titleRow, stars, badges, servingsRow, actions, why);
		if (!message.isEmpty()) {
			page.getChildren().add(messageLabel());
		}
		HBox columns = new HBox(16, new VBox(14, ingredientBox, nutritionBox), stepBox);
		for (Node c : columns.getChildren()) {
			HBox.setHgrow(c, Priority.ALWAYS);
		}
		((VBox) columns.getChildren().get(0)).setPrefWidth(340);
		stepBox.setPrefWidth(340);
		stepBox.setMaxHeight(Region.USE_PREF_SIZE);
		columns.setAlignment(Pos.TOP_LEFT);
		page.getChildren().add(columns);
		page.setPadding(new Insets(0, 8, 20, 4));

		ScrollPane scroll = new ScrollPane(page);
		scroll.setFitToWidth(true);
		scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
		body.getChildren().setAll(scroll);
	}

	private Node nutrientRow(String label, Nutrients n) {
		HBox row = new HBox(18);
		Label name = new Label(label);
		name.setMinWidth(90);
		name.getStyleClass().add("rc-meta");
		row.getChildren().add(name);
		row.getChildren().addAll(stat(Math.round(n.calories()) + "", "kcal"),
				stat(Math.round(n.protein()) + " g", "protein"), stat(Math.round(n.carbs()) + " g", "carbs"),
				stat(Math.round(n.fat()) + " g", "fat"));
		row.setAlignment(Pos.CENTER_LEFT);
		return row;
	}

	private Node stat(String value, String unit) {
		Label v = new Label(value);
		v.getStyleClass().add("rc-h2");
		Label u = new Label(unit);
		u.getStyleClass().add("rc-meta");
		return new VBox(0, v, u);
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
		tabBar.getChildren().clear();
		chipBar.getChildren().clear();

		Button back = new Button("← Back to recipes");
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

		HBox titleRow = new HBox(8, titleField, new Label("Servings"), servingsField);
		titleRow.setAlignment(Pos.CENTER_LEFT);
		HBox.setHgrow(titleField, Priority.ALWAYS);
		VBox form = new VBox(10, titleRow, ingredients, steps, new HBox(8, save, cancel), status);
		form.getStyleClass().add("rc-panel");
		form.setMaxWidth(720);

		VBox page = new VBox(14, back, title, form);
		ScrollPane scroll = new ScrollPane(page);
		scroll.setFitToWidth(true);
		scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
		body.getChildren().setAll(scroll);
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