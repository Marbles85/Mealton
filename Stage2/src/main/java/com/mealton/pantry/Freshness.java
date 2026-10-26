package com.mealton.pantry;

public enum Freshness {
	NO_DATE, FRESH, USE_SOON, EXPIRED;

	public static final int USE_SOON_THRESHOLD_DAYS = 3;
}
