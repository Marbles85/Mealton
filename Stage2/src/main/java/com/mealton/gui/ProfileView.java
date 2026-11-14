package com.mealton.gui;

import java.util.Optional;
import java.util.TreeSet;

import com.mealton.account.AccountService;
import com.mealton.facade.MealtonFacade;
import com.mealton.profile.Activity;
import com.mealton.profile.BodyUnits;
import com.mealton.profile.Goal;
import com.mealton.profile.TargetCalculator;
import com.mealton.profile.TargetCalculator.Suggestion;
import com.mealton.profile.UserProfile;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

/**
 * The Profile page (feature F01): who you are, allergies and dietary
 * restrictions on the left; on the right where the AI chat lives, the "track
 * your macros" switch, your daily targets with Mealton's suggestion (Mifflin-St
 * Jeor), and your account (log out, delete account).
 * <p>
 * Allergies, restrictions, activity and goal are edited freely and applied by
 * the "Save profile" button (each field is checked and a wrong one is marked in
 * red). The switch, the targets and the chat placement apply straight away.
 */
public class ProfileView extends VBox {
	private static final String NOT_SET = "Prefer not to say";
	private static final String HEIGHT_ERROR = "Enter a height between 100 and 250 cm.";
	private static final String WEIGHT_ERROR = "Enter a weight between 20 and 400 kg.";

	private final MealtonFacade facade;
	private final UiSettings settings;
	private final String username;
	private final Runnable onLogout;
	private final Runnable onDeleteAccount;
	private final Runnable onChanged;

	// ---- left: the form ----
	private final TextField nameField = new TextField();
	private final TextField emailField = new TextField();
	private final TextField dobField = new TextField();
	private final ComboBox<String> sexBox = new ComboBox<>();
	private final TextField weightField = new TextField();
	private final TextField heightField = new TextField();
	private final Label weightHint = hintLabel(), heightHint = hintLabel();
	private final Label nameErr = errorLabel(), emailErr = errorLabel(), dobErr = errorLabel(),
			weightErr = errorLabel(), heightErr = errorLabel();
	private final FlowPane activityPills = new FlowPane(8, 8);
	private final FlowPane goalPills = new FlowPane(8, 8);
	private final FlowPane allergyBox = new FlowPane(8, 8);
	private final FlowPane dietPills = new FlowPane(8, 8);
	private final TextField addAllergyField = new TextField();
	private final Label status = new Label();
	private Activity activity = Activity.LIGHT;
	private Goal goal = Goal.MAINTAIN;
	private final TreeSet<String> allergies = new TreeSet<>();
	private final TreeSet<String> diets = new TreeSet<>();

	// ---- right: assistant, tracking, targets, suggestion, account ----
	private final HBox modeRow = new HBox(10);
	private final VBox trackNoteBox = new VBox();
	private final StackPane switchTrack = new StackPane();
	private final Circle switchKnob = new Circle(11);
	private final VBox trackedBox = new VBox(16); // everything that only shows while tracking is on
	private final TextField kcalField = new TextField();
	private final TextField proteinField = new TextField();
	private final TextField carbsField = new TextField();
	private final TextField fatField = new TextField();
	private final Label targetsSource = new Label();
	private final Label sumLabel = new Label();
	private final Label tooLow = new Label("Below the 1,200 kcal safe minimum. Mealton won’t plan under that.");
	private final Label targetErr = errorLabel();
	private final VBox suggestionBody = new VBox(12);
	private final Label sugKcal = new Label();
	private final Label sugFormula = new Label();
	private final Label sugP = new Label(), sugC = new Label(), sugF = new Label();
	private final Button useButton = new Button();
	private final Label sugEmpty = new Label(
			"Add your date of birth, weight and height, then Save profile, to get a suggestion.");
	private final HBox cappedBox = new HBox();
	private final Label cappedLabel = new Label();
	private final Label whyText = new Label();
	private final Label avatar = new Label();
	private final Label accountName = new Label();
	private final Label accountEmail = new Label();
	private final VBox deleteBox = new VBox(10);
	private final Button deleteLink = new Button("Delete account…");
	private final TextField deleteConfirm = new TextField();
	private final Button deleteGo = new Button("Delete forever");

	private boolean applying; // true while this page itself changes the profile
	private boolean dirty; // true when the form has edits that are not saved yet
	private boolean loading; // true while the controls are being filled

	public ProfileView(MealtonFacade facade, UiSettings settings, String username, Runnable onLogout,
			Runnable onDeleteAccount, Runnable onChanged) {
		this.facade = facade;
		this.settings = settings;
		this.username = username;
		this.onLogout = onLogout;
		this.onDeleteAccount = onDeleteAccount;
		this.onChanged = onChanged;
		var css = ProfileView.class.getResource("/profile.css");
		if (css != null) {
			getStylesheets().add(css.toExternalForm());
		}
		getStyleClass().add("pf-page");

		Label title = new Label("Profile & dietary needs");
		title.getStyleClass().add("pf-title");
		Label sub = new Label("Mealton never plans a meal with your allergens in it.");
		sub.getStyleClass().add("pf-sub");
		VBox header = new VBox(2, title, sub);

		VBox left = buildForm();
		VBox right = buildSide();
		HBox.setHgrow(left, Priority.ALWAYS);
		left.setMaxWidth(Double.MAX_VALUE);
		right.setPrefWidth(350);
		right.setMinWidth(320);
		right.setMaxWidth(380);
		HBox columns = new HBox(24, left, right);
		columns.setAlignment(Pos.TOP_LEFT);

		VBox content = new VBox(24, header, columns);
		content.setPadding(new Insets(32, 40, 48, 40));
		ScrollPane scroll = new ScrollPane(content);
		scroll.setFitToWidth(true);
		scroll.getStyleClass().add("pf-scroll");
		VBox.setVgrow(scroll, Priority.ALWAYS);
		getChildren().add(scroll);

		wireEvents();
		loadFromProfile();
		facade.addProfileListener(p -> {
			if (!applying) { // the AI (or the CLI) changed the profile: show it
				loadFromProfile();
			}
		});
	}

