package com.mealton.gui;

import java.util.function.Consumer;

import com.mealton.account.AccountService;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * The first screen: log in, or create an account. Each user gets their own
 * profile, pantry, recipes, meal plan and food diary. Passwords are checked by
 * {@link AccountService} and never stored as typed.
 */
public class LoginView extends HBox {
	private final AccountService accounts;
	private final Consumer<String> onLoggedIn;
	private boolean loginMode = true;

	private final Label heading = new Label();
	private final Label subheading = new Label();
	private final Button loginTab = new Button("Log in");
	private final Button registerTab = new Button("Register");
	private final TextField user = new TextField();
	private final PasswordField password = new PasswordField();
	private final PasswordField again = new PasswordField();
	private final VBox againBox;
	private final CheckBox remember = new CheckBox("Remember me");
	private final HBox optionsRow = new HBox();
	private final Hyperlink forgot = new Hyperlink("Forgot password?");
	private final Button submit = new Button();
	private final HBox messageBox = new HBox(10);
	private final StackPane messageIcon = new StackPane();
	private final Label messageText = new Label();
	private final Label switchNote = new Label();
	private final Hyperlink switchLink = new Hyperlink();

	/**
	 * @param onLoggedIn called with the username after a successful login or
	 *                   sign-up
	 */
	public LoginView(AccountService accounts, Consumer<String> onLoggedIn) {
		this.accounts = accounts;
		this.onLoggedIn = onLoggedIn;

		VBox left = buildBrandPanel();
		left.prefWidthProperty().bind(widthProperty().multiply(0.45));
		left.setMinWidth(300);

		againBox = field("Confirm password", again);
		StackPane right = new StackPane(buildForm());
		right.setPadding(new Insets(48, 24, 48, 24));
		HBox.setHgrow(right, Priority.ALWAYS);

		getChildren().addAll(left, right);

		String remembered = UiSettings.rememberedUser();
		if (!remembered.isEmpty()) {
			user.setText(remembered);
			remember.setSelected(true);
		}
		showMode(true);
	}

	// ---------- left: the green brand panel ----------
	private VBox buildBrandPanel() {
		StackPane tile = new StackPane(Icons.of(Icons.LEAF, 24, Color.web("#2F4A34")));
		tile.getStyleClass().add("logo-tile");
		tile.setMinSize(44, 44);
		tile.setMaxSize(44, 44);
		Label brand = new Label("Mealton");
		brand.getStyleClass().add("login-brand");
		HBox logo = new HBox(12, tile, brand);
		logo.setAlignment(Pos.CENTER_LEFT);

		Label headline = new Label("Know what to eat. Use what you have.");
		headline.getStyleClass().add("login-headline");
		headline.setWrapText(true);
		Label lead = new Label("Your AI meal-planning agent builds the week around your pantry, your goals and "
				+ "your allergies \u2014 then writes the shopping list.");
		lead.getStyleClass().add("login-lead");
		lead.setWrapText(true);
		VBox pitch = new VBox(20, headline, lead);
		pitch.setMaxWidth(520);

		VBox bullets = new VBox(14, bullet("Weekly plans that respect calories, macros and allergies"),
				bullet("Recipes ranked by what\u2019s about to expire"),
				bullet("A prep-day schedule that fits the time you have"));

		Region top = new Region();
		Region bottom = new Region();
		VBox.setVgrow(top, Priority.ALWAYS);
		VBox.setVgrow(bottom, Priority.ALWAYS);
		VBox panel = new VBox(logo, top, pitch, bottom, bullets);
		panel.getStyleClass().add("login-left");
		panel.setPadding(new Insets(48, 56, 48, 56));
		return panel;
	}

	private static HBox bullet(String text) {
		Region dot = new Region();
		dot.getStyleClass().add("dot");
		dot.setMinSize(8, 8);
		dot.setMaxSize(8, 8);
		Label label = new Label(text);
		label.getStyleClass().add("login-bullet");
		label.setWrapText(true);
		HBox row = new HBox(12, dot, label);
		row.setAlignment(Pos.CENTER_LEFT);
		return row;
	}

