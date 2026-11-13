package com.mealton.gui;

import java.util.function.Consumer;

import com.mealton.account.AccountService;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * The first screen: log in, or create an account. Each user gets their own
 * profile, pantry, recipes, meal plan and food diary. Passwords are checked by
 * {@link AccountService} and never stored as typed.
 */
public class LoginView extends StackPane {
	private final AccountService accounts;
	private final Consumer<String> onLoggedIn;

	private final TextField loginUser = new TextField();
	private final PasswordField loginPassword = new PasswordField();
	private final Label loginStatus = new Label();

	private final TextField signupUser = new TextField();
	private final PasswordField signupPassword = new PasswordField();
	private final PasswordField signupAgain = new PasswordField();
	private final Label signupStatus = new Label();

	/**
	 * @param onLoggedIn called with the username after a successful login or
	 *                   sign-up
	 */
	public LoginView(AccountService accounts, Consumer<String> onLoggedIn) {
		this.accounts = accounts;
		this.onLoggedIn = onLoggedIn;

		Label title = new Label("Mealton");
		title.setStyle("-fx-font-size: 32px; -fx-font-weight: bold;");
		Label subtitle = new Label("Your AI recipe and meal planning assistant");

		TabPane tabs = new TabPane(new Tab("Log in", buildLogin()), new Tab("Create account", buildSignup()));
		tabs.getTabs().forEach(t -> t.setClosable(false));

		Label note = new Label("Each account has its own saved profile, pantry, recipes, plan and food diary. "
				+ "Everything stays on this computer, and passwords are stored scrambled (hashed), never as typed.");
		note.setWrapText(true);
		note.setStyle("-fx-text-fill: #666666;");

		VBox box = new VBox(12, title, subtitle, tabs, note);
		box.setAlignment(Pos.CENTER);
		box.setMaxWidth(400);
		box.setPadding(new Insets(24));
		getChildren().add(box);
		setPadding(new Insets(16));
	}

	private VBox buildLogin() {
		loginUser.setPromptText("Username");
		loginPassword.setPromptText("Password");
		Button login = new Button("Log in");
		login.setDefaultButton(true);
		login.setOnAction(e -> attempt(loginStatus, () -> {
			String name = accounts.logIn(loginUser.getText(), loginPassword.getText());
			loginPassword.clear();
			onLoggedIn.accept(name);
		}));
		loginPassword.setOnAction(e -> login.fire());
		VBox form = new VBox(10, loginUser, loginPassword, login, loginStatus);
		form.setPadding(new Insets(14, 4, 4, 4));
		return form;
	}

	private VBox buildSignup() {
		signupUser.setPromptText("Choose a username (3-20 letters, digits or _)");
		signupPassword
				.setPromptText("Choose a password (at least " + AccountService.MIN_PASSWORD_LENGTH + " characters)");
		signupAgain.setPromptText("Type the password again");
		Button create = new Button("Create account");
		create.setOnAction(e -> attempt(signupStatus, () -> {
			String name = accounts.signUp(signupUser.getText(), signupPassword.getText(), signupAgain.getText());
			signupPassword.clear();
			signupAgain.clear();
			onLoggedIn.accept(name);
		}));
		signupAgain.setOnAction(e -> create.fire());
		VBox form = new VBox(10, signupUser, signupPassword, signupAgain, create, signupStatus);
		form.setPadding(new Insets(14, 4, 4, 4));
		return form;
	}

	/** Runs an action and shows a red message if it fails with a normal error. */
	private static void attempt(Label status, Runnable action) {
		try {
			status.setText("");
			action.run();
		} catch (IllegalArgumentException | IllegalStateException e) {
			status.setStyle("-fx-text-fill: #c62828;");
			status.setWrapText(true);
			status.setText(e.getMessage());
		}
	}

	/**
	 * Clears the typed text and messages, for when this screen is shown again after
	 * logging out.
	 */
	public void reset() {
		loginUser.clear();
		loginPassword.clear();
		loginStatus.setText("");
		signupUser.clear();
		signupPassword.clear();
		signupAgain.clear();
		signupStatus.setText("");
	}
}