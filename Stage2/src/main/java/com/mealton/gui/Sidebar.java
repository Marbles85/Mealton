package com.mealton.gui;

import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * The left navigation: 232 px wide, or a 72 px icon rail when hidden (click the
 * Mealton logo to switch). Shows Recipes, Log food, Meal plan and Pantry &amp;
 * list, an "Ask Mealton" tab when the chat lives in the sidebar, and the
 * profile card with Log out at the bottom.
 */
public class Sidebar extends VBox {
	private static final double EXPANDED = 232;
	private static final double RAIL = 72;
	private static final String[][] NAV = { { MainWindow.RECIPES_PAGE, Icons.BOOK }, { MainWindow.LOG_PAGE, Icons.LOG },
			{ MainWindow.PLAN_PAGE, Icons.CALENDAR }, { MainWindow.PANTRY_PAGE, Icons.PANTRY }, };

	private final UiSettings settings;
	private final Consumer<String> onNavigate;
	private final Runnable onChatToggle;
	private final Runnable onLogout;

	private String active = "";
	private String displayName = "";
	private int expiring;
	private boolean chatInSidebar;
	private boolean chatOpen;

	public Sidebar(UiSettings settings, Consumer<String> onNavigate, Runnable onChatToggle, Runnable onLogout) {
		this.settings = settings;
		this.onNavigate = onNavigate;
		this.onChatToggle = onChatToggle;
		this.onLogout = onLogout;
		getStyleClass().add("sidebar");
		rebuild();
	}

	public void setActive(String page) {
		if (!page.equals(active)) {
			active = page;
			rebuild();
		}
	}

	public void setUser(String name) {
		if (!name.equals(displayName)) {
			displayName = name;
			rebuild();
		}
	}

	/** Number shown on the Pantry item: things that expire within a few days. */
	public void setExpiring(int count) {
		if (count != expiring) {
			expiring = count;
			rebuild();
		}
	}

	public void setChatInSidebar(boolean value) {
		if (value != chatInSidebar) {
			chatInSidebar = value;
			rebuild();
		}
	}

	public void setChatOpen(boolean value) {
		if (value != chatOpen) {
			chatOpen = value;
			rebuild();
		}
	}

	private void rebuild() {
		boolean rail = settings.sidebarHidden();
		double width = rail ? RAIL : EXPANDED;
		setMinWidth(width);
		setPrefWidth(width);
		setMaxWidth(width);
		setSpacing(4);
		setAlignment(rail ? Pos.TOP_CENTER : Pos.TOP_LEFT);
		setPadding(rail ? new Insets(24, 0, 24, 0) : new Insets(24, 16, 24, 16));
		getChildren().clear();

		getChildren().add(header(rail));
		for (String[] item : NAV) {
			getChildren().add(navButton(item[0], item[1], rail));
		}
		if (chatInSidebar) {
			getChildren().addAll(divider(rail), chatButton(rail));
		}
		Region spacer = new Region();
		VBox.setVgrow(spacer, Priority.ALWAYS);
		getChildren().addAll(spacer, divider(rail), profileButton(rail), logoutButton(rail));
	}

	private Node header(boolean rail) {
		Button logo = new Button();
		logo.getStyleClass().add("sidebar-logo");
		logo.setGraphic(Icons.of(Icons.LEAF, 22, Color.WHITE));
		logo.setTooltip(new Tooltip(rail ? "Show sidebar" : "Hide sidebar"));
		logo.setOnAction(e -> {
			settings.setSidebarHidden(!rail);
			rebuild();
		});
		if (rail) {
			HBox box = new HBox(logo);
			box.setAlignment(Pos.CENTER);
			box.setPadding(new Insets(0, 0, 16, 0));
			return box;
		}
		Label brand = new Label("Mealton");
		brand.getStyleClass().add("sidebar-brand");
		HBox box = new HBox(10, logo, brand);
		box.setAlignment(Pos.CENTER_LEFT);
		box.setPadding(new Insets(0, 0, 20, 4));
		return box;
	}

	private Button navButton(String page, String iconPath, boolean rail) {
		boolean on = page.equals(active);
		Color color = Color.web(on ? "#2F4A34" : "#3E4A42");
		Node icon = Icons.of(iconPath, 20, color);
		Label badge = null;
		if (MainWindow.PANTRY_PAGE.equals(page) && expiring > 0) {
			badge = new Label(String.valueOf(expiring));
			badge.getStyleClass().add("badge");
		}

		Button button = new Button();
		button.getStyleClass().add("nav-item");
		if (on) {
			button.getStyleClass().add("nav-item-active");
		}
		button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
		button.setMaxWidth(Double.MAX_VALUE);
		button.setOnAction(e -> onNavigate.accept(page));

		if (rail) {
			button.getStyleClass().add("nav-rail");
			button.setTooltip(new Tooltip(page));
			StackPane stack = new StackPane(icon);
			if (badge != null) {
				StackPane.setAlignment(badge, Pos.TOP_RIGHT);
				stack.getChildren().add(badge);
			}
			button.setGraphic(stack);
		} else {
			Label text = new Label(page);
			text.setTextFill(color);
			if (on) {
				text.setStyle("-fx-font-weight: bold;");
			}
			Region grow = new Region();
			HBox.setHgrow(grow, Priority.ALWAYS);
			HBox row = new HBox(12, icon, text, grow);
			row.setAlignment(Pos.CENTER_LEFT);
			if (badge != null) {
				row.getChildren().add(badge);
			}
			row.prefWidthProperty().bind(button.widthProperty().subtract(30));
			button.setGraphic(row);
		}
		return button;
	}

