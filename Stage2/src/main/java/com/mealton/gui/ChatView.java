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
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/**
 * The Mealton chat panel (F14). The user types a message, the AI agent works on
 * it in a background thread (so the window never freezes) and the answer
 * appears as a chat bubble. The names of the tools the agent used are shown in
 * the CLI, not here.
 */
public class ChatView extends BorderPane {

	private final MealtonFacade facade;
	private final Runnable onDataChanged; // lets other pages refresh after the agent changes data
	private final VBox messages = new VBox(12);
	private final ScrollPane scroll = new ScrollPane(messages);
	private final TextField input = new TextField();
	private final Button sendButton = new Button();
	private final FlowPane suggestions = new FlowPane(6, 6);

	/**
	 * @param onDataChanged called after the agent used a tool, so the pages can
	 *                      reload
	 * @param onClose       called when the user presses the collapse button
	 */
	public ChatView(MealtonFacade facade, Runnable onDataChanged, Runnable onClose) {
		this.facade = facade;
		this.onDataChanged = onDataChanged;

		// header
		StackPane avatar = new StackPane(Icons.of(Icons.LEAF, 22, Color.web("#2F4A34")));
		avatar.getStyleClass().add("chat-avatar");
		avatar.setMinSize(40, 40);
		avatar.setMaxSize(40, 40);
		Label title = new Label("Mealton");
		title.getStyleClass().add("chat-title");
		Label sub = new Label("Can edit your plan, pantry and list");
		sub.getStyleClass().add("chat-sub");
		VBox titles = new VBox(1, title, sub);
		Region grow = new Region();
		HBox.setHgrow(grow, Priority.ALWAYS);
		Button collapse = new Button();
		collapse.setGraphic(Icons.of(Icons.MINUS, 20, Color.WHITE));
		collapse.getStyleClass().add("chat-collapse");
		collapse.setTooltip(new Tooltip("Collapse chat"));
		collapse.setOnAction(e -> onClose.run());
		HBox header = new HBox(12, avatar, titles, grow, collapse);
		header.getStyleClass().add("chat-header");
		header.setAlignment(Pos.CENTER_LEFT);
		setTop(header);

		// messages
		messages.setPadding(new Insets(16));
		messages.getStyleClass().add("chat-messages");
		scroll.getStyleClass().add("chat-scroll");
		scroll.setFitToWidth(true);
		scroll.setFitToHeight(true); // the grey background fills the panel even before the first message
		scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		// always scroll down to the newest message
		messages.heightProperty().addListener((obs, oldH, newH) -> scroll.setVvalue(1.0));
		setCenter(scroll);

		// footer: suggestions and the text box
		for (String text : new String[] { "What\u2019s expiring?", "Make my week high-protein, no nuts" }) {
			Button chip = new Button(text);
			chip.getStyleClass().add("chat-suggestion");
			chip.setOnAction(e -> send(text));
			suggestions.getChildren().add(chip);
		}
		input.setPromptText("Ask Mealton to plan, cook or shop\u2026");
		HBox.setHgrow(input, Priority.ALWAYS);
		input.setOnAction(e -> send(input.getText()));
		sendButton.setGraphic(Icons.of(Icons.SEND, 20, Color.WHITE));
		sendButton.getStyleClass().add("send-button");
		sendButton.setTooltip(new Tooltip("Send message"));
		sendButton.setOnAction(e -> send(input.getText()));
		HBox bar = new HBox(8, input, sendButton);
		VBox footer = new VBox(10, suggestions, bar);
		footer.getStyleClass().add("chat-footer");
		setBottom(footer);

		if (facade.hasAgent()) {
			addBubble("Hi! I'm Mealton. I can look at your pantry and shopping list, and add things for you.", false);
		} else {
			addBubble("The AI assistant is switched off. Check LLM_PROVIDER and LLM_API_KEY in your local .env file, "
					+ "then restart the app.", false);
			input.setDisable(true);
			sendButton.setDisable(true);
			suggestions.setDisable(true);
		}
	}

	/** Sends a message to the agent without blocking the window. */
	private void send(String raw) {
		String text = raw == null ? "" : raw.trim();
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
		suggestions.setDisable(busy);
		if (!busy) {
			input.requestFocus();
		}
	}

	/**
	 * Adds a chat bubble: the user's on the right (green), Mealton's on the left
	 * (white).
	 */
	private void addBubble(String text, boolean fromUser) {
		TextFlow bubble = richText(text, fromUser ? Color.WHITE : Color.web("#1F2A22"));
		bubble.maxWidthProperty().bind(scroll.widthProperty().multiply(0.8));
		bubble.setMinHeight(Region.USE_PREF_SIZE); // stops long wrapped text from being cut off
		bubble.getStyleClass().add(fromUser ? "chat-user" : "chat-bot");
		HBox row = new HBox(bubble);
		row.setAlignment(fromUser ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
		messages.getChildren().add(row);
	}

	/**
	 * Turns the AI's markdown-style text into plain text with real bold: **word**
	 * becomes bold (the stars are removed), "- " at the start of a line becomes a
	 * bullet, and "#" headings lose their "#".
	 */
	private static TextFlow richText(String raw, Color color) {
		String text = raw == null ? "" : raw;
		text = text.replaceAll("(?m)^\\s*#{1,6}\\s*", ""); // headings
		text = text.replaceAll("(?m)^(\\s*)[-*]\\s+", "$1\u2022 "); // bullet lines
		TextFlow flow = new TextFlow();
		String[] parts = text.split("\\*\\*", -1);
		for (int i = 0; i < parts.length; i++) {
			if (parts[i].isEmpty()) {
				continue;
			}
			Text piece = new Text(parts[i]);
			piece.setFill(color);
			if (i % 2 == 1) { // text between a pair of ** **
				piece.setStyle("-fx-font-weight: bold;");
			}
			flow.getChildren().add(piece);
		}
		return flow;
	}

	/** Adds a small grey line (status). Returns it so it can be removed later. */
	private Label addNote(String text) {
		Label note = new Label(text);
		note.getStyleClass().add("chat-note");
		messages.getChildren().add(note);
		return note;
	}
}