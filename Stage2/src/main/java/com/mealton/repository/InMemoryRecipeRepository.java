package com.mealton.repository;

import com.mealton.recipe.Recipe;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Keeps recipes in memory (they are lost when the program closes). */
public class InMemoryRecipeRepository implements RecipeRepository {
    private final Map<Long, Recipe> recipes = new LinkedHashMap<>();
    private long nextId = 1;

    @Override
    public Recipe save(Recipe recipe) {
        if (recipe.getId() == 0) {
            recipe.setId(nextId++);
        }
        recipes.put(recipe.getId(), recipe);
        return recipe;
    }

    @Override
    public Optional<Recipe> findById(long id) {
        return Optional.ofNullable(recipes.get(id));
    }

    @Override
    public List<Recipe> findAll() {
        return new ArrayList<>(recipes.values());
    }

    @Override
    public void delete(long id) {
        recipes.remove(id);
    }

    @Override
    public Optional<Recipe> findByTitle(String title) {
        if (title == null) {
            return Optional.empty();
        }
        String wanted = title.trim();
        return recipes.values().stream().filter(r -> r.getTitle().equalsIgnoreCase(wanted)).findFirst();
    }
}