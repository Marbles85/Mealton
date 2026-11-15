package com.mealton.nutrition;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.mealton.util.UnitConverter;

/**
 * A small built-in table of common foods (feature F04). Values are typical
 * figures per 100 g, so they are good estimates, not lab results. Volume units
 * (cup, tbsp...) only work for foods with a known density, and counts ("2
 * eggs") only work for foods with a known piece weight. Foods that are not in
 * the table can still be logged with numbers typed by the user or estimated by
 * the AI.
 */
public final class FoodTable {

	/**
	 * @param per100g nutrition per 100 g; @param gramsPerPiece 0 if unknown; @param
	 *                gramsPerMl 0 if unknown
	 */
	private record Food(Nutrients per100g, double gramsPerPiece, double gramsPerMl) {
	}

	private static final Map<String, Food> FOODS = new LinkedHashMap<>();
	private static final Map<String, String> ALIASES = new LinkedHashMap<>();

	private static void food(String name, double kcal, double p, double c, double f, double piece, double density) {
		FOODS.put(name, new Food(new Nutrients(kcal, p, c, f), piece, density));
	}

	static {
		// meat, fish, eggs, dairy
		food("chicken breast", 165, 31, 0, 3.6, 0, 0);
		food("beef", 250, 26, 0, 15, 0, 0);
		food("pork", 242, 27, 0, 14, 0, 0);
		food("turkey", 135, 30, 0, 1, 0, 0);
		food("ham", 145, 21, 1.5, 6, 0, 0);
		food("bacon", 541, 37, 1.4, 42, 0, 0);
		food("salmon", 208, 20, 0, 13, 0, 0);
		food("tuna", 132, 28, 0, 1, 0, 0);
		food("shrimp", 99, 24, 0.2, 0.3, 0, 0);
		food("egg", 143, 12.6, 0.7, 9.5, 50, 0);
		food("milk", 61, 3.2, 4.8, 3.3, 0, 1.03);
		food("yogurt", 61, 3.5, 4.7, 3.3, 0, 1.03);
		food("cheese", 402, 25, 1.3, 33, 0, 0);
		food("butter", 717, 0.9, 0.1, 81, 0, 0.96);
		food("tofu", 76, 8, 1.9, 4.8, 0, 0);
		// grains and bread
		food("rice", 365, 7, 80, 0.7, 0, 0.77);
		food("cooked rice", 130, 2.7, 28, 0.3, 0, 0.67);
		food("pasta", 371, 13, 75, 1.5, 0, 0);
		food("cooked pasta", 158, 5.8, 31, 0.9, 0, 0);
		food("bread", 265, 9, 49, 3.2, 30, 0);
		food("tortilla", 312, 8, 52, 8, 45, 0);
		food("oats", 389, 17, 66, 7, 0, 0.38);
		food("cereal", 379, 7, 84, 2, 0, 0);
		food("flour", 364, 10, 76, 1, 0, 0.53);
		food("sugar", 387, 0, 100, 0, 0, 0.85);
		// vegetables and fruit
		food("potato", 77, 2, 17, 0.1, 170, 0);
		food("sweet potato", 86, 1.6, 20, 0.1, 130, 0);
		food("tomato", 18, 0.9, 3.9, 0.2, 120, 0);
		food("onion", 40, 1.1, 9.3, 0.1, 110, 0);
		food("carrot", 41, 0.9, 10, 0.2, 60, 0);
		food("broccoli", 34, 2.8, 7, 0.4, 0, 0);
		food("spinach", 23, 2.9, 3.6, 0.4, 0, 0);
		food("lettuce", 15, 1.4, 2.9, 0.2, 0, 0);
		food("cucumber", 15, 0.7, 3.6, 0.1, 300, 0);
		food("bell pepper", 31, 1, 6, 0.3, 120, 0);
		food("mushroom", 22, 3.1, 3.3, 0.3, 0, 0);
		food("garlic", 149, 6.4, 33, 0.5, 3, 0);
		food("corn", 86, 3.3, 19, 1.4, 0, 0);
		food("peas", 81, 5, 14, 0.4, 0, 0);
		food("apple", 52, 0.3, 14, 0.2, 180, 0);
		food("banana", 89, 1.1, 23, 0.3, 118, 0);
		food("orange", 47, 0.9, 12, 0.1, 130, 0);
		food("strawberry", 32, 0.7, 7.7, 0.3, 0, 0);
		food("avocado", 160, 2, 9, 15, 150, 0);
		// beans, nuts, fats, extras
		food("black beans", 132, 8.9, 24, 0.5, 0, 0);
		food("chickpeas", 164, 8.9, 27, 2.6, 0, 0);
		food("lentils", 352, 24.6, 63.4, 1.1, 0, 0); // dry weight, as recipes list them
		food("peanut butter", 588, 25, 20, 50, 0, 1.08);
		food("almonds", 579, 21, 22, 50, 0, 0.6);
		food("olive oil", 884, 0, 0, 100, 0, 0.92);
		food("vegetable oil", 884, 0, 0, 100, 0, 0.92);
		food("honey", 304, 0.3, 82, 0, 0, 1.42);
		food("chocolate", 546, 5, 60, 31, 0, 0);
		food("salt", 0, 0, 0, 0, 0, 1.2);
		food("soy sauce", 53, 8.1, 4.9, 0.6, 0, 1.15);
		// drinks
		food("orange juice", 45, 0.7, 10, 0.2, 0, 1.04);
		food("water", 0, 0, 0, 0, 0, 1);
		food("black coffee", 1, 0.1, 0, 0, 0, 1);

		ALIASES.put("chicken", "chicken breast");
		ALIASES.put("ground beef", "beef");
		ALIASES.put("steak", "beef");
		ALIASES.put("yoghurt", "yogurt");
		ALIASES.put("coffee", "black coffee");
		ALIASES.put("peppers", "bell pepper");
		ALIASES.put("pepper", "bell pepper");
		ALIASES.put("potatoe", "potato");
	}

