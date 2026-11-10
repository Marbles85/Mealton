package com.mealton.recipe;

/** Lists the recipes you are closest to being able to cook first. */
public class BestMatchStrategy implements RankingStrategy {

	@Override
	public String name() {
		return "best match";
	}

	@Override
	public int compare(RecipeMatch a, RecipeMatch b) {
		int byCoverage = Double.compare(b.coverage(), a.coverage()); // higher coverage first
		if (byCoverage != 0) {
			return byCoverage;
		}
		int byRating = Integer.compare(b.recipe().getRating(), a.recipe().getRating()); // better rated first
		if (byRating != 0) {
			return byRating;
		}
		return a.recipe().getTitle().compareToIgnoreCase(b.recipe().getTitle());
	}
}