	private Button chatButton(boolean rail) {
		Button button = new Button();
		button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
		button.setOnAction(e -> onChatToggle.run());
		if (rail) {
			button.getStyleClass().add("chat-rail-button");
			if (chatOpen) {
				button.getStyleClass().add("chat-rail-button-open");
			}
			button.setTooltip(new Tooltip("Ask Mealton"));
			button.setGraphic(Icons.of(Icons.LEAF, 20, Color.WHITE));
		} else {
			button.getStyleClass().addAll("nav-item", "chat-tab");
			if (chatOpen) {
				button.getStyleClass().add("chat-tab-open");
			}
			button.setMaxWidth(Double.MAX_VALUE);
			StackPane avatar = new StackPane(Icons.of(Icons.LEAF, 16, Color.WHITE));
			avatar.getStyleClass().add("mini-avatar");
			avatar.setMinSize(28, 28);
			avatar.setMaxSize(28, 28);
			Label text = new Label("Ask Mealton");
			text.setTextFill(Color.web("#2F4A34"));
			text.setStyle("-fx-font-weight: bold;");
			HBox row = new HBox(12, avatar, text);
			row.setAlignment(Pos.CENTER_LEFT);
			button.setGraphic(row);
		}
		return button;
	}

	private Button profileButton(boolean rail) {
		boolean on = MainWindow.PROFILE_PAGE.equals(active);
		Button button = new Button();
		button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
		button.setOnAction(e -> onNavigate.accept(MainWindow.PROFILE_PAGE));
		if (rail) {
			button.getStyleClass().add("profile-rail");
			if (on) {
				button.getStyleClass().add("profile-rail-active");
			}
			button.setTooltip(new Tooltip("Profile"));
			button.setGraphic(avatar(40));
		} else {
			button.getStyleClass().add("profile-card");
			if (on) {
				button.getStyleClass().add("profile-card-active");
			}
			button.setMaxWidth(Double.MAX_VALUE);
			Label name = new Label(displayName);
			name.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
			name.setMaxWidth(110);
			name.setTextOverrun(OverrunStyle.ELLIPSIS);
			Label sub = new Label("Profile & settings");
			sub.getStyleClass().add("muted");
			sub.setStyle("-fx-font-size: 13px;");
			Region grow = new Region();
			HBox.setHgrow(grow, Priority.ALWAYS);
			HBox row = new HBox(10, avatar(36), new VBox(name, sub), grow,
					Icons.of(Icons.CHEVRON_RIGHT, 16, Color.web("#5B6660")));
			row.setAlignment(Pos.CENTER_LEFT);
			row.prefWidthProperty().bind(button.widthProperty().subtract(24));
			button.setGraphic(row);
		}
		return button;
	}

	private Button logoutButton(boolean rail) {
		Button button = new Button();
		button.getStyleClass().add("link-button");
		button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
		button.setOnAction(e -> onLogout.run());
		Node icon = Icons.of(Icons.LOGOUT, 16, Color.web("#5B6660"));
		if (rail) {
			button.setTooltip(new Tooltip("Log out"));
			button.setGraphic(icon);
		} else {
			Label text = new Label("Log out");
			text.getStyleClass().add("muted");
			HBox row = new HBox(10, icon, text);
			row.setAlignment(Pos.CENTER_LEFT);
			button.setGraphic(row);
			button.setMaxWidth(Double.MAX_VALUE);
		}
		return button;
	}

	private Node divider(boolean rail) {
		Region line = new Region();
		line.getStyleClass().add("sidebar-divider");
		if (rail) {
			line.setMaxWidth(44);
		}
		VBox.setMargin(line, new Insets(8, 0, 8, 0));
		return line;
	}

	private StackPane avatar(double size) {
		Label letters = new Label(initials(displayName));
		letters.getStyleClass().add("avatar-text");
		StackPane circle = new StackPane(letters);
		circle.getStyleClass().add("avatar");
		circle.setMinSize(size, size);
		circle.setMaxSize(size, size);
		return circle;
	}

	private static String initials(String name) {
		String[] parts = name.trim().split("\\s+");
		String result = parts[0].isEmpty() ? "?" : parts[0].substring(0, 1);
		if (parts.length > 1 && !parts[1].isEmpty()) {
			result += parts[1].substring(0, 1);
		}
		return result.toUpperCase();
	}
}