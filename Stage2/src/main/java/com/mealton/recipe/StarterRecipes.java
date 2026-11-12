package com.mealton.recipe;

import java.util.ArrayList;
import java.util.List;

import com.mealton.facade.MealtonFacade;

/**
 * A small library of ready-made recipes that the app loads when it starts, so
 * the Recipes page is never empty and recipe ideas, profile warnings and the
 * weekly plan have something to work with right away. They are ordinary saved
 * recipes: they can be rated, scaled and deleted like any other.
 */
public final class StarterRecipes {

	private StarterRecipes() {
	}

	/** title, servings, ingredients (separated by ;), steps (separated by |) */
	private static final String[][] DATA = { { "Fluffy Pancakes", "2",
			"200 g flour; 2 egg; 300 ml milk; 1 tbsp sugar; 1 tbsp butter",
			"Whisk the flour, eggs, milk and sugar into a smooth batter|Melt the butter in a pan|Fry 2 minutes per side until golden" },
			{ "Spaghetti with Tomato Sauce", "2",
					"200 g pasta; 400 g tomato; 1 onion; 2 garlic; 2 tbsp olive oil; 1 tsp salt",
					"Boil the pasta in salted water|Soften the chopped onion and garlic in the oil|Add the tomatoes and simmer 15 minutes|Toss with the pasta" },
			{ "Chicken Fried Rice", "2",
					"300 g cooked rice; 200 g chicken; 2 egg; 100 g peas; 2 tbsp vegetable oil; 2 tbsp soy sauce",
					"Fry the diced chicken in the oil until cooked|Push aside and scramble the eggs|Add the rice and peas and stir-fry 5 minutes|Season with soy sauce" },
			{ "Vegetable Stir-Fry", "2",
					"1 bell pepper; 2 carrot; 200 g broccoli; 100 g mushroom; 2 tbsp vegetable oil; 2 tbsp soy sauce; 2 garlic",
					"Slice all the vegetables|Stir-fry the garlic and carrots in hot oil for 3 minutes|Add the rest and cook 5 minutes|Finish with soy sauce" },
			{ "Cheese Omelette", "1", "3 egg; 50 g cheese; 1 tbsp butter; 1 tsp salt",
					"Beat the eggs with the salt|Melt the butter in a pan and pour in the eggs|Add the cheese, fold and cook 2 minutes" },
			{ "Lentil Soup", "4", "250 g lentils; 1 onion; 2 carrot; 2 garlic; 1 l water; 1 tbsp olive oil; 1 tsp salt",
					"Soften the chopped onion, carrots and garlic in the oil|Add the lentils, water and salt|Simmer 25 minutes until soft" },
			{ "Tuna Pasta Salad", "2", "200 g pasta; 150 g tuna; 1 tomato; 1 cucumber; 2 tbsp olive oil",
					"Boil the pasta and let it cool|Dice the tomato and cucumber|Mix everything with the tuna and olive oil" },
			{ "Banana Oatmeal", "1", "50 g oats; 250 ml milk; 1 banana; 1 tbsp honey",
					"Simmer the oats in the milk for 5 minutes|Top with sliced banana and honey" },
			{ "Black Bean Tacos", "2", "4 tortilla; 200 g black beans; 1 avocado; 1 tomato; 1 onion",
					"Warm the beans|Dice the avocado, tomato and onion|Fill the warm tortillas" },
			{ "Baked Salmon with Potatoes", "2", "300 g salmon; 400 g potato; 1 tbsp olive oil; 1 tsp salt",
					"Heat the oven to 200 C|Cut the potatoes into wedges, toss with oil and salt and bake 15 minutes|Add the salmon and bake 15 minutes more" },
			{ "Peanut Butter Banana Toast", "1", "2 bread; 2 tbsp peanut butter; 1 banana",
					"Toast the bread|Spread with peanut butter|Top with sliced banana" },
			{ "Greek Salad", "2", "2 tomato; 1 cucumber; 1 onion; 100 g cheese; 2 tbsp olive oil",
					"Chop the vegetables into chunks|Crumble the cheese on top|Drizzle with olive oil" }, };

	/** The starter recipes (new objects each time). */
	public static List<Recipe> create() {
		List<Recipe> recipes = new ArrayList<>();
		for (String[] row : DATA) {
			recipes.add(new Recipe(row[0], Integer.parseInt(row[1]), RecipeParser.parseIngredients(row[2]),
					RecipeParser.parseSteps(row[3])));
		}
		return recipes;
	}

	/**
	 * Saves the starter recipes that are not saved yet (matched by title).
	 *
	 * @return how many were added
	 */
	public static int loadInto(MealtonFacade facade) {
		int added = 0;
		for (Recipe recipe : create()) {
			try {
				facade.saveRecipe(recipe);
				added++;
			} catch (IllegalArgumentException alreadySaved) {
				// a recipe with this title exists already: leave it alone
			}
		}
		return added;
	}
}