	/**
	 * Seasonings: tiny amounts, so one "piece" (a sprig, a pinch, a clove-sized
	 * bit) counts as 2 g.
	 */
	private static void spice(String name, double kcal, double p, double c, double f) {
		food(name, kcal, p, c, f, 2, 0.45);
	}

	static {
		// more meat, fish, dairy (kcal, protein, carbs, fat per 100 g; piece grams;
		// density g/ml)
		food("chicken thigh", 209, 26, 0, 10.9, 110, 0);
		food("chicken wing", 203, 30, 0, 8.1, 35, 0);
		food("chicken stock", 5, 0.5, 0.4, 0.2, 0, 1.0);
		food("beef stock", 5, 0.8, 0.3, 0.1, 0, 1.0);
		food("vegetable stock", 4, 0.2, 0.8, 0.1, 0, 1.0);
		food("fish stock", 5, 0.8, 0.1, 0.1, 0, 1.0);
		food("stock", 5, 0.5, 0.5, 0.1, 0, 1.0);
		food("stock cube", 238, 7, 29, 10, 10, 0);
		food("lamb", 294, 25, 0, 21, 0, 0);
		food("veal", 172, 24, 0, 8, 0, 0);
		food("duck", 337, 19, 0, 28, 0, 0);
		food("pork belly", 518, 9, 0, 53, 0, 0);
		food("pork chop", 231, 25, 0, 14, 150, 0);
		food("sausage", 301, 12, 2, 27, 60, 0);
		food("chorizo", 455, 24, 1.9, 38, 0, 0);
		food("prosciutto", 250, 26, 0, 16, 0, 0);
		food("salami", 336, 22, 1.2, 26, 0, 0);
		food("liver", 135, 20, 3.9, 3.6, 0, 0);
		food("cod", 82, 18, 0, 0.7, 0, 0);
		food("haddock", 90, 20, 0, 0.6, 0, 0);
		food("white fish", 90, 19, 0, 1, 0, 0);
		food("fish", 120, 20, 0, 4, 0, 0);
		food("sardine", 208, 25, 0, 11, 0, 0);
		food("anchovy", 210, 29, 0, 10, 4, 0);
		food("mackerel", 205, 19, 0, 14, 0, 0);
		food("smoked salmon", 117, 18, 0, 4.3, 0, 0);
		food("squid", 92, 16, 3, 1.4, 0, 0);
		food("mussel", 86, 12, 3.7, 2.2, 0, 0);
		food("crab", 87, 18, 0, 1.1, 0, 0);
		food("scallop", 88, 17, 2.4, 0.8, 0, 0);
		food("egg yolk", 322, 16, 3.6, 27, 17, 0);
		food("egg white", 52, 11, 0.7, 0.2, 33, 0);
		food("double cream", 445, 1.7, 2.7, 48, 0, 1.0);
		food("heavy cream", 340, 2.8, 2.8, 36, 0, 1.0);
		food("single cream", 195, 3, 4, 19, 0, 1.0);
		food("cream", 340, 2.8, 2.8, 36, 0, 1.0);
		food("sour cream", 198, 2.4, 4.6, 19, 0, 1.0);
		food("creme fraiche", 292, 2.1, 2.6, 30, 0, 1.0);
		food("cheddar", 403, 25, 1.3, 33, 0, 0);
		food("parmesan", 431, 38, 4.1, 29, 0, 0);
		food("mozzarella", 280, 28, 3.1, 17, 0, 0);
		food("feta", 264, 14, 4, 21, 0, 0);
		food("cream cheese", 342, 6, 4, 34, 0, 0);
		food("ricotta", 174, 11, 3, 13, 0, 0);
		food("halloumi", 321, 21, 2.2, 25, 0, 0);
		food("mascarpone", 429, 4.8, 4.8, 44, 0, 0);
		food("goat cheese", 364, 22, 0.1, 30, 0, 0);
		food("gruyere", 413, 30, 0.4, 32, 0, 0);
		food("margarine", 717, 0.2, 0.7, 81, 0, 0.96);
		food("ghee", 900, 0, 0, 100, 0, 0.91);
		food("lard", 902, 0, 0, 100, 0, 0.92);
		food("condensed milk", 321, 7.9, 54, 8.7, 0, 1.3);
		food("evaporated milk", 134, 6.8, 10, 7.6, 0, 1.07);
		food("coconut milk", 197, 2, 2.8, 21, 0, 1.0);
		food("coconut cream", 330, 3.6, 6.7, 34, 0, 1.0);
		food("buttermilk", 40, 3.3, 4.8, 0.9, 0, 1.03);
		food("greek yogurt", 97, 9, 3.9, 5, 0, 1.05);

		// more grains, baking
		food("rice noodles", 364, 6, 80, 0.6, 0, 0);
		food("noodles", 371, 13, 75, 1.5, 0, 0);
		food("egg noodles", 384, 14, 71, 4.4, 0, 0);
		food("couscous", 376, 13, 77, 0.6, 0, 0.7);
		food("quinoa", 368, 14, 64, 6, 0, 0.72);
		food("bulgur", 342, 12, 76, 1.3, 0, 0.7);
		food("breadcrumbs", 395, 13, 72, 5.3, 0, 0.45);
		food("pita", 275, 9, 56, 1.2, 60, 0);
		food("naan", 290, 9, 50, 5, 90, 0);
		food("puff pastry", 558, 7, 46, 38, 0, 0);
		food("shortcrust pastry", 450, 6, 45, 28, 0, 0);
		food("filo pastry", 300, 9, 56, 3.5, 0, 0);
		food("pastry", 450, 6, 45, 28, 0, 0);
		food("cornflour", 381, 0.3, 91, 0.1, 0, 0.54);
		food("baking powder", 53, 0, 28, 0, 0, 0.9);
		food("yeast", 325, 40, 41, 7.6, 7, 0);
		food("icing sugar", 389, 0, 100, 0, 0, 0.56);
		food("brown sugar", 380, 0.1, 98, 0, 0, 0.93);
		food("maple syrup", 260, 0, 67, 0.1, 0, 1.33);
		food("golden syrup", 325, 0, 80, 0, 0, 1.4);
		food("molasses", 290, 0, 75, 0.1, 0, 1.4);
		food("cocoa powder", 228, 19, 58, 14, 0, 0.5);
		food("dark chocolate", 546, 5, 60, 31, 0, 0);
		food("white chocolate", 539, 5.9, 59, 32, 0, 0);
		food("vanilla extract", 288, 0.1, 12, 0.1, 0, 0.88);
		food("jam", 250, 0.4, 69, 0.1, 0, 1.3);
		food("granola", 471, 10, 64, 20, 0, 0.45);
		food("biscuit", 480, 7, 69, 20, 12, 0);
		food("cracker", 430, 9, 71, 12, 8, 0);
		food("dough", 270, 8, 52, 2, 0, 0);

		// more vegetables, fruit
		food("spring onion", 32, 1.8, 7.3, 0.2, 15, 0);
		food("shallot", 72, 2.5, 17, 0.1, 30, 0);
		food("leek", 61, 1.5, 14, 0.3, 90, 0);
		food("celery", 16, 0.7, 3, 0.2, 40, 0);
		food("zucchini", 17, 1.2, 3.1, 0.3, 200, 0);
		food("courgette", 17, 1.2, 3.1, 0.3, 200, 0);
		food("eggplant", 25, 1, 6, 0.2, 300, 0);
		food("aubergine", 25, 1, 6, 0.2, 300, 0);
		food("cauliflower", 25, 1.9, 5, 0.3, 0, 0);
		food("cabbage", 25, 1.3, 5.8, 0.1, 0, 0);
		food("kale", 49, 4.3, 8.8, 0.9, 0, 0);
		food("rocket", 25, 2.6, 3.7, 0.7, 0, 0);
		food("chopped tomatoes", 21, 1.2, 4, 0.1, 0, 1.0);
		food("tomato puree", 82, 4.3, 19, 0.5, 0, 1.1);
		food("tomato sauce", 29, 1.3, 6, 0.2, 0, 1.03);
		food("ketchup", 112, 1.7, 26, 0.4, 0, 1.1);
		food("green beans", 31, 1.8, 7, 0.2, 0, 0);
		food("asparagus", 20, 2.2, 3.9, 0.1, 0, 0);
		food("beetroot", 43, 1.6, 10, 0.2, 80, 0);
		food("pumpkin", 26, 1, 6.5, 0.1, 0, 0);
		food("butternut squash", 45, 1, 12, 0.1, 0, 0);
		food("squash", 40, 1.1, 10, 0.1, 0, 0);
		food("parsnip", 75, 1.2, 18, 0.3, 100, 0);
		food("turnip", 28, 0.9, 6.4, 0.1, 0, 0);
		food("radish", 16, 0.7, 3.4, 0.1, 0, 0);
		food("ginger", 80, 1.8, 18, 0.8, 10, 0.4);
		food("chilli", 40, 1.9, 8.8, 0.4, 15, 0);
		food("chili", 40, 1.9, 8.8, 0.4, 15, 0);
		food("jalapeno", 29, 0.9, 6.5, 0.4, 14, 0);
		food("lemon", 29, 1.1, 9.3, 0.3, 60, 0);
		food("lemon juice", 22, 0.4, 6.9, 0.2, 0, 1.03);
		food("lime", 30, 0.7, 10.5, 0.2, 45, 0);
		food("lime juice", 25, 0.4, 8.4, 0.1, 0, 1.03);
		food("grapefruit", 42, 0.8, 11, 0.1, 230, 0);
		food("pineapple", 50, 0.5, 13, 0.1, 0, 0);
		food("mango", 60, 0.8, 15, 0.4, 200, 0);
		food("coconut", 354, 3.3, 15, 33, 0, 0);
		food("desiccated coconut", 660, 6, 24, 64, 0, 0.35);
		food("raisins", 299, 3.1, 79, 0.5, 0, 0.65);
		food("sultanas", 299, 3.1, 79, 0.5, 0, 0.65);
		food("dates", 282, 2.5, 75, 0.4, 8, 0);
		food("figs", 74, 0.8, 19, 0.3, 50, 0);
		food("apricot", 48, 1.4, 11, 0.4, 35, 0);
		food("peach", 39, 0.9, 10, 0.3, 150, 0);
		food("pear", 57, 0.4, 15, 0.1, 180, 0);
		food("plum", 46, 0.7, 11, 0.3, 66, 0);
		food("cherries", 50, 1, 12, 0.3, 0, 0);
		food("blueberries", 57, 0.7, 14, 0.3, 0, 0);
		food("raspberries", 52, 1.2, 12, 0.7, 0, 0);
		food("blackberries", 43, 1.4, 10, 0.5, 0, 0);
		food("cranberries", 46, 0.4, 12, 0.1, 0, 0);
		food("grapes", 69, 0.7, 18, 0.2, 0, 0);
		food("watermelon", 30, 0.6, 7.6, 0.2, 0, 0);
		food("kiwi", 61, 1.1, 15, 0.5, 70, 0);
		food("olives", 115, 0.8, 6, 11, 4, 0);
		food("capers", 23, 2.4, 4.9, 0.9, 0, 0);
		food("gherkin", 11, 0.3, 2.3, 0.2, 35, 0);
		food("sundried tomatoes", 258, 14, 56, 3, 0, 0);
		food("bean sprouts", 30, 3, 6, 0.2, 0, 0);
		food("bamboo shoots", 27, 2.6, 5.2, 0.3, 0, 0);
		food("water chestnut", 97, 1.4, 24, 0.1, 0, 0);
		food("pak choi", 13, 1.5, 2.2, 0.2, 0, 0);
		food("seaweed", 35, 5.8, 5, 0.3, 0, 0);
		food("artichoke", 47, 3.3, 11, 0.2, 0, 0);

		// beans, nuts, seeds, oils
		food("kidney beans", 127, 8.7, 23, 0.5, 0, 0);
		food("cannellini beans", 120, 7.5, 21, 0.5, 0, 0);
		food("butter beans", 110, 7, 20, 0.4, 0, 0);
		food("baked beans", 94, 4.8, 17, 0.6, 0, 1.1);
		food("beans", 127, 8.7, 23, 0.5, 0, 0);
		food("split peas", 352, 24, 63, 1.2, 0, 0);
		food("edamame", 121, 12, 9, 5, 0, 0);
		food("peanuts", 567, 26, 16, 49, 0, 0.6);
		food("cashews", 553, 18, 30, 44, 0, 0.55);
		food("walnuts", 654, 15, 14, 65, 0, 0.5);
		food("pecans", 691, 9, 14, 72, 0, 0.5);
		food("pistachios", 560, 20, 28, 45, 0, 0.55);
		food("hazelnuts", 628, 15, 17, 61, 0, 0.55);
		food("pine nuts", 673, 14, 13, 68, 0, 0.55);
		food("nuts", 600, 18, 20, 52, 0, 0.55);
		food("sesame seeds", 573, 18, 23, 50, 0, 0.6);
		food("sunflower seeds", 584, 21, 20, 51, 0, 0.55);
		food("chia seeds", 486, 17, 42, 31, 0, 0.7);
		food("tahini", 595, 17, 21, 54, 0, 1.0);
		food("coconut oil", 892, 0, 0, 99, 0, 0.92);
		food("sesame oil", 884, 0, 0, 100, 0, 0.92);
		food("sunflower oil", 884, 0, 0, 100, 0, 0.92);
		food("rapeseed oil", 884, 0, 0, 100, 0, 0.92);
		food("canola oil", 884, 0, 0, 100, 0, 0.92);
		food("oil", 884, 0, 0, 100, 0, 0.92);

		// sauces, condiments, drinks
		food("fish sauce", 35, 5.1, 3.6, 0, 0, 1.2);
		food("oyster sauce", 51, 1.4, 11, 0.3, 0, 1.2);
		food("hoisin sauce", 220, 3.3, 44, 3.4, 0, 1.2);
		food("worcestershire sauce", 78, 0, 19, 0, 0, 1.2);
		food("hot sauce", 11, 0.5, 0.8, 0.4, 0, 1.0);
		food("sriracha", 93, 2, 19, 1, 0, 1.1);
		food("mustard", 66, 4, 5, 4, 0, 1.05);
		food("mayonnaise", 680, 1, 0.6, 75, 0, 0.95);
		food("vinegar", 18, 0, 0.04, 0, 0, 1.01);
		food("balsamic vinegar", 88, 0.5, 17, 0, 0, 1.1);
		food("red wine", 85, 0.1, 2.6, 0, 0, 0.99);
		food("white wine", 82, 0.1, 2.6, 0, 0, 0.99);
		food("wine", 83, 0.1, 2.6, 0, 0, 0.99);
		food("beer", 43, 0.5, 3.6, 0, 0, 1.0);
		food("brandy", 231, 0, 0, 0, 0, 0.94);
		food("rum", 231, 0, 0, 0, 0, 0.94);
		food("tamarind paste", 239, 2.8, 63, 0.6, 0, 1.2);
		food("tamarind", 239, 2.8, 63, 0.6, 0, 1.2);
		food("curry paste", 134, 3, 15, 7, 0, 1.1);
		food("miso", 199, 12, 26, 6, 0, 1.2);
		food("shrimp paste", 153, 20, 4, 6, 0, 1.2);
		food("bagoong", 153, 20, 4, 6, 0, 1.2);
		food("pesto", 459, 5, 5, 47, 0, 0.95);
		food("salsa", 36, 1.5, 7, 0.2, 0, 1.05);
		food("hummus", 166, 8, 14, 9.6, 0, 1.0);
		food("teriyaki sauce", 89, 5.9, 16, 0, 0, 1.2);
		food("bbq sauce", 172, 0.8, 41, 0.6, 0, 1.15);
		food("gravy", 47, 1.5, 6, 2, 0, 1.0);
		food("coconut water", 19, 0.7, 3.7, 0.2, 0, 1.0);
		food("milk chocolate", 535, 7.7, 59, 30, 0, 0);
		food("lemonade", 40, 0, 10, 0, 0, 1.0);

		// herbs and spices (tiny amounts)
		spice("black pepper", 251, 10, 64, 3.3);
		spice("white pepper", 296, 10, 69, 2.1);
		spice("curry powder", 325, 14, 58, 14);
		spice("cumin", 375, 18, 44, 22);
		spice("paprika", 282, 14, 54, 13);
		spice("turmeric", 354, 8, 65, 10);
		spice("ground coriander", 298, 12, 55, 18);
		spice("cinnamon", 247, 4, 81, 1.2);
		spice("nutmeg", 525, 6, 49, 36);
		spice("oregano", 265, 9, 69, 4.3);
		spice("thyme", 101, 5.6, 24, 1.7);
		spice("rosemary", 131, 3.3, 21, 5.9);
		spice("sage", 315, 11, 61, 13);
		spice("bay leaf", 313, 7.6, 75, 8.4);
		spice("chili powder", 282, 13, 50, 14);
		spice("cayenne", 318, 12, 57, 17);
		spice("mixed herbs", 265, 9, 69, 4.3);
		spice("garam masala", 379, 15, 45, 15);
		spice("five spice", 300, 10, 60, 8);
		spice("ground ginger", 335, 9, 72, 4.2);
		spice("cardamom", 311, 11, 68, 6.7);
		spice("star anise", 337, 18, 50, 16);
		spice("garlic powder", 331, 17, 73, 0.7);
		spice("onion powder", 341, 10, 79, 1);
		spice("allspice", 263, 6.1, 72, 8.7);
		spice("saffron", 310, 11, 65, 6);
		spice("sesame", 573, 18, 23, 50);
		food("parsley", 36, 3, 6.3, 0.8, 3, 0.25);
		food("coriander", 23, 2.1, 3.7, 0.5, 3, 0.25);
		food("cilantro", 23, 2.1, 3.7, 0.5, 3, 0.25);
		food("basil", 23, 3.2, 2.7, 0.6, 3, 0.25);
		food("mint", 44, 3.3, 8.4, 0.7, 3, 0.25);
		food("dill", 43, 3.5, 7, 1.1, 3, 0.25);
		food("chives", 30, 3.3, 4.4, 0.7, 3, 0.25);

		ALIASES.put("minced beef", "beef");
		ALIASES.put("mince", "beef");
		ALIASES.put("minced pork", "pork");
		ALIASES.put("lamb mince", "lamb");
		ALIASES.put("prawn", "shrimp");
		ALIASES.put("prawns", "shrimp");
		ALIASES.put("king prawn", "shrimp");
		ALIASES.put("basmati rice", "rice");
		ALIASES.put("spaghetti", "pasta");
		ALIASES.put("penne", "pasta");
		ALIASES.put("macaroni", "pasta");
		ALIASES.put("lasagne", "pasta");
		ALIASES.put("noodle", "noodles");
		ALIASES.put("plain flour", "flour");
		ALIASES.put("self raising flour", "flour");
		ALIASES.put("caster sugar", "sugar");
		ALIASES.put("granulated sugar", "sugar");
		ALIASES.put("sweetcorn", "corn");
		ALIASES.put("cilantro leaves", "cilantro");
		ALIASES.put("scallion", "spring onion");
		ALIASES.put("spring onions", "spring onion");
		ALIASES.put("capsicum", "bell pepper");
		ALIASES.put("tomato paste", "tomato puree");
		ALIASES.put("passata", "tomato sauce");
		ALIASES.put("cornstarch", "cornflour");
		ALIASES.put("soya sauce", "soy sauce");
		ALIASES.put("rolled oats", "oats");
		ALIASES.put("oatmeal", "oats");
		ALIASES.put("garlic clove", "garlic");
		ALIASES.put("cloves garlic", "garlic");
	}

