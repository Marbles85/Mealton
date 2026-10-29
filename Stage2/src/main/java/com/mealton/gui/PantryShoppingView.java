package com.mealton.gui;

import java.util.ArrayList;
import java.util.List;

import com.mealton.facade.MealtonFacade;
import com.mealton.pantry.Freshness;
import com.mealton.pantry.PantryItem;
import com.mealton.pantry.PantryObserver;
import com.mealton.shopping.ItemState;
import com.mealton.shopping.ShoppingItem;
import com.mealton.util.Categories;
import com.mealton.util.Formats;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.util.Duration;

/**
 * The Pantry / Shopping List page (features F06 and F12). A tab switcher lets
 * the user move between the pantry and the shopping list on one page. This
 * class only draws the UI and forwards user actions to MealtonFacade. It is a
 * PantryObserver, so it is told when the pantry changes or an item is about to
 * spoil.
 */
public class PantryShoppingView extends BorderPane implements PantryObserver {
	private final MealtonFacade facade;

	private final TabPane tabs = new TabPane();
	private final Tab pantryTab = new Tab("Pantry");
	private final Tab shoppingTab = new Tab("Shopping List");
	private final ListView<PantryItem> pantryList = new ListView<>();
	private final ListView<ShoppingItem> shoppingList = new ListView<>();
	private final Label banner = new Label();
	private final Label status = new Label();
	private final List<String> alerts = new ArrayList<>();

	// pantry form
	private final TextField pantryName = new TextField();
	private final TextField pantryQty = new TextField();
	private final TextField pantryUnit = new TextField();
	private final DatePicker pantryExpiry = new DatePicker();
	private final ComboBox<String> pantryCategory = newCategoryBox();

	// shopping form
	private final TextField shopName = new TextField();
	private final TextField shopQty = new TextField();
	private final TextField shopUnit = new TextField();
	private final ComboBox<String> shopCategory = newCategoryBox();

	private final Button undoButton = new Button("Undo");
	private final Button redoButton = new Button("Redo");

	private Timeline freshnessTimer;

	public PantryShoppingView(MealtonFacade facade) {
		this.facade = facade;
		facade.attachPantryObserver(this);

		pantryTab.setContent(buildPantryTab());
		shoppingTab.setContent(buildShoppingTab());
		tabs.getTabs().addAll(pantryTab, shoppingTab);
		tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

		banner.setWrapText(true);
		banner.setMaxWidth(Double.MAX_VALUE);
		banner.setStyle("-fx-background-color: #fff3e0; -fx-text-fill: #e65100; -fx-padding: 8 12 8 12;");
		banner.setVisible(false);
		banner.setManaged(false);
		status.setPadding(new Insets(6, 12, 6, 12));

		setTop(banner);
		setCenter(tabs);
		setBottom(status);

		refreshPantry();
		refreshShopping();
		runFreshnessCheck();
		startFreshnessTimer();
		setStatus("Ready.", false);
	}

	// ------------------------------------------------------------------ building
	// the UI

