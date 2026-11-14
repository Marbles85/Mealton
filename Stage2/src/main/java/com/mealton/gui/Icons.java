package com.mealton.gui;

import javafx.scene.Group;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/** The line icons from the design, drawn as vector paths on a 24 x 24 grid. */
final class Icons {
	static final String LEAF = "M11 20A7 7 0 0 1 9.8 6.1C15.5 5 17 4.48 19 2c1 2 2 4.18 2 8c0 5.5-4.78 10-10 10Z "
			+ "M2 21c0-3 1.85-5.36 5.08-6C9.5 14.52 12 13 13 12";
	static final String BOOK = "M4 19.5A2.5 2.5 0 0 1 6.5 17H20V3H6.5A2.5 2.5 0 0 0 4 5.5z "
			+ "M4 19.5A2.5 2.5 0 0 0 6.5 22H20v-5";
	static final String LOG = "M5 3h11l3 3v15H5z M12 9v6 M9 12h6";
	static final String CALENDAR = "M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z "
			+ "M16 2v4 M8 2v4 M3 10h18";
	static final String PANTRY = "M5 7h14l-1.5 13h-11z M9 7a3 3 0 0 1 6 0";
	static final String PROFILE = "M16 8a4 4 0 1 1-8 0a4 4 0 0 1 8 0z M4 21a8 8 0 0 1 16 0";
	static final String SEND = "M22 2L11 13 M22 2l-7 20l-4-9l-9-4z";
	static final String MINUS = "M5 12h14";
	static final String CHEVRON_DOWN = "M6 9l6 6l6-6";
	static final String CHEVRON_RIGHT = "M9 18l6-6l-6-6";
	static final String ALERT = "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20z M12 8v4 M12 16h.01";
	static final String LOGOUT = "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4 M16 17l5-5l-5-5 M21 12H9";

	private Icons() {
	}

	/** An outline icon of the given pixel size and colour. */
	static StackPane of(String path, double size, Color color) {
		SVGPath shape = new SVGPath();
		shape.setContent(path);
		shape.setFill(null);
		shape.setStroke(color);
		shape.setStrokeWidth(2);
		shape.setStrokeLineCap(StrokeLineCap.ROUND);
		shape.setStrokeLineJoin(StrokeLineJoin.ROUND);

		// draw on a 24 x 24 pane, then shrink the pane to the wanted size
		Pane grid = new Pane(shape);
		grid.setMinSize(24, 24);
		grid.setPrefSize(24, 24);
		grid.setMaxSize(24, 24);
		double scale = size / 24.0;
		grid.setScaleX(scale);
		grid.setScaleY(scale);

		StackPane box = new StackPane(new Group(grid));
		box.setMinSize(size, size);
		box.setPrefSize(size, size);
		box.setMaxSize(size, size);
		box.setMouseTransparent(true);
		return box;
	}
}