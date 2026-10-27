package com.mealton.shopping;

public interface ItemState {

	String STYLE_NORMAL = "normal";
	String STYLE_CHECKED = "strikethrough+dimmed";

	void toggle(ShoppingItem item);

	String getStyle();

	boolean isChecked();
}
