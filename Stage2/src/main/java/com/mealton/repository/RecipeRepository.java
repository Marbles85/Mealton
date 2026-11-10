package com.mealton.repository;

import com.mealton.recipe.Recipe;
import java.util.Optional;

/** Where saved recipes are kept. Same Repository pattern as the pantry, plus a lookup by title. */
public interface RecipeRepository extends Repository<Recipe> {

    /** Finds a recipe by its title, ignoring upper/lower case. */
    Optional<Recipe> findByTitle(String title);
}