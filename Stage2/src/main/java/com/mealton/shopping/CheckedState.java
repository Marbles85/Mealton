package com.mealton.shopping;

public final class CheckedState implements ItemState {

	public static final CheckedState INSTANCE = new CheckedState();

	private CheckedState() {

	}

	@Override
	public void toggle(ShoppingItem item) {
		item.setState(PendingState.INSTANCE);
	}

	@Override
	public String getStyle() {
		return STYLE_CHECKED;
	}

	@Override
	public boolean isChecked() {
		return true;
	}
}
