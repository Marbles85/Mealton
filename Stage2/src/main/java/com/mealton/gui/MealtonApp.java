package com.mealton.gui;

import com.mealton.account.AccountService;
import com.mealton.account.Session;
import com.mealton.config.AppConfig;
import com.mealton.llm.LLMClient;
import com.mealton.llm.LLMClientFactory;

import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Screen;
import javafx.stage.Stage;

/** JavaFX entry point. Start it with: mvn javafx:run */
public class MealtonApp extends Application {

	private static final double PREFERRED_WIDTH = 980;
	private static final double PREFERRED_HEIGHT = 700;

	private final AccountService accounts = new AccountService(AccountService.defaultDirectory());
	private LLMClient llm; // null when the AI is off; one client is shared by every login
	private Stage stage;
	private Session session; // the logged-in user, or null on the login screen
	private MainWindow window; // the main window of the logged-in user, or null on the login screen
	private double width;
	private double height;

	@Override
	public void start(Stage stage) {
		this.stage = stage;

		// Turn on the AI assistant from the local .env (real Claude or the offline
		// mock).
		try {
			llm = LLMClientFactory.create(AppConfig.load());
		} catch (RuntimeException e) {
			// The rest of the app works without the AI; the Chat page explains it is
			// switched off.
			System.out.println("AI assistant is OFF: " + e.getMessage());
		}

		// Fit the window to the usable part of the screen (not under the taskbar) and
		// centre it,
		// so the title bar and buttons are always reachable on small laptop screens.
		Rectangle2D screen = Screen.getPrimary().getVisualBounds();
		width = Math.min(PREFERRED_WIDTH, screen.getWidth() * 0.9);
		height = Math.min(PREFERRED_HEIGHT, screen.getHeight() * 0.85);

		stage.setTitle("Mealton");
		stage.setMinWidth(Math.min(760, width));
		stage.setMinHeight(Math.min(520, height));
		stage.setX(screen.getMinX() + (screen.getWidth() - width) / 2);
		stage.setY(screen.getMinY() + (screen.getHeight() - height) / 2);
		// closing the window saves the user's data
		stage.setOnCloseRequest(e -> saveNow());

		showLogin();
		stage.show();
	}

	private void showLogin() {
		session = null;
		window = null;
		stage.setTitle("Mealton");
		stage.setScene(new Scene(new LoginView(accounts, this::logIn), width, height));
	}

	private void logIn(String username) {
		session = Session.start(accounts, username, llm);
		if (!session.warnings().isEmpty()) {
			new Alert(Alert.AlertType.WARNING, String.join("\n", session.warnings())).showAndWait();
		}
		stage.setTitle("Mealton - " + username);
		window = new MainWindow(session.facade(), username, this::saveNow, this::logOut);
		stage.setScene(new Scene(window, width, height));
	}

	private void logOut() {
		saveNow();
		showLogin();
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