package com.mealton.recipe;

/**
 * Lists the recipes that use the most ingredients about to spoil first, to cut
 * food waste.
 */
public class UseExpiringFirstStrategy implements RankingStrategy {

	private final BestMatchStrategy tieBreaker = new BestMatchStrategy();

	@Override
	public String name() {
		return "use expiring first";
	}

	@Override
	public int compare(RecipeMatch a, RecipeMatch b) {
		int byExpiring = Integer.compare(b.expiringUsed(), a.expiringUsed()); // more expiring items used first
		if (byExpiring != 0) {
			return byExpiring;
		}
		return tieBreaker.compare(a, b);
	}
}