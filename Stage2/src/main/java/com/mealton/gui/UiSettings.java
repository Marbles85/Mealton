package com.mealton.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Small look-and-feel settings kept on this computer (not part of any account's
 * data): where the AI chat lives, whether the sidebar is collapsed, where the
 * chat bubble was dropped, and "Remember me" (the username and a random token,
 * never the password).
 */
public class UiSettings {
	public static final String CHAT_ROAM = "roam"; // a draggable bubble
	public static final String CHAT_SIDEBAR = "sidebar"; // an "Ask Mealton" tab in the sidebar

	private static final Preferences PREFS = Preferences.userNodeForPackage(UiSettings.class);

	private final List<Runnable> chatModeListeners = new ArrayList<>();

	private static String get(String key, String fallback) {
		try {
			return PREFS.get(key, fallback);
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	private static void put(String key, String value) {
		try {
			if (value == null) {
				PREFS.remove(key);
			} else {
				PREFS.put(key, value);
			}
		} catch (RuntimeException e) {
			// settings are a convenience; the app works without them
		}
	}

	// ---- chat mode ----
	public String chatMode() {
		return CHAT_SIDEBAR.equals(get("chatMode", CHAT_ROAM)) ? CHAT_SIDEBAR : CHAT_ROAM;
	}

	public void setChatMode(String mode) {
		put("chatMode", CHAT_SIDEBAR.equals(mode) ? CHAT_SIDEBAR : CHAT_ROAM);
		chatModeListeners.forEach(Runnable::run);
	}

	/**
	 * Called whenever the chat mode changes (for example from the Profile page).
	 */
	public void addChatModeListener(Runnable listener) {
		chatModeListeners.add(listener);
	}

	// ---- sidebar ----
	public boolean sidebarHidden() {
		return "true".equals(get("sidebarHidden", "false"));
	}

	public void setSidebarHidden(boolean hidden) {
		put("sidebarHidden", String.valueOf(hidden));
	}

	// ---- chat bubble position ----
	public boolean hasBubblePosition() {
		return get("bubbleSide", null) != null;
	}

	/** "left" or "right". */
	public String bubbleSide() {
		return "left".equals(get("bubbleSide", "right")) ? "left" : "right";
	}

	/** Distance from the top, or NaN if the bubble was never moved. */
	public double bubbleY() {
		try {
			return Double.parseDouble(get("bubbleY", "NaN"));
		} catch (NumberFormatException e) {
			return Double.NaN;
		}
	}

	public void setBubblePosition(String side, double y) {
		put("bubbleSide", side);
		put("bubbleY", String.valueOf(y));
	}

	// ---- remember me ----
	public static String rememberedUser() {
		return get("rememberedUser", "");
	}

	/**
	 * The random "Remember me" token (see AccountService.createRememberToken), or
	 * "" if there is none.
	 */
	public static String rememberedToken() {
		return get("rememberedToken", "");
	}

	public static void remember(String username, String token) {
		put("rememberedUser", username);
		put("rememberedToken", token);
	}

	public static void forgetRemembered() {
		put("rememberedUser", null);
		put("rememberedToken", null);
	}
}