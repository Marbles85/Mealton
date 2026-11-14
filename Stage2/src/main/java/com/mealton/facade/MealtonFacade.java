package com.mealton.facade;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
import com.mealton.nutrition.RecipeNutrition;
import com.mealton.nutrition.RecipeNutritionEstimator;
import com.mealton.pantry.FreshnessScheduler;
import com.mealton.pantry.PantryInventory;
import com.mealton.pantry.PantryItem;
import com.mealton.pantry.PantryObserver;
import com.mealton.plan.CalendarService;
import com.mealton.plan.MealEntry;
import com.mealton.plan.PlanService;
import com.mealton.plan.PrepPlanner;
import com.mealton.plan.WeekPlan;
import com.mealton.profile.ProfileService;
import com.mealton.profile.UserProfile;
import com.mealton.recipe.HttpFetcher;
import com.mealton.recipe.Recipe;
import com.mealton.recipe.RecipeAdvisor;
import com.mealton.recipe.RecipeBook;
import com.mealton.recipe.RecipeDiscovery;
import com.mealton.recipe.RecipeImporter;
import com.mealton.recipe.RecipeIngredient;
import com.mealton.recipe.RecipeMatch;
import com.mealton.recipe.RecipePhotos;
import com.mealton.recipe.RecipeTag;
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
	/**
	 * A recipe counts as "almost there" when it is missing at most this many
	 * ingredients.
	 */
	public static final int ALMOST_THERE_LIMIT = 3;

	private final PantryInventory pantry;
	private final ShoppingService shopping;
	private final FreshnessScheduler scheduler;
	private final RecipeBook recipes = new RecipeBook();
	private RecipePhotos photos = new RecipePhotos(null); // set by setMediaDirectory once a user is logged in
	private final RecipeAdvisor advisor;
	private final ProfileService profile = new ProfileService();
	private final PlanService planner;
	private final CalendarService calendar;
	private final MacroTracker tracker = new MacroTracker(profile::get);
	private final AgentMemory memory = new AgentMemory();
	private MealtonAgent agent; // null until enableAgent() is called
	private RecipeImporter.Fetcher importFetcher = new HttpFetcher();
	private LLMClient llm; // null until enableAgent() is called; also writes the one-line recipe reasons

	public MealtonFacade() {
		this(Clock.systemDefaultZone());
	}

	public MealtonFacade(Clock clock) {
		this.pantry = new PantryInventory(new InMemoryPantryRepository(), clock);
		this.shopping = new ShoppingService();
		this.scheduler = new FreshnessScheduler(pantry);
		this.advisor = new RecipeAdvisor(recipes, pantry, shopping, profile);
		this.planner = new PlanService(recipes, advisor, profile);
		this.calendar = new CalendarService(planner, recipes, advisor, profile);
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

	/** Forgets the shopping undo/redo history (used after loading saved items). */
	public void forgetShoppingHistory() {
		shopping.forgetHistory();
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

	/** Every recipe: the ready-made library plus the user's own. */
	public List<Recipe> getRecipes() {
		return recipes.getAll();
	}

	/**
	 * "My Recipes": the user's own recipes plus the library recipes they saved with
	 * the Save heart.
	 */
	public List<Recipe> getMyRecipes() {
		return recipes.getMyRecipes();
	}

	/**
	 * The Save heart: adds a recipe to My Recipes. Saving it twice changes nothing.
	 */
	public Recipe saveToMyRecipes(long id) {
		return recipes.setSaved(id, true);
	}

	/**
	 * Takes a library recipe out of My Recipes again (the recipe itself stays in
	 * the browser).
	 */
	public Recipe removeFromMyRecipes(long id) {
		return recipes.setSaved(id, false);
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
		photos.remove(recipes.get(id));
		recipes.delete(id);
	}

	/**
	 * Edits one of your own recipes (title, servings, ingredients, steps, times,
	 * source). Its rating and photo stay.
	 */
	public Recipe updateRecipe(long id, Recipe edited) {
		return recipes.update(id, edited);
	}

	// ---------------- Recipes from the web and AI nutrition ----------------

	/** Replaces the internet access used to import recipes (tests use a fake). */
	public void setImportFetcher(RecipeImporter.Fetcher fetcher) {
		this.importFetcher = fetcher;
	}

	/**
	 * Reads a recipe web page and returns a draft recipe (not saved) plus its
	 * picture file, if one was found. Claude tidies the ingredient lines when it is
	 * switched on; otherwise a built-in parser does it. Slow (it uses the
	 * internet), so screens call it in the background.
	 */
	public RecipeImporter.Imported importRecipeFromLink(String url) throws RecipeImporter.ImportException {
		return new RecipeImporter(importFetcher, llm).importFrom(url);
	}

	/**
	 * Asks Claude for a nutrition estimate of one of your own recipes, but only
	 * when the built-in food table cannot cover every ingredient. Returns true if
	 * an estimate was stored. Slow, so screens call it in the background.
	 */
	public boolean estimateNutrition(long id) {
		Recipe recipe = recipes.get(id);
		if (recipe.isLibrary() || RecipeNutrition.of(recipe).isComplete()) {
			return false;
		}
		Optional<Nutrients> estimate = RecipeNutritionEstimator.estimate(llm, recipe);
		estimate.ifPresent(recipe::setAiEstimate);
		return estimate.isPresent();
	}

	// ---------------- Recipes invented by Claude (All recipes tab)
	// ----------------

	/**
	 * True when the All recipes tab can be refreshed with new recipes (Claude is
	 * switched on).
	 */
	public boolean canGenerateRecipes() {
		return llm != null;
	}

	/**
	 * The filter words Claude gave to recipes, most common first (for the AI-made
	 * filter chips). Empty until Claude has written or tagged some recipes.
	 */
	public java.util.List<String> aiFilterSuggestions(int max) {
		java.util.Map<String, Integer> counts = new java.util.HashMap<>();
		for (Recipe r : recipes.getAll()) {
			r.getAiTags().forEach(t -> counts.merge(t, 1, Integer::sum));
		}
		return counts.entrySet().stream()
				.sorted(java.util.Map.Entry.<String, Integer>comparingByValue().reversed()
						.thenComparing(java.util.Map.Entry.comparingByKey()))
				.limit(max).map(java.util.Map.Entry::getKey).toList();
	}

	/**
	 * Titles of every recipe that exists now (so new generated recipes can be
	 * different).
	 */
	public java.util.List<String> recipeTitles() {
		java.util.List<String> titles = new java.util.ArrayList<>();
		for (Recipe r : recipes.getAll()) {
			titles.add(r.getTitle());
		}
		return titles;
	}

	/**
	 * Step 1 of a refresh: asks Claude for new recipes (asked 10 at a time) and
	 * returns them without storing anything, so it is safe to run in the
	 * background. Returns an empty list if Claude gave nothing usable.
	 *
	 * @param count       how many new recipes to aim for
	 * @param hint        what the cook is interested in, for example the filters
	 *                    they typed (may be blank)
	 * @param avoidTitles titles that already exist (see {@link #recipeTitles()})
	 */
	public java.util.List<Recipe> generateRecipeDrafts(int count, String hint, java.util.List<String> avoidTitles) {
		if (llm == null) {
			throw new IllegalStateException("Turn Claude on (an API key in .env) to generate new recipes.");
		}
		java.util.List<Recipe> fresh = new java.util.ArrayList<>();
		for (int attempt = 0; attempt < 5 && fresh.size() < count; attempt++) {
			java.util.List<String> avoid = new java.util.ArrayList<>(avoidTitles);
			fresh.forEach(r -> avoid.add(r.getTitle()));
			for (Recipe r : RecipeDiscovery.generate(llm, Math.min(10, count - fresh.size()),
					profile.get().diet().label(), hint, avoid)) {
				if (fresh.size() < count) {
					fresh.add(r);
				}
			}
		}
		return fresh;
	}

	/**
	 * Step 2 of a refresh: swaps the new recipes in for the generated ones you did
	 * not keep. Recipes you saved or planned stay. Does nothing (returns 0) when
	 * the list is empty. Must run on the screen thread.
	 *
	 * @return how many new recipes were added
	 */
	public int swapInDiscoveredRecipes(java.util.List<Recipe> fresh) {
		if (fresh == null || fresh.isEmpty()) {
			return 0;
		}
		java.util.Set<String> planned = new java.util.HashSet<>();
		for (var day : getPlan().days()) {
			for (MealEntry entry : day.entries()) {
				planned.add(entry.recipe().getTitle());
			}
		}
		for (Recipe r : java.util.List.copyOf(recipes.getAll())) {
			if (r.isDiscovered() && !r.isSaved() && !planned.contains(r.getTitle())) {
				recipes.delete(r.getId());
			}
		}
		int added = 0;
		for (Recipe r : fresh) {
			r.setLibrary(true);
			r.setDiscovered(true);
			try {
				recipes.save(r);
				added++;
			} catch (IllegalArgumentException duplicate) {
				// the same title already exists: skip it
			}
		}
		return added;
	}

	/**
	 * Both steps in one go (used by the command line and the tests). @return how
	 * many new recipes were added
	 */
	public int refreshDiscoveredRecipes(int count, String hint) {
		return swapInDiscoveredRecipes(generateRecipeDrafts(count, hint, recipeTitles()));
	}

	/**
	 * Copies a recipe's photo to a temporary file (so a delete or a photo removal
	 * can be undone). Empty if it has none.
	 */
	public Optional<Path> backupRecipePhoto(long id) {
		Optional<Path> photo = photos.find(recipes.get(id));
		if (photo.isEmpty()) {
			return Optional.empty();
		}
		try {
			String name = photo.get().getFileName().toString();
			Path copy = java.nio.file.Files.createTempFile("mealton-undo-", name.substring(name.lastIndexOf('.')));
			java.nio.file.Files.copy(photo.get(), copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			copy.toFile().deleteOnExit();
			return Optional.of(copy);
		} catch (java.io.IOException e) {
			return Optional.empty();
		}
	}

	/**
	 * Undo for a delete: puts the same recipe (same id, rating, notes) back. Its
	 * photo is added again with setRecipePhoto.
	 */
	public Recipe restoreDeletedRecipe(Recipe recipe) {
		return recipes.save(recipe);
	}

	/**
	 * Chooses which ingredient chips show under one step of your own recipe (step 0
	 * is the first).
	 */
	public Recipe setStepIngredients(long id, int step, java.util.List<String> names) {
		Recipe recipe = recipes.get(id);
		if (recipe.isLibrary()) {
			throw new IllegalArgumentException("Ready-made recipes cannot be changed. Write your own recipe instead.");
		}
		recipe.setStepIngredients(step, names);
		return recipe;
	}

	/** Puts one step of your own recipe back to the automatic ingredient chips. */
	public Recipe resetStepIngredients(long id, int step) {
		Recipe recipe = recipes.get(id);
		recipe.resetStepIngredients(step);
		return recipe;
	}

	// ---------------- Recipe photos ----------------

	/**
	 * Tells the facade where this user's photos are kept (their own media folder).
	 */
	public void setMediaDirectory(Path directory) {
		this.photos = new RecipePhotos(directory);
	}

	/**
	 * Adds or replaces the photo of a recipe with a copy of a picture file (png,
	 * jpg, gif or bmp, up to 10 MB).
	 */
	public Recipe setRecipePhoto(long id, Path picture) {
		Recipe recipe = recipes.get(id);
		photos.attach(recipe, picture);
		return recipe;
	}

	public Recipe removeRecipePhoto(long id) {
		Recipe recipe = recipes.get(id);
		photos.remove(recipe);
		return recipe;
	}

	/** The photo file of a recipe, or empty if it has none. */
	public Optional<Path> getRecipePhoto(long id) {
		return photos.find(recipes.get(id));
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

	/**
	 * The "Can make now" tab: recipes with nothing missing. priority: "best"
	 * (default) or "expiring".
	 */
	public List<RecipeMatch> canMakeNow(String priority) {
		return advisor.canMakeNow(priority);
	}

	/** Same, keeping only recipes that have ALL the given tags (F09). */
	public List<RecipeMatch> canMakeNow(String priority, Set<RecipeTag> tags) {
		return RecipeTag.filter(advisor.canMakeNow(priority), tags);
	}

	/**
	 * The "Almost there" tab: recipes missing at most {@link #ALMOST_THERE_LIMIT}
	 * ingredients.
	 */
	public List<RecipeMatch> almostThere() {
		return advisor.almostThere(ALMOST_THERE_LIMIT);
	}

	/** Same, keeping only recipes that have ALL the given tags (F09). */
	public List<RecipeMatch> almostThere(Set<RecipeTag> tags) {
		return RecipeTag.filter(advisor.almostThere(ALMOST_THERE_LIMIT), tags);
	}

	/** The tags of a recipe right now, for example vegan or high-protein (F09). */
	public Set<RecipeTag> tagsOf(long recipeId) {
		return RecipeTag.tagsOf(advisor.check(recipeId));
	}

	// ---------------- Nutrition per recipe (F11) ----------------

	/** An estimate of what a recipe contains, per serving and in total. */
	public RecipeNutrition estimateNutrition(Recipe recipe) {
		return RecipeNutrition.of(recipe);
	}

	public RecipeNutrition recipeNutrition(long recipeId) {
		return RecipeNutrition.of(recipes.get(recipeId));
	}

	/**
	 * Writes servings of a recipe into today's food diary, using the nutrition
	 * estimate (marked as an estimate).
	 *
	 * @throws IllegalArgumentException if the amount is not positive or the recipe
	 *                                  has no nutrition estimate
	 */
	public LoggedFood logRecipeServings(long recipeId, double servings) {
		if (Double.isNaN(servings) || Double.isInfinite(servings) || servings <= 0) {
			throw new IllegalArgumentException("The number of servings must be more than 0.");
		}
		Recipe recipe = recipes.get(recipeId);
		RecipeNutrition nutrition = RecipeNutrition.of(recipe);
		if (nutrition.counted().isEmpty()) {
			throw new IllegalArgumentException("'" + recipe.getTitle() + "' has no nutrition estimate, "
					+ "so it cannot be logged. Log it by hand with numbers instead.");
		}
		String what = recipe.getTitle() + " (" + com.mealton.util.Formats.number(servings)
				+ (servings == 1 ? " serving)" : " servings)");
		return tracker.logCustom(today(), what, nutrition.perServing().times(servings), true);
	}

	/**
	 * A short reason why a recipe suits the pantry (Stage 1: the AI writes one line
	 * per match, the ranking itself stays deterministic). If the AI is off or
	 * fails, a plain built-in sentence is returned instead.
	 */
	public String explainRecipe(long id) {
		RecipeMatch match = advisor.check(id);
		String fallback = plainReason(match);
		if (llm == null) {
			return fallback;
		}
		try {
			String prompt = "In one or two short, friendly sentences, say why this recipe is a good idea for the cook "
					+ "right now. Do not invent ingredients and do not repeat the recipe steps.\n" + "Recipe: "
					+ match.recipe().getTitle() + "\n" + "Ingredients the cook already has: " + names(match.available())
					+ "\n" + "Ingredients still missing: " + names(match.missing()) + "\n"
					+ "Pantry items that must be used soon: " + match.expiringUsed() + "\n" + "Diet: "
					+ profile.get().diet().label()
					+ (match.warnings().isEmpty() ? "" : "\nProfile warnings: " + String.join("; ", match.warnings()));
			String answer = llm.generate(prompt);
			if (answer == null || answer.isBlank() || answer.equals(com.mealton.llm.MockLLMClient.DEFAULT_REPLY)) {
				return fallback; // the offline mock has nothing useful to say
			}
			return answer.trim();
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	private static String plainReason(RecipeMatch match) {
		if (match.isCookable()) {
			return "You have everything you need for " + match.recipe().getTitle() + "."
					+ (match.expiringUsed() > 0
							? " It also uses " + match.expiringUsed() + " item(s) that should be eaten soon."
							: "");
		}
		return "You already have " + match.available().size() + " of "
				+ (match.available().size() + match.missing().size()) + " ingredients for " + match.recipe().getTitle()
				+ "; missing: " + names(match.missing()) + ".";
	}

	private static String names(List<RecipeIngredient> list) {
		return list.isEmpty() ? "none" : String.join(", ", list.stream().map(RecipeIngredient::name).toList());
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

	// ---------------- Prep-day planner (F05) ----------------

	/**
	 * One combined ingredient list for the current weekly plan, with shared
	 * ingredients and batch cooking.
	 */
	public PrepPlanner.PrepPlan buildPrepPlan() {
		return PrepPlanner.build(planner.current());
	}

	/**
	 * A short prep-day schedule written by the AI from the prep plan. If the AI is
	 * off or fails, the plain prep plan is returned instead.
	 */
	public String prepSchedule() {
		PrepPlanner.PrepPlan prep = buildPrepPlan();
		String plain = prep.describe();
		if (llm == null) {
			return plain;
		}
		try {
			String prompt = "Write a short prep-day schedule of 4 to 5 numbered steps for the cook, using ONLY the "
					+ "information below. Say what to wash and chop first, what to cook in batches, and what to "
					+ "store. Each step must be ONE short sentence. Keep the whole answer under 120 words. "
					+ "Use plain text only: no bold, no asterisks, no headings. "
					+ "Do not add ingredients or recipes that are not listed.\n\n" + plain + "\nDiet: "
					+ profile.get().diet().label();
			String answer = llm.generate(prompt);
			if (answer == null || answer.isBlank() || answer.equals(com.mealton.llm.MockLLMClient.DEFAULT_REPLY)) {
				return plain; // the offline mock has nothing useful to say
			}
			return answer.trim();
		} catch (RuntimeException e) {
			return plain;
		}
	}

	// ---------------- Meal calendar (F03): the plan, loaded onto real dates
	// ----------------

	/**
	 * Loads the current weekly plan onto the Monday-to-Sunday week that contains
	 * this date. @return meals loaded
	 */
	public int loadPlanOnCalendar(LocalDate anyDateInWeek) {
		return calendar.loadPlan(anyDateInWeek);
	}

	public List<MealEntry> getCalendarMeals(LocalDate date) {
		return calendar.mealsOn(date);
	}

	public List<String> getCalendarWarnings(LocalDate date) {
		return calendar.warningsOn(date);
	}

	public void setCalendarMeal(LocalDate date, String meal, long recipeId) {
		Recipe chosen = recipes.get(recipeId);
		if (chosen.isDiscovered() && !chosen.isSaved()) {
			recipes.setSaved(recipeId, true); // a generated recipe you plan with is kept, so a refresh cannot remove it
		}
		calendar.set(date, meal, recipeId);
	}

	public void clearCalendarMeal(LocalDate date, String meal) {
		calendar.clear(date, meal);
	}

	/** Empties one calendar week. @return how many meals were removed */
	public int clearCalendarWeek(LocalDate anyDateInWeek) {
		return calendar.clearWeek(anyDateInWeek);
	}

	/** Every date that has a meal on the calendar (used when saving). */
	public List<LocalDate> getCalendarDates() {
		return calendar.plannedDates();
	}

	public int getCalendarMealsInWeek(LocalDate anyDateInWeek) {
		return calendar.mealsInWeek(anyDateInWeek);
	}

	/** Puts what a calendar week is missing on the shopping list. */
	public List<RecipeIngredient> addCalendarWeekToShoppingList(LocalDate anyDateInWeek) {
		return calendar.addWeekToShoppingList(anyDateInWeek);
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

	/** Every food ever logged, on every day (used when saving). */
	public List<LoggedFood> getAllLoggedFood() {
		return tracker.allEntries();
	}

	/** Puts a saved diary entry back on its original day (used when loading). */
	public LoggedFood restoreLoggedFood(LocalDate date, String description, Nutrients nutrients, boolean estimated) {
		return tracker.logCustom(date, description, nutrients, estimated);
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
		this.llm = llm;
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