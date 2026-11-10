package com.mealton.gui;

import com.mealton.config.AppConfig;
import com.mealton.facade.MealtonFacade;
import com.mealton.llm.LLMClientFactory;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** JavaFX entry point. Start it with: mvn javafx:run */
public class MealtonApp extends Application {

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

		stage.setTitle("Mealton");
		stage.setScene(new Scene(window, 980, 700));
		stage.setMinWidth(760);
		stage.setMinHeight(520);
		stage.show();
	}

	public static void main(String[] args) {
		launch(args);
	}
}