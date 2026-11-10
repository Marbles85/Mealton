package com.mealton.gui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mealton.facade.MealtonFacade;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Main window: a header with page navigation and the current page in the centre
 * (Stage 1: MainWindow). Pages that are not built yet appear disabled.
 */
public class MainWindow extends BorderPane {
	public static final String PANTRY_PAGE = "Pantry & List";
	public static final String CHAT_PAGE = "Mealton Chat";

	private final Map<String, Node> pages = new LinkedHashMap<>();

	public MainWindow(MealtonFacade facade) {
		PantryShoppingView pantryView = new PantryShoppingView(facade);
		pages.put(PANTRY_PAGE, pantryView);
		// after the AI agent uses a tool, the pantry/shopping page reloads what changed
		pages.put(CHAT_PAGE, new ChatView(facade, pantryView::refreshAll));

		Label title = new Label("Mealton");
		title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

		HBox nav = new HBox(8);
		nav.setAlignment(Pos.CENTER_LEFT);
		for (String name : pages.keySet()) {
			Button b = new Button(name);
			b.setOnAction(e -> showPage(name));
			nav.getChildren().add(b);
		}
		for (String planned : List.of("Calendar", "Recipes", "Profile")) {
			Button b = new Button(planned);
			b.setDisable(true);
			b.setTooltip(new Tooltip("Coming soon"));
			nav.getChildren().add(b);
		}

		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox header = new HBox(20, title, nav, spacer);
		header.setAlignment(Pos.CENTER_LEFT);
		header.setPadding(new Insets(10, 16, 10, 16));
		header.setStyle("-fx-border-color: #dddddd; -fx-border-width: 0 0 1 0;");
		setTop(header);

		showPage(PANTRY_PAGE);
	}

	/** Shows the page with this name in the centre of the window. */
	public void showPage(String name) {
		Node page = pages.get(name);
		if (page != null) {
			setCenter(page);
		}
	}
}