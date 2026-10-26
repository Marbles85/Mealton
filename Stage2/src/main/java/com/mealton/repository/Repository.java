package com.mealton.repository;

import java.util.List;
import java.util.Optional;

public interface Repository<T> {

	T save(T entity);

	Optional<T> findById(long id);

	List<T> findAll();

	void delete(long id);
}
