# EECS3311 Fall 2026 — Stage 1 Project Design Report
## Mealton: AI Smart Recipe and Meal Planning Agent

**Student:** _[Rebekah Uson - 221123310]_  

---

# 1. Project Overview

## 1.1 Project Description

**Problem.** Planning healthy meals is tedious: people juggle dietary restrictions, calorie/macro goals, what is already in the fridge, food that is about to spoil, and a grocery list. Most apps handle only one of these pieces. Food gets wasted, plans get abandoned, and shopping lists drift out of sync with what people actually need.

**Target users.** Students and young professionals who cook for themselves, people with allergies or dietary restrictions, and people tracking weight-loss or macro goals who want to reduce food waste.

**What the agent can do.** *Mealton* is an AI agent (not just a chatbot) that:
- builds a full weekly meal plan from the user's profile, calorie target, allergies and pantry;
- schedules a "prep day" (batch-cooking plan for the whole week within a time limit);
- recommends recipes based on current pantry contents, prioritising near-spoilage ingredients;
- finds recipes with missing ingredients and adds those ingredients to the shopping list;
- edits plans and lists from natural-language requests ("swap Tuesday dinner for something vegetarian").

**Why an agent is appropriate.** The task is multi-step and constraint-driven: Mealton must read the profile, check the pantry, search recipes, verify nutrition, validate against allergies, and write results to the calendar or list. No single prompt/response does that. It needs reasoning, planning, tool use, memory of the user's preferences, and re-planning when validation fails.

**AI model.** A tool-calling LLM (default: **Claude Sonnet via the Anthropic API**) accessed only through our own `LLMClient` interface (Adapter pattern), so any other provider (OpenAI, Gemini, a local model) or a `MockLLMClient` for testing can be swapped in. Nutrition data comes from a nutrition database API (USDA FoodData Central) behind a `NutritionProvider` adapter, with LLM estimation as a fallback.

**How the AI interacts with the rest of the system.** `MealtonAgent` runs a reasoning/tool-use loop: it sends the conversation and a list of tool schemas to `LLMClient`; if the model asks for a tool call, `MealtonAgent` looks the tool up in `ToolRegistry`, executes it (the tool calls normal services such as `PantryInventory`, `RecipeRepository`, `PlanService`), feeds the result back to the model, and repeats until the model produces a final answer. All AI output is **validated by deterministic code** (`PlanValidator`, allergen check, time check) before it is saved or shown as final.

## 1.2 Minimum Requirements Checklist

| Requirement | How it is met |
|---|---|
| GUI | Desktop GUI with pages: Login, Calendar/Meal Plan, Recipe Browser, Pantry/Shopping List, Profile, plus a floating, draggable Mealton chat avatar |
| CLI | `mealton` command-line app calling the same `MealtonFacade` as the GUI (see 1.4) |
| ≥ 10 features | 14 features (F01–F14); Login/Logout not counted |
| ≥ 5 design patterns | Strategy, Observer, Command, Factory Method, Adapter, Facade, Composite, State (+ MVC, Repository) |
| AI model | LLM through `LLMClient`; nutrition API through `NutritionProvider` |
| Agent behaviour | Reasoning loop, planning, tool use (5 tools), memory (`AgentMemory`), multi-step execution, validation and re-planning |

## 1.3 Overall Architecture

```mermaid
flowchart TB
  subgraph Presentation
    GUI[GUI Views<br/>Calendar, Recipes, Pantry/Shopping, Profile, Chat]
    CLI[CLI App]
  end
  subgraph Application
    FAC[MealtonFacade<br/>MVC controller + Facade]
  end
  subgraph Domain Services
    PS[PlanService + PlanValidator]
    MT[MacroTracker]
    PI[PantryInventory]
    RR[RecipeRecommender]
    RS[RecipeService]
    SS[ShoppingService]
    NS[NutritionService]
    PP[PrepPlanner]
    AS[AuthService / ProfileService]
  end
  subgraph Agent
    AG[MealtonAgent]
    TR[ToolRegistry + Tools]
    MEM[AgentMemory]
  end
  subgraph Infrastructure
    LLM[LLMClient adapters]
    NP[NutritionProvider adapter]
    REPO[Repositories - SQLite]
  end
  GUI --> FAC
  CLI --> FAC
  FAC --> PS & PI & RR & RS & SS & NS & PP & AS & AG
  AG --> TR --> PS & PI & RR & NS & SS
  AG --> MEM
  AG --> LLM
  PP --> LLM
  RS --> LLM
  SS --> LLM
  NS --> NP
  PS & PI & RS & SS & AS --> REPO
  PI -. notifies .-> GUI
  PS -. notifies .-> MT
```

## 1.4 CLI Overview

The CLI exposes the major functionality and calls the same `MealtonFacade` methods as the GUI.

```
mealton login --email a@b.com
mealton profile set --weight 70 --height 175 --allergies peanuts,shellfish
mealton plan-week --calories 2000 --diet vegetarian
mealton calendar add --date 2026-11-02 --label Lunch --recipe 14 --servings 2
mealton macros --date 2026-11-02
mealton prep --minutes 120
mealton pantry add "spinach" --qty 200 --unit g --expires 2026-11-05
mealton pantry list | mealton pantry expiring
mealton recipes --from-pantry --filter asian | mealton recipes --missing
mealton recipe scale 14 --servings 4 | mealton recipe nutrition 14 | mealton recipe rate 14 5
mealton shopping add|list|check|delete|clear-checked|undo
mealton chat "make my week high-protein with no nuts"
```

---

# 2. Detailed Feature Specifications

All features are reached through the GUI (and the equivalent CLI command). Login/Logout/Register exist (UC00) but are **not** counted among the 14 features.

### F01 — Profile and Dietary Constraints (Hybrid)
| | |
|---|---|
| **Description** | Stores name, DoB, weight, height, sex, goal, allergies, dietary restrictions, and computes a suggested daily calorie target |
| **User interaction** | Profile page: form fields, allergy/diet chips, "Save" button; calorie suggestion shown beside the goal selector |
| **Input** | Name, email, DoB, weight, height, sex, goal (lose/maintain/gain), allergies, restrictions |
| **Output** | Saved `Profile`, suggested calorie target, confirmation message |
| **AI involvement** | Hybrid: calorie target is deterministic (Mifflin–St Jeor); LLM only writes a short plain-language explanation of the target |
| **Workflow** | User edits form → `ProfileView.onSaveProfile()` → `MealtonFacade.saveProfile()` → `ProfileService` validates, calculates target, saves via `UserRepository` → target displayed |
| **Errors / alternatives** | Invalid or out-of-range values → inline error, nothing saved. LLM unavailable → target shown without explanation. Weight-loss target below a safe minimum → capped at the safe minimum with a warning |

### F02 — AI Weekly Meal Plan Generation (AI)
| | |
|---|---|
| **Description** | The agent builds a 7-day plan (breakfast/lunch/dinner/snacks) fitting calories, macros, allergies and pantry |
| **User interaction** | Calendar page → "Plan for the week" button → optional options dialog (diet, calorie override, cuisines) → plan appears on calendar |
| **Input** | Profile, constraints, pantry contents, optional preferences |
| **Output** | `WeekPlan` of `DayPlan`s of `MealEntry`s |
| **AI involvement** | AI (agent loop with tools), with deterministic validation |
| **Workflow** | `CalendarView.onPlanWeekClicked()` → `MealtonFacade.generateWeekPlan()` → `PlanService` → `MealtonAgent.generatePlan()` → LLM calls `recipe_search`, `pantry`, `nutrition` tools → plan drafted → `PlanValidator.validate()` → saved and displayed |
| **Errors / alternatives** | Validator finds an allergen or calorie violation → agent re-plans once with the violations as feedback, then reports failure if still invalid. LLM/API error → friendly message and option to retry or build manually (F03) |

### F03 — Meal Calendar Editing and Custom Labels (Deterministic)
| | |
|---|---|
| **Description** | Add, move, or remove meals in any day slot; use suggested labels (Breakfast, Lunch, Dinner, Snack) or create custom ones |
| **User interaction** | Click "+ Add meal" in a day box, choose recipe, label, servings; drag meals between slots; "New label…" option |
| **Input** | Date, label, recipe, servings |
| **Output** | Updated `WeekPlan`, refreshed calendar |
| **AI involvement** | Deterministic |
| **Workflow** | `CalendarView.onAddMealClicked()` → `MealtonFacade.addMeal()` → `PlanService.addMeal()` creates `AddMealCommand` → `CommandHistory.execute()` → plan changes → observers notified |
| **Errors / alternatives** | Recipe contains a user allergen → warning and confirmation required. Duplicate label name → rejected. Undo available through `CommandHistory` |

### F04 — Macro Tracker (Deterministic)
| | |
|---|---|
| **Description** | Shows calories, protein, carbs and fat per day and per week against the user's targets |
| **User interaction** | Panel beside the calendar, updated live; daily progress bars |
| **Input** | Current `WeekPlan` and `Profile` targets |
| **Output** | `MacroSummary` for a day/week |
| **AI involvement** | Deterministic |
| **Workflow** | `WeekPlan` notifies observers → `MacroTracker.onPlanChanged()` → recomputes using `PlanComponent.getMacros()` → `CalendarView.refreshMacros()` |
| **Errors / alternatives** | Recipe lacks nutrition data → flagged "incomplete" and excluded with a notice (F11 can fill it in) |

