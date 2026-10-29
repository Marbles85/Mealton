package com.mealton;

import com.mealton.cli.CliApp;
import com.mealton.facade.MealtonFacade;

/** Entry point. For now only the CLI exists. The GUI is added later. */
public class Main {

	public static void main(String[] args) {
		if (args.length > 0 && args[0].equalsIgnoreCase("gui")) {
			System.out.println("To start the GUI run: mvn javafx:run   (starting the CLI instead)");
		}
		MealtonFacade facade = new MealtonFacade();
		new CliApp(facade, System.in, System.out).run();
	}
}