	// ---------- right: the form ----------
	private VBox buildForm() {
		heading.getStyleClass().add("form-heading");
		subheading.getStyleClass().add("muted");
		subheading.setWrapText(true);
		VBox titles = new VBox(6, heading, subheading);

		loginTab.getStyleClass().add("seg-button");
		registerTab.getStyleClass().add("seg-button");
		loginTab.setMaxWidth(Double.MAX_VALUE);
		registerTab.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(loginTab, Priority.ALWAYS);
		HBox.setHgrow(registerTab, Priority.ALWAYS);
		HBox tabs = new HBox(4, loginTab, registerTab);
		tabs.getStyleClass().add("seg-bar");
		loginTab.setOnAction(e -> showMode(true));
		registerTab.setOnAction(e -> showMode(false));

		user.setPromptText("Username");
		user.setOnAction(e -> password.requestFocus());
		password.setOnAction(e -> {
			if (loginMode) {
				submit.fire();
			} else {
				again.requestFocus();
			}
		});
		again.setPromptText("Type the password again");
		again.setOnAction(e -> submit.fire());

		Region grow = new Region();
		HBox.setHgrow(grow, Priority.ALWAYS);
		optionsRow.getChildren().addAll(remember, grow, forgot);
		optionsRow.setAlignment(Pos.CENTER_LEFT);
		forgot.setOnAction(e -> showMessage("Mealton keeps accounts on this computer only, so there is no email "
				+ "password reset. If you forgot your password, create a new account.", false));

		submit.getStyleClass().add("primary");
		submit.setDefaultButton(true);
		submit.setMaxWidth(Double.MAX_VALUE);
		submit.setOnAction(e -> submit());

		messageBox.setAlignment(Pos.TOP_LEFT);
		messageText.setWrapText(true);
		messageText.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(messageText, Priority.ALWAYS);
		messageBox.getChildren().addAll(messageIcon, messageText);
		hideMessage();

		switchNote.getStyleClass().add("muted");
		HBox switchRow = new HBox(4, switchNote, switchLink);
		switchRow.setAlignment(Pos.CENTER);
		switchLink.setOnAction(e -> showMode(!loginMode));

		VBox form = new VBox(20, titles, tabs, field("Username", user), field("Password", password), againBox,
				optionsRow, submit, messageBox, switchRow);
		form.setMaxWidth(400);
		form.setAlignment(Pos.CENTER_LEFT);
		return form;
	}

	private static VBox field(String label, javafx.scene.Node control) {
		Label l = new Label(label);
		l.getStyleClass().add("field-label");
		return new VBox(8, l, control);
	}

	// ---------- behaviour ----------
	private void showMode(boolean login) {
		loginMode = login;
		heading.setText(login ? "Welcome back" : "Create your account");
		subheading.setText(login ? "Log in to see this week\u2019s plan."
				: "Pick a username and password \u2014 we\u2019ll set up your profile next.");
		password.setPromptText(login ? "Password" : "At least " + AccountService.MIN_PASSWORD_LENGTH + " characters");
		user.setPromptText(login ? "Username" : "3-20 letters, digits or _");

		loginTab.getStyleClass().remove("seg-button-on");
		registerTab.getStyleClass().remove("seg-button-on");
		(login ? loginTab : registerTab).getStyleClass().add("seg-button-on");

		againBox.setVisible(!login);
		againBox.setManaged(!login);
		optionsRow.setVisible(login);
		optionsRow.setManaged(login);
		submit.setText(login ? "Log in" : "Create account");
		switchNote.setText(login ? "New here?" : "Already registered?");
		switchLink.setText(login ? "Create an account" : "Log in");
		hideMessage();
	}

	private void submit() {
		hideMessage();
		String name;
		try {
			name = loginMode ? accounts.logIn(user.getText(), password.getText())
					: accounts.signUp(user.getText(), password.getText(), again.getText());
		} catch (IllegalArgumentException | IllegalStateException e) {
			showMessage(e.getMessage(), true);
			return;
		}
		if (loginMode) {
			UiSettings.setRememberedUser(remember.isSelected() ? name : null);
		}
		password.clear();
		again.clear();
		onLoggedIn.accept(name);
	}

	private void showMessage(String text, boolean error) {
		messageBox.getStyleClass().setAll(error ? "error-box" : "info-box");
		messageIcon.getChildren().setAll(Icons.of(Icons.ALERT, 18, Color.web(error ? "#8A2C1E" : "#8A4208")));
		messageText.setText(text);
		messageBox.setVisible(true);
		messageBox.setManaged(true);
	}

	private void hideMessage() {
		messageBox.setVisible(false);
		messageBox.setManaged(false);
	}
}