### F05 — Prep-Day Planner (AI)
| | |
|---|---|
| **Description** | Creates a step-by-step batch-cooking schedule so all the week's meals are prepared within a time limit |
| **User interaction** | Calendar page → "Schedule prep day" → pick date and available time (e.g. 2 hours) → timeline appears and is added to the calendar |
| **Input** | Week plan, chosen date, time limit |
| **Output** | `PrepPlan` (ordered `PrepStep`s with durations, parallel tasks) |
| **AI involvement** | AI generates the schedule; deterministic code checks total duration ≤ limit |
| **Workflow** | `CalendarView.onPrepDayClicked()` → `MealtonFacade.createPrepPlan()` → `PrepPlanner.createPrepPlan()` → `LLMClient.generate()` → parse and validate → `PlanService.schedulePrepDay()` |
| **Errors / alternatives** | Plan exceeds time limit → planner retries with fewer recipes batched and tells the user what was left out. Empty week plan → asks the user to create a plan first |

### F06 — Pantry Inventory and Freshness Tracking (Deterministic)
| | |
|---|---|
| **Description** | Track ingredients with quantity ("X amount of Z left"), category and expiry; notify when food is close to spoiling |
| **User interaction** | Pantry tab: add/edit item, swipe to delete, multiselect delete, near-spoilage badge and notification |
| **Input** | Item name, quantity, unit, category, expiry date |
| **Output** | Updated inventory, expiry alerts |
| **AI involvement** | Deterministic |
| **Workflow** | `PantryShoppingView.onAddItem()` → `MealtonFacade.addPantryItem()` → `PantryInventory.addItem()` → observers notified. `FreshnessScheduler.tick()` → `checkFreshness()` → `notifyExpiring()` for items within 3 days |
| **Errors / alternatives** | Invalid quantity or date → rejected. Item already exists → offer to merge quantities |

### F07 — Recipes from Current Pantry (Hybrid)
| | |
|---|---|
| **Description** | Shows what the user can cook now, ranked by pantry match, spoilage urgency or macro fit |
| **User interaction** | Recipe Browser → "Can make now" tab; ranking dropdown; clickable thumbnails |
| **Input** | Pantry contents, ranking choice, filters, profile restrictions |
| **Output** | Ranked `RecipeMatch` list with a short reason |
| **AI involvement** | Hybrid: ranking is deterministic (Strategy); LLM writes a one-line "why this recipe" |
| **Workflow** | `RecipeBrowserView.onFilterChanged()` → `MealtonFacade.recommendRecipes()` → `RecipeRecommender.selectStrategy()` + `recommend()` → `RankingStrategy.rank()` |
| **Errors / alternatives** | Empty pantry → prompt to add items or view all recipes. No matches → suggest the "missing ingredients" tab (F08) |

### F08 — Missing-Ingredient Suggestions → Shopping List (Hybrid)
| | |
|---|---|
| **Description** | Lists recipes you almost have the ingredients for, and lets you add the missing items to your shopping list in one click |
| **User interaction** | Recipe Browser → "Almost there" tab → "Add missing to list" |
| **Input** | Pantry, recipe ingredients, profile |
| **Output** | `RecipeMatch` with `missingIngredients`; new `ShoppingItem`s |
| **AI involvement** | Hybrid: missing set is computed deterministically; LLM suggests substitutions for hard-to-find items |
| **Workflow** | `MealtonFacade.addMissingToShopping()` → `ShoppingService.addMissing()` → one `AddShoppingItemCommand` per item → `CommandHistory` |
| **Errors / alternatives** | Item already on list → quantity merged. Undo removes the whole batch |

### F09 — Recipe Filters and AI Tagging (Hybrid)
| | |
|---|---|
| **Description** | Filter recipes by tags (sweet, Asian, rice, noodles, …); tags for new/user recipes are generated by AI |
| **User interaction** | Filter chips above clickable thumbnails |
| **Input** | Selected filters; recipe text for tagging |
| **Output** | Filtered list; tag set on recipes |
| **AI involvement** | Hybrid: filtering deterministic; tag generation by LLM |
| **Workflow** | `RecipeBrowserView.onFilterChanged()` → `MealtonFacade.filterRecipes()` → `RecipeService.filter()`. New recipe → `RecipeService.autoTag()` → `LLMClient.generate()` |
| **Errors / alternatives** | LLM unavailable → recipe saved untagged and tagged later. No results → "clear filters" suggestion |

### F10 — Serving Scaling and Unit Conversion (Deterministic)
| | |
|---|---|
| **Description** | Adjust servings and convert between grams, cups, ml, tbsp, etc. |
| **User interaction** | Servings stepper and unit toggle in the recipe view |
| **Input** | Recipe, new servings, target unit |
| **Output** | Scaled `Recipe` with converted quantities |
| **AI involvement** | Deterministic |
| **Workflow** | `RecipeBrowserView.onServingsChanged()` → `MealtonFacade.scaleRecipe()` → `RecipeService.scale()` → `UnitConverter.convert()` |
| **Errors / alternatives** | Servings ≤ 0 rejected. No density data for an ingredient (cups↔g) → shows original unit with a note |

### F11 — Nutrition Breakdown (Hybrid)
| | |
|---|---|
| **Description** | Per-recipe and per-serving calories, protein, carbs, fat |
| **User interaction** | "Nutrition" panel on recipe page |
| **Input** | Recipe ingredients and servings |
| **Output** | `NutritionInfo` |
| **AI involvement** | Hybrid: nutrition API lookup is primary; LLM estimates unknown ingredients (marked "estimated") |
| **Workflow** | `MealtonFacade.getNutrition()` → `NutritionService.getNutrition()` → `NutritionProvider.lookup()` per ingredient → sum |
| **Errors / alternatives** | API down or ingredient not found → LLM estimate flagged as estimated; if both fail, show partial totals |

### F12 — Shopping List with Categories, Check-off and Clear (Hybrid)
| | |
|---|---|
| **Description** | Shopping list on the pantry page (tab switcher); user or AI-suggested categories; tick items (strike-through and dulled); clear ticked; multiselect/swipe delete; edit; undo |
| **User interaction** | Pantry ⇄ List tab switcher; tap to tick; swipe/multiselect to delete; "Clear checked"; "Undo" |
| **Input** | Item details, category names |
| **Output** | Updated `ShoppingList` |
| **AI involvement** | Hybrid: operations deterministic; LLM suggests categories |
| **Workflow** | View → `MealtonFacade` → `ShoppingService` → `Command` object → `CommandHistory.execute()`; ticking calls `ShoppingItem.toggle()` which delegates to its `ItemState` |
| **Errors / alternatives** | Delete of nothing selected → ignored. Undo stack empty → button disabled. LLM down → default categories |

### F13 — Recipe Rating, My Recipes and Saved Recipes (Deterministic)
| | |
|---|---|
| **Description** | Rate recipes 1–5, save others' recipes, create "My Recipes" |
| **User interaction** | Star widget, "Save" heart, "My Recipes / Saved" tabs |
| **Input** | Recipe, stars, or new recipe data |
| **Output** | Updated rating/saved flag; new `Recipe` |
| **AI involvement** | Deterministic (new recipes trigger F09 auto-tagging) |
| **Workflow** | `MealtonFacade.rateRecipe()/saveRecipe()` → `RecipeService` → `RecipeRepository.update()` |
| **Errors / alternatives** | Rating out of range rejected. Duplicate save ignored |

### F14 — Mealton Chat Agent (AI)
| | |
|---|---|
| **Description** | Natural-language assistant that can answer questions and **act**: edit the plan, query/modify the pantry, build shopping lists, check nutrition. Its avatar can be dragged anywhere on the screen |
| **User interaction** | Draggable circular avatar; click to open chat panel; the CLI offers `mealton chat` |
| **Input** | Free-text message, conversation memory, user profile |
| **Output** | Text reply and any resulting changes to plan/pantry/list |
| **AI involvement** | AI (agent loop, tool use, memory) |
| **Workflow** | `MealtonChatView.sendMessage()` → `MealtonFacade.chat()` → `MealtonAgent.run()` → loop of `LLMClient.chat()` ↔ `Tool.execute()` → final `AgentReply` |
| **Errors / alternatives** | Tool error → the agent explains and tries an alternative. Step limit (8) reached → partial result and clarifying question. Ambiguous request → asks the user a question. Destructive actions (delete) require confirmation |

---
# 3. UML Design

# 3.1 Class Diagram

The class diagram is split into three views for readability. Together they form one model and use the same class names. **Diagram A:** presentation and control. **Diagram B:** domain and persistence. **Diagram C:** services and AI/agent components.

### Diagram A — Presentation and Control (MVC + Facade)