	// ====================================================================
	// building the screen
	// ====================================================================

	private VBox buildForm() {
		nameField.setPromptText("Your name");
		emailField.setPromptText("you@example.com");
		dobField.setPromptText("e.g. Mar 4, 2005");
		weightField.setPromptText("e.g. 62 kg or 135 lb");
		heightField.setPromptText("e.g. 165 cm or 5'6\"");
		for (TextField f : new TextField[] { nameField, emailField, dobField, weightField, heightField }) {
			f.getStyleClass().add("pf-input");
			f.setMaxWidth(Double.MAX_VALUE);
		}
		sexBox.getItems().addAll(NOT_SET, "Female", "Male");
		sexBox.getStyleClass().add("pf-combo");
		sexBox.setMaxWidth(Double.MAX_VALUE);

		GridPane grid = new GridPane();
		grid.setHgap(16);
		grid.setVgap(16);
		ColumnConstraints half = new ColumnConstraints();
		half.setPercentWidth(50);
		ColumnConstraints half2 = new ColumnConstraints();
		half2.setPercentWidth(50);
		grid.getColumnConstraints().addAll(half, half2);
		grid.add(field("Name", nameField, nameErr), 0, 0);
		grid.add(field("Email", emailField, emailErr), 1, 0);
		grid.add(field("Date of birth", dobField, dobErr), 0, 1);
		grid.add(field("Sex", sexBox), 1, 1);
		grid.add(field("Weight (kg or lb)", weightField, weightHint, weightErr), 0, 2);
		grid.add(field("Height (cm or ft/in)", heightField, heightHint, heightErr), 1, 2);

		addAllergyField.setPromptText("+ Add allergy");
		addAllergyField.getStyleClass().add("pf-add-allergy");

		Button save = new Button("Save profile");
		save.getStyleClass().add("pf-save");
		save.setOnAction(e -> save());
		status.getStyleClass().add("pf-status");
		status.setWrapText(true);
		HBox saveRow = new HBox(12, save, status);
		saveRow.setAlignment(Pos.CENTER_LEFT);

		VBox card = new VBox(22, grid, group("Activity level", activityPills), group("Goal", goalPills),
				group("Allergies", allergyBox), group("Dietary restrictions", dietPills), saveRow);
		card.getStyleClass().add("pf-card");
		card.setPadding(new Insets(24));
		return card;
	}

