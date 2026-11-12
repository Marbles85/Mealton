package com.mealton.gui;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import com.mealton.facade.MealtonFacade;
import com.mealton.recipe.Recipe;
import com.mealton.recipe.RecipeIngredient;
import com.mealton.recipe.RecipeMatch;
import com.mealton.recipe.RecipeTag;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The "Recipes" page (Stage 1: RecipeBrowserView), one section with three tabs:
 * <ul>
 * <li><b>Can make now</b>: library recipes you can cook with what is in the
 * pantry (F07)</li>
 * <li><b>Almost there</b>: recipes missing only a few ingredients, with a
 * button to put them on the shopping list (F08)</li>
 * <li><b>My Recipes</b>: recipes you made plus the ones you saved with the Save
 * button (F13)</li>
 * </ul>
 * The first two tabs can be filtered with tag checkboxes (F09). The ranking and
 * the tags are plain code; the AI only writes the one-line "Why this one?"
 * reason.
 */
public class RecipeBrowserView extends BorderPane {
	private final TabPane tabs = new TabPane();
	private final MatchPane canMakeNow;
	private final MatchPane almostThere;
	private final RecipesView myRecipes;

	public RecipeBrowserView(MealtonFacade facade, Runnable onDataChanged) {
		setPadding(new Insets(12));

		canMakeNow = new MatchPane(facade, facade::canMakeNow, true, false,
				"Nothing can be cooked with your pantry right now. Check the 'Almost there' tab for recipes that need a few more ingredients.",
				onDataChanged, this::refresh);
		almostThere = new MatchPane(facade, (priority, tags) -> facade.almostThere(tags), false, true,
				"No recipes are within " + MealtonFacade.ALMOST_THERE_LIMIT
						+ " ingredients of being cookable yet. Add more food to your pantry to see some here.",
				onDataChanged, this::refresh);
		myRecipes = new RecipesView(facade, onDataChanged, this::refresh);

		tabs.getTabs().addAll(new Tab("Can make now", canMakeNow), new Tab("Almost there", almostThere),
				new Tab("My Recipes", myRecipes));
		tabs.getTabs().forEach(t -> t.setClosable(false));
		tabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> refresh());

