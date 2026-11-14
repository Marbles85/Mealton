package com.mealton.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mealton.diary.MealLogService;
import com.mealton.facade.MealtonFacade;
import com.mealton.util.Formats;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The pop-ups shown right after a meal is logged, from the Log food page and
 * from the Recipes page ("I made this"): first what was logged and what came
 * out of the pantry (with Undo), then, when something is running low, a chance
 * to put it on the shopping list.
 */
final class LogFeedback {

	/** What the user did in the pop-ups. */
	record Outcome(boolean undone, int addedToList) {
	}

	private LogFeedback() {
	}

	private static Dialog<ButtonType> dialog(Node owner, String title) {
		Dialog<ButtonType> d = new Dialog<>();
		d.setTitle(title);
		d.setHeaderText(null);
		d.setGraphic(null);
		var main = LogFeedback.class.getResource("/mealton.css");
		if (main != null) {
			d.getDialogPane().getStylesheets().add(main.toExternalForm());
		}
		var css = LogFeedback.class.getResource("/logfood.css");
		if (css != null) {
			d.getDialogPane().getStylesheets().add(css.toExternalForm());
		}
		d.getDialogPane().getStyleClass().add("lf-dialog");
		if (owner != null && owner.getScene() != null && owner.getScene().getWindow() != null) {
			d.initOwner(owner.getScene().getWindow());
		}
		return d;
	}

	private static Label label(String text, String style) {
		Label l = new Label(text);
		l.getStyleClass().add(style);
		l.setWrapText(true);
		l.setMinHeight(Region.USE_PREF_SIZE);
		return l;
	}

	private static double round2(double v) {
		return Math.round(v * 100) / 100.0;
	}

	/**
	 * Shows what was logged and taken out of the pantry, offers Undo, then asks
	 * about the shopping list when items are low. Call on the FX thread.
	 */
	static Outcome afterLogging(Node owner, MealtonFacade facade, MealLogService.Result r) {
		Dialog<ButtonType> d = dialog(owner, "Logged");
		VBox box = new VBox(14);
		box.setPrefWidth(460);
		box.setPadding(new Insets(6, 6, 18, 6));
		Label check = new Label("\u2713");
		check.getStyleClass().add("lf-dlg-icon");
		check.setMinSize(40, 40);
		check.setAlignment(Pos.CENTER);
		VBox heading = new VBox(3, label("Logged", "lf-dlg-eyebrow"), label(r.entry().description(), "lf-h2"));
		HBox.setHgrow(heading, Priority.ALWAYS);
		HBox top = new HBox(14, check, heading);
		top.setAlignment(Pos.CENTER_LEFT);
		box.getChildren().add(top);
		if (facade.tracksMacros()) {
			Label pill = new Label("+" + Math.round(r.entry().nutrients().calories()) + " kcal added to today");
			pill.getStyleClass().add("lf-dlg-pill");
			box.getChildren().add(pill);
		}
		VBox list = new VBox(8);
		list.getStyleClass().add("lf-dlg-card");
		if (!r.pantryChanges().isEmpty()) {
			list.getChildren().add(label("Taken out of your pantry", "lf-label"));
			for (String c : r.pantryChanges()) {
				Label row = label(c.replace(" \u2192 ", "   \u2192   "), "lf-text");
				row.getStyleClass().add("lf-dlg-line");
				list.getChildren().add(row);
			}
		} else {
			list.getChildren().add(label("Nothing was taken out of your pantry.", "lf-muted"));
		}
		box.getChildren().add(list);
		if (!r.leftoverText().isEmpty()) {
			box.getChildren().add(label(r.leftoverText(), "lf-note"));
		}
		d.getDialogPane().setContent(box);
		ButtonType undo = new ButtonType("Undo", ButtonBar.ButtonData.LEFT);
		ButtonType done = new ButtonType("Done", ButtonBar.ButtonData.OK_DONE);
		d.getDialogPane().getButtonTypes().addAll(undo, done);
		Button doneBtn = (Button) d.getDialogPane().lookupButton(done);
		doneBtn.getStyleClass().add("lf-log-btn");
		ButtonType chosen = d.showAndWait().orElse(done);
		if (chosen == undo) {
			facade.undoMealLog(r.undo());
			return new Outcome(true, 0);
		}
		int added = 0;
		if (!r.low().isEmpty()) {
			added = askLowStock(owner, facade, r.low());
		}
		return new Outcome(false, added);
	}

