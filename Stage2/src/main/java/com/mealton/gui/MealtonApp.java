package com.mealton.gui;

import com.mealton.config.AppConfig;
import com.mealton.facade.MealtonFacade;
import com.mealton.llm.LLMClientFactory;

import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

/** JavaFX entry point. Start it with: mvn javafx:run */
public class MealtonApp extends Application {

	private static final double PREFERRED_WIDTH = 980;
	private static final double PREFERRED_HEIGHT = 700;

	@Override
	public void start(Stage stage) {
		MealtonFacade facade = new MealtonFacade(); // same facade the CLI uses

		// Turn on the AI assistant from the local .env (real Claude or the offline
		// mock).
		try {
			facade.enableAgent(LLMClientFactory.create(AppConfig.load()));
		} catch (RuntimeException e) {
			// The rest of the app works without the AI; the Chat page explains it is
			// switched off.
			System.out.println("AI assistant is OFF: " + e.getMessage());
		}

		MainWindow window = new MainWindow(facade);

		// Fit the window to the usable part of the screen (not under the taskbar) and
		// centre it,
		// so the title bar and buttons are always reachable on small laptop screens.
		Rectangle2D screen = Screen.getPrimary().getVisualBounds();
		double width = Math.min(PREFERRED_WIDTH, screen.getWidth() * 0.9);
		double height = Math.min(PREFERRED_HEIGHT, screen.getHeight() * 0.85);

		stage.setTitle("Mealton");
		stage.setScene(new Scene(window, width, height));
		stage.setMinWidth(Math.min(760, width));
		stage.setMinHeight(Math.min(520, height));
		stage.setX(screen.getMinX() + (screen.getWidth() - width) / 2);
		stage.setY(screen.getMinY() + (screen.getHeight() - height) / 2);
		stage.show();
	}

	public static void main(String[] args) {
		launch(args);
	}
}