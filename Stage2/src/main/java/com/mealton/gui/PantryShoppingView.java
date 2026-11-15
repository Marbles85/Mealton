package com.mealton.gui;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongConsumer;

import com.mealton.facade.MealtonFacade;
import com.mealton.pantry.CategoryLayout;
import com.mealton.pantry.Freshness;
import com.mealton.pantry.PantryItem;
import com.mealton.pantry.PantryObserver;
import com.mealton.shopping.ShoppingItem;
import com.mealton.util.Formats;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.StringConverter;

/**
 * The Pantry &amp; shopping list page. Both lists are grouped into categories
 * (Mealton's suggested ones and the user's own); the pantry shows how fresh
 * each item is and can hand an item to Log food, the shopping list can pin
 * items and clear checked ones. "Edit list" lets the user change names,
 * amounts, dates and categories, reorder and recolour categories and drag items
 * between them. This class only draws the page and forwards actions to
 * {@link MealtonFacade}.
 */
public class PantryShoppingView extends BorderPane implements PantryObserver {

	private static final int SHELF_STABLE_DAYS = 60;
	private static final String PINNED_COLOR = "#D2B85A";

	private final MealtonFacade facade;

	private boolean pantryView = true;
	private boolean editing;
	private boolean editTried;
	private boolean addingCategory;
	private String categoryError = "";
	private String message = "";
	private PantryItem pendingDuplicate;
	private PantryItem existingDuplicate;

	private final Set<Long> selected = new LinkedHashSet<>();
	private final Map<Long, Draft> drafts = new HashMap<>();
	private Long dragId;
	private String dragGroup;

	private LongConsumer onLogItem;
	private Runnable onSeeRecipes;

	// persistent form fields (so typing is not lost when the page redraws)
	private final TextField itemName = field("Baby spinach", 200);
	private final TextField itemQty = field("1", 80);
	private final TextField itemUnit = field("g, ml, pcs…", 90);
	private final ComboBox<String> itemCategory = new ComboBox<>();
	private final DatePicker itemExpiry = new DatePicker();
	private final TextField listName = field("Oats", 200);
	private final TextField listQty = field("1", 80);
	private final TextField listUnit = field("g, ml, pcs\u2026", 90);
	private final ComboBox<String> listCategory = new ComboBox<>();
	private final TextField newCategory = field("Category name", 180);
	private String formError = "";

	private final VBox body = new VBox(16);
	private final Button pantryTab = new Button("Pantry");
	private final Button listTab = new Button("Shopping list");

	/** What the user typed in an edit row, kept until Done or Cancel. */
	private static final class Draft {
		String name;
		String qty;
		String unit;
		String category;
		LocalDate expires;
	}

	public PantryShoppingView(MealtonFacade facade) {
		this.facade = facade;
		facade.attachPantryObserver(this);
		var css = PantryShoppingView.class.getResource("/pantry.css");
		if (css != null) {
			getStylesheets().add(css.toExternalForm());
		}
		getStyleClass().add("pn-page");

		itemCategory.getStyleClass().add("pn-combo");
		itemCategory.setPrefWidth(190);
		listCategory.getStyleClass().add("pn-combo");
		listCategory.setPrefWidth(190);
		itemExpiry.getStyleClass().add("pn-date");
		itemExpiry.setPromptText("Expires");
		itemExpiry.setPrefWidth(150);
		itemExpiry.setEditable(false);
		itemName.setOnAction(e -> addPantryFromForm());
		itemQty.setOnAction(e -> addPantryFromForm());
		itemUnit.setOnAction(e -> addPantryFromForm());
		listName.setOnAction(e -> addToList());
		listQty.setOnAction(e -> addToList());
		listUnit.setOnAction(e -> addToList());
		newCategory.setOnAction(e -> addCategory());

		pantryTab.setOnAction(e -> switchView(true));
		listTab.setOnAction(e -> switchView(false));

		body.setPadding(new Insets(28, 32, 32, 32));
		ScrollPane scroll = new ScrollPane(body);
		scroll.setFitToWidth(true);
		scroll.getStyleClass().add("pn-scroll");
		setCenter(scroll);

		rebuild();
	}

	// ------------------------------------------------------------------ hooks

	/**
	 * What a pantry row's Log button does (the app opens Log food with that item).
	 */
	public void setOnLogItem(LongConsumer action) {
		this.onLogItem = action;
	}

	/** What "See recipes that use them" does (the app opens Recipes). */
	public void setOnSeeRecipes(Runnable action) {
		this.onSeeRecipes = action;
	}

	/**
	 * Shows the shopping list tab (for the "View shopping list" link on Log food).
	 */
	public void showShoppingList() {
		switchView(false);
	}

	/**
	 * Reloads everything from the facade (called by other pages after they change
	 * things).
	 */
	public void refreshAll() {
		rebuild();
	}

	@Override
	public void onItemExpiring(PantryItem item) {
		// the banner is worked out from the pantry each time the page is drawn
	}

	@Override
	public void onPantryChanged() {
		Platform.runLater(this::rebuild);
	}

	// ------------------------------------------------------------------ state
	// changes

	private void switchView(boolean pantry) {
		if (editing) {
			cancelEdit();
		}
		pantryView = pantry;
		formError = "";
		message = "";
		addingCategory = false;
		categoryError = "";
		rebuild();
	}

