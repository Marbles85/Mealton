package com.mealton.gui;

import com.mealton.agent.AgentReply;
import com.mealton.facade.MealtonFacade;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The "Mealton Chat" page (F14). The user types a message, the AI agent works
 * on it in a background thread (so the window never freezes) and the answer
 * appears as a chat bubble, together with the names of the tools the agent
 * used.
 */
public class ChatView extends BorderPane {

	private static final double BUBBLE_MAX_WIDTH = 560;

	private final MealtonFacade facade;
	private final Runnable onDataChanged; // lets other pages refresh after the agent changes data
	private final VBox messages = new VBox(10);
	private final ScrollPane scroll = new ScrollPane(messages);
	private final TextField input = new TextField();
	private final Button sendButton = new Button("Send");

	public ChatView(MealtonFacade facade, Runnable onDataChanged) {
		this.facade = facade;
		this.onDataChanged = onDataChanged;

		messages.setPadding(new Insets(14));
		scroll.setFitToWidth(true);
		scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		// always scroll down to the newest message
		messages.heightProperty().addListener((obs, oldH, newH) -> scroll.setVvalue(1.0));
		setCenter(scroll);

		input.setPromptText("Ask Mealton, e.g. \"What is expiring soon?\" or \"Add pasta to my list\"");
		HBox.setHgrow(input, Priority.ALWAYS);
		input.setOnAction(e -> onSend());
		sendButton.setDefaultButton(false);
		sendButton.setOnAction(e -> onSend());
		HBox bar = new HBox(8, input, sendButton);
		bar.setPadding(new Insets(10, 14, 10, 14));
		bar.setStyle("-fx-border-color: #dddddd; -fx-border-width: 1 0 0 0;");
		setBottom(bar);

		if (facade.hasAgent()) {
			addBubble("Hi! I'm Mealton. I can look at your pantry and shopping list, and add things for you.", false);
		} else {
			addBubble("The AI assistant is switched off. Check LLM_PROVIDER and LLM_API_KEY in your local .env file, "
					+ "then restart the app.", false);
			input.setDisable(true);
			sendButton.setDisable(true);
		}
	}

	/** Sends the typed message to the agent without blocking the window. */
	private void onSend() {
		String text = input.getText() == null ? "" : input.getText().trim();
		if (text.isEmpty() || input.isDisabled()) {
			return;
		}
		input.clear();
		addBubble(text, true);
		Label thinking = addNote("Mealton is thinking...");
		setBusy(true);

		Task<AgentReply> task = new Task<>() {
			@Override
			protected AgentReply call() {
				return facade.chat(text);
			}
		};
		task.setOnSucceeded(e -> {
			messages.getChildren().remove(thinking);
			AgentReply reply = task.getValue();
			addBubble(reply.text(), false);
			if (!reply.toolsUsed().isEmpty()) {
				addNote("Tools used: " + String.join(", ", reply.toolsUsed()));
				onDataChanged.run(); // the agent may have changed the pantry or the list
			}
			setBusy(false);
		});
		task.setOnFailed(e -> {
			messages.getChildren().remove(thinking);
			Throwable problem = task.getException();
			addBubble("Sorry, something went wrong: " + (problem == null ? "unknown error" : problem.getMessage()),
					false);
			setBusy(false);
		});
		Thread worker = new Thread(task, "mealton-chat");
		worker.setDaemon(true);
		worker.start();
	}

	private void setBusy(boolean busy) {
		input.setDisable(busy);
		sendButton.setDisable(busy);
		if (!busy) {
			input.requestFocus();
		}
	}

	/**
	 * Adds a chat bubble: the user's on the right (blue), Mealton's on the left
	 * (grey).
	 */
	private void addBubble(String text, boolean fromUser) {
		Label bubble = new Label(text);
		bubble.setWrapText(true);
		bubble.setMaxWidth(BUBBLE_MAX_WIDTH);
		bubble.setMinHeight(Region.USE_PREF_SIZE); // stops long wrapped text from being cut off
		bubble.setPadding(new Insets(8, 12, 8, 12));
		bubble.setStyle(fromUser ? "-fx-background-color: #1e88e5; -fx-text-fill: white; -fx-background-radius: 12;"
				: "-fx-background-color: #eeeeee; -fx-text-fill: black; -fx-background-radius: 12;");
		HBox row = new HBox(bubble);
		row.setAlignment(fromUser ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
		messages.getChildren().add(row);
	}

	/**
	 * Adds a small grey line (status or "Tools used"). Returns it so it can be
	 * removed later.
	 */
	private Label addNote(String text) {
		Label note = new Label(text);
		note.setStyle("-fx-text-fill: #777777; -fx-font-size: 11px;");
		messages.getChildren().add(note);
		return note;
	}
}