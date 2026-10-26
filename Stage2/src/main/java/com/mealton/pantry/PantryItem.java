package com.mealton.pantry;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class PantryItem {
	private long id;
	private String name;
	private double quantity;
	private String unit;
	private String category;
	private LocalDate expiryDate;

	public PantryItem(String name, double quantity, String unit, String category, LocalDate expiryDate) {
		this.name = requireName(name);
		this.quantity = requireQuantity(quantity);
		this.unit = unit == null ? "" : unit.trim();
		this.category = (category == null || category.isBlank()) ? "Uncategorized" : category.trim();
		this.expiryDate = expiryDate;
	}

	private static String requireName(String name) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Ingredient name must not be empty.");
		}

		return name.trim();
	}

	private static double requireQuantity(double q) {
		if (Double.isNaN(q) || Double.isInfinite(q) || q <= 0) {
			throw new IllegalArgumentException("Quantity must be a positive number.");
		}

		return q;
	}

	public int daysLeft(LocalDate today) {
		if (expiryDate == null) {
			return Integer.MAX_VALUE;
		}
		return (int) ChronoUnit.DAYS.between(today, expiryDate);
	}

	public int daysLeft() {
		return daysLeft(LocalDate.now());
	}

	public Freshness freshness(LocalDate today) {
		if (expiryDate == null) {
			return Freshness.NO_DATE;
		}

		int d = daysLeft(today);
		if (d < 0) {
			return Freshness.EXPIRED;
		}

		return d <= Freshness.USE_SOON_THRESHOLD_DAYS ? Freshness.USE_SOON : Freshness.FRESH;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public double getQuantity() {
		return quantity;
	}

	public void setQuantity(double quantity) {
		this.quantity = requireQuantity(quantity);
	}

	public String getUnit() {
		return unit;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = (category == null || category.isBlank()) ? "Uncategorized" : category.trim();
	}

	public LocalDate getExpiryDate() {
		return expiryDate;
	}

	public void setExpiryDate(LocalDate expiryDate) {
		this.expiryDate = expiryDate;
	}
}
