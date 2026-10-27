package com.mealton.shopping;

public final class PendingState implements ItemState {

	public static final PendingState INSTANCE = new PendingState();

	private PendingState() {

	}

	@Override
	public void toggle(ShoppingItem item) {
		item.setState(CheckedState.INSTANCE);
	}

	@Override
	public String getStyle() {
		return STYLE_NORMAL;
	}

	@Override
	public boolean isChecked() {
		return false;
	}

}