	/**
	 * Gives a food a volume weight (grams per ml) so "1 cup" and "2 tbsp" can be
	 * counted.
	 */
	private static void density(String name, double gramsPerMl) {
		Food old = FOODS.get(name);
		if (old != null && old.gramsPerMl() <= 0) {
			FOODS.put(name, new Food(old.per100g(), old.gramsPerPiece(), gramsPerMl));
		}
	}

	static {
		// cup / tbsp weights for foods that were only known by grams (standard cooking
		// values)
		density("spinach", 0.127);
		density("lettuce", 0.15);
		density("rocket", 0.085);
		density("kale", 0.28);
		density("cabbage", 0.3);
		density("broccoli", 0.37);
		density("cauliflower", 0.45);
		density("mushroom", 0.3);
		density("onion", 0.68);
		density("carrot", 0.54);
		density("tomato", 0.76);
		density("bell pepper", 0.63);
		density("cucumber", 0.44);
		density("garlic", 0.57);
		density("corn", 0.65);
		density("peas", 0.64);
		density("strawberry", 0.7);
		density("potato", 0.63);
		density("sweet potato", 0.56);
		density("avocado", 0.63);
		density("apple", 0.53);
		density("banana", 0.63);
		density("orange", 0.77);
		density("black beans", 0.73);
		density("chickpeas", 0.69);
		density("lentils", 0.81);
		density("kidney beans", 0.73);
		density("cannellini beans", 0.73);
		density("butter beans", 0.73);
		density("beans", 0.73);
		density("celery", 0.43);
		density("zucchini", 0.48);
		density("courgette", 0.48);
		density("eggplant", 0.35);
		density("aubergine", 0.35);
		density("leek", 0.38);
		density("asparagus", 0.57);
		density("green beans", 0.46);
		density("beetroot", 0.58);
		density("pumpkin", 0.49);
		density("butternut squash", 0.59);
		density("squash", 0.55);
		density("parsnip", 0.56);
		density("turnip", 0.55);
		density("radish", 0.49);
		density("blueberries", 0.63);
		density("raspberries", 0.52);
		density("blackberries", 0.61);
		density("cranberries", 0.42);
		density("grapes", 0.64);
		density("pineapple", 0.7);
		density("mango", 0.7);
		density("cherries", 0.58);
		density("peach", 0.65);
		density("pear", 0.59);
		density("plum", 0.7);
		density("watermelon", 0.64);
		density("spring onion", 0.42);
		density("shallot", 0.68);
		density("ginger", 0.4);
		density("chilli", 0.4);
		density("chili", 0.4);
		density("jalapeno", 0.38);
		density("tofu", 0.55);
		density("pasta", 0.44);
		density("cooked pasta", 0.59);
		density("bread", 0.2);
		density("cereal", 0.2);
		density("chicken breast", 0.59);
		density("chicken thigh", 0.59);
		density("beef", 0.55);
		density("pork", 0.55);
		density("turkey", 0.58);
		density("ham", 0.57);
		density("bacon", 0.4);
		density("lamb", 0.55);
		density("salmon", 0.6);
		density("tuna", 0.85);
		density("shrimp", 0.61);
		density("cod", 0.6);
		density("crab", 0.57);
		density("fish", 0.6);
		density("cheddar", 0.48);
		density("parmesan", 0.42);
		density("mozzarella", 0.47);
		density("feta", 0.63);
		density("cream cheese", 0.98);
		density("ricotta", 1.04);
		density("halloumi", 0.6);
		density("sausage", 0.6);
		density("bean sprouts", 0.4);
		density("pak choi", 0.3);
		density("olives", 0.6);
		density("dates", 0.6);
		density("figs", 0.65);
		density("capers", 0.7);
		density("sundried tomatoes", 0.45);
		density("bamboo shoots", 0.6);
		density("water chestnut", 0.6);
		density("seaweed", 0.2);
		density("coconut", 0.35);
		density("edamame", 0.65);
		density("split peas", 0.85);
		density("rice noodles", 0.45);
		density("noodles", 0.4);
		density("egg noodles", 0.35);
		density("quinoa", 0.72);
		density("couscous", 0.7);
	}