	private VBox buildSide() {
		// --- Mealton assistant ---
		Label h = new Label("Mealton assistant");
		h.getStyleClass().add("pf-h2");
		Label hs = muted("Choose where the AI chat lives.");
		Label tip = muted("Applies to every page. Tip: click the Mealton logo in the sidebar to hide or show it.");
		tip.setWrapText(true);
		tip.setMinHeight(Region.USE_PREF_SIZE);
		VBox assistant = new VBox(14, new VBox(2, h, hs), modeRow, tip);
		assistant.getStyleClass().add("pf-card");

		// --- track your macros ---
		Label th = new Label("Track your macros");
		th.getStyleClass().add("pf-h2");
		Label ts = muted("Calories, protein, carbs and fat on every page.");
		VBox tt = new VBox(2, th, ts);
		HBox.setHgrow(tt, Priority.ALWAYS);
		switchTrack.setMinSize(48, 28);
		switchTrack.setPrefSize(48, 28);
		switchTrack.setMaxSize(48, 28);
		switchKnob.setFill(javafx.scene.paint.Color.WHITE);
		StackPane.setAlignment(switchKnob, Pos.CENTER_LEFT);
		switchTrack.getChildren().add(switchKnob);
		HBox trackRow = new HBox(12, tt, switchTrack);
		trackRow.setAlignment(Pos.CENTER_LEFT);
		trackRow.setCursor(javafx.scene.Cursor.HAND);
		trackRow.setOnMouseClicked(e -> toggleTracking());
		Label note = new Label("Macro tracking is off. The tracker, daily totals and progress bars are hidden "
				+ "everywhere. Recipes still show their nutrition.");
		note.getStyleClass().add("pf-note");
		note.setWrapText(true);
		note.setMinHeight(Region.USE_PREF_SIZE);
		note.setMaxWidth(Double.MAX_VALUE);
		trackNoteBox.getChildren().add(note);
		VBox track = new VBox(10, trackRow, trackNoteBox);
		track.getStyleClass().add("pf-card");

		// --- your daily targets ---
		Label gh = new Label("Your daily targets");
		gh.getStyleClass().add("pf-h2");
		targetsSource.getStyleClass().add("pf-muted");
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox gHead = new HBox(8, gh, spacer, targetsSource);
		gHead.setAlignment(Pos.BASELINE_LEFT);
		GridPane tg = new GridPane();
		tg.setHgap(10);
		tg.setVgap(10);
		ColumnConstraints half = new ColumnConstraints();
		half.setPercentWidth(50);
		ColumnConstraints half2 = new ColumnConstraints();
		half2.setPercentWidth(50);
		tg.getColumnConstraints().addAll(half, half2);
		tg.add(targetField("Calories", kcalField, "kcal"), 0, 0);
		tg.add(targetField("Protein", proteinField, "g"), 1, 0);
		tg.add(targetField("Carbs", carbsField, "g"), 0, 1);
		tg.add(targetField("Fat", fatField, "g"), 1, 1);
		sumLabel.setWrapText(true);
		sumLabel.setMinHeight(Region.USE_PREF_SIZE);
		tooLow.getStyleClass().add("pf-alert-red");
		tooLow.setWrapText(true);
		tooLow.setMinHeight(Region.USE_PREF_SIZE);
		VBox targets = new VBox(14, gHead, tg, sumLabel, tooLow, targetErr);
		targets.getStyleClass().add("pf-card");

		// --- Mealton suggests ---
		Label sh = new Label("Mealton suggests");
		sh.getStyleClass().add("pf-sug-h");
		sugKcal.getStyleClass().add("pf-sug-big");
		Label unit = new Label("kcal");
		unit.getStyleClass().add("pf-sug-unit");
		HBox big = new HBox(8, sugKcal, unit);
		big.setAlignment(Pos.BASELINE_LEFT);
		sugFormula.getStyleClass().add("pf-sug-small");
		sugFormula.setWrapText(true);
		sugFormula.setMinHeight(Region.USE_PREF_SIZE);
		HBox tiles = new HBox(8, tile(sugP, "protein"), tile(sugC, "carbs"), tile(sugF, "fat"));
		useButton.getStyleClass().add("pf-sug-btn");
		useButton.setMaxWidth(Double.MAX_VALUE);
		useButton.setOnAction(e -> useSuggestion());
		suggestionBody.getChildren().addAll(big, sugFormula, tiles, useButton);
		sugEmpty.getStyleClass().add("pf-sug-small");
		sugEmpty.setWrapText(true);
		sugEmpty.setMinHeight(Region.USE_PREF_SIZE);
		VBox suggest = new VBox(12, sh, suggestionBody, sugEmpty);
		suggest.getStyleClass().add("pf-sug");

		cappedLabel.getStyleClass().add("pf-alert-warn");
		cappedLabel.setWrapText(true);
		cappedLabel.setMinHeight(Region.USE_PREF_SIZE);
		cappedLabel.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(cappedLabel, Priority.ALWAYS);
		cappedBox.getChildren().add(cappedLabel);

		Label leaf = new Label("❀");
		leaf.getStyleClass().add("pf-why-icon");
		leaf.setMinSize(36, 36);
		leaf.setMaxSize(36, 36);
		Label wh = new Label("Why this number");
		wh.getStyleClass().add("pf-label");
		whyText.getStyleClass().add("pf-text");
		whyText.setWrapText(true);
		whyText.setMinHeight(Region.USE_PREF_SIZE);
		VBox whyCol = new VBox(6, wh, whyText);
		HBox.setHgrow(whyCol, Priority.ALWAYS);
		HBox why = new HBox(12, leaf, whyCol);
		why.getStyleClass().add("pf-card");
		trackedBox.getChildren().addAll(targets, suggest, cappedBox, why);

		// --- account ---
		Label ah = new Label("Account");
		ah.getStyleClass().add("pf-h2");
		avatar.getStyleClass().add("pf-avatar");
		avatar.setMinSize(44, 44);
		avatar.setMaxSize(44, 44);
		accountName.getStyleClass().add("pf-label");
		accountEmail.getStyleClass().add("pf-muted");
		HBox who = new HBox(12, avatar, new VBox(1, accountName, accountEmail));
		who.setAlignment(Pos.CENTER_LEFT);
		Button logout = new Button("Log out");
		logout.getStyleClass().add("pf-outline-btn");
		logout.setMaxWidth(Double.MAX_VALUE);
		logout.setOnAction(e -> {
			commitPending();
			onLogout.run();
		});
		deleteLink.getStyleClass().add("pf-del-link");
		deleteLink.setMaxWidth(Double.MAX_VALUE);
		deleteLink.setOnAction(e -> {
			deleteConfirm.clear();
			showDeleteBox(true);
		});
		buildDeleteBox();
		VBox account = new VBox(14, ah, who, logout, deleteLink, deleteBox);
		account.getStyleClass().add("pf-card");

		return new VBox(16, assistant, track, trackedBox, account);
	}

