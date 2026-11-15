package com.mealton.agent.tools;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.mealton.agent.Tool;
import com.mealton.agent.ToolResult;
import com.mealton.facade.MealtonFacade;
import com.mealton.llm.ToolSpec;
import com.mealton.pantry.PantryItem;
import com.mealton.shopping.ShoppingItem;

/**
 * A one-call summary of the whole app, so the agent can answer "how is my week
 * looking?" without guessing: profile and goals, pantry (and what is about to
 * expire), the shopping list, recipes, this week's meal plan and prep days.
 * Read-only.
 */
public class AppOverviewTool implements Tool {
	private final MealtonFacade facade;

	public AppOverviewTool(MealtonFacade facade) {
		this.facade = facade;
	}

	@Override
	public String name() {
		return "app_overview";
	}

	@Override
	public ToolSpec schema() {
		return new ToolSpec(name(),
				"A summary of everything in the user's Mealton: profile and daily goals, pantry and expiring items, "
						+ "shopping list, recipe counts, the next 7 days of the meal plan and any prep days. "
						+ "Use it first when the user asks a broad question about their situation. Read-only; use the "
						+ "other tools for details.",
				Map.of());
	}

	@Override
	public ToolResult execute(Map<String, String> args) {
		LocalDate today = facade.today();
		StringBuilder sb = new StringBuilder("Mealton overview (today is " + AgentDates.show(today) + ")");
		sb.append("\nProfile: ").append(facade.getProfile().summary());

		List<PantryItem> pantry = facade.getPantryItems();
		List<PantryItem> expiring = facade.getExpiringItems();
		sb.append("\nPantry: ").append(pantry.size()).append(" items");
		if (!expiring.isEmpty()) {
			sb.append("; expiring soon: ");
			for (int i = 0; i < expiring.size() && i < 8; i++) {
				sb.append(i == 0 ? "" : ", ").append(expiring.get(i).getName()).append(" (")
						.append(expiring.get(i).daysLeft()).append(" d)");
			}
		}

		List<ShoppingItem> shopping = facade.getShoppingItems();
		long open = shopping.stream().filter(i -> !i.isChecked()).count();
		sb.append("\nShopping list: ").append(open).append(" to buy, ").append(shopping.size() - open)
				.append(" ticked");

		sb.append("\nRecipes: ").append(facade.getRecipes().size()).append(" in total, ")
				.append(facade.getMyRecipes().size()).append(" in My recipes");

		int planned = 0;
		StringBuilder week = new StringBuilder();
		for (int i = 0; i < 7; i++) {
			LocalDate d = today.plusDays(i);
			var day = facade.getPlannedMeals(d);
			planned += day.size();
			week.append("\n  ").append(AgentDates.show(d)).append(": ");
			if (day.isEmpty()) {
				week.append("nothing planned");
			}
			for (int k = 0; k < day.size(); k++) {
				week.append(k == 0 ? "" : "; ").append(day.get(k).tag().isEmpty() ? "" : day.get(k).tag() + " ")
						.append(day.get(k).recipe().getTitle());
			}
			if (facade.getPrepDay(d) != null) {
				week.append(" [PREP DAY ").append(facade.getPrepDay(d).start()).append("]");
			}
		}
		sb.append("\nMeal plan, next 7 days (").append(planned).append(" meals):").append(week);
		return ToolResult.ok(sb.toString());
	}
}