	private Node buildPantryTab() {
		pantryName.setPromptText("Ingredient");
		pantryName.setPrefWidth(180);
		pantryQty.setPromptText("Qty");
		pantryQty.setPrefWidth(70);
		pantryUnit.setPromptText("Unit");
		pantryUnit.setPrefWidth(70);
		pantryExpiry.setPromptText("Expires");
		pantryExpiry.setPrefWidth(130);
		for (TextField f : List.of(pantryName, pantryQty, pantryUnit)) {
			f.setOnAction(e -> onAddItem());
		}

		Button add = new Button("Add");
		add.setOnAction(e -> onAddItem());
		HBox form = new HBox(8, pantryName, pantryQty, pantryUnit, pantryExpiry, pantryCategory, add);
		form.setAlignment(Pos.CENTER_LEFT);

		setupList(pantryList, "Your pantry is empty. Add an ingredient above.");
		pantryList.setCellFactory(lv -> new ListCell<PantryItem>() {
			@Override
			protected void updateItem(PantryItem item, boolean empty) {
				super.updateItem(item, empty);
				setText(null);
				setGraphic(empty || item == null ? null : pantryRow(item));
			}
		});

		Button remove = new Button("Remove selected");
		remove.setOnAction(e -> onSwipeDelete());
		Button check = new Button("Check freshness");
		check.setOnAction(e -> {
			runFreshnessCheck();
			setStatus(alerts.isEmpty() ? "Nothing is close to expiring." : alerts.size() + " item(s) need attention.",
					false);
		});
		HBox buttons = new HBox(8, remove, check);

		VBox box = new VBox(10, form, pantryList, buttons);
		VBox.setVgrow(pantryList, Priority.ALWAYS);
		box.setPadding(new Insets(12));
		return box;
	}

	private Node buildShoppingTab() {
		shopName.setPromptText("Item");
		shopName.setPrefWidth(180);
		shopQty.setPromptText("Qty (1)");
		shopQty.setPrefWidth(70);
		shopUnit.setPromptText("Unit");
		shopUnit.setPrefWidth(70);
		for (TextField f : List.of(shopName, shopQty, shopUnit)) {
			f.setOnAction(e -> onAddItem());
		}

		Button add = new Button("Add");
		add.setOnAction(e -> onAddItem());
		HBox form = new HBox(8, shopName, shopQty, shopUnit, shopCategory, add);
		form.setAlignment(Pos.CENTER_LEFT);

		setupList(shoppingList, "Your shopping list is empty. Add an item above.");
		shoppingList.setCellFactory(lv -> new ListCell<ShoppingItem>() {
			@Override
			protected void updateItem(ShoppingItem item, boolean empty) {
				super.updateItem(item, empty);
				setText(null);
				setGraphic(empty || item == null ? null : shoppingRow(item));
			}
		});

		Button remove = new Button("Delete selected");
		remove.setOnAction(e -> onSwipeDelete());
		Button clear = new Button("Clear checked");
		clear.setOnAction(e -> onClearChecked());
		undoButton.setOnAction(e -> onUndo());
		redoButton.setOnAction(e -> onRedo());
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox buttons = new HBox(8, remove, clear, spacer, undoButton, redoButton);

		VBox box = new VBox(10, form, shoppingList, buttons);
		VBox.setVgrow(shoppingList, Priority.ALWAYS);
		box.setPadding(new Insets(12));
		return box;
	}

	private static ComboBox<String> newCategoryBox() {
		ComboBox<String> box = new ComboBox<>(FXCollections.observableArrayList(Categories.DEFAULT));
		box.setEditable(true);
		box.setPromptText("Category");
		box.setPrefWidth(150);
		return box;
	}

