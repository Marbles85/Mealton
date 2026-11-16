# Mealton: AI Smart Recipe and Meal Planning Agent (Stage 2)

EECS 3311, Fall 2026. Java implementation of the Mealton design from Stage 1. It has a **command-line interface (CLI)**, a **JavaFX graphical interface (GUI)** and an **AI chat agent** (Claude) that can use the app's features as tools.

## What it does

| ID  | Feature | Where to use it |
|-----|---------|-----------------|
| UC00 | Accounts: sign up / log in, your data is saved per user | GUI login screen, CLI login prompt |
| F01 | Profile: diet, allergies, calorie and macro goals (recipes show warnings) | GUI "Profile", CLI `profile ...`, chat |
| F02 | AI meal plan for any dates (variety or pantry-first, cuisines, preview, rearrange, apply) | GUI "Meal plan" > Plan meals, CLI `plan ...`, chat |
| F03 | Meal calendar with custom labels, move, swap, undo, and Edit week (select, select all, delete, drag and drop) | GUI "Meal plan", CLI `calendar ...`, chat |
| F04 | Food diary / macro tracker compared with profile goals | GUI "Log food", CLI `food ...`, chat |
| F05 | Prep-day planner | GUI "Meal plan" > Prep day, CLI `plan prep`, chat |
| F06 | Pantry with freshness / expiry alerts | GUI "Pantry & list", CLI `pantry ...`, chat |
| F07 | Recipe ideas ranked against the pantry (Can make now, Almost there) | GUI "Recipes", CLI `recipe now/almost/suggest`, chat |
| F08 | Missing ingredients go to the shopping list | GUI button, CLI `recipe missing`, chat |
| F09 | Recipe filters, search (typos and plurals are fine) and tags | GUI "Recipes", CLI `recipe tags`, chat |
| F10 | Unit conversion and serving scaling | GUI recipe page, CLI `convert`, `scale`, chat |
| F11 | Nutrition breakdown (built-in table of about 370 foods; an AI estimate only when you press the estimate button) | GUI recipe page, CLI `recipe nutrition`, chat |
| F12 | Shopping list with undo / redo | GUI "Pantry & list", CLI `shop ...`, chat |
| F13 | Recipes: rate, save (heart), My Recipes, edit saved recipes, import from a link | GUI "Recipes", CLI `recipe ...`, chat |
| F14 | AI chat agent with tool use and memory | GUI "Ask Mealton", CLI `chat` |

The Stage 2 report has the full feature-to-code map.

Differences from the Stage 1 plan, and known limitations, are listed in the Stage 2 report.

## Requirements

- Java 17 or newer (JDK)
- Maven 3.8+ (Eclipse includes one: m2e)
- Internet access the first time (Maven downloads JavaFX, Jackson and JUnit)
- Optional: an Anthropic API key for the real AI. Without one the app still runs; only the AI chat and "Generate with Mealton" are off.
- Optional: a free Spoonacular key for more recipes. Without one, recipes come from TheMealDB only (no key needed).

## Install

1. Clone the repository: `git clone https://github.com/Marbles85/Mealton.git`
2. In Eclipse: **File → Import → Maven → Existing Maven Projects**, choose the `Stage2` folder, Finish.
3. Wait for Maven to finish downloading dependencies (bottom-right progress bar).

## Configure the AI (API key)

The API key is kept **only** in a local file that is never committed.

1. In the `Stage2` folder, copy `.env.example` to a new file named `.env`.
2. Edit `.env`:

```
LLM_PROVIDER=claude
LLM_MODEL=claude-haiku-5-5
LLM_API_KEY=paste-your-own-key-here
SPOONACULAR_API_KEY=paste-your-own-key-here
```

3. `.env` is listed in `.gitignore`. Never commit it, never share it, and never show it in a recording.

Settings:

| Setting | Meaning |
|---------|---------|
| `LLM_PROVIDER` | `claude` for the real Claude API, or `mock` (default) for an offline stand-in with no real AI |
| `LLM_MODEL` | Claude model name (default `claude-haiku-5-5`) |
| `LLM_API_KEY` | your Anthropic API key (needed for `claude`) |
| `SPOONACULAR_API_KEY` | optional; free key from https://spoonacular.com/food-api. Its free plan has a daily limit; when it is used up the app says so and uses TheMealDB only |

Real environment variables with the same names override `.env`. If the key is missing or wrong, the app prints "AI assistant is OFF: ..." and everything except chat keeps working.

## Launch

**CLI** (Eclipse): right-click `src/main/java/com/mealton/Main.java` → Run As → Java Application. Use the Console tab. On startup it prints `AI assistant ready (claude, ...)`.

**GUI** (Eclipse): Run → Run Configurations → Maven Build → New. Base directory: `${workspace_loc:/Stage2}` (use your project name). Goals: `javafx:run`. Run.

From a terminal in `Stage2`: GUI with `mvn javafx:run`; CLI with `mvn compile exec:java`.

## Using the CLI

Type `help` for the full list. Your data is saved to your account file when you log out or exit (the GUI also saves as you work).