```mermaid
classDiagram
direction TB
class MainWindow { +showPage(name) }
class LoginView { +submitLogin() +submitRegister() }
class ProfileView { +onSaveProfile() }
class CalendarView { +onPlanWeekClicked() +onAddMealClicked() +onPrepDayClicked() +refreshMacros(summary) +onPlanChanged(plan) }
class RecipeBrowserView { +onFilterChanged() +onServingsChanged(n) +onRate(stars) +onSave() }
class PantryShoppingView { +switchTab() +onAddItem() +onSwipeDelete() +onTick(id) +onClearChecked() +onUndo() +onItemExpiring(item) }
class MealtonChatView { +sendMessage(text) +moveAvatar(x, y) +showReply(reply) }
class CliApp { +main(args) +parse(args) +onItemExpiring(item) }
class PlanObserver { <<interface>> +onPlanChanged(plan) }
class PantryObserver { <<interface>> +onItemExpiring(item) +onPantryChanged() }
class MealtonFacade {
  +register(name, email, password) User
  +login(email, password) User
  +saveProfile(profile) Profile
  +generateWeekPlan(req) WeekPlan
  +addMeal(date, label, recipeId, servings)
  +removeMeal(entryId)
  +createLabel(name) MealLabel
  +getMacroSummary(date) MacroSummary
  +createPrepPlan(weekStart, maxMinutes) PrepPlan
  +addPantryItem(item)
  +removePantryItems(ids)
  +recommendRecipes(strategyName, filters) List~RecipeMatch~
  +addMissingToShopping(recipeId)
  +filterRecipes(filters) List~Recipe~
  +scaleRecipe(recipeId, servings) Recipe
  +getNutrition(recipeId) NutritionInfo
  +rateRecipe(recipeId, stars)
  +saveRecipe(recipeId)
  +addShoppingItem(item)
  +deleteShoppingItems(ids)
  +toggleShoppingItem(id)
  +clearChecked()
  +suggestCategories() List~String~
  +undo()
  +chat(message) AgentReply
}
MainWindow *-- LoginView
MainWindow *-- ProfileView
MainWindow *-- CalendarView
MainWindow *-- RecipeBrowserView
MainWindow *-- PantryShoppingView
MainWindow *-- MealtonChatView
LoginView ..> MealtonFacade
ProfileView ..> MealtonFacade
CalendarView ..> MealtonFacade
RecipeBrowserView ..> MealtonFacade
PantryShoppingView ..> MealtonFacade
MealtonChatView ..> MealtonFacade
CliApp ..> MealtonFacade
CalendarView ..|> PlanObserver
PantryShoppingView ..|> PantryObserver
CliApp ..|> PantryObserver
```

### Diagram B — Domain Model, Composite, State, Persistence

```mermaid
classDiagram
direction TB
class User { -id -name -email -passwordHash }
class Profile { -dob -weightKg -heightCm -sex -goal -allergies -restrictions -calorieTarget }
class MealLabel { -name -isCustom }
class Recipe { -id -title -steps -baseServings -tags -rating -isUserCreated -isSaved -imageUrl +scaledTo(n) Recipe }
class RecipeIngredient { -name -quantity -unit }
class NutritionInfo { -kcal -protein -carbs -fat +plus(other) NutritionInfo }
class MacroSummary { -date -total -target }
class RecipeMatch { -recipe -score -missingIngredients -reason }
class PrepPlan { -date -totalMinutes -steps +validate(maxMinutes) bool }
class PrepStep { -order -description -durationMin -recipes }
class PlanComponent { <<interface>> +getMacros() NutritionInfo +getName() String }
class WeekPlan { -weekStart +add(day) +attach(obs) +detach(obs) +notifyObservers() +getMacros() NutritionInfo }
class DayPlan { -date +add(entry) +remove(entryId) +getMacros() NutritionInfo }
class MealEntry { -id -servings +getMacros() NutritionInfo }
class PantryItem { -id -name -quantity -unit -category -expiryDate +daysLeft() int }
class ShoppingList { -items +add(item) +remove(ids) +checkedItems() List~ShoppingItem~ }
class ShoppingItem { -id -name -quantity -unit -category +toggle() +setState(s) +getStyle() String }
class ItemState { <<interface>> +toggle(item) +getStyle() String }
class PendingState { +toggle(item) +getStyle() String }
class CheckedState { +toggle(item) +getStyle() String }
class Repository~T~ { <<interface>> +save(t) +findById(id) T +findAll() List~T~ +delete(id) }
class UserRepository { +findByEmail(email) User }
class RecipeRepository { +search(filters) List~Recipe~ +update(recipe) }
class PantryRepository
class PlanRepository { +findWeek(weekStart) WeekPlan }
class ShoppingRepository
class SqliteRepositories { <<implementation>> }

User "1" *-- "1" Profile
User "1" o-- "0..*" Recipe : owns
Recipe "1" *-- "1..*" RecipeIngredient
Recipe "1" *-- "0..1" NutritionInfo
WeekPlan ..|> PlanComponent
DayPlan ..|> PlanComponent
MealEntry ..|> PlanComponent
WeekPlan "1" *-- "7" DayPlan
DayPlan "1" *-- "0..*" MealEntry
MealEntry "*" --> "1" Recipe
MealEntry "*" --> "1" MealLabel
PrepPlan "1" *-- "1..*" PrepStep
RecipeMatch "*" --> "1" Recipe
ShoppingList "1" *-- "0..*" ShoppingItem
ShoppingItem "1" --> "1" ItemState
PendingState ..|> ItemState
CheckedState ..|> ItemState
UserRepository --|> Repository
RecipeRepository --|> Repository
PantryRepository --|> Repository
PlanRepository --|> Repository
ShoppingRepository --|> Repository
SqliteRepositories ..|> UserRepository
SqliteRepositories ..|> RecipeRepository
SqliteRepositories ..|> PantryRepository
SqliteRepositories ..|> PlanRepository
SqliteRepositories ..|> ShoppingRepository
```

### Diagram C — Services and AI/Agent Components

```mermaid
classDiagram
direction TB
class MealtonFacade
class AuthService { +register(name, email, pw) User +login(email, pw) User }
class ProfileService { +saveProfile(userId, profile) Profile +calculateCalorieTarget(profile) int }
class PlanService { +generateWeekPlan(req) WeekPlan +addMeal(date, label, recipeId, servings) +removeMeal(entryId) +createLabel(name) MealLabel +getPlan(weekStart) WeekPlan +schedulePrepDay(prep, date) }
class PlanValidator { +validate(plan, profile) ValidationResult }
class MacroTracker { +onPlanChanged(plan) +getDailySummary(date) MacroSummary }
class PantryInventory { -items -observers +addItem(item) +updateItem(item) +removeItems(ids) +getItems() List~PantryItem~ +checkFreshness() +attach(obs) +detach(obs) +notifyExpiring(item) }
class FreshnessScheduler { +start() +tick() }
class RecipeRecommender { -strategy +selectStrategy(name) +recommend(filters) List~RecipeMatch~ +recommendWithMissing(filters) List~RecipeMatch~ +explain(match) String +onPantryChanged() +onItemExpiring(item) }
class RankingStrategy { <<interface>> +rank(recipes, ctx) List~RecipeMatch~ }
class PantryMatchStrategy
class SpoilageUrgencyStrategy
class MacroFitStrategy
class RecipeService { +filter(filters) List~Recipe~ +scale(recipe, n) Recipe +rate(id, stars) +save(id) +createUserRecipe(r) Recipe +autoTag(recipe) List~String~ }
class UnitConverter { +convert(qty, from, to) double }
class NutritionService { +getNutrition(recipe) NutritionInfo }
class NutritionProvider { <<interface>> +lookup(ingredient) NutritionInfo }
class UsdaNutritionAdapter { -apiClient +lookup(ingredient) NutritionInfo }
class ShoppingService { +addItem(item) +addMissing(recipe, pantry) +deleteItems(ids) +toggle(id) +clearChecked() +suggestCategories() List~String~ +undo() }
class Command { <<interface>> +execute() +undo() }
class CommandHistory { -undoStack -redoStack +execute(cmd) +undo() +redo() }
class AddShoppingItemCommand
class DeleteItemsCommand
class ToggleItemCommand
class AddMealCommand
class RemoveMealCommand
class PrepPlanner { +createPrepPlan(plan, maxMinutes) PrepPlan }
class MealtonAgent { -maxSteps +run(message) AgentReply +generatePlan(req) WeekPlan }
class AgentMemory { +addTurn(role, text) +getRecentContext(n) List~Message~ +setPreference(k, v) +getPreferences() Map }
class LLMClient { <<interface>> +chat(messages, toolSpecs) LLMResponse +generate(prompt) String }
class ClaudeAdapter { -sdk }
class OpenAIAdapter { -sdk }
class MockLLMClient
class Tool { <<interface>> +name() String +schema() ToolSpec +execute(args) ToolResult }
class PantryTool
class RecipeSearchTool
class NutritionTool
class ShoppingListTool
class CalendarTool
class ToolRegistry { +register(tool) +get(name) Tool +specs() List~ToolSpec~ }
class ToolFactory { <<abstract>> +createTool() Tool +registerWith(registry) }
class PantryToolFactory
class RecipeSearchToolFactory
class NutritionToolFactory
class ShoppingListToolFactory
class CalendarToolFactory

MealtonFacade ..> AuthService
MealtonFacade ..> ProfileService
MealtonFacade ..> PlanService
MealtonFacade ..> PantryInventory
MealtonFacade ..> RecipeRecommender
MealtonFacade ..> RecipeService
MealtonFacade ..> NutritionService
MealtonFacade ..> ShoppingService
MealtonFacade ..> PrepPlanner
MealtonFacade ..> MacroTracker
MealtonFacade ..> MealtonAgent
PlanService ..> PlanValidator
PlanService ..> MealtonAgent
PlanService o-- CommandHistory
PlanService ..> AddMealCommand
PlanService ..> RemoveMealCommand
ShoppingService o-- CommandHistory
ShoppingService ..> AddShoppingItemCommand
ShoppingService ..> DeleteItemsCommand
ShoppingService ..> ToggleItemCommand
CommandHistory "1" o-- "*" Command
AddShoppingItemCommand ..|> Command
DeleteItemsCommand ..|> Command
ToggleItemCommand ..|> Command
AddMealCommand ..|> Command
RemoveMealCommand ..|> Command
MacroTracker ..|> PlanObserver
RecipeRecommender ..|> PantryObserver
PantryInventory "1" o-- "*" PantryObserver : notifies
FreshnessScheduler --> PantryInventory
RecipeRecommender o-- RankingStrategy
PantryMatchStrategy ..|> RankingStrategy
SpoilageUrgencyStrategy ..|> RankingStrategy
MacroFitStrategy ..|> RankingStrategy
RecipeRecommender ..> PantryInventory
RecipeService ..> UnitConverter
RecipeService ..> LLMClient
NutritionService --> NutritionProvider
NutritionService ..> LLMClient
UsdaNutritionAdapter ..|> NutritionProvider
PrepPlanner ..> LLMClient
ShoppingService ..> LLMClient
MealtonAgent --> LLMClient
MealtonAgent --> ToolRegistry
MealtonAgent --> AgentMemory
ClaudeAdapter ..|> LLMClient
OpenAIAdapter ..|> LLMClient
MockLLMClient ..|> LLMClient
ToolRegistry "1" o-- "5" Tool
PantryTool ..|> Tool
RecipeSearchTool ..|> Tool
NutritionTool ..|> Tool
ShoppingListTool ..|> Tool
CalendarTool ..|> Tool
PantryToolFactory --|> ToolFactory
RecipeSearchToolFactory --|> ToolFactory
NutritionToolFactory --|> ToolFactory
ShoppingListToolFactory --|> ToolFactory
CalendarToolFactory --|> ToolFactory
ToolFactory ..> Tool : creates
PantryTool ..> PantryInventory
RecipeSearchTool ..> RecipeService
NutritionTool ..> NutritionService
ShoppingListTool ..> ShoppingService
CalendarTool ..> PlanService
```