	private void buildDeleteBox() {
		Label t = new Label("Delete your account?");
		t.getStyleClass().add("pf-del-title");
		Label body = new Label("This permanently removes your profile, meal plans, pantry, shopping list, logged "
				+ "meals and your own recipes. It can’t be undone.");
		body.getStyleClass().add("pf-del-text");
		body.setWrapText(true);
		body.setMinHeight(Region.USE_PREF_SIZE);
		Label type = new Label("Type DELETE to confirm");
		type.getStyleClass().add("pf-del-text");
		deleteConfirm.getStyleClass().add("pf-del-input");
		Button keep = new Button("Keep account");
		keep.getStyleClass().add("pf-del-keep");
		keep.setMaxWidth(Double.MAX_VALUE);
		keep.setOnAction(e -> showDeleteBox(false));
		deleteGo.getStyleClass().add("pf-del-go");
		deleteGo.setMaxWidth(Double.MAX_VALUE);
		deleteGo.setDisable(true);
		deleteGo.setOnAction(e -> {
			if (deleteConfirm.getText().trim().equals("DELETE")) {
				onDeleteAccount.run();
			}
		});
		deleteConfirm.textProperty().addListener((obs, o, n) -> deleteGo.setDisable(!n.trim().equals("DELETE")));
		HBox.setHgrow(keep, Priority.ALWAYS);
		HBox.setHgrow(deleteGo, Priority.ALWAYS);
		deleteBox.getChildren().addAll(t, body, new VBox(6, type, deleteConfirm), new HBox(8, keep, deleteGo));
		deleteBox.getStyleClass().add("pf-del-box");
		showDeleteBox(false);
	}

	private void showDeleteBox(boolean open) {
		deleteBox.setVisible(open);
		deleteBox.setManaged(open);
		deleteLink.setVisible(!open);
		deleteLink.setManaged(!open);
	}

	// ---- small builders ----

	private static Label errorLabel() {
		Label l = new Label();
		l.getStyleClass().add("pf-error");
		l.setWrapText(true);
		l.setMinHeight(Region.USE_PREF_SIZE);
		l.setVisible(false);
		l.setManaged(false);
		return l;
	}

	private static Label muted(String text) {
		Label l = new Label(text);
		l.getStyleClass().add("pf-muted");
		return l;
	}

	private static VBox field(String labelText, Node input, Label... extras) {
		Label l = new Label(labelText);
		l.getStyleClass().add("pf-label");
		VBox box = new VBox(6, l, input);
		for (Label extra : extras) {
			if (extra != null) {
				box.getChildren().add(extra);
			}
		}
		return box;
	}

	private static Label hintLabel() {
		Label l = new Label();
		l.getStyleClass().add("pf-muted");
		l.setVisible(false);
		l.setManaged(false);
		return l;
	}

	private static VBox group(String labelText, Node content) {
		Label l = new Label(labelText);
		l.getStyleClass().add("pf-label");
		return new VBox(8, l, content);
	}

	private static VBox tile(Label number, String what) {
		number.getStyleClass().add("pf-sug-tile-n");
		Label w = new Label(what);
		w.getStyleClass().add("pf-sug-small");
		VBox box = new VBox(number, w);
		box.getStyleClass().add("pf-sug-tile");
		HBox.setHgrow(box, Priority.ALWAYS);
		box.setMaxWidth(Double.MAX_VALUE);
		return box;
	}

	private VBox targetField(String labelText, TextField input, String unit) {
		input.getStyleClass().add("pf-input");
		input.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(input, Priority.ALWAYS);
		Label u = new Label(unit);
		u.getStyleClass().add("pf-muted");
		HBox row = new HBox(6, input, u);
		row.setAlignment(Pos.CENTER_LEFT);
		Label l = new Label(labelText);
		l.getStyleClass().add("pf-label");
		int step = "kcal".equals(unit) ? 10 : 1;
		input.setOnKeyPressed(e -> {
			if (e.getCode() == KeyCode.UP || e.getCode() == KeyCode.DOWN) {
				int delta = e.getCode() == KeyCode.UP ? step : -step;
				input.setText(String.valueOf(Math.max(0, number(input) + delta)));
				commitTargets();
				e.consume();
			}
		});
		input.setOnAction(e -> commitTargets());
		input.focusedProperty().addListener((obs, was, is) -> {
			if (!is) {
				commitTargets();
			}
		});
		return new VBox(6, l, row);
	}

	private Button pill(String text, boolean on, Runnable action) {
		Button b = new Button(text);
		b.getStyleClass().add(on ? "pf-pill-on" : "pf-pill");
		if (on) {
			b.getStyleClass().add("pf-pill");
		}
		b.setOnAction(e -> action.run());
		return b;
	}

	// ====================================================================
	// events and loading
	// ====================================================================

	private void wireEvents() {
		for (TextField f : new TextField[] { nameField, emailField, dobField, weightField, heightField }) {
			f.textProperty().addListener((obs, o, n) -> {
				if (!loading) {
					markDirty();
					refreshSuggestion();
				}
			});
		}
		weightField.textProperty().addListener((obs, o, n) -> updateHints());
		heightField.textProperty().addListener((obs, o, n) -> updateHints());
		sexBox.setOnAction(e -> {
			if (!loading) {
				markDirty();
				refreshSuggestion();
			}
		});
		addAllergyField.setOnAction(e -> {
			String text = addAllergyField.getText() == null ? "" : addAllergyField.getText().trim().toLowerCase();
			if (!text.isEmpty()) {
				allergies.add(text);
				addAllergyField.clear();
				markDirty();
				drawAllergies();
			}
		});
	}

