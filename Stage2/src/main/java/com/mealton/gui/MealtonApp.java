package com.mealton.gui;

import java.net.URL;

import com.mealton.account.AccountService;
import com.mealton.account.Session;
import com.mealton.config.AppConfig;
import com.mealton.llm.LLMClient;
import com.mealton.llm.LLMClientFactory;

import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Screen;
import javafx.stage.Stage;

/** JavaFX entry point. Start it with: mvn javafx:run */
public class MealtonApp extends Application {

	// The window opens this big, but never bigger than the usable screen (see
	// start()).
	private static final double PREFERRED_WIDTH = 1440;
	private static final double PREFERRED_HEIGHT = 900;

	private final AccountService accounts = new AccountService(AccountService.defaultDirectory());
	private LLMClient llm; // null when the AI is off; one client is shared by every login
	private Stage stage;
	private Session session; // the logged-in user, or null on the login screen
	private MainWindow window; // the main window of the logged-in user, or null on the login screen

	@Override
	public void start(Stage stage) {
		this.stage = stage;

		// Turn on the AI assistant from the local .env (real Claude or the offline
		// mock).
		try {
			llm = LLMClientFactory.create(AppConfig.load());
		} catch (RuntimeException e) {
			// The rest of the app works without the AI; the Chat panel explains it is
			// switched off.
			System.out.println("AI assistant is OFF: " + e.getMessage());
		}

		// Size the whole window (title bar included) to almost the usable screen, not
		// under the taskbar,
		// and centre it, so nothing is cut off when the app starts.
		Rectangle2D screen = Screen.getPrimary().getVisualBounds();
		double width = Math.min(PREFERRED_WIDTH, screen.getWidth() * 0.96);
		double height = Math.min(PREFERRED_HEIGHT, screen.getHeight() * 0.94);
		stage.setTitle("Mealton");
		stage.setWidth(width);
		stage.setHeight(height);
		stage.setMinWidth(Math.min(900, width));
		stage.setMinHeight(Math.min(620, height));
		stage.setX(screen.getMinX() + (screen.getWidth() - width) / 2);
		stage.setY(screen.getMinY() + (screen.getHeight() - height) / 2);
		// closing the window saves the user's data
		stage.setOnCloseRequest(e -> saveNow());

		showLogin();
		stage.show();
	}

	/**
	 * A scene with the Mealton stylesheet attached (the app still works, just
	 * plainer, if it is missing).
	 */
	private Scene styledScene(Parent root) {
		Scene scene = new Scene(root); // the window's own size is used
		URL css = MealtonApp.class.getResource("/mealton.css");
		if (css != null) {
			scene.getStylesheets().add(css.toExternalForm());
		} else {
			System.out.println("Note: mealton.css not found, using the default look.");
		}
		return scene;
	}

	private void showLogin() {
		session = null;
		window = null;
		stage.setTitle("Mealton");
		stage.setScene(styledScene(new LoginView(accounts, this::logIn)));
	}

	private void logIn(String username) {
		session = Session.start(accounts, username, llm);
		if (!session.warnings().isEmpty()) {
			new Alert(Alert.AlertType.WARNING, String.join("\n", session.warnings())).showAndWait();
		}
		stage.setTitle("Mealton - " + username);
		window = new MainWindow(session.facade(), username, this::saveNow, this::logOut, this::deleteAccount);
		stage.setScene(styledScene(window));
	}

	private void logOut() {
		saveNow();
		showLogin();
	}

	/**
	 * Permanently deletes the logged-in account (the user already typed DELETE on
	 * the Profile page): nothing is saved first, and the saved "Remember me" login
	 * for it is forgotten.
	 */
	private void deleteAccount() {
		if (session == null) {
			return;
		}
		String user = session.username();
		try {
			accounts.deleteAccount(user);
		} catch (RuntimeException e) {
			new Alert(Alert.AlertType.ERROR, "The account could not be deleted: " + e.getMessage()).show();
			return;
		}
		if (user.equals(UiSettings.rememberedUser())) {
			UiSettings.forgetRemembered();
		}
		showLogin(); // clears the session, so closing the window cannot save the old data again
	}

	/**
	 * Saves the logged-in user's data; a problem is shown instead of silently
	 * losing the data.
	 */
	private void saveNow() {
		if (session == null) {
			return;
		}
		if (window != null) {
			window.commitPending(); // apply anything typed but not yet saved
		}
		String problem = session.save();
		if (problem != null) {
			System.out.println("Warning: " + problem);
			new Alert(Alert.AlertType.WARNING, problem).show();
		}
	}

	public static void main(String[] args) {
		launch(args);
	}
}