---

# 3.2 Design Patterns

Eight patterns are used. Each solves a specific problem in Mealton.

### 1. Strategy — recipe ranking
- **Problem:** The Recipe Browser must rank recipes in different ways (best pantry match, use-up-spoiling-food-first, best macro fit), and the user can switch at runtime.
- **Participants:** `RankingStrategy` (strategy interface), `PantryMatchStrategy`, `SpoilageUrgencyStrategy`, `MacroFitStrategy` (concrete strategies), `RecipeRecommender` (context).
- **Roles:** the recommender holds one strategy and delegates `rank()`; each strategy encapsulates one scoring algorithm.
- **Why appropriate:** the algorithms are interchangeable and chosen by the user.
- **Without it:** `RecipeRecommender` would contain a growing `if/else` block on ranking mode, and adding a new ranking rule would mean editing and re-testing the whole class.

### 2. Observer — pantry alerts and live macro updates
- **Problem:** Several components must react when state changes without the source knowing about them: near-spoilage items must alert the UI and the recommender; any plan change must update macros and the calendar.
- **Participants:** `PantryInventory` (subject) with `PantryObserver` (`PantryShoppingView`, `CliApp`, `RecipeRecommender`); `WeekPlan` (subject) with `PlanObserver` (`MacroTracker`, `CalendarView`).
- **Roles:** subjects keep observer lists and call `notifyExpiring()` / `notifyObservers()`; observers update themselves.
- **Why appropriate:** one-to-many, event-driven updates; the GUI and CLI both listen to the same events.
- **Without it:** the pantry and plan classes would need direct references to every view and service (tight coupling), and polling code would be needed for freshness alerts.

### 3. Command — undoable edits
- **Problem:** Shopping-list edits (add, multiselect delete, tick, clear checked) and calendar edits must be undoable, and one user action (e.g. "add all missing ingredients") must be undone as a single unit.
- **Participants:** `Command`, `AddShoppingItemCommand`, `DeleteItemsCommand`, `ToggleItemCommand`, `AddMealCommand`, `RemoveMealCommand` (concrete commands), `CommandHistory` (invoker), `ShoppingList` / `WeekPlan` (receivers), `ShoppingService` / `PlanService` (clients).
- **Roles:** each command knows how to `execute()` and `undo()`; the history keeps undo/redo stacks.
- **Why appropriate:** swipe-delete and bulk actions are easy to do by accident, so undo is a core usability need.
- **Without it:** each operation would need ad-hoc "previous state" bookkeeping scattered across services, and undo of compound actions would be error-prone.

### 4. Factory Method — creating agent tools
- **Problem:** The agent's tools need different dependencies (pantry, recipes, nutrition, list, calendar), and new tools should be addable without changing the agent.
- **Participants:** `ToolFactory` (creator with `createTool()` and `registerWith()`), `PantryToolFactory`, `RecipeSearchToolFactory`, `NutritionToolFactory`, `ShoppingListToolFactory`, `CalendarToolFactory` (concrete creators), `Tool` (product), concrete tools, `ToolRegistry`.
- **Roles:** each concrete factory builds its tool with the right services injected; the registry only sees `Tool`.
- **Why appropriate:** `MealtonAgent` depends only on the `Tool` abstraction.
- **Without it:** the agent or registry would use `new` on every concrete tool and know all their dependencies, so every new tool would modify core agent code.

### 5. Adapter — LLM and nutrition API
- **Problem:** Vendor SDKs (Anthropic, OpenAI, USDA API) have incompatible interfaces, and we want to swap providers and test without network calls.
- **Participants:** `LLMClient` (target), `ClaudeAdapter`, `OpenAIAdapter`, `MockLLMClient` (adapters/test double), `NutritionProvider` (target), `UsdaNutritionAdapter` (adapter); clients are `MealtonAgent`, `PrepPlanner`, `RecipeService`, `NutritionService`, `ShoppingService`.
- **Roles:** adapters translate our `chat()` / `generate()` / `lookup()` calls to the vendor API and map responses back to our types.
- **Why appropriate:** isolates third-party APIs at one boundary.
- **Without it:** vendor-specific code would be spread through all services; changing models or APIs, or writing offline tests, would be painful.

### 6. Facade — one entry point for GUI and CLI
- **Problem:** The GUI views and the CLI need the same functionality, which involves ~10 services. We do not want UI code to know about services, agents and repositories.
- **Participants:** `MealtonFacade` (facade); `AuthService`, `ProfileService`, `PlanService`, `PantryInventory`, `RecipeRecommender`, `RecipeService`, `NutritionService`, `ShoppingService`, `PrepPlanner`, `MealtonAgent` (subsystem); `CliApp` and the views (clients).
- **Roles:** the facade offers simple use-case-level methods and coordinates subsystem calls. It also acts as the MVC controller.
- **Why appropriate:** the CLI/GUI parity requirement is satisfied by construction, and subsystems can change without touching the UI.
- **Without it:** GUI and CLI would duplicate orchestration logic and become tightly coupled to every service.

### 7. Composite — week/day/meal hierarchy
- **Problem:** Macros and names must be computed uniformly for one meal, one day and the whole week.
- **Participants:** `PlanComponent` (component), `MealEntry` (leaf), `DayPlan` and `WeekPlan` (composites).
- **Roles:** `getMacros()` on a composite sums its children; a leaf returns its recipe's scaled nutrition.
- **Why appropriate:** the plan is naturally a tree and the macro tracker treats every level the same way.
- **Without it:** `MacroTracker` would contain nested loops with special cases for each level, and adding another level (e.g. "month") would require rewrites.

### 8. State — shopping-item behaviour
- **Problem:** A shopping item behaves differently when ticked (strike-through, dulled colour, eligible for "clear checked") than when pending.
- **Participants:** `ShoppingItem` (context), `ItemState` (state interface), `PendingState`, `CheckedState` (concrete states).
- **Roles:** the item delegates `toggle()` and `getStyle()` to its current state; the state switches the item to the other state.
- **Why appropriate:** the item's appearance and available actions depend on its state, and toggling must be reversible (works with Command undo).
- **Without it:** `ShoppingItem` would be full of `if (checked)` checks in the UI, list and clear logic, and adding a state (e.g. "out of stock") would touch many places.

**Also used (not counted):** *MVC* (views = View, `MealtonFacade` = Controller, domain classes = Model) and *Repository* (persistence behind interfaces so storage can be changed or mocked).

