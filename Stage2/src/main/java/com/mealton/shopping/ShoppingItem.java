package com.mealton.shopping;

public class ShoppingItem {

	private long id;
	private final String name;
	private double quantity;
	private final String unit;
	private String category;
	private ItemState state = PendingState.INSTANCE;

	public ShoppingItem(String name, double quantity, String unit, String category) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Item name must not be empty.");
		}

		if (Double.isNaN(quantity) || Double.isInfinite(quantity) || quantity <= 0) {
			throw new IllegalArgumentException("Quantity must be a positive number.");
		}

		this.name = name.trim();
		this.quantity = quantity;
		this.unit = unit == null ? "" : unit.trim();
		this.category = (category == null || category.isBlank()) ? "Uncategorized" : category.trim();
	}

	public void toggle() {
		state.toggle(this);
	}

	public void setState(ItemState state) {
		this.state = state;
	}

	public String getStyle() {
		return state.getStyle();
	}

	public boolean isChecked() {
		return state.isChecked();
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public double getQuantity() {
		return quantity;
	}

	public void addtQuantity(double delta) {
		this.quantity += delta;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = (category == null || category.isBlank()) ? "Uncategorized" : category.trim();
	}

	public String getName() {
		return name;
	}

	public String getUnit() {
		return unit;
	}

}
