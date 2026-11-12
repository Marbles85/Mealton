package com.mealton.facade;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import com.mealton.agent.AgentMemory;
import com.mealton.agent.AgentReply;
import com.mealton.agent.MealtonAgent;
import com.mealton.agent.ToolRegistry;
import com.mealton.agent.tools.ConvertTool;
import com.mealton.agent.tools.MealIdeasTool;
import com.mealton.agent.tools.NutritionTool;
import com.mealton.agent.tools.PantryTool;
import com.mealton.agent.tools.PlanTool;
import com.mealton.agent.tools.ProfileTool;
import com.mealton.agent.tools.RecipeTool;
import com.mealton.agent.tools.ShoppingListTool;
import com.mealton.llm.LLMClient;
import com.mealton.nutrition.DailyProgress;
import com.mealton.nutrition.LoggedFood;
import com.mealton.nutrition.MacroTracker;
import com.mealton.nutrition.Nutrients;
import com.mealton.pantry.FreshnessScheduler;
import com.mealton.pantry.PantryInventory;
import com.mealton.pantry.PantryItem;
import com.mealton.pantry.PantryObserver;
import com.mealton.plan.PlanService;
import com.mealton.plan.WeekPlan;
import com.mealton.profile.ProfileService;
import com.mealton.profile.UserProfile;
import com.mealton.recipe.Recipe;
import com.mealton.recipe.RecipeAdvisor;
import com.mealton.recipe.RecipeBook;
import com.mealton.recipe.RecipeIngredient;
import com.mealton.recipe.RecipeMatch;
import com.mealton.repository.InMemoryPantryRepository;
import com.mealton.shopping.ShoppingItem;
import com.mealton.shopping.ShoppingService;
import com.mealton.util.UnitConverter;

/**
 * Facade pattern + MVC controller: the single entry point used by BOTH the GUI
 * and the CLI (Stage 1: MealtonFacade). More services (plans, recipes,
 * agent...) will be added here.
 */
public class MealtonFacade {
	private final PantryInventory pantry;
	private final ShoppingService shopping;
	private final FreshnessScheduler scheduler;
	private final RecipeBook recipes = new RecipeBook();
	private final RecipeAdvisor advisor;
	private final ProfileService profile = new ProfileService();
	private final PlanService planner;
	private final MacroTracker tracker = new MacroTracker(profile::get);
	private final AgentMemory memory = new AgentMemory();
	private MealtonAgent agent; // null until enableAgent() is called

	public MealtonFacade() {
		this(Clock.systemDefaultZone());
	}

	public MealtonFacade(Clock clock) {
		this.pantry = new PantryInventory(new InMemoryPantryRepository(), clock);
		this.shopping = new ShoppingService();
		this.scheduler = new FreshnessScheduler(pantry);
		this.advisor = new RecipeAdvisor(recipes, pantry, shopping, profile);
		this.planner = new PlanService(recipes, advisor, profile);
		// whenever the profile changes, the agent's memory learns it too (so Claude
		// always knows the diet)
		profile.addListener(this::syncProfileToMemory);
		syncProfileToMemory(profile.get());
	}

	/**
	 * Copies the profile into the agent's remembered facts; blank values are
	 * forgotten.
	 */
	private void syncProfileToMemory(UserProfile p) {
		memory.setPreference("name", p.name());
		memory.setPreference("diet", p.diet().label());
		memory.setPreference("allergies", String.join(", ", p.allergies()));
		memory.setPreference("calorie goal", p.calorieGoal() > 0 ? p.calorieGoal() + " kcal per day" : "");
		memory.setPreference("macro goals", p.proteinGoal() > 0 || p.carbsGoal() > 0 || p.fatGoal() > 0
				? "protein " + p.proteinGoal() + " g, carbs " + p.carbsGoal() + " g, fat " + p.fatGoal() + " g per day"
				: "");
	}

	// ---------------- Pantry (F06) ----------------

	public PantryItem addPantryItem(PantryItem item) {
		return pantry.addItem(item);
	}

	public int removePantryItems(Collection<Long> ids) {
		return pantry.removeItems(ids).size();
	}

	public List<PantryItem> getPantryItems() {
		return pantry.getItems();
	}

	public List<PantryItem> getExpiringItems() {
		return pantry.getExpiringItems();
	}

	/**
	 * Runs a freshness check now; observers are notified about each expiring item.
	 */
	public void checkFreshness() {
		scheduler.tick();
	}

	public void startFreshnessScheduler() {
		scheduler.start();
	}

	public void attachPantryObserver(PantryObserver observer) {
		pantry.attach(observer);
	}

	public LocalDate today() {
		return pantry.today();
	}

	// ---------------- Shopping list (F12) ----------------

	public ShoppingItem addShoppingItem(ShoppingItem item) {
		return shopping.addItem(item);
	}

	public int deleteShoppingItems(Collection<Long> ids) {
		return shopping.deleteItems(ids);
	}

	public void toggleShoppingItem(long id) {
		shopping.toggle(id);
	}

	public int clearChecked() {
		return shopping.clearChecked();
	}

	public boolean undo() {
		return shopping.undo();
	}

	public boolean redo() {
		return shopping.redo();
	}

	public boolean canUndo() {
		return shopping.canUndo();
	}

	public boolean canRedo() {
		return shopping.canRedo();
	}

	public List<ShoppingItem> getShoppingItems() {
		return shopping.getItems();
	}

	// ---------------- Recipes: save, rate, My Recipes (F13) ----------------

	public Recipe saveRecipe(Recipe recipe) {
		return recipes.save(recipe);
	}

	public Recipe rateRecipe(long id, int stars) {
		return recipes.rate(id, stars);
	}

