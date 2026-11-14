package com.mealton.gui;

import java.util.LinkedHashMap;
import java.util.Map;

import com.mealton.facade.MealtonFacade;
import com.mealton.profile.UserProfile;

import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;

/**
 * Main window: the sidebar on the left, the current page in the middle, and the
 * AI chat floating on top (Stage 1: MainWindow).
 */
public class MainWindow extends BorderPane {
	public static final String RECIPES_PAGE = "Recipes";
	public static final String LOG_PAGE = "Log food";
	public static final String PLAN_PAGE = "Meal plan";
	public static final String PANTRY_PAGE = "Pantry & list";
	public static final String PROFILE_PAGE = "Profile";

	private final Map<String, Node> pages = new LinkedHashMap<>();

	private final MealtonFacade facade;
	private final String username;
	private final Runnable onSave;
	private final UiSettings settings = new UiSettings();
	private final ProfileView profileView;
	private final BorderPane pageHost = new BorderPane();
	private final ChatLayer chatLayer;
	private final Sidebar sidebar;

	/**
	 * @param username the logged-in user
	 * @param onSave   saves the user's data; called whenever the user changes page
	 * @param onLogout called when the user presses "Log out"
	 */
	public MainWindow(MealtonFacade facade, String username, Runnable onSave, Runnable onLogout) {
		this.facade = facade;
		this.username = username;
		this.onSave = onSave;

		PantryShoppingView pantryView = new PantryShoppingView(facade);
		RecipeBrowserView recipesView = new RecipeBrowserView(facade, pantryView::refreshAll);
		MealPlanView planView = new MealPlanView(facade, pantryView::refreshAll);
		FoodDiaryView diaryView = new FoodDiaryView(facade);
		profileView = new ProfileView(facade);
		pages.put(RECIPES_PAGE, recipesView);
		pages.put(LOG_PAGE, diaryView);
		pages.put(PLAN_PAGE, planView);
		pages.put(PANTRY_PAGE, pantryView);
		pages.put(PROFILE_PAGE, profileView);

		// after the AI agent uses a tool, the pages reload what changed
		ChatView chat = new ChatView(facade, () -> {
			pantryView.refreshAll();
			recipesView.refresh();
			planView.refresh();
			diaryView.refresh();
			refreshSidebarInfo();
		}, this::closeChat);
		chatLayer = new ChatLayer(chat, settings, this::chatOpenChanged);
		sidebar = new Sidebar(settings, this::showPage, chatLayer::toggle, () -> {
			commitPending(); // keep anything typed on the Profile page
			onLogout.run();
		});

		setLeft(sidebar);
		setCenter(new StackPane(pageHost, chatLayer));

		settings.addChatModeListener(this::applyChatMode);
		applyChatMode();
		show(RECIPES_PAGE);
	}

	private void closeChat() {
		chatLayer.close();
	}

	private void chatOpenChanged() {
		sidebar.setChatOpen(chatLayer.isOpen());
	}

	private void applyChatMode() {
		chatLayer.setMode(settings.chatMode());
		sidebar.setChatInSidebar(UiSettings.CHAT_SIDEBAR.equals(settings.chatMode()));
	}

	/**
	 * The name in the sidebar, and the "about to expire" number on the Pantry item.
	 */
	private void refreshSidebarInfo() {
		UserProfile profile = facade.getProfile();
		String name = profile.name() == null || profile.name().isBlank() ? username : profile.name();
		sidebar.setUser(name);
		int expiringCount;
		try {
			expiringCount = facade.getExpiringItems().size();
		} catch (RuntimeException e) {
			expiringCount = 0;
		}
		sidebar.setExpiring(expiringCount);
	}

	/**
	 * Applies anything typed but not yet saved (the Profile page's name and goals).
	 * The app calls this before every save, including when the window is closed.
	 */
	public void commitPending() {
		profileView.commitPending();
	}

	/** Shows the page with this name, and saves the user's data first. */
	public void showPage(String name) {
		commitPending();
		onSave.run(); // leaving a page is a good moment to save
		show(name);
	}

	private void show(String name) {
		Node page = pages.get(name);
		if (page != null) {
			// these pages depend on the pantry, recipes and profile, so reload them every
			// time they are opened
			if (page instanceof RecipeBrowserView browser) {
				browser.refresh();
			} else if (page instanceof MealPlanView plan) {
				plan.refresh();
			} else if (page instanceof FoodDiaryView diary) {
				diary.refresh();
			}
			pageHost.setCenter(page);
			sidebar.setActive(name);
			refreshSidebarInfo();
		}
	}
}