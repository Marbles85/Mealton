# Mealton: AI Smart Recipe and Meal Planning Agent

**EECS3311 Fall 2026 course project** · _[Rebekah Uson - 221123310]_

Mealton is an AI agent that helps people decide what to eat. It plans a week of meals around allergies, calorie and macro goals and what is already in the pantry, schedules a batch-cooking prep day, suggests recipes that use up food before it spoils, and builds the shopping list. It works through a GUI and a command line, and you can ask Mealton in plain language to make changes.

## Project outline

| Stage | What it covers | Status |
|---|---|---|
| **Stage 1: Design** | Project description, feature specifications, UML diagrams, design patterns, and feature-to-design traceability | **Complete: [read the Stage 1 report](Stage1/Stage1_Report.md)** |
| **Stage 2: Implementation** | Working Java application (GUI, command line and AI chat agent), tests, report, and demo videos | **Complete: [read the Stage 2 report](Stage2/Stage2_Report.md)** |
| Stage 3 | Coming soon | Not started |

## Stage 1 at a glance

- **Features:** 14, including AI meal planning, a prep-day planner, pantry and freshness tracking, recipe recommendations, a shopping list, and the Mealton chat agent.
- **Design patterns:** Strategy, Observer, Command, Factory Method, Adapter, Facade, Composite and State.
- **Diagrams:** class, use-case and sequence diagrams are in the [`diagrams/`](Stage1/diagrams/) folder and in the [Stage 1 report](Stage1/Stage1_Report.md).

## Stage 2 at a glance

- **Application:** Java 17, Maven and JavaFX, with a GUI, a command-line version, and the Mealton chat agent (Claude). Recipes come from TheMealDB and Spoonacular.
- **Report:** [`Stage2/Stage2_Report.md`](Stage2/Stage2_Report.md) has the feature map, design patterns and UML, changes from Stage 1, the AI usage log, testing, and the demo timestamps.
- **Set up and run:** [`Stage2/README.md`](Stage2/README.md)
- **API key template:** [`Stage2/.env.example`](Stage2/.env.example) (placeholders only; the real `.env` is never committed)
- **Demo videos:** [GUI demo](https://youtu.be/rCZ-0A1Pc_M) and [CLI demo](https://youtu.be/7llMMoYiqjQ)
