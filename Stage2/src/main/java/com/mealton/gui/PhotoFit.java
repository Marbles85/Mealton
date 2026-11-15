package com.mealton.gui;

import java.nio.file.Path;

import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/**
 * Shows a photo so it fills a box without being stretched: the extra edges are
 * cropped.
 */
final class PhotoFit {
	private PhotoFit() {
	}

	/**
	 * An image view of exactly {@code w} x {@code h} showing the middle of the
	 * photo, or null if it cannot be read.
	 */
	static ImageView cover(Path file, double w, double h) {
		try {
			double bound = Math.max(w, h) * 2.5;
			Image img = new Image(file.toUri().toString(), bound, bound, true, true);
			if (img.isError() || img.getWidth() <= 0 || img.getHeight() <= 0) {
				return null;
			}
			double scale = Math.max(w / img.getWidth(), h / img.getHeight());
			double vw = w / scale;
			double vh = h / scale;
			ImageView view = new ImageView(img);
			view.setViewport(new Rectangle2D((img.getWidth() - vw) / 2, (img.getHeight() - vh) / 2, vw, vh));
			view.setPreserveRatio(false); // the crop already has the box's shape, so nothing is squashed
			view.setFitWidth(w);
			view.setFitHeight(h);
			return view;
		} catch (RuntimeException e) {
			return null;
		}
	}
}