	/**
	 * "You're running low": pick what to add to the shopping list. Returns how many
	 * were added.
	 */
	static int askLowStock(Node owner, MealtonFacade facade, List<MealLogService.LowItem> low) {
		Dialog<ButtonType> d = dialog(owner, "You’re running low");
		VBox box = new VBox(12);
		box.setPrefWidth(460);
		box.setPadding(new Insets(4));
		box.getChildren().add(label("You’re running low", "lf-h2"));
		box.getChildren().add(
				label("You’re running low on these items after logging this meal. " + "Add them to your shopping list?",
						"lf-text"));
		List<CheckBox> checks = new ArrayList<>();
		Label count = label("", "lf-label");
		Label selectAll = label("", "lf-link-text");
		VBox rows = new VBox(8);
		for (MealLogService.LowItem l : low) {
			CheckBox c = new CheckBox(l.name());
			c.setSelected(true);
			c.getStyleClass().add("lf-low-check");
			checks.add(c);
			boolean out = l.left() <= 1e-9;
			Label badge = label(out ? "Out" : Formats.quantityWithUnit(round2(l.left()), l.unit()) + " left",
					out ? "lf-badge-out" : "lf-badge-low");
			badge.setWrapText(false);
			Label why = label(
					l.why().isEmpty() || l.why().equalsIgnoreCase("All used up") ? "Used up in this meal" : l.why(),
					"lf-muted");
			VBox text = new VBox(2, c, why);
			HBox.setHgrow(text, Priority.ALWAYS);
			HBox row = new HBox(10, text, badge);
			row.setAlignment(Pos.CENTER_LEFT);
			row.getStyleClass().add("lf-low-row");
			rows.getChildren().add(row);
		}
		ButtonType no = new ButtonType("No thanks", ButtonBar.ButtonData.CANCEL_CLOSE);
		ButtonType some = new ButtonType("Add selected", ButtonBar.ButtonData.OK_DONE);
		ButtonType all = new ButtonType("Yes, add all", ButtonBar.ButtonData.APPLY);
		d.getDialogPane().getButtonTypes().addAll(no, some, all);
		javafx.scene.Node bar = d.getDialogPane().lookup(".button-bar");
		if (bar instanceof ButtonBar bb) {
			bb.setButtonOrder(ButtonBar.BUTTON_ORDER_NONE);
		}
		((Button) d.getDialogPane().lookupButton(all)).getStyleClass().add("lf-log-btn");
		Button someBtn = (Button) d.getDialogPane().lookupButton(some);
		Runnable update = () -> {
			long n = checks.stream().filter(CheckBox::isSelected).count();
			count.setText("Items to add · " + n + " of " + low.size());
			selectAll.setText(n == low.size() ? "Deselect all" : "Select all");
			someBtn.setText("Add selected (" + n + ")");
			someBtn.setDisable(n == 0);
		};
		for (CheckBox c : checks) {
			c.setOnAction(e -> update.run());
		}
		selectAll.setOnMouseClicked(e -> {
			boolean target = checks.stream().anyMatch(c -> !c.isSelected());
			checks.forEach(c -> c.setSelected(target));
			update.run();
		});
		update.run();
		HBox head = new HBox(10, count, selectAll);
		HBox.setHgrow(count, Priority.ALWAYS);
		count.setMaxWidth(Double.MAX_VALUE);
		head.setAlignment(Pos.CENTER_LEFT);
		box.getChildren().addAll(head, rows);
		d.getDialogPane().setContent(box);
		Optional<ButtonType> result = d.showAndWait();
		ButtonType chosen = result.orElse(no);
		List<MealLogService.LowItem> toAdd = new ArrayList<>();
		if (chosen == all) {
			toAdd.addAll(low);
		} else if (chosen == some) {
			for (int i = 0; i < low.size(); i++) {
				if (checks.get(i).isSelected()) {
					toAdd.add(low.get(i));
				}
			}
		}
		return toAdd.isEmpty() ? 0 : facade.addLowItemsToShoppingList(toAdd);
	}