---
# 3.3 Use-Case Diagram and Descriptions

## Actors
- **User** (primary actor; uses GUI or CLI)
- **LLM Service** (external AI service, secondary actor)
- **Nutrition API** (external service, secondary actor)
- **Scheduler / System Clock** (triggers daily freshness checks)

There is no administrator role: all users manage only their own data.

```mermaid
flowchart LR
  U([User])
  L([LLM Service])
  N([Nutrition API])
  C([Scheduler / Clock])

  subgraph Mealton System
    UC00((UC00 Register / Login))
    UC01((UC01 Manage Profile))
    UC02((UC02 Generate Weekly Plan))
    UC03((UC03 Edit Meal Calendar))
    UC04((UC04 View Macro Tracker))
    UC05((UC05 Plan Prep Day))
    UC06((UC06 Manage Pantry))
    UC07((UC07 Find Recipes From Pantry))
    UC08((UC08 Shop Missing Ingredients))
    UC09((UC09 Browse and Filter Recipes))
    UC10((UC10 Scale Recipe / Convert Units))
    UC11((UC11 View Nutrition))
    UC12((UC12 Manage Shopping List))
    UC13((UC13 Rate and Save Recipes))
    UC14((UC14 Chat with Mealton))
    UC15((UC15 Expiry Notification))
  end

  U --- UC00 & UC01 & UC02 & UC03 & UC04 & UC05 & UC06 & UC07 & UC08 & UC09 & UC10 & UC11 & UC12 & UC13 & UC14
  UC01 --- L
  UC02 --- L
  UC05 --- L
  UC09 --- L
  UC11 --- L
  UC12 --- L
  UC14 --- L
  UC11 --- N
  UC02 --- N
  C --- UC15
  UC15 -.-> U
  UC02 -. include .-> UC04
  UC03 -. include .-> UC04
  UC08 -. extend .-> UC07
  UC08 -. include .-> UC12
  UC14 -. extend .-> UC02
  UC14 -. extend .-> UC03
  UC14 -. extend .-> UC12
```

UC15 is the system-triggered side of Feature F06 (freshness notification). UC00 is supporting (not counted as a feature). `include` = always uses the other use case; `extend` = optional extra.

## Use-Case Descriptions

### UC00 — Register / Login
- **Actor:** User · **Goal:** access a personal account · **Preconditions:** app open
- **Trigger:** user clicks Login/Register (or `mealton login`)
- **Main:** 1) user enters email and password; 2) system looks up user; 3) password hash is verified; 4) session starts; 5) main window shown
- **Alternatives:** wrong credentials → error, retry; unknown email on Register → account created; duplicate email → error
- **Postconditions:** user authenticated · **Related:** supports all features

### UC01 — Manage Profile
- **Actor:** User; LLM Service (optional explanation) · **Goal:** keep health data and restrictions up to date
- **Preconditions:** logged in · **Trigger:** user opens Profile page and saves
- **Main:** 1) user enters details; 2) system validates; 3) calorie target calculated; 4) profile saved; 5) confirmation and target shown
- **Alternatives:** invalid values → error; LLM down → target without explanation; unsafe target → capped with warning
- **Postconditions:** profile persisted · **Related:** F01

### UC02 — Generate Weekly Plan
- **Actor:** User; LLM Service; Nutrition API · **Goal:** get a weekly plan fitting goals/allergies/pantry
- **Preconditions:** logged in, profile exists · **Trigger:** click "Plan for the week"
- **Main:** 1) user sets optional preferences; 2) agent reads profile and pantry; 3) agent searches recipes with tools; 4) agent drafts plan; 5) validator checks allergens and calories; 6) plan saved and shown; 7) macros update
- **Alternatives:** validation fails → one re-plan with feedback; still fails → error and manual option; LLM/API error → retry message
- **Postconditions:** `WeekPlan` saved · **Related:** F02, F04

### UC03 — Edit Meal Calendar
- **Actor:** User · **Goal:** manually add/remove meals and manage labels
- **Preconditions:** logged in · **Trigger:** click "+ Add meal"
- **Main:** 1) user picks day, label (or creates custom), recipe, servings; 2) system checks allergens; 3) meal added; 4) macros refresh
- **Alternatives:** allergen warning → confirm or cancel; duplicate label → rejected; undo is available
- **Postconditions:** plan updated · **Related:** F03, F04

### UC04 — View Macro Tracker
- **Actor:** User · **Goal:** see calories/macros vs targets
- **Preconditions:** logged in · **Trigger:** viewing the calendar or any plan change
- **Main:** 1) plan changes; 2) tracker recomputes totals; 3) progress bars updated
- **Alternatives:** recipe lacks nutrition → flagged as incomplete
- **Postconditions:** summary displayed · **Related:** F04

### UC05 — Plan Prep Day
- **Actor:** User; LLM Service · **Goal:** batch-cook the week's meals in limited time
- **Preconditions:** a week plan exists · **Trigger:** click "Schedule prep day"
- **Main:** 1) user picks date and time limit; 2) planner builds task list from the plan; 3) LLM orders and parallelises tasks; 4) total time validated; 5) prep day added to the calendar
- **Alternatives:** over time limit → retry with reduced scope and notify; no plan → ask to create one
- **Postconditions:** `PrepPlan` saved · **Related:** F05

### UC06 — Manage Pantry
- **Actor:** User · **Goal:** keep an accurate inventory
- **Preconditions:** logged in · **Trigger:** open Pantry tab
- **Main:** 1) user adds/edits item with quantity and expiry; 2) inventory saved; 3) observers notified; 4) list refreshed. Swipe/multiselect removes items
- **Alternatives:** invalid input → rejected; duplicate item → merge offered
- **Postconditions:** inventory updated · **Related:** F06

### UC07 — Find Recipes From Pantry
- **Actor:** User · **Goal:** know what can be cooked now
- **Preconditions:** logged in · **Trigger:** open "Can make now" tab
- **Main:** 1) user chooses ranking; 2) system loads pantry and recipes; 3) strategy ranks; 4) results shown as thumbnails with reason
- **Alternatives:** empty pantry → prompt to add items; no matches → suggest UC08
- **Postconditions:** none · **Related:** F07

### UC08 — Shop Missing Ingredients
- **Actor:** User · **Goal:** buy what is missing for a nearly-possible recipe
- **Preconditions:** logged in · **Trigger:** "Add missing to list" on a recipe
- **Main:** 1) system computes missing ingredients; 2) adds each to the shopping list as one undoable batch; 3) confirmation shown
- **Alternatives:** item already on list → quantity merged; undo removes batch
- **Postconditions:** list updated · **Related:** F08

### UC09 — Browse and Filter Recipes
- **Actor:** User; LLM Service (tagging) · **Goal:** find recipes by type
- **Preconditions:** logged in · **Trigger:** select filter chips
- **Main:** 1) user selects filters; 2) system filters recipes; 3) thumbnails updated
- **Alternatives:** no results → suggest clearing filters; LLM tagging unavailable → recipe untagged for now
- **Postconditions:** none · **Related:** F09

### UC10 — Scale Recipe / Convert Units
- **Actor:** User · **Goal:** adjust servings and measurement units
- **Preconditions:** a recipe is open · **Trigger:** change servings or unit
- **Main:** 1) user enters servings; 2) system scales quantities; 3) converts units; 4) updated recipe shown
- **Alternatives:** servings ≤ 0 → rejected; no density data → original unit with note
- **Postconditions:** none · **Related:** F10

### UC11 — View Nutrition
- **Actor:** User; Nutrition API; LLM Service · **Goal:** see nutrition for a recipe
- **Preconditions:** recipe open · **Trigger:** open Nutrition panel
- **Main:** 1) system looks up each ingredient; 2) sums values per serving; 3) panel shows totals
- **Alternatives:** API fails → LLM estimate flagged; both fail → partial totals
- **Postconditions:** nutrition cached on recipe · **Related:** F11

### UC12 — Manage Shopping List
- **Actor:** User; LLM Service (categories) · **Goal:** maintain a grocery list
- **Preconditions:** logged in · **Trigger:** open List tab
- **Main:** 1) user adds items; 2) assigns/accepts suggested categories; 3) ticks items (strike-through, dulled); 4) clears ticked or deletes by swipe/multiselect; 5) may undo
- **Alternatives:** nothing selected → ignored; empty undo stack → disabled; LLM down → default categories
- **Postconditions:** list persisted · **Related:** F12

### UC13 — Rate and Save Recipes
- **Actor:** User · **Goal:** organise favourites and own recipes
- **Preconditions:** recipe visible · **Trigger:** click stars/heart or "New recipe"
- **Main:** 1) user rates or saves; 2) system updates the recipe; 3) shown in My/Saved tabs
- **Alternatives:** invalid rating rejected; duplicate save ignored
- **Postconditions:** recipe updated · **Related:** F13

