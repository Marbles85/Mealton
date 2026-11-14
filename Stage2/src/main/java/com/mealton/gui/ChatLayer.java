package com.mealton.gui;

import java.util.function.DoubleSupplier;

import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * A transparent layer over the whole window (sidebar included) that holds the
 * AI chat. Two modes (see {@link UiSettings}): "roam" - a round bubble you can
 * drag anywhere (it snaps to the left or right edge) and click to open;
 * "sidebar" - no bubble; the sidebar's "Ask Mealton" tab opens the same panel
 * next to the sidebar.
 */
public class ChatLayer extends Pane {
	private static final double SIZE = 64;
	private static final double MARGIN = 20;

	private final UiSettings settings;
	private final Runnable onOpenChanged;
	private final DoubleSupplier leftInset; // width of the sidebar, so the sidebar-mode panel opens beside it
	private final StackPane panel = new StackPane();
	private final StackPane bubble = new StackPane();
	private final Label hint = new Label("Drag me anywhere");

	private String mode;
	private boolean open;
	private String side; // "left" or "right"
	private double restY; // top of the bubble when it is resting (NaN = default spot)
	private boolean showHint;
	private boolean dragging;
	private boolean moved;
	private double pressX;
	private double pressY;
	private double dragX;
	private double dragY;

	/**
	 * @param chat          the chat panel to show
	 * @param onOpenChanged called when the panel opens or closes (so the sidebar
	 *                      tab can update)
	 * @param leftInset     gives the current sidebar width
	 */
	public ChatLayer(Node chat, UiSettings settings, Runnable onOpenChanged, DoubleSupplier leftInset) {
		this.settings = settings;
		this.onOpenChanged = onOpenChanged;
		this.leftInset = leftInset;
		this.mode = settings.chatMode();
		this.side = settings.bubbleSide();
		this.restY = settings.bubbleY();
		this.showHint = !settings.hasBubblePosition();

		setPickOnBounds(false); // clicks on empty space go through to the page underneath

		panel.getStyleClass().add("chat-panel");
		panel.getChildren().add(chat);
		Rectangle round = new Rectangle();
		round.setArcWidth(38);
		round.setArcHeight(38);
		round.widthProperty().bind(panel.widthProperty());
		round.heightProperty().bind(panel.heightProperty());
		chat.setClip(round);
		panel.setVisible(false);

		bubble.getStyleClass().add("chat-bubble");
		bubble.setMinSize(SIZE, SIZE);
		bubble.setPrefSize(SIZE, SIZE);
		bubble.setMaxSize(SIZE, SIZE);
		bubble.setFocusTraversable(true);
		bubble.setOnMousePressed(e -> {
			pressX = e.getSceneX();
			pressY = e.getSceneY();
			moved = false;
		});
		bubble.setOnMouseDragged(e -> {
			if (!moved && Math.abs(e.getSceneX() - pressX) + Math.abs(e.getSceneY() - pressY) < 6) {
				return; // a click, not a drag
			}
			moved = true;
			dragging = true;
			showHint = false;
			Point2D p = sceneToLocal(e.getSceneX(), e.getSceneY());
			dragX = p.getX();
			dragY = p.getY();
			bubble.setCursor(Cursor.CLOSED_HAND);
			requestLayout();
		});
		bubble.setOnMouseReleased(e -> {
			if (dragging) {
				Point2D p = sceneToLocal(e.getSceneX(), e.getSceneY());
				side = p.getX() < getWidth() / 2 ? "left" : "right";
				restY = clampY(p.getY() - SIZE / 2, getHeight());
				settings.setBubblePosition(side, restY);
				dragging = false;
				bubble.setCursor(Cursor.HAND);
			} else if (!moved) {
				showHint = false;
				setOpen(!open);
			}
			moved = false;
			requestLayout();
		});
		bubble.setOnKeyPressed(e -> {
			if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) {
				showHint = false;
				setOpen(!open);
				e.consume();
			}
		});

		hint.getStyleClass().add("chat-hint");
		hint.setMouseTransparent(true);

		panel.setManaged(false);
		bubble.setManaged(false);
		hint.setManaged(false);
		getChildren().addAll(panel, bubble, hint);
		refreshBubble();
	}

	public boolean isOpen() {
		return open;
	}

	public void toggle() {
		setOpen(!open);
	}

	public void close() {
		setOpen(false);
	}

	public void setOpen(boolean value) {
		if (open == value) {
			return;
		}
		open = value;
		refreshBubble();
		requestLayout();
		onOpenChanged.run();
	}

	/** Switches between the draggable bubble and the sidebar tab. */
	public void setMode(String newMode) {
		if (newMode.equals(mode)) {
			return;
		}
		mode = newMode;
		open = false;
		refreshBubble();
		requestLayout();
		onOpenChanged.run();
	}

	private boolean roam() {
		return UiSettings.CHAT_ROAM.equals(mode);
	}

	private void refreshBubble() {
		bubble.getStyleClass().remove("chat-bubble-open");
		if (open && roam()) {
			bubble.getStyleClass().add("chat-bubble-open");
			bubble.getChildren().setAll(Icons.of(Icons.CHEVRON_DOWN, 24, Color.WHITE));
		} else {
			bubble.getChildren().setAll(Icons.of(Icons.LEAF, 28, Color.WHITE));
		}
	}

	private static double clampY(double y, double height) {
		return Math.max(MARGIN, Math.min(height - SIZE - MARGIN, y));
	}

	@Override
	protected void layoutChildren() {
		double w = getWidth();
		double h = getHeight();
		boolean roam = roam();
		bubble.setVisible(roam);
		panel.setVisible(open);
		hint.setVisible(roam && showHint && !open && !dragging);

		if (roam) {
			double rest = clampY(Double.isNaN(restY) ? h - SIZE - 32 : restY, h);
			boolean leftSide = "left".equals(side);
			double left = dragging ? dragX - SIZE / 2 : (leftSide ? MARGIN : w - SIZE - MARGIN);
			double top = dragging ? dragY - SIZE / 2 : rest;
			bubble.resizeRelocate(left, top, SIZE, SIZE);

			hint.autosize();
			hint.relocate(leftSide ? MARGIN + SIZE + 10 : w - MARGIN - SIZE - 10 - hint.getWidth(), rest + 20);

			double panelW = Math.max(0, Math.min(400, w - 2 * MARGIN));
			double panelH = Math.max(0, Math.min(640, h - 2 * MARGIN));
			double panelX = leftSide ? MARGIN + SIZE + 12 : w - MARGIN - SIZE - 12 - panelW;
			double panelY = Math.max(MARGIN, Math.min(h - panelH - MARGIN, rest + SIZE - panelH));
			panel.resizeRelocate(panelX, panelY, panelW, panelH);
		} else {
			// sidebar mode: open just to the right of the sidebar
			double x = leftInset.getAsDouble() + 12;
			panel.resizeRelocate(x, 12, Math.max(0, Math.min(400, w - x - 12)), Math.max(0, h - 24));
		}
	}
}