	/**
	 * Asks for the name of a new meal tag in a pop-up styled like the rest of the
	 * app. Adds it through the facade and returns the saved name, or empty if
	 * cancelled.
	 */
	static Optional<String> askNewTag(Node owner, MealtonFacade facade) {
		Dialog<ButtonType> d = dialog(owner, "New meal tag");
		VBox box = new VBox(10);
		box.setPrefWidth(380);
		box.setPadding(new Insets(4));
		box.getChildren().add(label("New meal tag", "lf-h2"));
		box.getChildren().add(label("Name a meal the way you eat it, like Snack or Post-workout. "
				+ "It shows up everywhere you pick a meal.", "lf-text"));
		javafx.scene.control.TextField field = new javafx.scene.control.TextField();
		field.setPromptText("e.g. Post-workout");
		field.getStyleClass().add("lf-input");
		Label error = label("", "lf-error");
		error.setVisible(false);
		error.setManaged(false);
		FlowTags existing = new FlowTags(facade.getMealTags());
		box.getChildren().addAll(field, error, label("Your tags", "lf-label"), existing.node());
		d.getDialogPane().setContent(box);
		ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
		ButtonType add = new ButtonType("Add tag", ButtonBar.ButtonData.OK_DONE);
		d.getDialogPane().getButtonTypes().addAll(cancel, add);
		Button addBtn = (Button) d.getDialogPane().lookupButton(add);
		addBtn.getStyleClass().add("lf-log-btn");
		String[] saved = { null };
		addBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
			try {
				saved[0] = facade.addMealTag(field.getText());
			} catch (IllegalArgumentException ex) {
				error.setText(ex.getMessage());
				error.setVisible(true);
				error.setManaged(true);
				ev.consume();
			}
		});
		javafx.application.Platform.runLater(field::requestFocus);
		d.showAndWait();
		return Optional.ofNullable(saved[0]);
	}

	/** The existing tags shown as small pills. */
	private record FlowTags(List<String> tags) {
		Node node() {
			javafx.scene.layout.FlowPane flow = new javafx.scene.layout.FlowPane(6, 6);
			for (String t : tags) {
				Label l = new Label(t);
				l.getStyleClass().add("lf-tag-pill");
				flow.getChildren().add(l);
			}
			return flow;
		}
	}

	/**
	 * Edit a logged entry (also one from a past day): day, meal, time, note and,
	 * when macro tracking is on, the numbers. Returns the edited copy (same id) or
	 * empty if cancelled. Editing the food log does not change the pantry.
	 */
	static Optional<com.mealton.nutrition.LoggedFood> editEntry(Node owner, MealtonFacade facade,
			com.mealton.nutrition.LoggedFood f, java.time.LocalDate today) {
		Dialog<ButtonType> d = dialog(owner, "Edit entry");
		VBox box = new VBox(12);
		box.setPrefWidth(460);
		box.setPadding(new Insets(6, 6, 18, 6));
		box.getChildren().add(label("Edit entry", "lf-h2"));
		box.getChildren().add(label(f.description(), "lf-text-strong"));

		javafx.scene.control.DatePicker day = new javafx.scene.control.DatePicker(f.date());
		day.setEditable(false);
		day.setMaxWidth(Double.MAX_VALUE);
		javafx.scene.control.ComboBox<String> meal = new javafx.scene.control.ComboBox<>();
		List<String> tags = new ArrayList<>(facade.getMealTags());
		tags.add(0, "");
		if (!f.meal().isEmpty() && !tags.contains(f.meal())) {
			tags.add(f.meal());
		}
		meal.getItems().addAll(tags);
		meal.setValue(f.meal());
		meal.getStyleClass().add("lf-combo");
		meal.setMaxWidth(Double.MAX_VALUE);
		javafx.scene.control.TextField time = new javafx.scene.control.TextField(f.time());
		time.setPromptText("HH:mm");
		time.getStyleClass().add("lf-input");
		javafx.scene.control.TextField note = new javafx.scene.control.TextField(f.note());
		note.setPromptText("Note (optional)");
		note.getStyleClass().add("lf-input");

		VBox dayBox = new VBox(4, label("Day", "lf-label"), day);
		VBox mealBox = new VBox(4, label("Meal", "lf-label"), meal);
		VBox timeBox = new VBox(4, label("Time", "lf-label"), time);
		HBox.setHgrow(dayBox, Priority.ALWAYS);
		HBox.setHgrow(mealBox, Priority.ALWAYS);
		timeBox.setPrefWidth(90);
		box.getChildren().addAll(new HBox(10, dayBox, mealBox, timeBox), new VBox(4, label("Note", "lf-label"), note));

		javafx.scene.control.TextField[] nums = new javafx.scene.control.TextField[4];
		if (facade.tracksMacros()) {
			String[] names = { "Calories", "Protein g", "Carbs g", "Fat g" };
			double[] vals = { f.nutrients().calories(), f.nutrients().protein(), f.nutrients().carbs(),
					f.nutrients().fat() };
			HBox row = new HBox(8);
			for (int k = 0; k < 4; k++) {
				nums[k] = new javafx.scene.control.TextField(Formats.number(Math.round(vals[k] * 10) / 10.0));
				nums[k].getStyleClass().add("lf-input");
				VBox cell = new VBox(4, label(names[k], "lf-label"), nums[k]);
				HBox.setHgrow(cell, Priority.ALWAYS);
				cell.setMinWidth(0);
				row.getChildren().add(cell);
			}
			box.getChildren().add(row);
		}
		box.getChildren().add(label("This only changes your food log. Your pantry stays as it is.", "lf-muted"));
		Label error = label("", "lf-error");
		error.setVisible(false);
		error.setManaged(false);
		box.getChildren().add(error);
		d.getDialogPane().setContent(box);
		ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
		ButtonType save = new ButtonType("Save changes", ButtonBar.ButtonData.OK_DONE);
		d.getDialogPane().getButtonTypes().addAll(cancel, save);
		Button saveBtn = (Button) d.getDialogPane().lookupButton(save);
		saveBtn.getStyleClass().add("lf-log-btn");
		com.mealton.nutrition.LoggedFood[] result = { null };
		saveBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
			try {
				String t = time.getText() == null ? "" : time.getText().trim();
				if (!t.isEmpty() && !t.matches("([01]?\\d|2[0-3]):[0-5]\\d")) {
					throw new IllegalArgumentException("Time should look like 18:30.");
				}
				if (day.getValue() == null) {
					throw new IllegalArgumentException("Pick a day.");
				}
				com.mealton.nutrition.Nutrients n = f.nutrients();
				boolean estimated = f.estimated();
				if (facade.tracksMacros()) {
					double[] v = new double[4];
					for (int k = 0; k < 4; k++) {
						try {
							v[k] = Double.parseDouble(nums[k].getText().trim().replace(',', '.'));
						} catch (NumberFormatException bad) {
							throw new IllegalArgumentException("Enter numbers for calories and macros.");
						}
					}
					n = new com.mealton.nutrition.Nutrients(v[0], v[1], v[2], v[3]);
					if (Math.abs(n.calories() - f.nutrients().calories()) > 0.5
							|| Math.abs(n.protein() - f.nutrients().protein()) > 0.05
							|| Math.abs(n.carbs() - f.nutrients().carbs()) > 0.05
							|| Math.abs(n.fat() - f.nutrients().fat()) > 0.05) {
						estimated = false;
					}
				}
				String m = meal.getValue() == null ? "" : meal.getValue();
				String time2 = t.isEmpty() ? f.time() : t;
				result[0] = new com.mealton.nutrition.LoggedFood(f.id(), day.getValue(), f.description(), n, estimated,
						m, time2, note.getText() == null ? "" : note.getText().trim(), f.source());
			} catch (IllegalArgumentException ex) {
				error.setText(ex.getMessage());
				error.setVisible(true);
				error.setManaged(true);
				ev.consume();
			}
		});
		d.showAndWait();
		return Optional.ofNullable(result[0]);
	}
}