	private CategoryLayout catLayout() {
		return facade.getCategoryLayout(pantryView ? "pantry" : "list");
	}

	private void say(String text) {
		message = text;
	}

	private void addCategory() {
		try {
			catLayout().addCustom(newCategory.getText());
			newCategory.clear();
			addingCategory = false;
			categoryError = "";
		} catch (IllegalArgumentException e) {
			categoryError = e.getMessage();
		}
		rebuild();
	}

	private void addPantryFromForm() {
		String name = itemName.getText() == null ? "" : itemName.getText().trim();
		if (name.isEmpty()) {
			formError = "Type the item's name first.";
			rebuild();
			return;
		}
		double qty;
		try {
			qty = itemQty.getText() == null || itemQty.getText().isBlank() ? 1
					: Formats.parseQuantity(itemQty.getText());
		} catch (IllegalArgumentException e) {
			formError = e.getMessage();
			rebuild();
			return;
		}
		String unit = itemUnit.getText() == null ? "" : itemUnit.getText().trim();
		String cat = itemCategory.getValue() == null || itemCategory.getValue().startsWith("Auto") ? "Uncategorized"
				: itemCategory.getValue();
		PantryItem item;
		try {
			item = new PantryItem(name, qty, unit, cat, itemExpiry.getValue());
		} catch (IllegalArgumentException e) {
			formError = e.getMessage();
			rebuild();
			return;
		}
		formError = "";
		var dup = facade.findPantryDuplicate(name, unit);
		if (dup.isPresent()) {
			pendingDuplicate = item;
			existingDuplicate = dup.get();
			rebuild();
			return;
		}
		facade.addPantryItem(item);
		clearForm();
		say(name + " added to your pantry.");
		rebuild();
	}

	private void clearForm() {
		itemName.clear();
		itemQty.clear();
		itemUnit.clear();
		itemExpiry.setValue(null);
		itemCategory.getSelectionModel().selectFirst();
	}

	private void resolveDuplicate(boolean merge) {
		if (pendingDuplicate != null) {
			if (merge) {
				facade.addPantryItem(pendingDuplicate);
				say(pendingDuplicate.getName() + " added to what you already had.");
			} else {
				facade.addPantryItemSeparate(pendingDuplicate);
				say(pendingDuplicate.getName() + " added as a separate line.");
			}
			clearForm();
		}
		pendingDuplicate = null;
		existingDuplicate = null;
		rebuild();
	}

	private void addToList() {
		String name = listName.getText() == null ? "" : listName.getText().trim();
		if (name.isEmpty()) {
			formError = "Type the item's name first.";
			rebuild();
			return;
		}
		double qty;
		try {
			qty = listQty.getText() == null || listQty.getText().isBlank() ? 1
					: Formats.parseQuantity(listQty.getText());
		} catch (IllegalArgumentException e) {
			formError = e.getMessage();
			rebuild();
			return;
		}
		String unit = listUnit.getText() == null ? "" : listUnit.getText().trim();
		String cat = listCategory.getValue() == null || listCategory.getValue().startsWith("Auto") ? "Uncategorized"
				: listCategory.getValue();
		try {
			facade.addShoppingItem(new ShoppingItem(name, qty, unit, cat));
			listName.clear();
			listQty.clear();
			listUnit.clear();
			listCategory.getSelectionModel().selectFirst();
			formError = "";
			say(name + " added to the list.");
		} catch (IllegalArgumentException e) {
			formError = e.getMessage();
		}
		rebuild();
	}

	private void deleteSelected() {
		int n = facade.removePantryItems(new ArrayList<>(selected));
		selected.clear();
		say(n == 1 ? "1 item deleted. Undo brings it back." : n + " items deleted. Undo brings them back.");
		rebuild();
	}

	private void undo() {
		boolean ok = pantryView ? facade.undoPantry() : facade.undo();
		say(ok ? "Undone." : "Nothing to undo.");
		rebuild();
	}

	private void clearChecked() {
		int n = facade.clearChecked();
		say(n == 1 ? "1 checked item cleared." : n + " checked items cleared.");
		rebuild();
	}

	private void startEdit() {
		editing = true;
		editTried = false;
		drafts.clear();
		rebuild();
	}

	private void cancelEdit() {
		editing = false;
		editTried = false;
		drafts.clear();
		rebuild();
	}

	private Draft draftOf(long id, String name, double qty, String unit, String category, LocalDate expires) {
		return drafts.computeIfAbsent(id, k -> {
			Draft d = new Draft();
			d.name = name;
			d.qty = Formats.number(qty);
			d.unit = unit;
			d.category = category;
			d.expires = expires;
			return d;
		});
	}

	private void finishEdit() {
		editTried = true;
		boolean bad = false;
		List<PantryItem> changedPantry = new ArrayList<>();
		for (PantryItem p : facade.getPantryItems()) {
			Draft d = drafts.get(p.getId());
			if (d == null || !pantryView) {
				continue;
			}
			double qty = parseQty(d.qty);
			if (d.name.isBlank() || qty <= 0) {
				bad = true;
				continue;
			}
			PantryItem c = p.copy();
			c.setName(d.name.trim());
			c.setQuantity(qty);
			c.setUnit(d.unit);
			c.setCategory(d.category);
			c.setExpiryDate(d.expires);
			changedPantry.add(c);
		}
		List<Runnable> listChanges = new ArrayList<>();
		for (ShoppingItem s : facade.getShoppingItems()) {
			Draft d = drafts.get(s.getId());
			if (d == null || pantryView) {
				continue;
			}
			double qty = parseQty(d.qty);
			if (d.name.isBlank() || qty <= 0) {
				bad = true;
				continue;
			}
			listChanges.add(() -> {
				s.setName(d.name);
				s.setQuantity(qty);
				s.setUnit(d.unit);
				s.setCategory(d.category);
			});
		}
		if (bad) {
			say("Fill in the name and a quantity above 0 on the lines marked in red.");
			rebuild();
			return;
		}
		facade.updatePantryItems(changedPantry);
		listChanges.forEach(Runnable::run);
		editing = false;
		editTried = false;
		drafts.clear();
		say("Changes saved.");
		rebuild();
	}