	/**
	 * Foods whose nutrition is zero need no weight at all ("a pinch of salt",
	 * "water").
	 */
	private static boolean isZero(Food food) {
		Nutrients n = food.per100g();
		return n.calories() <= 0 && n.protein() <= 0 && n.carbs() <= 0 && n.fat() <= 0;
	}

	private static String wordKey(String w) {
		String x = w.toLowerCase(Locale.ROOT);
		if (x.endsWith("leaves")) {
			return x.substring(0, x.length() - 6) + "leaf"; // bay leaves = bay leaf
		}
		if (x.equals("chilies") || x.equals("chillies") || x.equals("chiles")) {
			return "chili";
		}
		if (x.equals("chilli") || x.equals("chile")) {
			return "chili";
		}
		return singular(x);
	}

	private static java.util.Set<String> wordsOf(String text) {
		java.util.Set<String> out = new java.util.LinkedHashSet<>();
		for (String w : text.toLowerCase(Locale.ROOT).split("[^a-z]+")) {
			if (!w.isEmpty()) {
				out.add(wordKey(w));
			}
		}
		return out;
	}

	/** Word sets of the table names, worked out once. */
	private static final java.util.Map<String, java.util.Set<String>> NEED_WORDS = new java.util.concurrent.ConcurrentHashMap<>();
	/** Answers already worked out (an ingredient name is looked up many times). */
	private static final java.util.Map<String, java.util.Optional<String>> FOUND = new java.util.concurrent.ConcurrentHashMap<>();