```
pantry add milk 1 l --expires 2026-11-20
pantry list | expiring | check | remove <id>
shop add eggs 12 | shop list | shop check <id> | shop undo | shop redo
recipe add "Pancakes" 2 "200 g flour; 2 egg; 0.3 l milk" "Mix|Fry"
recipe list | show <id> | rate <id> 5 | scale <id> 4
recipe suggest [best|expiring] | recipe check <id> | recipe missing <id>
profile diet vegetarian | profile allergy add peanut | profile calories 2000
profile macros 120 200 60 | profile | profile reset
food log egg 3 | food log chicken 200 g | food custom "pizza slice" 285 12 36 10
food today | food list | food delete <id>
plan generate variety dinner | plan generate pantry lunch,dinner
plan | plan set fri dinner 2 | plan clear mon lunch | plan check | plan shop
convert 2 cup ml | scale 200 2 5
chat | chat <message> | exit
```

Ingredient lines in `recipe add` use `<amount> <unit> <name>`; known units are g kg oz lb ml l tsp tbsp cup (anything else is part of the name, so write `2 bread`, not `2 slice bread`).

### Examples of the AI chat

```
chat what is in my pantry that will expire soon?
chat I'm vegan now, and allergic to peanuts
chat I had a slice of pizza and 2 eggs for lunch
chat plan my dinners for the week and tell me what I need to buy
```

The reply ends with `[tools used: ...]`, which shows which features the AI used.

## Using the GUI

Log in or create an account (an email address is used as the username). Your data is saved per user automatically as you work, and again when you log out or close the app.

- **Recipes**: four tabs (Can make now, Almost there, All recipes, My Recipes), 30 recipes per page. Search is forgiving (capitals, spaces, plurals and typos are fine). Filter chips can be stacked, and a **Mealton generated** chip shows recipes the AI wrote or edited. **Refresh** and the search bar look for new recipes in TheMealDB and Spoonacular (no AI tokens). The **Generate with Mealton** button at the bottom asks Claude to write one recipe; it is saved to My Recipes. Click a recipe to see steps, nutrition, scaling, the heart (save), and Edit (for saved recipes).
- **Log food**: log meals and see macro bars against your goals.
- **Meal plan**: the calendar, **Plan meals** (choose dates, meals, cuisines, then preview, drag to rearrange, copy a meal with the Duplicate toggle, remove a meal with the ✕ button, lock the ones you like and regenerate the rest, which also fills any empty meal, then apply). **Also look for new recipes online** adds extra recipes from TheMealDB and Spoonacular before planning. **Edit week** on the calendar lets you tick meals (or a whole day), select all, delete and drag meals around. **Prep day** schedules the cooking and shows what to buy; it remembers your start time and time limit.
- **Pantry & list**: pantry with expiry colours; shopping list with undo / redo. Click a category's coloured dot (or Tick all) to tick everything in it, and clear the ticked items to move them into your pantry.
- **Profile**: diet, allergies, goals.
- **Ask Mealton**: chat with the AI; changes it makes appear on the other pages straight away.

Some recipes loaded from the web can come through incomplete or glitched. Each one has a "View original" link for the most accurate version.

### AI cost

Only these use Claude: the chat, the Generate with Mealton button, the nutrition estimate button (for a recipe whose ingredients the built-in table does not know), import-from-link tidy-up and the prep-day text. Searching, filtering and Refresh never do. Every Claude call prints a line in the console, for example `[Mealton] Claude call from ...` followed by `Claude replied: N input / N output tokens`, so you can see exactly what is used.

## Run the tests

In Eclipse: right-click `src/test/java` → Run As → JUnit Test. Or `mvn test` in `Stage2`. The tests never call the real AI; they use a scripted mock and a local fake server.

## Design patterns used

Facade/MVC (`MealtonFacade`), Command (shopping and calendar undo/redo), State (shopping item), Observer (pantry alerts, profile changes), Repository (pantry, recipes, saved user data), Adapter (`LLMClient`, `ClaudeAdapter`, the recipe web APIs), Factory (`LLMClientFactory`), Strategy (recipe ranking, meal planning, `RecipeSource`), Composite (`WeekPlan`, `DayPlan`, `MealEntry`).

## Project layout (`src/main/java/com/mealton`)

`pantry`, `shopping`, `command`, `recipe`, `profile`, `nutrition`, `diary`, `plan`, `repository`, `persistence`, `account` (data and logic) · `facade` (single entry point) · `cli`, `gui` (user interfaces) · `llm`, `agent`, `agent/tools` (AI) · `config`, `util`.

## Security notes

- API keys live only in the local `.env` (gitignored). `.env.example` contains a placeholder only.
- The app never prints the key. The usage summary shows token counts only.
- If a key is ever committed by mistake, revoke it in the Anthropic Console immediately and remove it from the git history.

## Known limitations

- Spoonacular's free plan has a daily limit; the app reports it and keeps working with TheMealDB.
- Recipes loaded from the web can be incomplete or glitched; check the original link.
- The profile check uses ingredient words (for example "chicken is not vegetarian"). It is a helpful warning, not a safety guarantee, and does not judge gluten-free or keto.
- Food values come from a built-in table of about 370 typical foods. Recipes with ingredients the table does not know show "Nutrition incomplete" until you press the estimate button, and those AI estimates are marked "(estimate)".
- When two planned recipes share an ingredient, the shopping list gets the larger amount, not the sum.
- A few GUI conveniences (for example month-view drag and drop) have no CLI equivalent.
