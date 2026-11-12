# Mealton: AI Smart Recipe and Meal Planning Agent (Stage 2)

EECS 3311, Fall 2026. Java implementation of the Mealton design from Stage 1. It has a **command-line interface (CLI)**, a **JavaFX graphical interface (GUI)** and an **AI chat agent** (Claude) that can use the app's features as tools.

## What it does

| ID  | Feature | Where to use it |
|-----|---------|-----------------|
| F06 | Pantry with freshness / expiry alerts | CLI `pantry ...`, GUI "Pantry & List", chat |
| F12 | Shopping list with undo / redo | CLI `shop ...`, GUI "Pantry & List", chat |
| F13 | Recipes: save, rate, list, scale | CLI `recipe ...`, chat |
| F07 | Recipe ideas ranked against the pantry | CLI `recipe suggest`, chat |
| F08 | Missing ingredients go to the shopping list | CLI `recipe missing`, chat |
| F10 | Unit conversion and serving scaling | CLI `convert`, `scale`, chat |
| F01 | Profile: diet, allergies, calorie and macro goals (recipes show warnings) | CLI `profile ...`, chat |
| F04 | Food diary / macro tracker compared with profile goals | CLI `food ...`, chat |
| F02 | Weekly meal plan (variety or pantry-first) | CLI `plan ...`, chat |
| F14 | AI chat agent with tool use and memory | CLI `chat`, GUI "Mealton Chat" |

Stage 1 features that are **not** implemented are listed, with reasons, in the Stage 2 report.

## Requirements

- Java 17 or newer (JDK)
- Maven 3.8+ (Eclipse includes one: m2e)
- Internet access the first time (Maven downloads JavaFX, Jackson and JUnit)
- Optional: an Anthropic API key for the real AI. Without one the app still runs; only the AI chat is off.

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
```

3. `.env` is listed in `.gitignore`. Never commit it, never share it, and never show it in a recording.

Settings:

| Setting | Meaning |
|---------|---------|
| `LLM_PROVIDER` | `claude` for the real Claude API, or `mock` (default) for an offline stand-in with no real AI |
| `LLM_MODEL` | Claude model name (default `claude-haiku-5-5`) |
| `LLM_API_KEY` | your Anthropic API key (needed for `claude`) |

Real environment variables with the same names override `.env`. If the key is missing or wrong, the app prints "AI assistant is OFF: ..." and everything except chat keeps working.

## Launch

**CLI** (Eclipse): right-click `src/main/java/com/mealton/Main.java` → Run As → Java Application. Use the Console tab. On startup it prints `AI assistant ready (claude, ...)`.

**GUI** (Eclipse): Run → Run Configurations → Maven Build → New. Base directory: `${workspace_loc:/Stage2}` (use your project name). Goals: `javafx:run`. Run.

From a terminal in `Stage2`: GUI with `mvn javafx:run`; CLI with `mvn compile exec:java`.

## Using the CLI

Type `help` for the full list. All data is kept in memory while the program runs.

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

- **Pantry & List**: add and remove pantry items, see expiry colours, manage the shopping list with undo/redo.
- **Mealton Chat**: talk to the AI. Changes it makes appear in the other page straight away.
- Calendar, Recipes and Profile pages are not built; the same features are available through chat and the CLI.

## Run the tests

In Eclipse: right-click `src/test/java` → Run As → JUnit Test. Or `mvn test` in `Stage2`. The tests never call the real AI; they use a scripted mock and a local fake server.

## Design patterns used

Facade/MVC (`MealtonFacade`), Command (shopping undo/redo), State (shopping item), Observer (pantry alerts, profile changes), Repository (pantry, recipes), Adapter (`LLMClient`, `ClaudeAdapter`), Factory (`LLMClientFactory`), Strategy (recipe ranking, meal planning), Composite (`WeekPlan`, `DayPlan`, `MealEntry`).

## Project layout (`src/main/java/com/mealton`)

`pantry`, `shopping`, `command`, `recipe`, `profile`, `nutrition`, `plan`, `repository` (data and logic) · `facade` (single entry point) · `cli`, `gui` (user interfaces) · `llm`, `agent`, `agent/tools` (AI) · `config`, `util`.

## Security notes

- API keys live only in the local `.env` (gitignored). `.env.example` contains a placeholder only.
- The app never prints the key. The usage summary shows token counts only.
- If a key is ever committed by mistake, revoke it in the Anthropic Console immediately and remove it from the git history.

## Known limitations

- Data is not saved between runs (in-memory only).
- The profile check uses ingredient words (for example "chicken is not vegetarian"). It is a helpful warning, not a safety guarantee, and does not judge gluten-free or keto.
- Food values come from a small built-in table of typical figures; other foods are AI estimates and are marked "(estimate)".
- When two planned recipes share an ingredient, the shopping list gets the larger amount, not the sum.