	/**
	 * Multi-select, Delete/Backspace key and right-click "Delete" (desktop version
	 * of swipe-to-delete).
	 */
	private void setupList(ListView<?> list, String emptyText) {
		list.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		list.setPlaceholder(new Label(emptyText));
		list.setOnKeyPressed(e -> {
			if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) {
				onSwipeDelete();
			}
		});
		MenuItem delete = new MenuItem("Delete");
		delete.setOnAction(e -> onSwipeDelete());
		list.setContextMenu(new ContextMenu(delete));
	}

	private Node pantryRow(PantryItem item) {
		Label name = new Label(item.getName());
		name.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
		Label qty = new Label(Formats.quantityWithUnit(item.getQuantity(), item.getUnit()));
		Label category = new Label(item.getCategory());
		category.setStyle("-fx-background-color: #eeeeee; -fx-padding: 2 8 2 8; -fx-background-radius: 10;");
		Label badge = new Label(Formats.expiryText(item, facade.today()));
		badge.setStyle(badgeStyle(item.freshness(facade.today())));

		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox row = new HBox(12, name, qty, spacer, category, badge);
		row.setAlignment(Pos.CENTER_LEFT);
		return row;
	}

	private static String badgeStyle(Freshness freshness) {
		String color = switch (freshness) {
		case EXPIRED -> "#c62828";
		case USE_SOON -> "#ef6c00";
		case FRESH -> "#2e7d32";
		case NO_DATE -> "#757575";
		};
		return "-fx-background-color: " + color + "; -fx-text-fill: white;"
				+ " -fx-padding: 2 8 2 8; -fx-background-radius: 10;";
	}

	private Node shoppingRow(ShoppingItem item) {
		CheckBox tick = new CheckBox();
		tick.setSelected(item.isChecked());
		tick.setOnAction(e -> onTick(item.getId()));

		Text name = new Text(item.getName());
		name.setStyle("-fx-font-size: 14px;");
		Text qty = new Text(Formats.quantityWithUnit(item.getQuantity(), item.getUnit()));
		Label category = new Label(item.getCategory());
		category.setStyle("-fx-background-color: #eeeeee; -fx-padding: 2 8 2 8; -fx-background-radius: 10;");

		// State pattern at work: the item's state decides how it is drawn
		boolean done = ItemState.STYLE_CHECKED.equals(item.getStyle());
		name.setStrikethrough(done);
		qty.setStrikethrough(done);

		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox row = new HBox(12, tick, name, qty, spacer, category);
		row.setAlignment(Pos.CENTER_LEFT);
		row.setOpacity(done ? 0.45 : 1.0);
		return row;
	}

	// ------------------------------------------------------------------ user
	// actions

	/** Switches between the Pantry and Shopping List tabs. */
	public void switchTab() {
		boolean onPantry = tabs.getSelectionModel().getSelectedItem() == pantryTab;
		tabs.getSelectionModel().select(onPantry ? shoppingTab : pantryTab);
	}

	/** Adds the item typed into the form of the current tab. */
	public void onAddItem() {
		try {
			if (tabs.getSelectionModel().getSelectedItem() == pantryTab) {
				addPantryItemFromForm();
			} else {
				addShoppingItemFromForm();
			}
		} catch (IllegalArgumentException ex) {
			setStatus(ex.getMessage(), true);
		}
	}

	private void addPantryItemFromForm() {
		double qty = Formats.parseQuantity(pantryQty.getText());
		PantryItem item = new PantryItem(pantryName.getText(), qty, pantryUnit.getText(), categoryText(pantryCategory),
				pantryExpiry.getValue());
		int before = facade.getPantryItems().size();
		PantryItem stored = facade.addPantryItem(item); // triggers onPantryChanged()
		boolean merged = facade.getPantryItems().size() == before;

		pantryName.clear();
		pantryQty.clear();
		pantryUnit.clear();
		pantryExpiry.setValue(null);
		pantryCategory.setValue(null);
		pantryCategory.getEditor().clear();
		pantryName.requestFocus();
		setStatus(merged ? "Merged into existing item: " + stored.getName() : "Added " + stored.getName(), false);
	}

	private void addShoppingItemFromForm() {
		double qty = Formats.parseQuantityOrDefault(shopQty.getText(), 1);
		ShoppingItem item = new ShoppingItem(shopName.getText(), qty, shopUnit.getText(), categoryText(shopCategory));
		int before = facade.getShoppingItems().size();
		ShoppingItem stored = facade.addShoppingItem(item);
		boolean merged = facade.getShoppingItems().size() == before;
		refreshShopping();

		shopName.clear();
		shopQty.clear();
		shopUnit.clear();
		shopCategory.setValue(null);
		shopCategory.getEditor().clear();
		shopName.requestFocus();
		setStatus(merged ? "Added to the existing entry: " + stored.getName() : "Added " + stored.getName(), false);
	}

	/**
	 * Deletes the selected rows of the current tab (Delete key, right-click or
	 * button).
	 */
	public void onSwipeDelete() {
		try {
			if (tabs.getSelectionModel().getSelectedItem() == pantryTab) {
				List<Long> ids = pantryList.getSelectionModel().getSelectedItems().stream().map(PantryItem::getId)
						.toList();
				if (ids.isEmpty()) {
					setStatus("Select one or more items first.", true);
					return;
				}
				int n = facade.removePantryItems(ids); // triggers onPantryChanged()
				setStatus("Removed " + n + " item(s).", false);
			} else {
				List<Long> ids = shoppingList.getSelectionModel().getSelectedItems().stream().map(ShoppingItem::getId)
						.toList();
				if (ids.isEmpty()) {
					setStatus("Select one or more items first.", true);
					return;
				}
				int n = facade.deleteShoppingItems(ids);
				refreshShopping();
				setStatus("Deleted " + n + " item(s). Click Undo to bring them back.", false);
			}
		} catch (IllegalArgumentException ex) {
			setStatus(ex.getMessage(), true);
		}
	}

	/** Ticks / un-ticks a shopping item. */
	public void onTick(long id) {
		try {
			facade.toggleShoppingItem(id);
			refreshShopping();
		} catch (IllegalArgumentException ex) {
			setStatus(ex.getMessage(), true);
		}
	}

	/** Deletes every ticked item as one undoable step. */
	public void onClearChecked() {
		int n = facade.clearChecked();
		refreshShopping();
		setStatus(n == 0 ? "No ticked items to clear."
				: "Cleared " + n + " ticked item(s). Click Undo to bring them back.", false);
	}

	public void onUndo() {
		boolean undone = facade.undo();
		refreshShopping();
		setStatus(undone ? "Undone." : "Nothing to undo.", false);
	}

	public void onRedo() {
		boolean redone = facade.redo();
		refreshShopping();
		setStatus(redone ? "Redone." : "Nothing to redo.", false);
	}

	// ------------------------------------------------------------------
	// PantryObserver

	/** Called by the pantry when an item is expired or about to expire. */
	@Override
	public void onItemExpiring(PantryItem item) {
		alerts.add(item.getName() + " " + Formats.expiryText(item, facade.today()));
	}

	/** Called by the pantry after add / update / remove. */
	@Override
	public void onPantryChanged() {
		refreshPantry();
		runFreshnessCheck();
	}

	// ------------------------------------------------------------------ helpers

	/**
	 * Asks the pantry to check freshness. The observer callbacks fill "alerts",
	 * then the banner is updated. Runs on the JavaFX thread (no background thread
	 * needed in the GUI).
	 */
	public void runFreshnessCheck() {
		alerts.clear();
		facade.checkFreshness();
		boolean any = !alerts.isEmpty();
		banner.setText("Use soon: " + String.join("   |   ", alerts));
		banner.setVisible(any);
		banner.setManaged(any);
	}

	private void startFreshnessTimer() {
		freshnessTimer = new Timeline(new KeyFrame(Duration.minutes(30), e -> runFreshnessCheck()));
		freshnessTimer.setCycleCount(Animation.INDEFINITE);
		freshnessTimer.play();
	}

	private void refreshPantry() {
		pantryList.getItems().setAll(facade.getPantryItems());
	}

	private void refreshShopping() {
		shoppingList.getItems().setAll(facade.getShoppingItems());
		undoButton.setDisable(!facade.canUndo()); // grey out when there is nothing to undo
		redoButton.setDisable(!facade.canRedo());
	}

	private static String categoryText(ComboBox<String> box) {
		String text = box.getEditor().getText();
		return text == null || text.isBlank() ? null : text.trim();
	}

	private void setStatus(String message, boolean error) {
		status.setText(message);
		status.setStyle(error ? "-fx-text-fill: #c62828;" : "-fx-text-fill: #555555;");
	}
}