### UC14 — Chat with Mealton
- **Actor:** User; LLM Service · **Goal:** accomplish tasks via natural language
- **Preconditions:** logged in · **Trigger:** user sends a message (avatar chat or `mealton chat`)
- **Main:** 1) message sent to agent; 2) agent loads memory; 3) LLM decides tool calls; 4) tools run and results are returned; 5) steps repeat until done; 6) reply shown; 7) changes appear in calendar/pantry/list
- **Alternatives:** tool error → agent retries or explains; step limit → partial result and question; destructive action → confirmation first
- **Postconditions:** memory updated; possible data changes · **Related:** F14

### UC15 — Expiry Notification
- **Actor:** Scheduler/Clock; User · **Goal:** avoid food waste
- **Preconditions:** pantry has items with expiry dates · **Trigger:** daily timer
- **Main:** 1) scheduler ticks; 2) system finds items expiring within 3 days; 3) observers notified; 4) UI/CLI shows alert; 5) recommender prioritises matching recipes
- **Alternatives:** no expiring items → nothing shown
- **Postconditions:** user informed · **Related:** F06, F07

---
# 3.4 Sequence Diagrams

All participants and method names come from the class diagrams above.

### SD01 — Login and Save Profile (UC00, UC01 · F01)
```mermaid
sequenceDiagram
autonumber
actor U as User
participant LV as LoginView
participant PV as ProfileView
participant F as MealtonFacade
participant A as AuthService
participant PS as ProfileService
participant UR as UserRepository
U->>LV: enter email + password, click Login
LV->>F: login(email, password)
F->>A: login(email, password)
A->>UR: findByEmail(email)
UR-->>A: User
alt password valid
  A-->>F: User
  F-->>LV: User (session started)
else invalid
  A-->>F: AuthException
  F-->>LV: error message
end
U->>PV: edit profile, click Save
PV->>F: saveProfile(profile)
F->>PS: saveProfile(userId, profile)
alt valid input
  PS->>PS: calculateCalorieTarget(profile)
  PS->>UR: save(user)
  PS-->>F: Profile
  F-->>PV: Profile + calorie target
else invalid input
  PS-->>F: ValidationException
  F-->>PV: show inline errors
end
```

### SD02 — Generate Weekly Plan (UC02 · F02)
```mermaid
sequenceDiagram
autonumber
actor U as User
participant CV as CalendarView
participant F as MealtonFacade
participant PS as PlanService
participant AG as MealtonAgent
participant LLM as LLMClient
participant TR as ToolRegistry
participant RT as RecipeSearchTool
participant PT as PantryTool
participant V as PlanValidator
participant MT as MacroTracker
U->>CV: click "Plan for the week"
CV->>F: generateWeekPlan(req)
F->>PS: generateWeekPlan(req)
PS->>AG: generatePlan(req)
loop until model returns final plan (max steps)
  AG->>LLM: chat(messages, toolSpecs)
  LLM-->>AG: LLMResponse (toolCall or final plan)
  opt toolCall
    AG->>TR: get(toolName)
    TR-->>AG: Tool
    alt pantry
      AG->>PT: execute(args)
      PT-->>AG: ToolResult (pantry items)
    else recipe search
      AG->>RT: execute(args)
      RT-->>AG: ToolResult (candidate recipes)
    end
  end
end
AG-->>PS: WeekPlan (draft)
PS->>V: validate(plan, profile)
alt valid
  V-->>PS: ok
  PS->>PS: save plan, attach observers
  PS-->>F: WeekPlan
  F-->>CV: WeekPlan
  PS-)MT: onPlanChanged(plan)
  PS-)CV: onPlanChanged(plan)
else violations (allergen / calories)
  V-->>PS: ValidationResult(violations)
  PS->>AG: generatePlan(req + violations)
  AG-->>PS: WeekPlan (revised)
  Note over PS,V: second validation; on failure return error to user
else LLM error
  LLM-->>AG: error
  AG-->>F: AgentException
  F-->>CV: show retry / build manually
end
```

### SD03 — Edit Calendar and Update Macros (UC03, UC04 · F03, F04)
```mermaid
sequenceDiagram
autonumber
actor U as User
participant CV as CalendarView
participant F as MealtonFacade
participant PS as PlanService
participant CH as CommandHistory
participant C as AddMealCommand
participant D as DayPlan
participant W as WeekPlan
participant MT as MacroTracker
U->>CV: click "+ Add meal", choose recipe, label, servings
CV->>F: addMeal(date, label, recipeId, servings)
F->>PS: addMeal(date, label, recipeId, servings)
opt label does not exist
  PS->>PS: createLabel(name)
end
alt recipe contains allergen
  PS-->>CV: allergen warning (user confirms or cancels)
end
PS->>C: new AddMealCommand(...)
PS->>CH: execute(cmd)
CH->>C: execute()
C->>D: add(entry)
C->>W: notifyObservers()
W-)MT: onPlanChanged(plan)
MT->>W: getMacros()
W-->>MT: NutritionInfo
W-)CV: onPlanChanged(plan)
CV->>F: getMacroSummary(date)
F->>MT: getDailySummary(date)
MT-->>CV: MacroSummary
CV->>CV: refreshMacros(summary)
```

### SD04 — Plan Prep Day (UC05 · F05)
```mermaid
sequenceDiagram
autonumber
actor U as User
participant CV as CalendarView
participant F as MealtonFacade
participant PP as PrepPlanner
participant PS as PlanService
participant LLM as LLMClient
U->>CV: click "Schedule prep day" (date, time limit)
CV->>F: createPrepPlan(weekStart, maxMinutes)
F->>PS: getPlan(weekStart)
PS-->>F: WeekPlan
alt plan is empty
  F-->>CV: error "create a plan first"
else plan exists
  F->>PP: createPrepPlan(plan, maxMinutes)
  PP->>LLM: generate(prompt with recipes + time limit)
  LLM-->>PP: draft schedule
  PP->>PP: parse to PrepPlan, validate(maxMinutes)
  alt exceeds limit
    PP->>LLM: generate(prompt with reduced scope)
    LLM-->>PP: revised schedule
  end
  PP-->>F: PrepPlan
  F->>PS: schedulePrepDay(prep, date)
  F-->>CV: PrepPlan timeline shown
end
```

### SD05 — Pantry Update and Expiry Notification (UC06, UC15 · F06)
```mermaid
sequenceDiagram
autonumber
actor U as User
participant PV as PantryShoppingView
participant F as MealtonFacade
participant PI as PantryInventory
participant PR as PantryRepository
participant RR as RecipeRecommender
participant FS as FreshnessScheduler
U->>PV: add item / swipe to delete
PV->>F: addPantryItem(item)  /  removePantryItems(ids)
F->>PI: addItem(item)  /  removeItems(ids)
PI->>PR: save(item)  /  delete(id)
PI-)RR: onPantryChanged()
PI-)PV: onPantryChanged()
PV-->>U: list refreshed
Note over FS,PI: daily timer
FS->>PI: checkFreshness()
loop each item with daysLeft() <= 3
  PI->>PI: notifyExpiring(item)
  PI-)PV: onItemExpiring(item)
  PV-->>U: show alert badge
  PI-)RR: onItemExpiring(item)
end
```

### SD06 — Recipes From Pantry and Missing Ingredients (UC07, UC08, UC09 · F07, F08, F09)
```mermaid
sequenceDiagram
autonumber
actor U as User
participant RV as RecipeBrowserView
participant F as MealtonFacade
participant RR as RecipeRecommender
participant S as RankingStrategy
participant PI as PantryInventory
participant RS as RecipeService
participant SS as ShoppingService
participant CH as CommandHistory
U->>RV: open "Can make now", choose ranking + filters
RV->>F: recommendRecipes(strategyName, filters)
F->>RR: selectStrategy(strategyName)
F->>RR: recommend(filters)
RR->>PI: getItems()
PI-->>RR: pantry items
RR->>RS: filter(filters)
RS-->>RR: candidate recipes
RR->>S: rank(recipes, ctx)
S-->>RR: List of RecipeMatch
RR->>RR: explain(match) for top results
RR-->>RV: ranked matches
alt no matches
  RV-->>U: suggest "Almost there" tab
end
U->>RV: click "Add missing to list"
RV->>F: addMissingToShopping(recipeId)
F->>SS: addMissing(recipe, pantry)
loop each missing ingredient
  SS->>CH: execute(AddShoppingItemCommand)
end
SS-->>RV: confirmation (undo available)
```

### SD07 — Scale Recipe, Nutrition, Rate and Save (UC10, UC11, UC13 · F10, F11, F13)
```mermaid
sequenceDiagram
autonumber
actor U as User
participant RV as RecipeBrowserView
participant F as MealtonFacade
participant RS as RecipeService
participant UC as UnitConverter
participant NS as NutritionService
participant NP as NutritionProvider
participant LLM as LLMClient
participant RP as RecipeRepository
U->>RV: change servings / unit
RV->>F: scaleRecipe(recipeId, servings)
F->>RS: scale(recipe, servings)
RS->>UC: convert(qty, from, to)
UC-->>RS: converted quantity
RS-->>RV: scaled Recipe
U->>RV: open Nutrition panel
RV->>F: getNutrition(recipeId)
F->>NS: getNutrition(recipe)
loop each ingredient
  NS->>NP: lookup(ingredient)
  alt found
    NP-->>NS: NutritionInfo
  else not found / API error
    NS->>LLM: generate(estimate prompt)
    LLM-->>NS: estimated values (flagged)
  end
end
NS-->>RV: NutritionInfo
U->>RV: rate 4 stars / save
RV->>F: rateRecipe(recipeId, 4) / saveRecipe(recipeId)
F->>RS: rate(id, 4) / save(id)
RS->>RP: update(recipe)
RS-->>RV: updated
```