	private static double parseQty(String text) {
		try {
			return Formats.parseQuantity(text);
		} catch (RuntimeException e) {
			return -1;
		}
	}

	// ------------------------------------------------------------------ drawing

	private void rebuild() {
		selected.retainAll(idsOfPantry());
		body.getChildren().clear();
		body.getChildren().add(header());
		if (!message.isEmpty()) {
			Label m = new Label(message);
			m.getStyleClass().add("pn-message");
			body.getChildren().add(m);
		}
		if (pantryView) {
			Node banner = expiryBanner();
			if (banner != null) {
				body.getChildren().add(banner);
			}
		}
		body.getChildren().add(categoriesCard());
		body.getChildren().add(addForm());
		if (pendingDuplicate != null) {
			body.getChildren().add(duplicateCard());
		}
		body.getChildren().add(listCard());
	}

	private Set<Long> idsOfPantry() {
		Set<Long> ids = new LinkedHashSet<>();
		for (PantryItem p : facade.getPantryItems()) {
			ids.add(p.getId());
		}
		return ids;
	}

	private Node header() {
		Label title = new Label("Pantry & shopping list");
		title.getStyleClass().add("pn-title");
		Label sub = new Label(pantryView ? "What you have, how fresh it is, and what to use up first."
				: "What you need to buy. Pin the important things to the top.");
		sub.getStyleClass().add("pn-sub");
		VBox left = new VBox(4, title, sub);
		HBox.setHgrow(left, Priority.ALWAYS);

		pantryTab.getStyleClass().setAll("button", "pn-seg");
		listTab.getStyleClass().setAll("button", "pn-seg");
		(pantryView ? pantryTab : listTab).getStyleClass().add("pn-seg-on");
		HBox seg = new HBox(2, pantryTab, listTab);
		seg.getStyleClass().add("pn-seg-bar");
		seg.setAlignment(Pos.CENTER);
		seg.setMaxHeight(Region.USE_PREF_SIZE);

		HBox row = new HBox(16, left, seg);
		row.setAlignment(Pos.CENTER_LEFT);
		return row;
	}

	private Node expiryBanner() {
		List<PantryItem> soon = facade.getExpiringItems();
		if (soon.isEmpty()) {
			return null;
		}
		List<String> names = new ArrayList<>();
		for (PantryItem p : soon) {
			names.add(p.getName());
		}
		String shown = names.size() > 4
				? String.join(", ", names.subList(0, 4)) + " and " + (names.size() - 4) + " more"
				: String.join(", ", names);
		Label title = new Label(
				soon.size() == 1 ? "1 item is expiring soon" : soon.size() + " items are expiring soon");
		title.getStyleClass().add("pn-banner-title");
		Label text = new Label(shown);
		text.getStyleClass().add("pn-banner-text");
		text.setWrapText(true);
		VBox left = new VBox(2, title, text);
		HBox.setHgrow(left, Priority.ALWAYS);
		Button see = new Button("See recipes that use them");
		see.getStyleClass().setAll("button", "pn-banner-btn");
		see.setOnAction(e -> {
			if (onSeeRecipes != null) {
				onSeeRecipes.run();
			}
		});
		HBox box = new HBox(12, left, see);
		box.setAlignment(Pos.CENTER_LEFT);
		box.getStyleClass().add("pn-banner");
		return box;
	}

	private Node categoriesCard() {
		CategoryLayout l = catLayout();
		Label h = new Label("Categories");
		h.getStyleClass().add("pn-h2");
		Label sub = new Label("Make your own, or let Mealton sort items for you.");
		sub.getStyleClass().add("pn-muted");
		VBox titles = new VBox(2, h, sub);
		HBox.setHgrow(titles, Priority.ALWAYS);

		Label swl = new Label("Suggested categories");
		swl.getStyleClass().add("pn-label");
		HBox top = new HBox(12, titles, swl, switchNode(l.isSuggestedOn(), () -> {
			l.setSuggestedOn(!l.isSuggestedOn());
			rebuild();
		}));
		top.setAlignment(Pos.CENTER_LEFT);

		FlowPane chips = new FlowPane(8, 8);
		if (l.isSuggestedOn()) {
			for (String s : CategoryLayout.SUGGESTED) {
				Label c = new Label(l.labelOf(s));
				c.getStyleClass().add("pn-chip-suggested");
				chips.getChildren().add(c);
			}
		}
		for (String own : l.getCustom()) {
			Label name = new Label(l.labelOf(own));
			name.getStyleClass().add("pn-chip-text");
			Button x = new Button("×");
			x.getStyleClass().setAll("button", "pn-chip-x");
			x.setOnAction(e -> {
				l.removeCustom(own);
				rebuild();
			});
			HBox chip = new HBox(2, name, x);
			chip.setAlignment(Pos.CENTER_LEFT);
			chip.getStyleClass().add("pn-chip");
			chips.getChildren().add(chip);
		}
		if (addingCategory) {
			Button add = new Button("Add");
			add.getStyleClass().setAll("button", "pn-small-primary");
			add.setOnAction(e -> addCategory());
			Button cancel = new Button("Cancel");
			cancel.getStyleClass().setAll("button", "pn-link");
			cancel.setOnAction(e -> {
				addingCategory = false;
				categoryError = "";
				newCategory.clear();
				rebuild();
			});
			chips.getChildren().add(new HBox(8, newCategory, add, cancel));
		} else {
			Button plus = new Button("+ Add");
			plus.getStyleClass().setAll("button", "pn-add-chip");
			plus.setOnAction(e -> {
				addingCategory = true;
				rebuild();
				newCategory.requestFocus();
			});
			chips.getChildren().add(plus);
		}
		VBox card = new VBox(12, top, chips);
		if (!categoryError.isEmpty()) {
			Label err = new Label(categoryError);
			err.getStyleClass().add("pn-error");
			card.getChildren().add(err);
		}
		card.getStyleClass().add("pn-card");
		return card;
	}