	private void markDirty() {
		dirty = true;
		setStatus("You have unsaved changes.", "pf-status");
	}

	private void setStatus(String text, String styleClass) {
		status.setText(text);
		status.getStyleClass().removeAll("pf-status", "pf-status-ok", "pf-status-bad");
		status.getStyleClass().add(styleClass);
	}

	/** Fills every control from the saved profile (and discards unsaved edits). */
	private void loadFromProfile() {
		loading = true;
		try {
			UserProfile p = facade.getProfile();
			nameField.setText(p.name());
			emailField
					.setText(p.email().isEmpty() && username != null && username.contains("@") ? username : p.email());
			dobField.setText(p.birthDateText());
			sexBox.setValue("female".equals(p.sex()) ? "Female" : "male".equals(p.sex()) ? "Male" : NOT_SET);
			weightField.setText(p.weightKg() <= 0 ? "" : trimmed(p.weightKg()));
			heightField.setText(p.heightCm() <= 0 ? "" : String.valueOf(p.heightCm()));
			activity = p.activity();
			goal = p.goal();
			allergies.clear();
			allergies.addAll(p.allergies());
			diets.clear();
			diets.addAll(p.restrictions());
			for (TextField f : new TextField[] { nameField, emailField, dobField, weightField, heightField }) {
				clearError(f);
			}
			for (Label l : new Label[] { nameErr, emailErr, dobErr, weightErr, heightErr }) {
				showError(l, null);
			}
			dirty = false;
			setStatus("", "pf-status");
		} finally {
			loading = false;
		}
		drawPills();
		drawModes();
		drawTracking();
		refreshTargetFields();
		refreshSuggestion();
		drawAccount();
	}

	private static String trimmed(double kg) {
		return kg == Math.rint(kg) ? String.valueOf((int) kg) : String.valueOf(kg);
	}

	private void drawPills() {
		activityPills.getChildren().clear();
		for (Activity a : Activity.values()) {
			activityPills.getChildren().add(pill(a.label(), a == activity, () -> {
				activity = a;
				markDirty();
				drawPills();
				refreshSuggestion();
			}));
		}
		goalPills.getChildren().clear();
		for (Goal g : Goal.values()) {
			goalPills.getChildren().add(pill(g.label(), g == goal, () -> {
				goal = g;
				markDirty();
				drawPills();
				refreshSuggestion();
			}));
		}
		dietPills.getChildren().clear();
		for (String d : UserProfile.RESTRICTIONS) {
			if (d.equals("keto")) {
				continue; // keto is available in chat and the CLI; the page shows the common eight
			}
			boolean on = diets.contains(d);
			dietPills.getChildren().add(pill(capital(d), on, () -> {
				if (on) {
					diets.remove(d);
				} else {
					diets.add(d);
				}
				markDirty();
				drawPills();
				refreshSuggestion();
			}));
		}
		drawAllergies();
	}

	private static String capital(String s) {
		return s.substring(0, 1).toUpperCase() + s.substring(1);
	}

	private void drawAllergies() {
		allergyBox.getChildren().clear();
		for (String a : allergies) {
			Label text = new Label(a);
			text.getStyleClass().add("pf-allergy-text");
			Button x = new Button("×");
			x.getStyleClass().add("pf-allergy-x");
			x.setOnAction(e -> {
				allergies.remove(a);
				markDirty();
				drawAllergies();
			});
			HBox chip = new HBox(2, text, x);
			chip.setAlignment(Pos.CENTER_LEFT);
			chip.getStyleClass().add("pf-allergy");
			allergyBox.getChildren().add(chip);
		}
		allergyBox.getChildren().add(addAllergyField);
	}

	// ====================================================================
	// saving the form
	// ====================================================================

	/**
	 * Applies anything typed but not yet saved. The app calls this before every
	 * save and when you leave the page; a wrong value is simply not saved.
	 */
	public void commitPending() {
		commitTargets();
		if (dirty) {
			save();
		}
	}

