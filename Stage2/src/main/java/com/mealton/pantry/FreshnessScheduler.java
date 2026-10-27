package com.mealton.pantry;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class FreshnessScheduler {

	private final PantryInventory inventory;
	private ScheduledExecutorService executor;

	public FreshnessScheduler(PantryInventory inventory) {
		this.inventory = inventory;
	}

	public void tick() {
		inventory.checkFreshness();
	}

	public synchronized void start() {
		if (executor != null) {
			return;
		}

		executor = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "freshness-scheduler");
			t.setDaemon(true);
			return t;
		});
		executor.scheduleAtFixedRate(this::tick, 0, 24, TimeUnit.HOURS);
	}

	public synchronized void stop() {
		if (executor != null) {
			executor.shutdownNow();
			executor = null;
		}
	}
}
