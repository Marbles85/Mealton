package com.mealton.gui;

import com.mealton.facade.MealtonFacade;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** JavaFX entry point. Start it with: mvn javafx:run */
public class MealtonApp extends Application {

	@Override
	public void start(Stage stage) {
		MealtonFacade facade = new MealtonFacade(); // the same facade the CLI uses
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