	private void save() {
		int problems = 0;
		UserProfile p = facade.getProfile();
		try {
			p = p.withName(nameField.getText());
			showError(nameErr, null);
			clearError(nameField);
		} catch (IllegalArgumentException e) {
			problems += fail(nameField, nameErr, e.getMessage());
		}
		String email = emailField.getText() == null ? "" : emailField.getText().trim();
		try {
			if (!email.isEmpty()) {
				AccountService.checkEmail(email);
			}
			p = p.withEmail(email);
			showError(emailErr, null);
			clearError(emailField);
		} catch (IllegalArgumentException e) {
			problems += fail(emailField, emailErr, e.getMessage());
		}
		try {
			p = p.withBirthDate(dobField.getText());
			showError(dobErr, null);
			clearError(dobField);
		} catch (IllegalArgumentException e) {
			problems += fail(dobField, dobErr, e.getMessage());
		}
		p = p.withSex(sexValue());

		double weight = p.weightKg();
		boolean weightOk = true;
		try {
			weight = parseWeight();
			p.withBody(weight, p.heightCm()); // checks the range
			showError(weightErr, null);
			clearError(weightField);
		} catch (IllegalArgumentException e) {
			weightOk = false;
			problems += fail(weightField, weightErr, WEIGHT_ERROR);
		}
		int height = p.heightCm();
		boolean heightOk = true;
		try {
			height = parseHeight();
			p.withBody(p.weightKg(), height);
			showError(heightErr, null);
			clearError(heightField);
		} catch (IllegalArgumentException e) {
			heightOk = false;
			problems += fail(heightField, heightErr, HEIGHT_ERROR);
		}
		p = p.withBody(weightOk ? weight : p.weightKg(), heightOk ? height : p.heightCm());

		if (problems > 0) {
			setStatus("Fix " + problems + (problems == 1 ? " field" : " fields") + " to save.", "pf-status-bad");
			return;
		}
		try {
			p = p.withActivity(activity).withGoal(goal).withRestrictions(diets);
			UserProfile current = facade.getProfile();
			for (String a : allergies) {
				if (!current.allergies().contains(a)) {
					p = p.withAllergy(a);
				}
			}
			for (String a : current.allergies()) {
				if (!allergies.contains(a)) {
					p = p.withoutAllergy(a);
				}
			}
			applying = true;
			facade.saveProfile(p);
			dirty = false;
			setStatus("Saved.", "pf-status-ok");
		} catch (IllegalArgumentException e) {
			setStatus(e.getMessage(), "pf-status-bad");
			return;
		} finally {
			applying = false;
		}
		drawAccount();
		refreshTargetFields();
		refreshSuggestion();
		onChanged.run();
	}

	private String sexValue() {
		String v = sexBox.getValue();
		return "Female".equals(v) ? "female" : "Male".equals(v) ? "male" : "";
	}

	/** Weight in kg from what was typed (kg or lb); 0 when blank. */
	private double parseWeight() {
		String t = weightField.getText() == null ? "" : weightField.getText().trim();
		if (t.isEmpty()) {
			return 0;
		}
		try {
			return BodyUnits.parseWeightKg(t);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(WEIGHT_ERROR);
		}
	}

	/** Height in cm from what was typed (cm, m, ft/in or in); 0 when blank. */
	private int parseHeight() {
		String t = heightField.getText() == null ? "" : heightField.getText().trim();
		if (t.isEmpty()) {
			return 0;
		}
		try {
			return BodyUnits.parseHeightCm(t);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(HEIGHT_ERROR);
		}
	}

	/**
	 * Shows "\u2248 61.2 kg" / "\u2248 168 cm" when something other than kg / cm
	 * was typed.
	 */
	private void updateHints() {
		String w = weightField.getText();
		String h = heightField.getText();
		String wText = null;
		String hText = null;
		try {
			if (w != null && !w.isBlank() && !BodyUnits.isMetric(w)) {
				wText = "\u2248 " + trimmed(BodyUnits.parseWeightKg(w)) + " kg";
			}
		} catch (IllegalArgumentException e) {
			// the error appears when saving
		}
		try {
			if (h != null && !h.isBlank() && !BodyUnits.isMetric(h)) {
				hText = "\u2248 " + BodyUnits.parseHeightCm(h) + " cm";
			}
		} catch (IllegalArgumentException e) {
			// the error appears when saving
		}
		showHint(weightHint, wText);
		showHint(heightHint, hText);
	}

	private static void showHint(Label l, String text) {
		l.setText(text == null ? "" : text);
		l.setVisible(text != null);
		l.setManaged(text != null);
	}

	private int fail(TextField field, Label err, String message) {
		showError(err, message);
		if (!field.getStyleClass().contains("pf-input-error")) {
			field.getStyleClass().add("pf-input-error");
		}
		return 1;
	}

	private static void clearError(TextField field) {
		field.getStyleClass().remove("pf-input-error");
	}

	private static void showError(Label l, String message) {
		l.setText(message == null ? "" : message);
		l.setVisible(message != null);
		l.setManaged(message != null);
	}

	/**
	 * What the form says right now, using the saved value for any field that is not
	 * valid (so the suggestion updates while you type).
	 */
	private UserProfile draft() {
		UserProfile p = facade.getProfile();
		try {
			p = p.withBirthDate(dobField.getText());
		} catch (IllegalArgumentException e) {
			// keep the saved date
		}
		p = p.withSex(sexValue());
		try {
			p = p.withBody(parseWeight(), p.heightCm());
		} catch (IllegalArgumentException e) {
			// keep the saved weight
		}
		try {
			p = p.withBody(p.weightKg(), parseHeight());
		} catch (IllegalArgumentException e) {
			// keep the saved height
		}
		p = p.withActivity(activity).withGoal(goal);
		try {
			p = p.withRestrictions(diets);
		} catch (IllegalArgumentException e) {
			// keep the saved restrictions
		}
		return p;
	}

	// ====================================================================
	// assistant placement, tracking switch, targets, suggestion, account
	// ====================================================================

	private void drawModes() {
		modeRow.getChildren().clear();
		String current = settings.chatMode();
		modeRow.getChildren().add(modeCard(UiSettings.CHAT_ROAM, "Free roam",
				"A bubble you can drag anywhere. Click to open, collapse when done.", current));
		modeRow.getChildren().add(modeCard(UiSettings.CHAT_SIDEBAR, "In the sidebar",
				"An “Ask Mealton” tab in the sidebar. No bubble.", current));
	}