	private static java.util.Set<String> needOf(String tableName) {
		return NEED_WORDS.computeIfAbsent(tableName, FoodTable::wordsOf);
	}

	private static Food bestContained(String name) {
		String k = searchKey(name);
		return k == null ? null : FOODS.get(k);
	}

	/**
	 * The table name a (messy) ingredient name stands for, or null when the table
	 * does not know it.
	 */
	public static String canonical(String name) {
		if (name == null || name.isBlank()) {
			return null;
		}
		String k = key(name);
		if (FOODS.containsKey(k)) {
			return k;
		}
		return searchKey(name);
	}

	/**
	 * True when both names are the same food: "baby spinach" and "spinach", "red
	 * onions" and "onion".
	 */
	public static boolean sameFood(String a, String b) {
		String x = canonical(a);
		return x != null && x.equals(canonical(b));
	}

	private static String searchKey(String name) {
		return FOUND
				.computeIfAbsent(name.toLowerCase(Locale.ROOT).trim(), n -> java.util.Optional.ofNullable(search(n)))
				.orElse(null);
	}

	private static String search(String name) {
		java.util.Set<String> have = wordsOf(name);
		String best = null;
		int bestWords = 0;
		for (String foodName : FOODS.keySet()) {
			java.util.Set<String> need = needOf(foodName);
			if (need.size() > bestWords && have.containsAll(need)) {
				best = foodName;
				bestWords = need.size();
			}
		}
		for (java.util.Map.Entry<String, String> e : ALIASES.entrySet()) {
			java.util.Set<String> need = needOf(e.getKey());
			if (FOODS.containsKey(e.getValue()) && need.size() > bestWords && have.containsAll(need)) {
				best = e.getValue();
				bestWords = need.size();
			}
		}
		return best;
	}