### SD08 — Shopping List: Add, Tick, Delete, Clear, Undo (UC12 · F12)
```mermaid
sequenceDiagram
autonumber
actor U as User
participant PV as PantryShoppingView
participant F as MealtonFacade
participant SS as ShoppingService
participant CH as CommandHistory
participant L as ShoppingList
participant I as ShoppingItem
participant ST as ItemState
U->>PV: add item
PV->>F: addShoppingItem(item)
F->>SS: addItem(item)
SS->>CH: execute(AddShoppingItemCommand)
CH->>L: add(item)
U->>PV: tick item
PV->>F: toggleShoppingItem(id)
F->>SS: toggle(id)
SS->>CH: execute(ToggleItemCommand)
CH->>I: toggle()
I->>ST: toggle(item)
ST->>I: setState(CheckedState)
PV->>I: getStyle()
I->>ST: getStyle()
ST-->>PV: "strike-through + dulled"
U->>PV: click "Clear checked"
PV->>F: clearChecked()
F->>SS: clearChecked()
SS->>L: checkedItems()
SS->>CH: execute(DeleteItemsCommand(ids))
U->>PV: click Undo
PV->>F: undo()
F->>SS: undo()
SS->>CH: undo()
CH-->>PV: previous state restored
opt AI category suggestion
  U->>PV: "Suggest categories"
  PV->>F: suggestCategories()
  F->>SS: suggestCategories()
  alt LLM available
    SS-->>PV: suggested categories
  else LLM error
    SS-->>PV: default categories
  end
end
```

### SD09 — Chat with Mealton (UC14 · F14)
```mermaid
sequenceDiagram
autonumber
actor U as User
participant CV as MealtonChatView
participant F as MealtonFacade
participant AG as MealtonAgent
participant M as AgentMemory
participant LLM as LLMClient
participant TR as ToolRegistry
participant T as Tool
U->>CV: "Make my week high-protein with no nuts"
CV->>F: chat(message)
F->>AG: run(message)
AG->>M: getRecentContext(n)
M-->>AG: previous turns + preferences
loop until final answer or step limit
  AG->>LLM: chat(messages, toolSpecs)
  LLM-->>AG: LLMResponse
  alt toolCall requested
    AG->>TR: get(toolName)
    TR-->>AG: Tool
    AG->>T: execute(args)
    alt success
      T-->>AG: ToolResult
    else tool error
      T-->>AG: error result (agent may retry or explain)
    end
  else final text
    AG->>M: addTurn(role, text)
    AG-->>F: AgentReply
  end
end
alt step limit reached
  AG-->>F: partial AgentReply + clarifying question
end
F-->>CV: AgentReply
CV->>CV: showReply(reply)
Note over T,CV: if the plan changed, PlanService notifies PlanObservers and the calendar refreshes
```

---
# 4. Feature-to-Design Traceability Table

| Feature | Description | Type | Use Case | Classes | Key Methods | Sequence Diagram | Design Pattern(s) |
|---|---|---|---|---|---|---|---|
| F01 | Profile and dietary constraints | Hybrid | UC01 | ProfileView, MealtonFacade, ProfileService, UserRepository, Profile | saveProfile(), calculateCalorieTarget() | SD01 | Facade, MVC, Repository |
| F02 | AI weekly plan generation | AI | UC02 | CalendarView, MealtonFacade, PlanService, MealtonAgent, LLMClient, ToolRegistry, RecipeSearchTool, PantryTool, PlanValidator, WeekPlan | generateWeekPlan(), generatePlan(), chat(), execute(), validate() | SD02 | Facade, Adapter, Factory Method, Composite, Observer |
| F03 | Meal calendar editing and custom labels | Deterministic | UC03 | CalendarView, MealtonFacade, PlanService, CommandHistory, AddMealCommand, DayPlan, MealLabel | addMeal(), createLabel(), execute(), undo() | SD03 | Command, Composite, Observer |
| F04 | Macro tracker | Deterministic | UC04 | MacroTracker, WeekPlan, DayPlan, MealEntry, CalendarView | onPlanChanged(), getDailySummary(), getMacros() | SD03 | Observer, Composite |
| F05 | Prep-day planner | AI | UC05 | CalendarView, MealtonFacade, PrepPlanner, PlanService, LLMClient, PrepPlan | createPrepPlan(), generate(), validate(), schedulePrepDay() | SD04 | Facade, Adapter |
| F06 | Pantry inventory and freshness tracking | Deterministic | UC06, UC15 | PantryShoppingView, MealtonFacade, PantryInventory, PantryRepository, FreshnessScheduler, PantryItem | addItem(), removeItems(), checkFreshness(), notifyExpiring() | SD05 | Observer, Repository |
| F07 | Recipes from current pantry | Hybrid | UC07 | RecipeBrowserView, MealtonFacade, RecipeRecommender, RankingStrategy (3 concrete), PantryInventory | selectStrategy(), recommend(), rank(), explain() | SD06 | Strategy, Observer, Facade |
| F08 | Missing-ingredient suggestions to shopping list | Hybrid | UC08 | RecipeRecommender, ShoppingService, CommandHistory, AddShoppingItemCommand | recommendWithMissing(), addMissing(), execute() | SD06 | Strategy, Command |
| F09 | Recipe filters and AI tagging | Hybrid | UC09 | RecipeBrowserView, RecipeService, LLMClient, RecipeRepository | filter(), autoTag(), generate() | SD06 | Adapter, Facade |
| F10 | Serving scaling and unit conversion | Deterministic | UC10 | RecipeBrowserView, RecipeService, UnitConverter, Recipe | scaleRecipe(), scale(), convert() | SD07 | Facade |
| F11 | Nutrition breakdown | Hybrid | UC11 | NutritionService, NutritionProvider, UsdaNutritionAdapter, LLMClient, NutritionInfo | getNutrition(), lookup(), generate() | SD07 | Adapter |
| F12 | Shopping list, categories, check-off, clear, undo | Hybrid | UC12 | PantryShoppingView, ShoppingService, CommandHistory, Add/Delete/ToggleItemCommand, ShoppingList, ShoppingItem, ItemState, PendingState, CheckedState | addItem(), toggle(), clearChecked(), deleteItems(), undo(), suggestCategories(), getStyle() | SD08 | Command, State |
| F13 | Rate recipes, My/Saved recipes | Deterministic | UC13 | RecipeBrowserView, RecipeService, RecipeRepository, Recipe | rateRecipe(), saveRecipe(), createUserRecipe(), update() | SD07 | Repository, Facade |
| F14 | Mealton chat agent | AI | UC14 | MealtonChatView, MealtonFacade, MealtonAgent, AgentMemory, LLMClient, ToolRegistry, ToolFactory + 5 concrete tools | chat(), run(), getRecentContext(), addTurn(), execute() | SD09 | Factory Method, Adapter, Facade |

**Pattern coverage:** Strategy (F07, F08) · Observer (F02, F03, F04, F06, F07) · Command (F03, F08, F12) · Factory Method (F02, F14) · Adapter (F02, F05, F09, F11, F14) · Facade (all) · Composite (F02, F03, F04) · State (F12).

---

# 5. How Each Feature Is Realized

### F01 — Profile and Dietary Constraints
**Use case:** UC01 · **Sequence diagram:** SD01
**Classes:** `ProfileView` (collects form data), `MealtonFacade` (entry point), `ProfileService` (validation and calorie target), `UserRepository` (persistence), `Profile` (data).
**Methods:** `ProfileView.onSaveProfile()`, `MealtonFacade.saveProfile()`, `ProfileService.saveProfile()`, `ProfileService.calculateCalorieTarget()`.
**Execution:** On Save, the view passes the form to the facade, which forwards it to `ProfileService`. The service validates ranges, computes the target with Mifflin–St Jeor (adjusted for the goal and floored at a safe minimum), and stores the `Profile` through `UserRepository`. The target is returned and shown. Allergies saved here are later used by `PlanValidator`.

### F02 — AI Weekly Meal Plan
**Use case:** UC02 · **Sequence diagram:** SD02
**Classes:** `CalendarView`, `MealtonFacade`, `PlanService`, `MealtonAgent`, `LLMClient` (`ClaudeAdapter`), `ToolRegistry`, `RecipeSearchTool`, `PantryTool`, `PlanValidator`, `WeekPlan`/`DayPlan`/`MealEntry`.
**Methods:** `CalendarView.onPlanWeekClicked()`, `MealtonFacade.generateWeekPlan()`, `PlanService.generateWeekPlan()`, `MealtonAgent.generatePlan()`, `LLMClient.chat()`, `Tool.execute()`, `PlanValidator.validate()`.
**Execution:** The facade passes the request to `PlanService`, which hands it to `MealtonAgent`. The agent loops: it sends the context and tool schemas to the LLM, executes requested tools via the registry (reading the pantry, searching recipes), and returns the observations to the LLM until a draft `WeekPlan` is produced. `PlanService` validates the draft against allergies and calorie bounds; on violations it asks the agent to re-plan once. A valid plan is saved, observers (`MacroTracker`, `CalendarView`) are notified, and the calendar shows it.