	private Node modeCard(String id, String name, String desc, String current) {
		Region preview;
		StackPane box = new StackPane();
		box.setPrefHeight(64);
		box.setMinHeight(64);
		box.setMaxHeight(64);
		box.getStyleClass().add("pf-mode-preview");
		if (UiSettings.CHAT_SIDEBAR.equals(id)) {
			VBox side = new VBox(4);
			side.setPadding(new Insets(8, 6, 8, 6));
			side.getStyleClass().add("pf-mode-side");
			side.setMaxWidth(46);
			side.setPrefWidth(46);
			for (int i = 0; i < 2; i++) {
				Region bar = new Region();
				bar.setPrefHeight(4);
				bar.setMinHeight(4);
				bar.setMaxHeight(4);
				bar.getStyleClass().add("pf-mode-bar");
				side.getChildren().add(bar);
			}
			Region on = new Region();
			on.setPrefHeight(8);
			on.setMinHeight(8);
			on.setMaxHeight(8);
			on.getStyleClass().add("pf-mode-bar-on");
			VBox.setMargin(on, new Insets(4, 0, 0, 0));
			side.getChildren().add(on);
			StackPane.setAlignment(side, Pos.CENTER_LEFT);
			box.getChildren().add(side);
		} else {
			preview = new Region();
			preview.setMinSize(18, 18);
			preview.setPrefSize(18, 18);
			preview.setMaxSize(18, 18);
			preview.getStyleClass().add("pf-mode-dot");
			StackPane.setAlignment(preview, Pos.BOTTOM_RIGHT);
			StackPane.setMargin(preview, new Insets(0, 8, 8, 0));
			box.getChildren().add(preview);
		}
		Label n = new Label(name);
		n.getStyleClass().add("pf-mode-name");
		Label d = muted(desc);
		d.setWrapText(true);
		d.setMinHeight(Region.USE_PREF_SIZE);
		VBox card = new VBox(8, box, n, d);
		card.getStyleClass().add("pf-mode");
		if (id.equals(current)) {
			card.getStyleClass().add("pf-mode-on");
		}
		card.setOnMouseClicked(e -> {
			settings.setChatMode(id);
			drawModes();
		});
		HBox.setHgrow(card, Priority.ALWAYS);
		card.setPrefWidth(10);
		card.setMaxWidth(Double.MAX_VALUE);
		return card;
	}

	private void toggleTracking() {
		try {
			applying = true;
			facade.setTrackMacros(!facade.tracksMacros());
		} finally {
			applying = false;
		}
		drawTracking();
		onChanged.run();
	}

	private void drawTracking() {
		boolean on = facade.tracksMacros();
		switchTrack.getStyleClass().removeAll("pf-switch", "pf-switch-on");
		switchTrack.getStyleClass().add(on ? "pf-switch-on" : "pf-switch");
		StackPane.setMargin(switchKnob, new Insets(0, 0, 0, on ? 23 : 3));
		trackNoteBox.setVisible(!on);
		trackNoteBox.setManaged(!on);
		trackedBox.setVisible(on);
		trackedBox.setManaged(on);
	}

	private static int number(TextField f) {
		String t = f.getText() == null ? "" : f.getText().trim();
		if (t.isEmpty()) {
			return 0;
		}
		try {
			int n = Integer.parseInt(t);
			if (n < 0) {
				throw new IllegalArgumentException("Targets cannot be negative.");
			}
			return n;
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Enter whole numbers for your targets.");
		}
	}

	/**
	 * Saves the four target fields to the profile (an invalid value is explained).
	 */
	private void commitTargets() {
		if (loading) {
			return;
		}
		UserProfile now = facade.getProfile();
		try {
			int kcal = number(kcalField), p = number(proteinField), c = number(carbsField), f = number(fatField);
			if (kcal != now.calorieGoal() || p != now.proteinGoal() || c != now.carbsGoal() || f != now.fatGoal()) {
				applying = true;
				facade.saveProfile(now.withCalorieGoal(kcal).withMacroGoals(p, c, f));
			}
			showError(targetErr, null);
		} catch (IllegalArgumentException e) {
			showError(targetErr, e.getMessage());
		} finally {
			applying = false;
		}
		drawTargetInfo();
		refreshSuggestion();
	}

	private void refreshTargetFields() {
		loading = true;
		try {
			UserProfile p = facade.getProfile();
			kcalField.setText(p.calorieGoal() == 0 ? "" : String.valueOf(p.calorieGoal()));
			proteinField.setText(p.proteinGoal() == 0 ? "" : String.valueOf(p.proteinGoal()));
			carbsField.setText(p.carbsGoal() == 0 ? "" : String.valueOf(p.carbsGoal()));
			fatField.setText(p.fatGoal() == 0 ? "" : String.valueOf(p.fatGoal()));
			showError(targetErr, null);
		} finally {
			loading = false;
		}
		drawTargetInfo();
	}