	private Node switchNode(boolean on, Runnable toggle) {
		Region knob = new Region();
		knob.getStyleClass().add("pn-knob");
		StackPane track = new StackPane(knob);
		track.getStyleClass().addAll("pn-switch", on ? "pn-switch-on" : "pn-switch-off");
		track.setMinSize(46, 26);
		track.setPrefSize(46, 26);
		track.setMaxSize(46, 26);
		StackPane.setAlignment(knob, on ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
		knob.setMinSize(20, 20);
		knob.setPrefSize(20, 20);
		knob.setMaxSize(20, 20);
		StackPane.setMargin(knob, new Insets(0, 3, 0, 3));
		track.setOnMouseClicked(e -> toggle.run());
		track.setStyle("-fx-cursor: hand;");
		return track;
	}

	private Node addForm() {
		CategoryLayout l = catLayout();
		List<String> options = new ArrayList<>();
		options.add("Auto");
		for (String g : l.visibleGroups(pantryView, true)) {
			if (!CategoryLayout.RECIPES.equals(g) && !CategoryLayout.UNCATEGORIZED.equals(g)) {
				options.add(g);
			}
		}
		ComboBox<String> combo = pantryView ? itemCategory : listCategory;
		String keep = combo.getValue();
		combo.getItems().setAll(options);
		combo.setConverter(new StringConverter<String>() {
			@Override
			public String toString(String s) {
				return s == null ? "" : (options.indexOf(s) > 0 ? l.labelOf(s) : s);
			}

			@Override
			public String fromString(String s) {
				return s;
			}
		});
		combo.setValue(keep != null && options.contains(keep) ? keep : options.get(0));

		Button add = new Button("Add");
		add.getStyleClass().setAll("button", "pn-primary");
		add.setOnAction(e -> {
			if (pantryView) {
				addPantryFromForm();
			} else {
				addToList();
			}
		});

		HBox row;
		if (pantryView) {
			row = new HBox(10, labeled("Item", itemName), labeled("Quantity", itemQty), labeled("Unit", itemUnit),
					labeled("Category", itemCategory), labeled("Expires", itemExpiry), addColumn(add));
		} else {
			row = new HBox(10, labeled("Item", listName), labeled("Quantity", listQty), labeled("Unit", listUnit),
					labeled("Category", listCategory), addColumn(add));
		}
		row.setAlignment(Pos.BOTTOM_LEFT);
		VBox card = new VBox(8, row);
		if (!formError.isEmpty()) {
			Label err = new Label(formError);
			err.getStyleClass().add("pn-error");
			card.getChildren().add(err);
		}
		card.getStyleClass().add("pn-card");
		return card;
	}

	private Node addColumn(Button b) {
		VBox v = new VBox(b);
		v.setAlignment(Pos.BOTTOM_LEFT);
		return v;
	}

	private Node duplicateCard() {
		String have = Formats.quantityWithUnit(existingDuplicate.getQuantity(), existingDuplicate.getUnit());
		Label t = new Label(pendingDuplicate.getName() + " is already in your pantry (" + have + ").");
		t.getStyleClass().add("pn-banner-title");
		Label sub = new Label(
				"Add the new amount to it, or keep it as a separate line (for example a different expiry date).");
		sub.getStyleClass().add("pn-banner-text");
		sub.setWrapText(true);
		Button merge = new Button("Add to it");
		merge.getStyleClass().setAll("button", "pn-small-primary");
		merge.setOnAction(e -> resolveDuplicate(true));
		Button separate = new Button("Keep separate");
		separate.getStyleClass().setAll("button", "pn-outline");
		separate.setOnAction(e -> resolveDuplicate(false));
		Button cancel = new Button("Cancel");
		cancel.getStyleClass().setAll("button", "pn-link");
		cancel.setOnAction(e -> {
			pendingDuplicate = null;
			existingDuplicate = null;
			rebuild();
		});
		VBox texts = new VBox(2, t, sub);
		HBox.setHgrow(texts, Priority.ALWAYS);
		HBox box = new HBox(12, texts, merge, separate, cancel);
		box.setAlignment(Pos.CENTER_LEFT);
		box.getStyleClass().add("pn-banner");
		return box;
	}

	private Node toolbar() {
		if (editing) {
			Label t = new Label("Editing " + (pantryView ? "pantry" : "shopping list")
					+ ". Change any field, drag \u283F to reorder. Drag onto another category to move it.");
			t.setWrapText(true);
			t.getStyleClass().add("pn-banner-text");
			HBox.setHgrow(t, Priority.ALWAYS);
			Button cancel = new Button("Cancel");
			cancel.getStyleClass().setAll("button", "pn-outline");
			cancel.setOnAction(e -> cancelEdit());
			Button done = new Button("Done");
			done.getStyleClass().setAll("button", "pn-small-primary");
			done.setOnAction(e -> finishEdit());
			HBox bar = new HBox(12, t, cancel, done);
			bar.setAlignment(Pos.CENTER_LEFT);
			bar.getStyleClass().add("pn-edit-bar");
			return bar;
		}
		Button undo = new Button("Undo");
		undo.getStyleClass().setAll("button", "pn-outline");
		undo.setDisable(pantryView ? !facade.canUndoPantry() : !facade.canUndo());
		undo.setOnAction(e -> undo());
		Button edit = new Button("Edit list");
		edit.getStyleClass().setAll("button", "pn-outline");
		edit.setOnAction(e -> startEdit());
		Region grow = new Region();
		HBox.setHgrow(grow, Priority.ALWAYS);
		HBox bar = new HBox(10);
		bar.setAlignment(Pos.CENTER_LEFT);
		bar.getStyleClass().add("pn-toolbar");
		if (pantryView) {
			int n = facade.getPantryItems().size();
			Label count = new Label(n + (n == 1 ? " item" : " items") + " \u00B7 " + selected.size() + " selected");
			count.getStyleClass().add("pn-label");
			Button del = new Button("Delete selected");
			del.getStyleClass().setAll("button", "pn-danger");
			del.setDisable(selected.isEmpty());
			del.setOnAction(e -> deleteSelected());
			bar.getChildren().addAll(count, grow, undo, del, edit);
		} else {
			int checked = 0;
			for (ShoppingItem s : facade.getShoppingItems()) {
				if (s.isChecked()) {
					checked++;
				}
			}
			int total = facade.getShoppingItems().size();
			Label count = new Label(total + (total == 1 ? " item" : " items"));
			count.getStyleClass().add("pn-label");
			Button clear = new Button("Clear checked (" + checked + ")");
			clear.getStyleClass().setAll("button", "pn-outline");
			clear.setDisable(checked == 0);
			clear.setOnAction(e -> clearChecked());
			bar.getChildren().addAll(count, grow, undo, clear, edit);
		}
		return bar;
	}

	// ------------------------------------------------------------------ groups

	/** One card holding the toolbar and every category, like the mock-up. */
	private Node listCard() {
		VBox card = new VBox(0);
		card.getStyleClass().add("pn-listcard");
		roundClip(card, 16);
		card.getChildren().add(toolbar());
		CategoryLayout l = catLayout();
		Map<String, List<Object>> byGroup = new LinkedHashMap<>();
		List<Object> pinned = new ArrayList<>();
		boolean anyUncategorized = false;
		if (pantryView) {
			for (PantryItem p : facade.getPantryItems()) {
				String name = p.getName();
				String cat = p.getCategory();
				if (editing) {
					Draft d = draftOf(p.getId(), p.getName(), p.getQuantity(), p.getUnit(), p.getCategory(),
							p.getExpiryDate());
					name = d.name;
					cat = d.category;
				}
				String g = l.groupOf(name, cat);
				byGroup.computeIfAbsent(g, k -> new ArrayList<>()).add(p);
				anyUncategorized |= CategoryLayout.UNCATEGORIZED.equals(g);
			}
		} else {
			for (ShoppingItem s : facade.getShoppingItems()) {
				if (s.isPinned()) {
					pinned.add(s);
					continue;
				}
				String name = s.getName();
				String cat = s.getCategory();
				if (editing) {
					Draft d = draftOf(s.getId(), s.getName(), s.getQuantity(), s.getUnit(), s.getCategory(), null);
					name = d.name;
					cat = d.category;
				}
				String g = l.groupOf(name, cat);
				byGroup.computeIfAbsent(g, k -> new ArrayList<>()).add(s);
				anyUncategorized |= CategoryLayout.UNCATEGORIZED.equals(g);
			}
		}
		List<String> showing = new ArrayList<>(l.visibleGroups(pantryView, anyUncategorized || editing));
		if (!pantryView) {
			card.getChildren().add(groupSection(CategoryLayout.PINNED, pinned, showing, l));
		}
		for (String g : showing) {
			List<Object> inGroup = byGroup.getOrDefault(g, List.of());
			boolean hideEmpty = !editing && inGroup.isEmpty()
					&& (CategoryLayout.SUGGESTED.contains(g) || CategoryLayout.UNCATEGORIZED.equals(g));
			if (hideEmpty) {
				continue;
			}
			card.getChildren().add(groupSection(g, inGroup, showing, l));
		}
		return card;
	}

	private static void roundClip(Region r, double radius) {
		Rectangle c = new Rectangle();
		c.setArcWidth(radius * 2);
		c.setArcHeight(radius * 2);
		c.widthProperty().bind(r.widthProperty());
		c.heightProperty().bind(r.heightProperty());
		r.setClip(c);
	}

	private Node groupSection(String group, List<Object> items, List<String> showing, CategoryLayout l) {
		boolean pinnedGroup = CategoryLayout.PINNED.equals(group);
		boolean recipes = CategoryLayout.RECIPES.equals(group);
		String color = pinnedGroup ? PINNED_COLOR : l.colorOf(group);
		Circle dot = new Circle(6);
		dot.setStyle("-fx-fill: " + color + ";");

		HBox head = new HBox(10);
		head.setAlignment(Pos.CENTER_LEFT);
		head.getStyleClass().add(pinnedGroup ? "pn-head-pin" : recipes ? "pn-head-rec" : "pn-head");
		if (editing && !pinnedGroup) {
			Label handle = new Label("\u283F");
			handle.getStyleClass().add("pn-handle");
			head.getChildren().add(handle);
			head.setStyle("-fx-cursor: move;");
			head.setOnDragDetected(e -> {
				if (e.getTarget() instanceof TextField) {
					return;
				}
				Dragboard db = head.startDragAndDrop(TransferMode.MOVE);
				ClipboardContent c = new ClipboardContent();
				c.putString(group);
				db.setContent(c);
				dragGroup = group;
				dragId = null;
				e.consume();
			});
		}
		head.getChildren().add(dot);

		boolean canRename = editing && !pinnedGroup && !recipes;
		if (canRename) {
			TextField rename = field("", 180);
			rename.setText(l.labelOf(group));
			rename.setOnAction(e -> {
				l.rename(group, rename.getText());
				rebuild();
			});
			rename.focusedProperty().addListener((o, was, is) -> {
				if (!is) {
					l.rename(group, rename.getText());
				}
			});
			head.getChildren().add(rename);
		} else {
			Label name = new Label(pinnedGroup ? "Pinned" : l.labelOf(group));
			name.getStyleClass().add("pn-group-name");
			head.getChildren().add(name);
		}
		Label count = new Label(items.size() + (items.size() == 1 ? " item" : " items"));
		count.getStyleClass().add("pn-muted");
		head.getChildren().add(count);
		if (l.getCustom().contains(group)) {
			head.getChildren().add(tag("yours", true));
		} else if (CategoryLayout.SUGGESTED.contains(group)) {
			head.getChildren().add(tag("suggested", false));
		}
		int soonCount = 0;
		if (pantryView) {
			for (Object o : items) {
				PantryItem p = (PantryItem) o;
				if (p.getExpiryDate() != null && p.daysLeft(facade.today()) <= Freshness.USE_SOON_THRESHOLD_DAYS) {
					soonCount++;
				}
			}
		}
		if (soonCount > 0) {
			Label soon = new Label(soonCount + " expiring soon");
			soon.getStyleClass().add("pn-soon");
			head.getChildren().add(soon);
		}
		Region grow = new Region();
		HBox.setHgrow(grow, Priority.ALWAYS);
		head.getChildren().add(grow);

		if (editing && !pinnedGroup) {
			Button color1 = new Button("Colour");
			color1.getStyleClass().setAll("button", "pn-link");
			color1.setOnAction(e -> showPalette(color1, group, l));
			head.getChildren().add(color1);
			if (l.getCustom().contains(group)) {
				Button del = new Button("Delete category");
				del.getStyleClass().setAll("button", "pn-link-danger");
				del.setOnAction(e -> {
					l.removeCustom(group);
					say("Category removed. Its items moved to Uncategorized.");
					rebuild();
				});
				head.getChildren().add(del);
			}
		} else if (pantryView && !editing && !items.isEmpty()) {
			boolean allOn = true;
			for (Object o : items) {
				allOn &= selected.contains(((PantryItem) o).getId());
			}
			final boolean deselect = allOn;
			Button all = new Button(deselect ? "Deselect all" : "Select all");
			all.getStyleClass().setAll("button", "pn-link");
			all.setOnAction(e -> {
				for (Object o : items) {
					long id = ((PantryItem) o).getId();
					if (deselect) {
						selected.remove(id);
					} else {
						selected.add(id);
					}
				}
				rebuild();
			});
			head.getChildren().add(all);
		}

		VBox rows = new VBox(0);
		for (Object o : items) {
			Node row = pantryView ? pantryRow((PantryItem) o, l) : listRow((ShoppingItem) o, l);
			if (editing) {
				long id = pantryView ? ((PantryItem) o).getId() : ((ShoppingItem) o).getId();
				row.setOnDragOver(e -> {
					if (dragId != null && dragId != id) {
						e.acceptTransferModes(TransferMode.MOVE);
					}
					e.consume();
				});
				row.setOnDragEntered(e -> {
					if (dragId != null && dragId != id) {
						row.getStyleClass().add("pn-drop-row");
					}
				});
				row.setOnDragExited(e -> row.getStyleClass().remove("pn-drop-row"));
				row.setOnDragDropped(e -> {
					if (dragId != null) {
						dropItem(dragId, group, id);
						dragId = null;
						e.setDropCompleted(true);
						rebuild();
					}
					e.consume();
				});
			}
			rows.getChildren().add(row);
		}
		if (items.isEmpty()) {
			Label hint = new Label(recipes && !editing
					? "Leftovers you save from Log food land here, so you can see how long they\u2019ll keep."
					: editing ? (pinnedGroup ? "Drag items here to pin them." : "Empty \u2014 drag items here.")
							: "Empty \u2014 use Edit list to move items here.");
			hint.getStyleClass().add("pn-empty");
			hint.setWrapText(true);
			rows.getChildren().add(hint);
		}

		VBox section = new VBox(0, head, rows);
		section.getStyleClass().add("pn-section");
		if (editing) {
			section.setOnDragOver(e -> {
				if (dragId != null || (dragGroup != null && !pinnedGroup && !dragGroup.equals(group))) {
					e.acceptTransferModes(TransferMode.MOVE);
				}
				e.consume();
			});
			section.setOnDragEntered(e -> {
				if (dragId != null || (dragGroup != null && !pinnedGroup)) {
					section.getStyleClass().add("pn-drop-section");
				}
			});
			section.setOnDragExited(e -> section.getStyleClass().remove("pn-drop-section"));
			section.setOnDragDropped(e -> {
				if (dragGroup != null) {
					if (!pinnedGroup) {
						l.moveGroupBefore(dragGroup, group, showing);
					}
				} else if (dragId != null) {
					dropItem(dragId, group, -1);
				}
				dragId = null;
				dragGroup = null;
				e.setDropCompleted(true);
				rebuild();
				e.consume();
			});
		}
		return section;
	}

	/**
	 * An item was dropped on a category (beforeId = the row it landed on, or -1 for
	 * the end).
	 */
	private void dropItem(long id, String group, long beforeId) {
		boolean toPinned = CategoryLayout.PINNED.equals(group);
		if (pantryView) {
			if (!toPinned) {
				Draft d = drafts.get(id);
				if (d != null) {
					d.category = group;
				}
			}
			facade.movePantryItem(id, beforeId);
			return;
		}
		for (ShoppingItem s : facade.getShoppingItems()) {
			if (s.getId() != id) {
				continue;
			}
			if (toPinned != s.isPinned()) {
				facade.toggleShoppingPin(id);
			}
			if (!toPinned) {
				Draft d = drafts.get(id);
				if (d != null) {
					d.category = group;
				}
			}
		}
		facade.moveShoppingItem(id, beforeId);
	}

	private void showPalette(Node anchor, String group, CategoryLayout l) {
		FlowPane swatches = new FlowPane(6, 6);
		swatches.setPrefWrapLength(190);
		ContextMenu menu = new ContextMenu();
		for (String hex : CategoryLayout.palette()) {
			Button b = new Button();
			b.getStyleClass().setAll("button", "pn-swatch");
			b.setStyle("-fx-background-color: " + hex + ";");
			b.setOnAction(e -> {
				l.setColor(group, hex);
				menu.hide();
				rebuild();
			});
			swatches.getChildren().add(b);
		}
		Button auto = new Button("Auto");
		auto.getStyleClass().setAll("button", "pn-link");
		auto.setOnAction(e -> {
			l.setColor(group, null);
			menu.hide();
			rebuild();
		});
		swatches.getChildren().add(auto);
		CustomMenuItem item = new CustomMenuItem(swatches, false);
		menu.getItems().add(item);
		menu.show(anchor, javafx.geometry.Side.BOTTOM, 0, 4);
	}

	private Label tag(String text, boolean yours) {
		Label t = new Label(text);
		t.getStyleClass().add(yours ? "pn-tag-yours" : "pn-tag");
		return t;
	}

	// ------------------------------------------------------------------ rows

	private Node pantryRow(PantryItem p, CategoryLayout l) {
		if (editing) {
			Draft d = draftOf(p.getId(), p.getName(), p.getQuantity(), p.getUnit(), p.getCategory(), p.getExpiryDate());
			return editRow(p.getId(), d, l, true);
		}
		CheckBox box = new CheckBox();
		box.setSelected(selected.contains(p.getId()));
		box.setOnAction(e -> {
			if (box.isSelected()) {
				selected.add(p.getId());
			} else {
				selected.remove(p.getId());
			}
			rebuild();
		});
		Label name = new Label(p.getName());
		name.getStyleClass().add("pn-item-name");
		Label left = new Label(Formats.quantityWithUnit(p.getQuantity(), p.getUnit()) + " left");
		left.getStyleClass().add("pn-muted");
		Region grow = new Region();
		HBox.setHgrow(grow, Priority.ALWAYS);
		HBox row = new HBox(12, box, name, left, grow);
		row.setAlignment(Pos.CENTER_LEFT);
		Label badge = badge(p);
		if (badge != null) {
			row.getChildren().add(badge);
		}
		Button log = new Button("Log");
		log.getStyleClass().setAll("button", "pn-log");
		log.setOnAction(e -> {
			if (onLogItem != null) {
				onLogItem.accept(p.getId());
			}
		});
		row.getChildren().add(log);
		row.getStyleClass().add("pn-row");
		return row;
	}

	private Label badge(PantryItem p) {
		if (p.getExpiryDate() == null) {
			return null;
		}
		int d = p.daysLeft(facade.today());
		Label b;
		if (d < 0) {
			b = new Label("Expired");
			b.getStyleClass().add("pn-badge-bad");
		} else if (d <= Freshness.USE_SOON_THRESHOLD_DAYS) {
			b = new Label(d == 0 ? "Today" : d == 1 ? "1 day left" : d + " days left");
			b.getStyleClass().add("pn-badge-soon");
		} else if (d > SHELF_STABLE_DAYS) {
			b = new Label("Shelf-stable");
			b.getStyleClass().add("pn-badge-ok");
		} else {
			b = new Label("Good · " + d + " days");
			b.getStyleClass().add("pn-badge-ok");
		}
		return b;
	}

	private Node listRow(ShoppingItem s, CategoryLayout l) {
		if (editing) {
			Draft d = draftOf(s.getId(), s.getName(), s.getQuantity(), s.getUnit(), s.getCategory(), null);
			return editRow(s.getId(), d, l, false);
		}
		CheckBox box = new CheckBox();
		box.setSelected(s.isChecked());
		Label name = new Label(s.getName());
		name.getStyleClass().add(s.isChecked() ? "pn-item-done" : "pn-item-name");
		Label qty = new Label(Formats.quantityWithUnit(s.getQuantity(), s.getUnit()));
		qty.getStyleClass().add("pn-muted");
		box.setOnAction(e -> {
			facade.toggleShoppingItem(s.getId());
			rebuild();
		});
		Region grow = new Region();
		HBox.setHgrow(grow, Priority.ALWAYS);
		HBox row = new HBox(12, box, name, qty);
		if (!s.getSource().isEmpty()) {
			Label from = new Label("from " + s.getSource());
			from.getStyleClass().add("pn-from");
			row.getChildren().add(from);
		}
		Button pin = new Button(s.isPinned() ? "★" : "☆");
		pin.getStyleClass().setAll("button", s.isPinned() ? "pn-pin-on" : "pn-pin");
		pin.setOnAction(e -> {
			facade.toggleShoppingPin(s.getId());
			rebuild();
		});
		row.getChildren().addAll(grow, pin);
		row.setAlignment(Pos.CENTER_LEFT);
		row.getStyleClass().add("pn-row");
		return row;
	}

	private Node editRow(long id, Draft d, CategoryLayout l, boolean pantry) {
		Label handle = new Label("⠇");
		handle.getStyleClass().add("pn-handle");

		TextField name = field("Name", 200);
		name.setText(d.name);
		name.textProperty().addListener((o, a, b) -> d.name = b);
		TextField qty = field("Qty", 80);
		qty.setText(d.qty);
		qty.textProperty().addListener((o, a, b) -> d.qty = b);
		TextField unit = field("Unit", 70);
		unit.setText(d.unit);
		unit.textProperty().addListener((o, a, b) -> d.unit = b);
		if (editTried && d.name.isBlank()) {
			name.getStyleClass().add("pn-input-error");
		}
		if (editTried && parseQty(d.qty) <= 0) {
			qty.getStyleClass().add("pn-input-error");
		}

		List<String> groups = new ArrayList<>(l.visibleGroups(pantry, true));
		groups.remove(CategoryLayout.RECIPES);
		if (pantry && CategoryLayout.RECIPES.equals(l.groupOf(d.name, d.category))) {
			groups.add(0, CategoryLayout.RECIPES);
		}
		ComboBox<String> cat = new ComboBox<>();
		cat.getStyleClass().add("pn-combo");
		cat.getItems().setAll(groups);
		cat.setConverter(new StringConverter<String>() {
			@Override
			public String toString(String s) {
				return s == null ? "" : l.labelOf(s);
			}

			@Override
			public String fromString(String s) {
				return s;
			}
		});
		cat.setValue(l.groupOf(d.name, d.category));
		cat.setPrefWidth(150);
		cat.valueProperty().addListener((o, a, b) -> {
			if (b != null && !b.equals(d.category)) {
				d.category = b;
				Platform.runLater(this::rebuild);
			}
		});

		HBox row = new HBox(8, handle, name, qty, unit, cat);
		if (pantry) {
			DatePicker date = new DatePicker(d.expires);
			date.getStyleClass().add("pn-date");
			date.setEditable(false);
			date.setPrefWidth(140);
			date.valueProperty().addListener((o, a, b) -> d.expires = b);
			row.getChildren().add(date);
		}
		Region grow = new Region();
		HBox.setHgrow(grow, Priority.ALWAYS);
		Button del = new Button("Delete");
		del.getStyleClass().setAll("button", "pn-link-danger");
		del.setOnAction(e -> {
			drafts.remove(id);
			if (pantry) {
				facade.removePantryItems(List.of(id));
			} else {
				facade.deleteShoppingItems(List.of(id));
			}
			say("Deleted. Undo brings it back.");
			rebuild();
		});
		row.getChildren().addAll(grow, del);
		row.setAlignment(Pos.CENTER_LEFT);
		row.getStyleClass().add("pn-row");

		handle.setOnDragDetected(e -> {
			Dragboard db = handle.startDragAndDrop(TransferMode.MOVE);
			ClipboardContent c = new ClipboardContent();
			c.putString(Long.toString(id));
			db.setContent(c);
			dragId = id;
			dragGroup = null;
			e.consume();
		});
		handle.setStyle("-fx-cursor: move;");
		return row;
	}

	// ------------------------------------------------------------------ small
	// helpers

	private static TextField field(String prompt, double width) {
		TextField f = new TextField();
		f.setPromptText(prompt);
		f.setPrefWidth(width);
		f.getStyleClass().add("pn-input");
		return f;
	}

	private static Node labeled(String label, Node control) {
		Label l = new Label(label);
		l.getStyleClass().add("pn-label");
		return new VBox(4, l, control);
	}
}