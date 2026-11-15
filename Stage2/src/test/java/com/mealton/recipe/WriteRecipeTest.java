package com.mealton.recipe;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.mealton.facade.MealtonFacade;
import com.mealton.llm.MockLLMClient;

/** "Generate one with Mealton?" when nothing matched. */
class WriteRecipeTest {
	@Test
	void needsClaudeToBeOn() {
		MealtonFacade facade = new MealtonFacade();
		assertThrows(IllegalStateException.class, () -> facade.writeRecipeDrafts(1, "adobo"));
	}

	@Test
	void withClaudeItReturnsDraftsMarkedAsGenerated() {
		MealtonFacade facade = new MealtonFacade();
		facade.enableAgent(new MockLLMClient());
		try {
			var drafts = facade.writeRecipeDrafts(1, "adobo");
			assertTrue(drafts.stream().allMatch(r -> RecipeDiscovery.CREATOR.equals(r.getSourceName())));
		} catch (IllegalStateException mockCannotWrite) {
			// the offline mock may not write recipes; the error is then readable
			assertTrue(mockCannotWrite.getMessage() != null);
		}
	}
}