	/** The source label, the "adds up to" line and the safe-minimum warning. */
	private void drawTargetInfo() {
		UserProfile p = facade.getProfile();
		int sum = TargetCalculator.macroCalories(p.proteinGoal(), p.carbsGoal(), p.fatGoal());
		sumLabel.getStyleClass().removeAll("pf-sum", "pf-sum-off");
		if (p.calorieGoal() == 0) {
			sumLabel.setText(sum == 0 ? "Set your targets, or use Mealton’s suggestion below."
					: "Your macros add up to " + TargetCalculator.fmt(sum) + " kcal.");
			sumLabel.getStyleClass().add("pf-sum");
		} else {
			double off = Math.abs(sum - p.calorieGoal()) / (double) p.calorieGoal();
			if (off > 0.05) {
				sumLabel.setText("Your macros add up to " + TargetCalculator.fmt(sum) + " kcal — that’s "
						+ TargetCalculator.fmt(Math.abs(sum - p.calorieGoal())) + " kcal "
						+ (sum > p.calorieGoal() ? "over" : "under") + " your calorie target.");
				sumLabel.getStyleClass().add("pf-sum-off");
			} else {
				sumLabel.setText("Your macros add up to " + TargetCalculator.fmt(sum)
						+ " kcal, which matches your calorie target.");
				sumLabel.getStyleClass().add("pf-sum");
			}
		}
		boolean low = p.calorieGoal() > 0 && p.calorieGoal() < TargetCalculator.MIN_CALORIES;
		tooLow.setVisible(low);
		tooLow.setManaged(low);
		Optional<Suggestion> s = TargetCalculator.suggest(draft());
		boolean unset = p.calorieGoal() == 0 && sum == 0;
		boolean isSuggested = s.isPresent() && s.get().calories() == p.calorieGoal()
				&& s.get().protein() == p.proteinGoal() && s.get().carbs() == p.carbsGoal()
				&& s.get().fat() == p.fatGoal();
		targetsSource.setText(unset ? "Not set" : isSuggested ? "Using Mealton’s suggestion" : "Custom");
	}

	private void refreshSuggestion() {
		Optional<Suggestion> opt = TargetCalculator.suggest(draft());
		boolean has = opt.isPresent();
		suggestionBody.setVisible(has);
		suggestionBody.setManaged(has);
		sugEmpty.setVisible(!has);
		sugEmpty.setManaged(!has);
		cappedBox.setVisible(false);
		cappedBox.setManaged(false);
		if (!has) {
			whyText.setText("Once Mealton knows your age, height and weight it can estimate how much your body uses "
					+ "each day and suggest a target for your goal.");
			drawTargetInfo();
			return;
		}
		Suggestion s = opt.get();
		sugKcal.setText(TargetCalculator.fmt(s.calories()));
		sugFormula.setText(s.formulaText());
		sugP.setText(s.protein() + " g");
		sugC.setText(s.carbs() + " g");
		sugF.setText(s.fat() + " g");
		UserProfile p = facade.getProfile();
		boolean inUse = s.calories() == p.calorieGoal() && s.protein() == p.proteinGoal() && s.carbs() == p.carbsGoal()
				&& s.fat() == p.fatGoal();
		useButton.setText(inUse ? "In use" : "Use these targets");
		useButton.setDisable(inUse);
		if (s.capped()) {
			cappedLabel.setText("That goal would put you at " + TargetCalculator.fmt(s.rawCalories())
					+ " kcal. Mealton caps targets at a safe minimum of 1,200 kcal.");
			cappedBox.setVisible(true);
			cappedBox.setManaged(true);
		}
		whyText.setText(s.explanation());
		drawTargetInfoQuiet(inUse);
	}

	/**
	 * Updates only the "source" label (used by refreshSuggestion to avoid a loop).
	 */
	private void drawTargetInfoQuiet(boolean inUse) {
		UserProfile p = facade.getProfile();
		int sum = TargetCalculator.macroCalories(p.proteinGoal(), p.carbsGoal(), p.fatGoal());
		boolean unset = p.calorieGoal() == 0 && sum == 0;
		targetsSource.setText(unset ? "Not set" : inUse ? "Using Mealton’s suggestion" : "Custom");
	}

	private void useSuggestion() {
		Optional<Suggestion> s = TargetCalculator.suggest(draft());
		if (s.isEmpty()) {
			return;
		}
		try {
			applying = true;
			facade.saveProfile(facade.getProfile().withCalorieGoal(s.get().calories()).withMacroGoals(s.get().protein(),
					s.get().carbs(), s.get().fat()));
		} finally {
			applying = false;
		}
		refreshTargetFields();
		refreshSuggestion();
	}

	private void drawAccount() {
		UserProfile p = facade.getProfile();
		String name = p.name().isBlank() ? username : p.name();
		String email = p.email().isBlank() ? (username.contains("@") ? username : "") : p.email();
		accountName.setText(name);
		accountEmail.setText(email);
		accountEmail.setVisible(!email.isEmpty());
		accountEmail.setManaged(!email.isEmpty());
		avatar.setText(initials(name));
	}

	private static String initials(String name) {
		String[] words = name.trim().split("[\\s._@-]+");
		StringBuilder sb = new StringBuilder();
		for (String w : words) {
			if (!w.isEmpty() && sb.length() < 2) {
				sb.append(Character.toUpperCase(w.charAt(0)));
			}
		}
		return sb.length() == 0 ? "?" : sb.toString();
	}
}