package qupath.edu.tours;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.edu.EduExtension;
import qupath.edu.gui.CustomDialogs;
import qupath.edu.util.ReflectionUtil;
import qupath.edu.api.EduAPI;
import qupath.edu.api.Roles;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.QuPathViewerListener;
import qupath.lib.images.ImageData;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.PathObject;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

public class SlideTour implements QuPathViewerListener {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final String TOUR_ENTRIES_KEY = "TOUR_ENTRIES";

	private QuPathViewer viewer;

	private final SimpleBooleanProperty isMenuMinimizedProperty = new SimpleBooleanProperty(false);

	/**
	 * The index of currently opened entry.
	 */
	private final SimpleIntegerProperty currentIndexProperty = new SimpleIntegerProperty();
	private final SimpleStringProperty  entryTextProperty    = new SimpleStringProperty();

	private final ObservableList<SlideTourEntry> tourEntries = FXCollections.observableArrayList();
	private final BorderPane pane = new BorderPane();

	private ImageData<BufferedImage> imageData;
	private Collection<PathObject> annotations;

	/**
	 * Has the ImageData been modified. Slide Tours add new annotations & remove, which makes
	 * QuPath think that some modifications were made. This value is restored once the tour is over.
	 *
	 * Editing a tour entry will set this to true to ensure that changes to tours are saved.
	 */
	private boolean imageDataChanged;

	private boolean isTourActive = false;

	public SlideTour(QuPathViewer viewer) {
		this.viewer = viewer;

		currentIndexProperty.addListener((obs, oldValue, newValue) -> {
			int index = newValue.intValue();

			if (index == -1 || index >= tourEntries.size()) {
				entryTextProperty.set("No tour of this slide available.");
			} else if (isVisible()) {
				SlideTourEntry entry = tourEntries.get(index);

				if (entry.getText() == null) {
					entryTextProperty.set("Description not set");
				} else {
					entryTextProperty.set(entry.getText());
				}

				smoothZoomAndPan(entry.getX(), entry.getY(), entry.getMagnification(), entry.getRotation());

				viewer.getImageData().getHierarchy().getSelectionModel().clearSelection();
				viewer.getImageData().getHierarchy().clearAll();
				viewer.getImageData().getHierarchy().addPathObjects(entry.getAnnotations());
			}
		});

		viewer.addViewerListener(this);
	}

	public void setVisible(boolean visible) {
		pane.setVisible(visible);
	}

	public boolean isVisible() {
		return pane.isVisible();
	}

	public Node getNode() {
		pane.setPadding(new Insets(10));
		pane.setBackground(new Background(new BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY)));
		pane.setBorder(new Border(new BorderStroke(
			Color.LIGHTGRAY, Color.LIGHTGRAY, Color.LIGHTGRAY, Color.LIGHTGRAY,
			BorderStrokeStyle.SOLID, BorderStrokeStyle.SOLID, BorderStrokeStyle.SOLID, BorderStrokeStyle.SOLID,
			CornerRadii.EMPTY, new BorderWidths(1), Insets.EMPTY))
		);

		drawPane();