### F03 — Meal Calendar Editing and Custom Labels
**Use case:** UC03 · **Sequence diagram:** SD03
**Classes:** `CalendarView`, `MealtonFacade`, `PlanService`, `AddMealCommand`/`RemoveMealCommand`, `CommandHistory`, `DayPlan`, `WeekPlan`, `MealLabel`.
**Methods:** `CalendarView.onAddMealClicked()`, `MealtonFacade.addMeal()`, `PlanService.addMeal()`, `PlanService.createLabel()`, `CommandHistory.execute()`/`undo()`, `DayPlan.add()`.
**Execution:** The service wraps the edit in an `AddMealCommand` and runs it through `CommandHistory`, which makes it undoable. The command adds a `MealEntry` to the `DayPlan` and the plan notifies its observers. Custom labels are created on demand as `MealLabel(isCustom = true)`.

### F04 — Macro Tracker
**Use case:** UC04 · **Sequence diagram:** SD03
**Classes:** `MacroTracker`, `PlanObserver`, `WeekPlan`/`DayPlan`/`MealEntry` (`PlanComponent`), `CalendarView`, `NutritionInfo`, `MacroSummary`.
**Methods:** `MacroTracker.onPlanChanged()`, `PlanComponent.getMacros()`, `MacroTracker.getDailySummary()`, `CalendarView.refreshMacros()`.
**Execution:** `MacroTracker` observes the plan. On every change it asks the composite for totals (each level sums its children), compares them with the `Profile` targets, and the calendar redraws the progress bars.

### F05 — Prep-Day Planner
**Use case:** UC05 · **Sequence diagram:** SD04
**Classes:** `CalendarView`, `MealtonFacade`, `PrepPlanner`, `PlanService`, `LLMClient`, `PrepPlan`, `PrepStep`.
**Methods:** `CalendarView.onPrepDayClicked()`, `MealtonFacade.createPrepPlan()`, `PrepPlanner.createPrepPlan()`, `LLMClient.generate()`, `PrepPlan.validate()`, `PlanService.schedulePrepDay()`.
**Execution:** The planner extracts the recipes from the week plan, asks the LLM to produce an ordered, parallelised task schedule, parses it into a `PrepPlan`, and checks that total duration fits the limit. If not, it retries with reduced scope. The result is added to the calendar.

### F06 — Pantry Inventory and Freshness Tracking
**Use case:** UC06, UC15 · **Sequence diagram:** SD05
**Classes:** `PantryShoppingView`, `MealtonFacade`, `PantryInventory` (subject), `PantryRepository`, `FreshnessScheduler`, `PantryObserver`, `PantryItem`.
**Methods:** `PantryInventory.addItem()`, `removeItems()`, `checkFreshness()`, `notifyExpiring()`, `FreshnessScheduler.tick()`, `PantryItem.daysLeft()`.
**Execution:** CRUD operations are saved through the repository and observers are notified. Once a day the scheduler calls `checkFreshness()`, which notifies observers about items with 3 days or fewer left. The view shows an alert and `RecipeRecommender` boosts recipes that use them.

### F07 — Recipes From Current Pantry
**Use case:** UC07 · **Sequence diagram:** SD06
**Classes:** `RecipeBrowserView`, `MealtonFacade`, `RecipeRecommender`, `RankingStrategy` + `PantryMatchStrategy`/`SpoilageUrgencyStrategy`/`MacroFitStrategy`, `PantryInventory`, `RecipeService`, `RecipeMatch`.
**Methods:** `MealtonFacade.recommendRecipes()`, `RecipeRecommender.selectStrategy()`, `recommend()`, `RankingStrategy.rank()`, `RecipeRecommender.explain()`.
**Execution:** The facade selects the strategy chosen by the user and calls `recommend()`. The recommender gets the pantry, filters recipes (excluding allergens), delegates scoring to the strategy, and asks the LLM for a one-line reason for the top results.

### F08 — Missing-Ingredient Suggestions
**Use case:** UC08 · **Sequence diagram:** SD06
**Classes:** `RecipeRecommender`, `RecipeMatch`, `ShoppingService`, `CommandHistory`, `AddShoppingItemCommand`.
**Methods:** `RecipeRecommender.recommendWithMissing()`, `MealtonFacade.addMissingToShopping()`, `ShoppingService.addMissing()`.
**Execution:** Missing items are computed by comparing a recipe's ingredients with the pantry. Clicking "Add missing" creates one `AddShoppingItemCommand` per item (merging duplicates), so the whole action can be undone.

### F09 — Recipe Filters and AI Tagging
**Use case:** UC09 · **Sequence diagram:** SD06
**Classes:** `RecipeBrowserView`, `RecipeService`, `RecipeRepository`, `LLMClient`.
**Methods:** `MealtonFacade.filterRecipes()`, `RecipeService.filter()`, `RecipeService.autoTag()`.
**Execution:** Selected chips become filters applied by `RecipeService` over stored tags. When a recipe is created or imported without tags, `autoTag()` asks the LLM for tags from a fixed vocabulary plus free tags, and stores them.

### F10 — Serving Scaling and Unit Conversion
**Use case:** UC10 · **Sequence diagram:** SD07
**Classes:** `RecipeBrowserView`, `RecipeService`, `UnitConverter`, `Recipe`, `RecipeIngredient`.
**Methods:** `MealtonFacade.scaleRecipe()`, `RecipeService.scale()`, `Recipe.scaledTo()`, `UnitConverter.convert()`.
**Execution:** Each ingredient quantity is multiplied by `newServings / baseServings`, then converted to the display unit via ingredient-specific density tables (grams ⇄ cups).

### F11 — Nutrition Breakdown
**Use case:** UC11 · **Sequence diagram:** SD07
**Classes:** `NutritionService`, `NutritionProvider`, `UsdaNutritionAdapter`, `LLMClient`, `NutritionInfo`.
**Methods:** `MealtonFacade.getNutrition()`, `NutritionService.getNutrition()`, `NutritionProvider.lookup()`, `NutritionInfo.plus()`.
**Execution:** For each ingredient the service queries the provider; results are scaled by quantity and summed. Unknown ingredients are estimated by the LLM and flagged "estimated".

### F12 — Shopping List
**Use case:** UC12 · **Sequence diagram:** SD08
**Classes:** `PantryShoppingView`, `ShoppingService`, `CommandHistory`, `AddShoppingItemCommand`, `DeleteItemsCommand`, `ToggleItemCommand`, `ShoppingList`, `ShoppingItem`, `ItemState`, `PendingState`, `CheckedState`.
**Methods:** `ShoppingService.addItem()`, `toggle()`, `deleteItems()`, `clearChecked()`, `undo()`, `suggestCategories()`, `ShoppingItem.toggle()`, `ItemState.getStyle()`.
**Execution:** Every list change is a `Command` executed through `CommandHistory`. Ticking calls `ShoppingItem.toggle()`, which delegates to the current `ItemState` and swaps `PendingState` ↔ `CheckedState`. The view asks `getStyle()` to draw strike-through and dimmed text. "Clear checked" deletes all items in the checked state as one undoable command.

### F13 — Rate, Save, My Recipes
**Use case:** UC13 · **Sequence diagram:** SD07
**Classes:** `RecipeBrowserView`, `RecipeService`, `RecipeRepository`, `Recipe`.
**Methods:** `MealtonFacade.rateRecipe()`, `saveRecipe()`, `RecipeService.rate()`, `save()`, `createUserRecipe()`, `RecipeRepository.update()`.
**Execution:** The service validates the rating (1–5), updates the recipe flags, and persists them. "My Recipes" shows recipes with `isUserCreated = true`; "Saved" shows `isSaved = true`.

### F14 — Mealton Chat Agent
**Use case:** UC14 · **Sequence diagram:** SD09
**Classes:** `MealtonChatView`, `MealtonFacade`, `MealtonAgent`, `AgentMemory`, `LLMClient`, `ToolRegistry`, `ToolFactory` + five concrete factories/tools (`CalendarTool`, `PantryTool`, `RecipeSearchTool`, `NutritionTool`, `ShoppingListTool`).
**Methods:** `MealtonChatView.sendMessage()`, `MealtonFacade.chat()`, `MealtonAgent.run()`, `AgentMemory.getRecentContext()`, `LLMClient.chat()`, `ToolRegistry.get()`, `Tool.execute()`, `AgentMemory.addTurn()`.
**Execution:** The agent loads recent turns and stored preferences, then runs the reasoning/tool loop. Each tool wraps an existing service (e.g. `CalendarTool` → `PlanService`), so agent actions use the same validated code paths as manual actions, and the same observers refresh the UI. A step limit prevents endless loops; destructive tool calls require user confirmation.

---

*End of Stage 1 report.*