	private FoodTable() {
	}

	/**
	 * True if the food is in the table (plurals and a few common alternative names
	 * are understood).
	 */
	public static boolean knows(String name) {
		return find(name) != null;
	}

	/** The table's food names, for the AI and for help text. */
	public static java.util.Set<String> names() {
		return java.util.Collections.unmodifiableSet(FOODS.keySet());
	}

	/**
	 * Nutrition for an amount of a food.
	 *
	 * @return empty if the food is not in the table
	 * @throws IllegalArgumentException if the food is known but the unit cannot be
	 *                                  used for it
	 */
	public static Optional<Nutrients> lookup(String name, double quantity, String unit) {
		if (Double.isNaN(quantity) || Double.isInfinite(quantity) || quantity <= 0) {
			throw new IllegalArgumentException("The amount must be more than 0.");
		}
		String key = key(name);
		Food food = find(name);
		if (food == null) {
			return Optional.empty();
		}
		if (isZero(food)) {
			return Optional.of(Nutrients.ZERO); // salt, water: counted, adds nothing
		}
		double grams = toGrams(key, food, quantity, unit == null ? "" : unit.trim().toLowerCase(Locale.ROOT));
		return Optional.of(food.per100g().times(grams / 100.0));
	}

	private static double toGrams(String key, Food food, double quantity, String unit) {
		if (unit.isEmpty() || unit.equals("piece") || unit.equals("pieces") || unit.equals("slice")
				|| unit.equals("slices")) {
			if (food.gramsPerPiece() <= 0) {
				throw new IllegalArgumentException(
						"I can't count '" + key + "' by the piece. " + "Use a weight such as grams.");
			}
			return quantity * food.gramsPerPiece();
		}
		if (!UnitConverter.isKnown(unit)) {
			throw new IllegalArgumentException(
					"I don't know the unit '" + unit + "'. Use g, kg, oz, lb, " + "ml, l, tsp, tbsp or cup.");
		}
		try {
			return UnitConverter.convert(quantity, unit, "g");
		} catch (IllegalArgumentException volume) {
			// a volume unit: only possible when the food has a known density
			if (food.gramsPerMl() <= 0) {
				throw new IllegalArgumentException(
						"I can't turn " + unit + " of '" + key + "' into grams. Use a weight such as grams.");
			}
			return UnitConverter.convert(quantity, unit, "ml") * food.gramsPerMl();
		}
	}

	private static Food find(String name) {
		Food exact = FOODS.get(key(name));
		if (exact != null) {
			return exact;
		}
		// "boneless chicken thighs", "fresh red chilli": the longest food name found
		// among its words
		return name == null || name.isBlank() ? null : bestContained(name);
	}

	/**
	 * The table name for what the user typed: lower case, plural removed,
	 * alternative names mapped.
	 */
	private static String key(String name) {
		String n = name == null ? "" : name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
		for (String candidate : new String[] { n, singular(n) }) {
			if (FOODS.containsKey(candidate)) {
				return candidate;
			}
			if (ALIASES.containsKey(candidate)) {
				return ALIASES.get(candidate);
			}
		}
		return n;
	}

	private static String singular(String n) {
		if (n.endsWith("ies") && n.length() > 4) {
			return n.substring(0, n.length() - 3) + "y";
		}
		if (n.endsWith("oes") && n.length() > 4) {
			return n.substring(0, n.length() - 2);
		}
		if (n.endsWith("s") && !n.endsWith("ss") && n.length() > 3) {
			return n.substring(0, n.length() - 1);
		}
		return n;
	}
}