		return pane;
	}

	private void startTour() {
		isTourActive = true;

		drawPane();

		imageDataChanged = imageData.isChanged();

		annotations = imageData.getHierarchy().getAnnotationObjects();
		viewer.getImageData().getHierarchy().getSelectionModel().clearSelection();
		viewer.getImageData().getHierarchy().clearAll();

		// Done this way to trigger indexProperty change.
		if (tourEntries.size() == 0) {
			this.currentIndexProperty.set(0); this.currentIndexProperty.set(-1);
		} else {
			this.currentIndexProperty.set(-1); this.currentIndexProperty.set(0);
		}

		ReflectionUtil.setAnalysisPaneVisible(false);
	}

	private void endTour() {
		isTourActive = false;

		drawPane();

		stopAnimation();

		viewer.getImageData().getHierarchy().getSelectionModel().clearSelection();
		viewer.getImageData().getHierarchy().clearAll();
		viewer.getImageData().getHierarchy().addPathObjects(annotations);

		imageData.setChanged(imageDataChanged);
		ReflectionUtil.setAnalysisPaneVisible(true);
	}

	private synchronized void drawPane() {
		pane.setVisible(viewer.getImageData() != null);

		if (isTourActive) {
			drawTourPane();
		} else if (tourEntries.size() > 0 || EduAPI.hasRole(Roles.MANAGE_PROJECTS)) {
			drawTourStartPane();
		} else {
			pane.setVisible(false);
		}
	}

	private void drawTourStartPane() {
		/* Buttons */

		Button btnStartTour = new Button("Start tour");
		btnStartTour.setOnAction(e -> startTour());
		btnStartTour.visibleProperty().bind(isMenuMinimizedProperty.not());
		btnStartTour.managedProperty().bind(isMenuMinimizedProperty.not());

		Button btnMaximize = new Button("\u2bc6");
		btnMaximize.setTooltip(new Tooltip("Maximize"));
		btnMaximize.visibleProperty().bind(isMenuMinimizedProperty);
		btnMaximize.managedProperty().bind(isMenuMinimizedProperty);
		btnMaximize.setOnAction(e -> isMenuMinimizedProperty.set(false));

		Button btnMinimize = new Button("\u2bc5");
		btnMinimize.setTooltip(new Tooltip("Minimize"));
		btnMinimize.visibleProperty().bind(isMenuMinimizedProperty.not());
		btnMinimize.managedProperty().bind(isMenuMinimizedProperty.not());
		btnMinimize.setOnAction(e -> isMenuMinimizedProperty.set(true));

		GridPane buttons = new GridPane();
		buttons.add(btnMinimize, 0, 0);
		buttons.add(btnMaximize, 0, 0);
		buttons.add(btnStartTour, 1, 0);
		buttons.setHgap(5);

		/* Pane */

		pane.setPrefWidth(Region.USE_COMPUTED_SIZE);
		pane.setTop(buttons);
		pane.setCenter(null);
	}

	private void drawTourPane() {
		/* Text */

		Text text = new Text();
		text.textProperty().bind(entryTextProperty);
		text.setWrappingWidth(300);

		/* Buttons */

		Button btnExit = new Button("Exit");
		btnExit.setOnAction(e -> endTour());

		Button btnNext = new Button("Next");
		btnNext.setOnAction(a -> currentIndexProperty.set(currentIndexProperty.get() + 1));
		btnNext.disableProperty().bind(currentIndexProperty.isEqualTo(Bindings.size(tourEntries).subtract(1)));

		Button btnPrevious = new Button("Previous");
		btnPrevious.setOnAction(a -> currentIndexProperty.set(currentIndexProperty.get() - 1));
		btnPrevious.disableProperty().bind(currentIndexProperty.lessThanOrEqualTo(0));

		MenuItem btnNew = new MenuItem("Create New");
		btnNew.setOnAction(a -> createNewEntry());

		MenuItem btnEdit = new MenuItem("Edit Current");
		btnEdit.setOnAction(a -> editCurrentEntry());
		btnEdit.disableProperty().bind(currentIndexProperty.isEqualTo(-1));

		MenuItem btnDelete = new MenuItem("Delete Current");
		btnDelete.setOnAction(a -> deleteCurrentEntry());
		btnDelete.disableProperty().bind(currentIndexProperty.isEqualTo(-1));

		MenuItem btnViewAll = new MenuItem("View all frames");
		btnViewAll.setOnAction(a -> viewAllEntries());

		MenuButton btnMore = new MenuButton("More \u22ee");
		btnMore.disableProperty().bind(EduExtension.getEditModeManager().editModeEnabledProperty().not());
		btnMore.getItems().addAll(btnNew, btnEdit, btnDelete, btnViewAll);

		GridPane buttons = PaneTools.createColumnGridControls(btnExit, btnPrevious, btnNext, btnMore);
		buttons.setHgap(5);

		/* Pane */

		BorderPane.setMargin(text, new Insets(10, 0, 10, 0));
		pane.setPrefWidth(300);
		pane.setTop(buttons);
		pane.setCenter(text);
	}

	private void deleteCurrentEntry() {
		var confirm = Dialogs.showConfirmDialog("Delete entry", "Are you sure you want to delete this entry?");

		if (!confirm) {
			return;
		}

		tourEntries.remove(currentIndexProperty.get());
		currentIndexProperty.set(tourEntries.size() - 1);

		syncSlideTours();
	}

	private void editCurrentEntry() {
		SlideTourEntry entry = tourEntries.get(currentIndexProperty.get());

		String[] choices = { "Everything", "Viewer position", "Annotations", "Text" };
		String edit = Dialogs.showChoiceDialog("Edit ...", "Choose what to edit", choices, "Everything");

		if (edit == null) {
			return;
		}

		switch (edit) {
			case "Everything" -> {
				editLocation(entry);
				editAnnotations(entry);
				editText(entry);
			}
			case "Viewer position" -> editLocation(entry);
			case "Annotations" -> editAnnotations(entry);
			case "Text" -> editText(entry);
		}

		syncSlideTours();
	}

	private void editLocation(SlideTourEntry entry) {
		entry.setLocation(viewer.getCenterPixelX(), viewer.getCenterPixelY(), viewer.getMagnification(), viewer.getRotation());
	}

	private void editText(SlideTourEntry entry) {
		String text = CustomDialogs.showTextAreaDialog("Text", "", entry.getText());

		if (text != null) {
			entry.setText(text);
			entryTextProperty.set(text);
		}
	}

	private void editAnnotations(SlideTourEntry entry) {
		entry.setAnnotations(viewer.getImageData().getHierarchy().getAnnotationObjects());
	}

	private void createNewEntry() {
		double x = viewer.getCenterPixelX();
		double y = viewer.getCenterPixelY();
		double magnification = viewer.getMagnification();
		double rotation = viewer.getRotation();

		tourEntries.add(new SlideTourEntry(null, x, y, magnification, rotation, Collections.emptyList()));

		viewer.setMagnification(1);
		viewer.setCenterPixelLocation(x, y);
		viewer.setRotation(0);

		currentIndexProperty.set(tourEntries.size() - 1);

		syncSlideTours();
	}

	private static final DataFormat SERIALIZED_MIME_TYPE = new DataFormat("application/x-java-serialized-object");

	private void viewAllEntries() {
		/* Table */

		TableView<SlideTourEntry> table = new TableView<>();
		table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
		table.setPlaceholder(new Text("No entries for this slide tour."));

		/* Re-order entries */

		table.setRowFactory(tableView -> {
			TableRow<SlideTourEntry> row = new TableRow<>();

			row.setOnMouseClicked(event -> {
				if (!row.isEmpty() && event.getClickCount() == 1 && event.getButton() == MouseButton.PRIMARY) {
					currentIndexProperty.set(tourEntries.indexOf(row.getItem()));
				}
			});

			row.setOnDragDetected(event -> {
				if (!row.isEmpty()) {
					Integer index = row.getIndex();
					Dragboard db = row.startDragAndDrop(TransferMode.MOVE);
					db.setDragView(row.snapshot(null, null));
					ClipboardContent cc = new ClipboardContent();
					cc.put(SERIALIZED_MIME_TYPE, index);
					db.setContent(cc);
					event.consume();
				}
			});

			row.setOnDragOver(event -> {
				Dragboard db = event.getDragboard();

				if (db.hasContent(SERIALIZED_MIME_TYPE)) {
					if (row.getIndex() != (Integer) db.getContent(SERIALIZED_MIME_TYPE)) {
						event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
						event.consume();
					}
				}
			});

			row.setOnDragDropped(event -> {
				Dragboard db = event.getDragboard();

				if (db.hasContent(SERIALIZED_MIME_TYPE)) {
					int draggedIndex = (Integer) db.getContent(SERIALIZED_MIME_TYPE);
					SlideTourEntry draggedPerson = tableView.getItems().remove(draggedIndex);

					int dropIndex;

					if (row.isEmpty()) {
						dropIndex = tableView.getItems().size();
					} else {
						dropIndex = row.getIndex();
					}

					tableView.getItems().add(dropIndex, draggedPerson);

					event.setDropCompleted(true);
					tableView.getSelectionModel().select(dropIndex);
					event.consume();
					syncSlideTours();
				}
			});

			return row;
		});

		TableColumn<SlideTourEntry, String> indexColumn = new TableColumn<>("#");
		indexColumn.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(tourEntries.indexOf(data.getValue()) + 1)));
		indexColumn.setReorderable(false);
		indexColumn.setMinWidth(28);
		indexColumn.setMaxWidth(28);
		indexColumn.setStyle("-fx-alignment: center");

		TableColumn<SlideTourEntry, String> textColumn = new TableColumn<>("Text");
		textColumn.setCellValueFactory(new PropertyValueFactory<>("text"));
		textColumn.setReorderable(false);

		table.getColumns().addAll(indexColumn, textColumn);
		table.setItems(tourEntries);

		table.setPrefWidth(600);
		table.setPrefHeight(300);

		/* Buttons */

		Button btnEdit = new Button("Edit selected");
		btnEdit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
		btnEdit.setOnAction(a -> { editCurrentEntry(); table.refresh(); });

		Button btnDelete = new Button("Delete selected");
		btnDelete.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
		btnDelete.setOnAction(a -> deleteCurrentEntry());

		Button btnNew = new Button("Create new entry");
		btnNew.setOnAction(a -> createNewEntry());

		GridPane buttons = PaneTools.createColumnGridControls(btnEdit, btnDelete, btnNew);
		buttons.setHgap(5);

		/* Pane */

		BorderPane pane = new BorderPane();
		pane.setCenter(table);
		pane.setBottom(buttons);

		BorderPane.setMargin(table, new Insets(10, 0, 10, 0));

		Dialogs.builder()
				.title("Slide Tour entries")
				.width(600)
				.height(300)
				.resizable()
				.content(pane)
				.buttons(ButtonType.CLOSE)
				.modality(Modality.NONE)
				.build()
				.show();
	}

	/**
	 * Saves the entries in the current images metadata.
	 */
	private void syncSlideTours() {
		try {
			imageDataChanged = true;
			this.imageData.setProperty(TOUR_ENTRIES_KEY, GsonTools.getInstance().toJson(tourEntries));
		} catch (Exception e) {
			logger.error("Error when saving slide tour data", e);
		}
	}

	private Timeline timeline;

	private void smoothZoomAndPan(double x, double y, double magnification, double rotation) {
		double currentMagnification = viewer.getMagnification();
		double currentRotation = viewer.getRotation();
		double currentX = viewer.getCenterPixelX();
		double currentY = viewer.getCenterPixelY();

		double diffMagnification = magnification - currentMagnification;
		double diffRotation = rotation - currentRotation;
		double diffX = x - currentX;
		double diffY = y - currentY;
		int diff = (int) Math.hypot(diffX, diffY);

		// 2500 pixels is travelled in 1000 ms, up to maximum of 5000 ms
		int maxSteps = Math.min(5, Math.max(1, (diff / 2500))) * 20;
		// Utils.clamp();
		AtomicInteger steps = new AtomicInteger(1);

		stopAnimation();

		// TODO: High CPU usage occasionally
		timeline = new Timeline(
			new KeyFrame(
				Duration.millis(50),
				event -> {
					double multiplier = Math.min(1, 1.0 * steps.get() / maxSteps);
					viewer.setMagnification(currentMagnification + diffMagnification * multiplier);
					viewer.setCenterPixelLocation(currentX + diffX * multiplier, currentY + diffY * multiplier);
					viewer.setRotation(currentRotation + diffRotation * multiplier);

					steps.getAndIncrement();
				}
			)
		);

		timeline.setCycleCount(maxSteps);
		timeline.play();
	}

	/**
	 * Stops any active panning & zooming animation.
	 */
	public void stopAnimation() {
		if (timeline != null && timeline.getStatus() == Animation.Status.RUNNING) {
			timeline.stop();
		}
	}

	@Override
	public void imageDataChanged(QuPathViewer viewer, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
		this.viewer = viewer;
		this.imageData = imageDataNew;
		this.isTourActive = false;
		this.tourEntries.clear();

		if (imageData != null && imageData.getProperties().containsKey(TOUR_ENTRIES_KEY)) {
			try {
				SlideTourEntry[] entries = GsonTools.getInstance().fromJson((String) imageData.getProperty(TOUR_ENTRIES_KEY), SlideTourEntry[].class);
				this.tourEntries.addAll(FXCollections.observableArrayList(entries));
			} catch (Exception e) {
				logger.error("Error when loading slide tour", e);
			}
		}

		drawPane();
	}

	@Override
	public void viewerClosed(QuPathViewer viewer) {
		this.viewer = null;
		setVisible(false);
	}

	@Override
	public void visibleRegionChanged(QuPathViewer viewer, Shape shape) {

	}

	@Override
	public void selectedObjectChanged(QuPathViewer viewer, PathObject pathObjectSelected) {

	}
}