		Label intro = new Label("Recipes picked for your pantry. Save the ones you like to My Recipes.");
		intro.setPadding(new Insets(0, 0, 8, 0));
		setTop(intro);
		setCenter(tabs);
		refresh();
	}

	/**
	 * Reloads all three tabs (call this whenever the pantry, recipes or profile may
	 * have changed).
	 */
	public void refresh() {
		canMakeNow.refresh();
		almostThere.refresh();
		myRecipes.refresh();
	}

	/**
	 * One list of recipe matches with details, tag filters, a Save button and an AI
	 * "why" line.
	 */
	private static final class MatchPane extends BorderPane {
		private final MealtonFacade facade;
		private final BiFunction<String, Set<RecipeTag>, List<RecipeMatch>> source;
		private final Set<RecipeTag> wantedTags = EnumSet.noneOf(RecipeTag.class);
		private final FlowPane tagBar = new FlowPane(10, 4);
		private final String emptyHelp;
		private final Runnable onListChanged; // pantry/shopping pages refresh
		private final Runnable onRecipesChanged; // all recipe tabs refresh
		private final ListView<RecipeMatch> list = new ListView<>();
		private final TextArea detail = new TextArea();
		private final Label why = new Label();
		private final Label status = new Label();
		private final Button save = new Button("Save to My Recipes");
		private final Button whyButton = new Button("Why this one? (AI)");
		private final ComboBox<String> priority = new ComboBox<>();

		MatchPane(MealtonFacade facade, BiFunction<String, Set<RecipeTag>, List<RecipeMatch>> source,
				boolean offerPriority, boolean offerMissing, String emptyHelp, Runnable onListChanged,
				Runnable onRecipesChanged) {
			this.facade = facade;
			this.source = source;
			this.emptyHelp = emptyHelp;
			this.onListChanged = onListChanged;
			this.onRecipesChanged = onRecipesChanged;
			setPadding(new Insets(10, 0, 0, 0));

			list.setPrefWidth(280);
			list.setCellFactory(v -> new ListCell<>() {
				@Override
				protected void updateItem(RecipeMatch m, boolean empty) {
					super.updateItem(m, empty);
					if (empty || m == null) {
						setText(null);
						return;
					}
					String state = m.isCookable() ? "ready" : m.missing().size() + " missing";
					setText(m.recipe().getTitle() + "  (" + state + ")"
							+ (m.recipe().isInMyRecipes() ? "  [saved]" : "")
							+ (m.warnings().isEmpty() ? "" : "  [warning]"));
				}
			});
			list.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
				why.setText("");
				showSelected();
			});

			// tag filter row (F09): a recipe must have ALL the ticked tags
			tagBar.getChildren().add(new Label("Only show:"));
			for (RecipeTag tag : RecipeTag.values()) {
				CheckBox box = new CheckBox(tag.label());
				box.setOnAction(e -> {
					if (box.isSelected()) {
						wantedTags.add(tag);
					} else {
						wantedTags.remove(tag);
					}
					refresh();
				});
				tagBar.getChildren().add(box);
			}
			tagBar.setPadding(new Insets(0, 0, 8, 0));
			setTop(tagBar);

			VBox left = new VBox(8, list);
			VBox.setVgrow(list, Priority.ALWAYS);
			left.setPadding(new Insets(0, 12, 0, 0));
			setLeft(left);

			detail.setEditable(false);
			detail.setWrapText(true);
			VBox.setVgrow(detail, Priority.ALWAYS);
			why.setWrapText(true);
			why.setStyle("-fx-font-style: italic;");

			save.setOnAction(e -> attempt(() -> {
				Recipe r = selectedMatch().recipe();
				facade.saveToMyRecipes(r.getId());
				onRecipesChanged.run();
				ok("Saved '" + r.getTitle() + "' to My Recipes.");
			}));
			whyButton.setOnAction(e -> askWhy());

			HBox buttons = new HBox(8, save, whyButton);
			if (offerMissing) {
				Button missing = new Button("Add missing to shopping list");
				missing.setOnAction(e -> attempt(() -> {
					RecipeMatch m = selectedMatch();
					List<RecipeIngredient> added = facade.addMissingToShoppingList(m.recipe().getId());
					onListChanged.run();
					refresh();
					ok(added.isEmpty() ? "Nothing to add: the pantry and shopping list already cover it."
							: "Added to the shopping list: " + joinIngredients(added) + ".");
				}));
				buttons.getChildren().add(missing);
			}
			if (offerPriority) {
				priority.getItems().addAll("best", "expiring");
				priority.setValue("best");
				priority.setOnAction(e -> refresh());
				buttons.getChildren().addAll(new Label("Show first:"), priority);
			}

			VBox centre = new VBox(8, detail, why, buttons, status);
			setCenter(centre);
		}

		void refresh() {
			Recipe previous = list.getSelectionModel().getSelectedItem() == null ? null
					: list.getSelectionModel().getSelectedItem().recipe();
			List<RecipeMatch> matches = source.apply(priority.getValue() == null ? "best" : priority.getValue(),
					EnumSet.copyOf(wantedTags));
			list.getItems().setAll(matches);
			if (previous != null) {
				for (RecipeMatch m : matches) {
					if (m.recipe().getId() == previous.getId()) {
						list.getSelectionModel().select(m);
						break;
					}
				}
			}
			showSelected();
		}

		private void showSelected() {
			RecipeMatch m = list.getSelectionModel().getSelectedItem();
			if (m == null) {
				save.setDisable(true);
				whyButton.setDisable(true);
				if (list.getItems().isEmpty()) {
					detail.setText(!wantedTags.isEmpty()
							? "No recipes here have all of the ticked tags. Untick one to see more."
							: facade.getPantryItems().isEmpty()
									? "Your pantry is empty. Add food on the 'Pantry & List' page and recipes you can make will show up here."
									: emptyHelp);
				} else {
					detail.setText("Select a recipe on the left to see it.");
				}
				return;
			}
			boolean inMine = m.recipe().isInMyRecipes();
			save.setText(inMine ? "In My Recipes" : "Save to My Recipes");
			save.setDisable(inMine);
			whyButton.setDisable(false);
			Set<RecipeTag> tags = RecipeTag.tagsOf(m);
			String tagText = tags.isEmpty() ? "none"
					: tags.stream().map(RecipeTag::label).collect(Collectors.joining(", "));
			detail.setText(
					RecipesView.describe(m.recipe()) + "\n\nTags: " + tagText + "\n\n" + RecipesView.describeMatch(m));
		}

		private RecipeMatch selectedMatch() {
			RecipeMatch m = list.getSelectionModel().getSelectedItem();
			if (m == null) {
				throw new IllegalArgumentException("Select a recipe on the left first.");
			}
			return m;
		}

		/**
		 * Asks the AI for the one-line reason in the background, so the window never
		 * freezes.
		 */
		private void askWhy() {
			RecipeMatch m = list.getSelectionModel().getSelectedItem();
			if (m == null) {
				return;
			}
			long id = m.recipe().getId();
			whyButton.setDisable(true);
			why.setText("Asking the AI...");
			Task<String> task = new Task<>() {
				@Override
				protected String call() {
					return facade.explainRecipe(id);
				}
			};
			task.setOnSucceeded(e -> {
				RecipeMatch now = list.getSelectionModel().getSelectedItem();
				if (now != null && now.recipe().getId() == id) {
					why.setText(task.getValue());
					whyButton.setDisable(false);
				}
			});
			task.setOnFailed(e -> {
				why.setText("Could not get a reason right now.");
				whyButton.setDisable(false);
			});
			Thread thread = new Thread(task, "recipe-why");
			thread.setDaemon(true);
			thread.start();
		}

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

		private static String joinIngredients(List<RecipeIngredient> ingredients) {
			return ingredients.stream().map(RecipeIngredient::toString).collect(Collectors.joining(", "));
		}
	}
}