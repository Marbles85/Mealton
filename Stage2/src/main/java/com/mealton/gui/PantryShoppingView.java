package com.mealton.gui;

import java.util.ArrayList;
import java.util.List;

import com.mealton.facade.MealtonFacade;
import com.mealton.pantry.Freshness;
import com.mealton.pantry.PantryItem;
import com.mealton.pantry.PantryObserver;
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
import javafx.util.Duration;

/**
 * The Pantry / Shopping List page (features F06 and F12). This class only draws
 * the screen and forwards user actions to MealtonFacade. It is a
 * PantryObserver, so it is told when the pantry changes or an item is about to
 * spoil.
 *
 * PART A: the Pantry tab only. The Shopping List tab is added in part B.
 */
public class PantryShoppingView extends BorderPane implements PantryObserver {

	private final MealtonFacade facade;

	private final TabPane tabs = new TabPane();
	private final Tab pantryTab = new Tab("Pantry");
	private final ListView<PantryItem> pantryList = new ListView<>();
	private final Label banner = new Label();
	private final Label status = new Label();
	private final List<String> alerts = new ArrayList<>();

	// the "add ingredient" form
	private final TextField pantryName = new TextField();
	private final TextField pantryQty = new TextField();
	private final TextField pantryUnit = new TextField();
	private final DatePicker pantryExpiry = new DatePicker();
	private final ComboBox<String> pantryCategory = newCategoryBox();

	private Timeline freshnessTimer;

	public PantryShoppingView(MealtonFacade facade) {
		this.facade = facade;
		facade.attachPantryObserver(this); // we want to be told about pantry events

		pantryTab.setContent(buildPantryTab());
		tabs.getTabs().add(pantryTab);
		tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

		// orange banner at the top, only visible when something is about to expire
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
		runFreshnessCheck();
		startFreshnessTimer();
		setStatus("Ready.", false);
	}

	// ------------------------------------------------------------------ building
	// the screen

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
			f.setOnAction(e -> onAddItem()); // pressing Enter adds the item
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

	private static ComboBox<String> newCategoryBox() {
		ComboBox<String> box = new ComboBox<>(FXCollections.observableArrayList(Categories.DEFAULT));
		box.setEditable(true);
		box.setPromptText("Category");
		box.setPrefWidth(150);
		return box;
	}

	/**
	 * Multi-select, Delete or Backspace key, and right-click "Delete" (the desktop
	 * version of swipe-to-delete).
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

	/**
	 * One row of the pantry list: name, quantity, category tag and a coloured
	 * expiry badge.
	 */
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

	// ------------------------------------------------------------------ user
	// actions

	/** Adds the ingredient typed into the form. */
	public void onAddItem() {
		try {
			double qty = Formats.parseQuantity(pantryQty.getText());
			PantryItem item = new PantryItem(pantryName.getText(), qty, pantryUnit.getText(),
					categoryText(pantryCategory), pantryExpiry.getValue());
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
		} catch (IllegalArgumentException ex) {
			setStatus(ex.getMessage(), true); // bad input: show the message, never crash
		}
	}

	/** Removes the selected rows (Delete key, right-click, or the button). */
	public void onSwipeDelete() {
		List<Long> ids = pantryList.getSelectionModel().getSelectedItems().stream().map(PantryItem::getId).toList();
		if (ids.isEmpty()) {
			setStatus("Select one or more items first.", true);
			return;
		}
		int n = facade.removePantryItems(ids); // triggers onPantryChanged()
		setStatus("Removed " + n + " item(s).", false);
	}

	// ------------------------------------------------------------------
	// PantryObserver

	/** Called by the pantry when an item is expired or about to expire. */
	@Override
	public void onItemExpiring(PantryItem item) {
		alerts.add(item.getName() + " " + Formats.expiryText(item, facade.today()));
	}

	/** Called by the pantry after an add, update or remove. */
	@Override
	public void onPantryChanged() {
		refreshPantry();
		runFreshnessCheck();
	}

	// ------------------------------------------------------------------ helpers

	/**
	 * Asks the pantry to check freshness. The observer callback fills "alerts",
	 * then the orange banner is updated to match.
	 */
	public void runFreshnessCheck() {
		alerts.clear();
		facade.checkFreshness();
		boolean any = !alerts.isEmpty();
		banner.setText("Use soon: " + String.join("   |   ", alerts));
		banner.setVisible(any);
		banner.setManaged(any);
	}

	/** Re-checks every 30 minutes while the window is open. */
	private void startFreshnessTimer() {
		freshnessTimer = new Timeline(new KeyFrame(Duration.minutes(30), e -> runFreshnessCheck()));
		freshnessTimer.setCycleCount(Animation.INDEFINITE);
		freshnessTimer.play();
	}

	private void refreshPantry() {
		pantryList.getItems().setAll(facade.getPantryItems());
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