	public Recipe getRecipe(long id) {
		return recipes.get(id);
	}

	public List<Recipe> getRecipes() {
		return recipes.getAll();
	}

	public List<Recipe> getTopRatedRecipes() {
		return recipes.getTopRated();
	}

	/**
	 * A copy of a saved recipe for another number of servings; the saved one is
	 * unchanged.
	 */
	public Recipe scaledRecipe(long id, int servings) {
		return recipes.scaled(id, servings);
	}

	public void deleteRecipe(long id) {
		recipes.delete(id);
	}

	// ---------------- Recipe ideas from the pantry (F07) and missing ingredients
	// (F08) ----------------

	/**
	 * Saved recipes ranked against the pantry. priority: "best" (default) or
	 * "expiring".
	 */
	public List<RecipeMatch> suggestRecipes(String priority) {
		return advisor.suggest(priority);
	}

	/** How one saved recipe fits the pantry right now. */
	public RecipeMatch checkRecipe(long id) {
		return advisor.check(id);
	}

	/**
	 * Puts what a recipe is missing on the shopping list; returns what was added.
	 */
	public List<RecipeIngredient> addMissingToShoppingList(long recipeId) {
		return advisor.addMissingToShoppingList(recipeId);
	}

	// ---------------- Profile (F01) ----------------

	public UserProfile getProfile() {
		return profile.get();
	}

	public UserProfile setProfileName(String name) {
		return profile.setName(name);
	}

	public UserProfile setDiet(String diet) {
		return profile.setDiet(diet);
	}

	public UserProfile addAllergy(String allergy) {
		return profile.addAllergy(allergy);
	}

	public UserProfile removeAllergy(String allergy) {
		return profile.removeAllergy(allergy);
	}

	public UserProfile setCalorieGoal(int calories) {
		return profile.setCalorieGoal(calories);
	}

	public UserProfile setMacroGoals(int protein, int carbs, int fat) {
		return profile.setMacroGoals(protein, carbs, fat);
	}

	public UserProfile resetProfile() {
		return profile.reset();
	}

	/**
	 * Lets the GUI hear about profile changes (for example when the AI changes the
	 * profile in chat).
	 */
	public void addProfileListener(java.util.function.Consumer<UserProfile> listener) {
		profile.addListener(listener);
	}

	// ---------------- Weekly meal plan (F02) ----------------

	/**
	 * Makes a new weekly plan. style: "variety" or "pantry"; meals: "dinner",
	 * "lunch,dinner" or "all".
	 */
	public WeekPlan generatePlan(String style, String meals) {
		return planner.generate(style, meals);
	}

	public WeekPlan getPlan() {
		return planner.current();
	}

	public WeekPlan setPlannedMeal(String day, String meal, long recipeId) {
		return planner.set(day, meal, recipeId);
	}

	public WeekPlan clearPlannedMeal(String day, String meal) {
		return planner.clearMeal(day, meal);
	}

	public void clearPlan() {
		planner.clearAll();
	}

	/**
	 * Problems with the current plan (profile conflicts, too many repeats). Empty
	 * if it looks fine.
	 */
	public List<String> checkPlan() {
		return planner.validate();
	}

	/**
	 * Puts the missing ingredients of every planned recipe on the shopping list.
	 */
	public List<RecipeIngredient> addPlanToShoppingList() {
		return planner.addMissingToShoppingList();
	}

	// ---------------- Macro tracker / food diary (F04) ----------------

	/** Logs an amount of a food from the built-in table for today. */
	public LoggedFood logFood(String name, double quantity, String unit) {
		return tracker.logFood(today(), name, quantity, unit);
	}

	/** Logs a food with your own numbers (or an AI estimate) for today. */
	public LoggedFood logCustomFood(String description, Nutrients nutrients, boolean estimated) {
		return tracker.logCustom(today(), description, nutrients, estimated);
	}

	public List<LoggedFood> getTodaysFood() {
		return tracker.entriesOn(today());
	}

	public LoggedFood deleteLoggedFood(long id) {
		return tracker.delete(id);
	}

	/** Today's totals compared with the profile's daily goals. */
	public DailyProgress getTodaysProgress() {
		return tracker.progress(today());
	}

	// ---------------- Unit conversion and scaling (F10) ----------------

	/** Converts an amount between two units of the same kind, e.g. 2 cup to ml. */
	public double convertUnits(double amount, String from, String to) {
		return UnitConverter.convert(amount, from, to);
	}

	/** Scales an ingredient amount from one number of servings to another. */
	public double scaleAmount(double amount, int fromServings, int toServings) {
		return UnitConverter.scale(amount, fromServings, toServings);
	}

	// ---------------- AI chat agent (F14) ----------------

	/**
	 * Creates the chat agent with the given LLM. It can use the pantry, shopping
	 * list, recipe, profile, food diary and meal plan tools.
	 */
	public void enableAgent(LLMClient llm) {
		ToolRegistry registry = new ToolRegistry();
		registry.register(new PantryTool(pantry));
		registry.register(new ShoppingListTool(shopping));
		registry.register(new ConvertTool());
		registry.register(new RecipeTool(recipes));
		registry.register(new MealIdeasTool(advisor));
		registry.register(new ProfileTool(profile));
		registry.register(new NutritionTool(tracker, this::today));
		registry.register(new PlanTool(planner));
		this.agent = new MealtonAgent(llm, registry, memory);
	}

	public boolean hasAgent() {
		return agent != null;
	}

	/**
	 * Sends one message to the agent and returns its reply (and which tools it
	 * used).
	 */
	public AgentReply chat(String message) {
		if (agent == null) {
			throw new IllegalStateException("The AI agent is not set up. Check LLM_PROVIDER in your .env file.");
		}
		return agent.run(message);
	}
}