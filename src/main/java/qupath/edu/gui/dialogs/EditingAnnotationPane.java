package qupath.edu.gui.dialogs;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import org.controlsfx.glyphfont.FontAwesome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.controls.PredicateTextField;
import qupath.fx.utils.GridPaneUtils;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.gui.panes.ObjectDescriptionPane;
import qupath.lib.gui.panes.SelectedMeasurementTableView;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.IconFactory;
import qupath.lib.gui.tools.PathObjectLabels;
import qupath.lib.images.ImageData;
import qupath.lib.objects.DefaultPathObjectComparator;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.objects.hierarchy.events.PathObjectSelectionListener;
import qupath.lib.regions.ImagePlane;

import java.awt.image.BufferedImage;
import java.util.*;

import static qupath.lib.gui.actions.ActionTools.createAction;
import static qupath.lib.gui.actions.ActionTools.createMenuItem;

/**
 * This is a stripped-down version of {@link qupath.lib.gui.panes.AnnotationPane} with
 * limited features intended for teachers doing minimal annotation changes.
 */
public class EditingAnnotationPane implements PathObjectSelectionListener, ChangeListener<ImageData<BufferedImage>>, PathObjectHierarchyListener {

    private static StringProperty titleMeasurements = QuPathResources.getLocalizedResourceManager().createProperty("AnalysisPane.measurementsTab");
    private static StringProperty titleDescription = QuPathResources.getLocalizedResourceManager().createProperty("AnalysisPane.descriptionTab");

    private static final Logger logger = LoggerFactory.getLogger(qupath.lib.gui.panes.AnnotationPane.class);

    private final QuPathGUI qupath;

    // Need to preserve this to guard against garbage collection
    @SuppressWarnings("unused")
    private final ObservableValue<ImageData<BufferedImage>> imageDataProperty;

    // For convenience - store current image & hierarchy
    private ImageData<BufferedImage> imageData;
    private PathObjectHierarchy hierarchy;

    private final BooleanProperty disableUpdates = new SimpleBooleanProperty(false);

    private final BooleanProperty hasImageData = new SimpleBooleanProperty(false);

    private final BorderPane pane = new BorderPane();

    /*
     * Request that we only synchronize to the primary selection; otherwise synchronizing to
     * multiple selections from long lists can be a performance bottleneck
     */
    private static boolean synchronizePrimarySelectionOnly = true;

    private final PredicateTextField<PathObject> filter = new PredicateTextField<>(PathObject::getDisplayedName);
    private final ObservableList<PathObject> allAnnotations = FXCollections.observableArrayList();
    private final FilteredList<PathObject> filteredAnnotations = new FilteredList<>(allAnnotations);

    private final ContextMenu menuAnnotations = new ContextMenu();

    /*
     * List displaying annotations in the current hierarchy
     */
    private final ListView<PathObject> listAnnotations = new ListView<>(filteredAnnotations);

    /*
     * Selection being changed by outside forces, i.e. don't fire an event
     */
    private boolean suppressSelectionChanges = false;

    /**
     * Constructor.
     * @param qupath current QuPath instance.
     */
    public EditingAnnotationPane(final QuPathGUI qupath) {
        this(qupath, qupath.imageDataProperty());
    }

    /**
     * Constructor.
     * @param qupath current QuPath instance.
     * @param imageDataProperty the current {@link ImageData}
     */
    public EditingAnnotationPane(final QuPathGUI qupath, ObservableValue<ImageData<BufferedImage>> imageDataProperty) {
        this.qupath = qupath;
        this.imageDataProperty = imageDataProperty;
        this.disableUpdates.addListener((v, o, n) -> {
            if (!n)
                enableUpdates();
        });

        setImageData(imageDataProperty.getValue());

        initializeFilter();
        initializeAnnotationList();
        GuiTools.populateAnnotationsMenu(qupath, menuAnnotations);
        menuAnnotations.getItems().add(3,
            createMenuItem(createAction(() -> EditAnnotationAnswerDialog.openDialog(this), "Set answer properties"))
        );

        var paneAnnotations = createAnnotationTitledPane();
        var tabAnnotationsMeasurements = createMeasurementsAndDescriptionsPane();

        SplitPane splitAnnotations = new SplitPane();
        splitAnnotations.setOrientation(Orientation.VERTICAL);
        splitAnnotations.setDividerPosition(0, 0.5);
        splitAnnotations.getItems().addAll(paneAnnotations, tabAnnotationsMeasurements);

        pane.setCenter(splitAnnotations);
        imageDataProperty.addListener(this);
    }

    /**
     * Property that may be used to prevent updates on every hierarchy or selection change event.
     * This can be used to improve performance by preventing the list being updated even when
     * it is not visible to the user.
     * @return
     */
    public BooleanProperty disableUpdatesProperty() {
        return disableUpdates;
    }

    private void enableUpdates() {
        if (hierarchy == null)
            return;
        hierarchyChanged(PathObjectHierarchyEvent.createStructureChangeEvent(this, hierarchy, hierarchy.getRootObject()));
        selectedPathObjectChanged(hierarchy.getSelectionModel().getSelectedObject(), null, hierarchy.getSelectionModel().getSelectedObjects());
    }

    private void initializeFilter() {
        filter.setPromptText("Filter annotations");
        filter.setIgnoreCase(true);
        filteredAnnotations.predicateProperty().bind(filter.predicateProperty());
    }

    private void initializeAnnotationList() {
        hierarchyChanged(null); // Force update

        listAnnotations.setCellFactory(v -> PathObjectLabels.createListCell());

        listAnnotations.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        listAnnotations.getSelectionModel().getSelectedItems().addListener(
            (ListChangeListener.Change<? extends PathObject> c) -> synchronizeHierarchySelectionToListSelection()
        );
        listAnnotations.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> synchronizeHierarchySelectionToListSelection());

        listAnnotations.setOnMouseClicked(e -> {
            if (e.getClickCount() > 1) {
                PathObject pathObject = listAnnotations.getSelectionModel().getSelectedItem();
                if (pathObject == null || !pathObject.hasROI())
                    return;
                qupath.getViewer().centerROI(pathObject.getROI());
            }
        });

        PathPrefs.colorDefaultObjectsProperty().addListener((v, o, n) -> listAnnotations.refresh());
        listAnnotations.setContextMenu(menuAnnotations);
    }

    private TabPane createMeasurementsAndDescriptionsPane() {
        var tabpaneObjectsShared = new TabPane();

        var objectMeasurementsTable = new SelectedMeasurementTableView(qupath.imageDataProperty());

        tabpaneObjectsShared.setSide(Side.BOTTOM);
        var tabSharedTable = createTab(titleMeasurements, objectMeasurementsTable.getPane());
        var descriptionPane = ObjectDescriptionPane.createPane(qupath.imageDataProperty(), true);
        var tabSharedDescription = createTab(titleDescription, descriptionPane);

        tabpaneObjectsShared.getTabs().addAll(tabSharedDescription, tabSharedTable);
        tabpaneObjectsShared.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        return tabpaneObjectsShared;
    }

    private static Tab createTab(StringProperty title, Node content) {
        var tab = new Tab();
        tab.textProperty().bind(title);
        if (content != null)
            tab.setContent(content);
        return tab;
    }

    private TitledPane createAnnotationTitledPane() {

        // Add the main annotation list
        BorderPane panelObjects = new BorderPane();
        panelObjects.setCenter(listAnnotations);

        // Add buttons
        Button btnSelectAll = new Button("Select all");
        btnSelectAll.setOnAction(e -> listAnnotations.getSelectionModel().selectAll());
        btnSelectAll.setTooltip(new Tooltip("Select all annotations"));

        Button btnDelete = new Button("Delete");
        btnDelete.setOnAction(e -> GuiTools.promptToClearAllSelectedObjects(imageData));
        btnDelete.setTooltip(new Tooltip("Delete all selected objects"));

        // Create a button to show context menu (makes it more obvious to the user that it exists)
        Button btnMore = GuiTools.createMoreButton(menuAnnotations, Side.RIGHT);
        GridPane panelButtons = new GridPane();
        panelButtons.add(btnSelectAll, 0, 0);
        panelButtons.add(btnDelete, 1, 0);
        panelButtons.add(btnMore, 2, 0);
        GridPane.setHgrow(btnSelectAll, Priority.ALWAYS);
        GridPane.setHgrow(btnDelete, Priority.ALWAYS);

        GridPaneUtils.setMaxWidth(Double.MAX_VALUE,
            btnSelectAll, btnDelete);

        BooleanBinding disableButtons = hasImageData.not();
        btnSelectAll.disableProperty().bind(disableButtons);
        btnDelete.disableProperty().bind(disableButtons);
        btnMore.disableProperty().bind(disableButtons);

        // Add support for delete/backspace
        listAnnotations.addEventFilter(KeyEvent.KEY_RELEASED, e -> {
            if (e.isConsumed())
                return;
            if (e.getCode() == KeyCode.BACK_SPACE || e.getCode() == KeyCode.DELETE) {
                btnDelete.fire();
                e.consume();
            }
        });

        panelObjects.setBottom(new VBox(filter, panelButtons));

        var btnProperties = new Button(null, IconFactory.createNode(FontAwesome.Glyph.PENCIL, 12));
        btnProperties.setTooltip(new Tooltip("Set selected annotation properties"));
        btnProperties.disableProperty().bind(Bindings.isEmpty(listAnnotations.getSelectionModel().getSelectedItems()));
        btnProperties.setOnAction(e -> {
            var hierarchy = qupath.getViewer().getHierarchy();
            if (hierarchy != null) {
                // TODO: We lose the selection here...
                GuiTools.promptToSetActiveAnnotationProperties(hierarchy);
                // Try to recover the selection
                var model = hierarchy.getSelectionModel();
                selectedPathObjectChanged(
                    model.getSelectedObject(), model.getSelectedObject(), model.getSelectedObjects());
            }
        });


        var titled = GuiTools.createLeftRightTitledPane("Annotation list", btnProperties);
        titled.textProperty().bind(Bindings.createStringBinding(() -> {
            int nAll = allAnnotations.size();
            int nFiltered = filteredAnnotations.size();
            if (nAll == 0)
                return "Annotation list";
            else if (nAll == nFiltered)
                return "Annotation list (" + nAll + ")";
            else
                return "Annotation list (" + nFiltered + "/" + nAll + ")";
        }, allAnnotations, filteredAnnotations));
        // TODO: Consider additional buttons (e.g. to delete)

        titled.setContent(panelObjects);
        panelObjects.setPadding(Insets.EMPTY);
        titled.setCollapsible(false);
        titled.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        return titled;
    }


    /**
     * Update the selected objects in the hierarchy to match those in the list,
     * unless selection changes should be suppressed.
     */
    void synchronizeHierarchySelectionToListSelection() {
        if (hierarchy == null || suppressSelectionChanges)
            return;
        suppressSelectionChanges = true;
        Set<PathObject> selectedSet = new HashSet<>(listAnnotations.getSelectionModel().getSelectedItems());
        PathObject selectedObject = listAnnotations.getSelectionModel().getSelectedItem();
        if (!selectedSet.contains(selectedObject))
            selectedObject = null;
        hierarchy.getSelectionModel().setSelectedObjects(selectedSet, selectedObject);
        suppressSelectionChanges = false;
    }

    /**
     * Get the pane for display.
     * @return
     */
    public Pane getPane() {
        return pane;
    }

    public ListView<PathObject> getListAnnotations() {
        return listAnnotations;
    }

    public PathObjectHierarchy getHierarchy() {
        return hierarchy;
    }

    void setImageData(ImageData<BufferedImage> imageData) {
        if (this.imageData == imageData)
            return;

        // Deal with listeners for the current ImageData
        if (this.hierarchy != null) {
            hierarchy.removeListener(this);
            hierarchy.getSelectionModel().removePathObjectSelectionListener(this);
        }
        this.imageData = imageData;
        if (this.imageData != null) {
            hierarchy = imageData.getHierarchy();
            hierarchy.getSelectionModel().addPathObjectSelectionListener(this);
            hierarchy.addListener(this);
            PathObject selected = hierarchy.getSelectionModel().getSelectedObject();
            allAnnotations.setAll(hierarchy.getAnnotationObjects());
            hierarchy.getSelectionModel().setSelectedObject(selected);
        } else {
            allAnnotations.clear();
            hierarchy = null;
        }
        hasImageData.set(this.imageData != null);
    }

    @Override
    public void selectedPathObjectChanged(final PathObject pathObjectSelected, final PathObject previousObject, Collection<PathObject> allSelected) {
        if (!Platform.isFxApplicationThread()) {
            // Do not synchronize to changes on other threads (since these may interfere with scripts)
//			Platform.runLater(() -> selectedPathObjectChanged(pathObjectSelected, previousObject, allSelected));
            return;
        }

        if (suppressSelectionChanges || disableUpdates.get())
            return;

        suppressSelectionChanges = true;
        if (synchronizePrimarySelectionOnly) {
            try {
                var listSelectionModel = listAnnotations.getSelectionModel();
                listSelectionModel.clearSelection();
                if (pathObjectSelected != null && pathObjectSelected.isAnnotation()) {
                    listSelectionModel.select(pathObjectSelected);
                    listAnnotations.scrollTo(pathObjectSelected);
                }
                return;
            } finally {
                suppressSelectionChanges = false;
            }
        }

        try {

            var hierarchySelected = new TreeSet<>(DefaultPathObjectComparator.getInstance());
            hierarchySelected.addAll(allSelected);

            // Determine the objects to select
            MultipleSelectionModel<PathObject> model = listAnnotations.getSelectionModel();
            List<PathObject> selected = new ArrayList<>();
            for (PathObject pathObject : hierarchySelected) {
                if (pathObject == null)
                    logger.warn("Selected object is null!");
                else if (pathObject.isAnnotation())
                    selected.add(pathObject);
            }
            if (selected.isEmpty()) {
                if (!model.isEmpty())
                    model.clearSelection();
                return;
            }
            // Check if we're making changes
            List<PathObject> currentlySelected = model.getSelectedItems();
            if (selected.size() == currentlySelected.size() && (hierarchySelected.containsAll(currentlySelected))) {
                listAnnotations.refresh();
                return;
            }

            if (hierarchySelected.containsAll(listAnnotations.getItems())) {
                model.selectAll();
                return;
            }

            int[] inds = new int[selected.size()];
            int i = 0;
            model.clearSelection();
            boolean firstInd = true;
            for (PathObject temp : selected) {
                int idx = listAnnotations.getItems().indexOf(temp);
                if (idx >= 0 && firstInd) {
                    Arrays.fill(inds, idx);
                    firstInd = false;
                }
                inds[i] = idx;
                i++;
            }

            if (inds.length == 1 && pathObjectSelected instanceof PathAnnotationObject)
                listAnnotations.scrollTo(pathObjectSelected);

            if (firstInd) {
                suppressSelectionChanges = false;
                return;
            }
            if (inds.length == 1)
                model.select(inds[0]);
            else if (inds.length > 1)
                model.selectIndices(inds[0], inds);
        } finally {
            suppressSelectionChanges = false;
        }
    }

    @Override
    public void changed(ObservableValue<? extends ImageData<BufferedImage>> source, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
        setImageData(imageDataNew);
    }

    @Override
    public void hierarchyChanged(PathObjectHierarchyEvent event) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> hierarchyChanged(event));
            return;
        }

        if (hierarchy == null) {
            allAnnotations.clear();
            return;
        }

        if (disableUpdates.get())
            return;

        // Create a sorted list of annotations
        // (Could use a treeset to sort as we insert, but doesn't seem to be faster)
        var annotationSet = hierarchy.getObjects(new HashSet<>(), PathAnnotationObject.class);
        // This code tries to stop early if the hierarchy is changed, to avoid sluggishness when dragging an annotation
        // in an image with very large numbers of annotations
        if (event.isChanging() &&
            annotationSet.size() == listAnnotations.getItems().size() &&
            annotationSet.containsAll(listAnnotations.getItems())) {
            return;
        }
        // If we're done changing, make sure we have sorted the annotations - this operation can be expensive
        // if we have a very large number of annotations
        List<PathObject> newList = new ArrayList<>(annotationSet);
        newList.sort(annotationListComparator);

        // If the lists are the same, we just need to refresh the appearance (because e.g. classifications or measurements now differ)
        // For some reason, 'equals' alone wasn't behaving nicely (perhaps due to ordering?)... so try a more manual test instead
        if (newList.equals(allAnnotations)) {
            // Don't refresh unless there is good reason to believe the list should appear different now
            // This was introduced due to flickering as annotations were dragged
            // TODO: Reconsider when annotation list is refreshed
//			if (event.getEventType() == HierarchyEventType.CHANGE_CLASSIFICATION || event.getEventType() == HierarchyEventType.CHANGE_MEASUREMENTS || (event.getStructureChangeBase() != null && event.getStructureChangeBase().isPoint()) || PathObjectTools.containsPointObject(event.getChangedObjects()))
            if (!event.isChanging())
                listAnnotations.refresh();
            return;
        }
        // If the lists are different, we need to update accordingly - but we don't want to trigger accidental selection updates
//		listAnnotations.getSelectionModel().clearSelection(); // Clearing the selection would cause annotations to disappear when interactively training a classifier!
        boolean lastChanging = suppressSelectionChanges;
        suppressSelectionChanges = true;
        allAnnotations.setAll(newList);
        suppressSelectionChanges = lastChanging;
    }

    Comparator<PathObject> annotationListComparator = Comparator.nullsFirst(Comparator
        .comparing(this::getImagePlane)
        .thenComparing(this::getClassificationString) // Since v0.6.0 - previously 'toString()' but this was slow!
        .thenComparingDouble(this::getBoundsY)
        .thenComparingDouble(this::getBoundsX)
        .thenComparing(PathObject::getID)
    );

    private double getBoundsX(PathObject pathObject) {
        var roi = pathObject == null ? null : pathObject.getROI();
        return roi == null ? -1 : roi.getBoundsX();
    }

    private double getBoundsY(PathObject pathObject) {
        var roi = pathObject == null ? null : pathObject.getROI();
        return roi == null ? -1 : roi.getBoundsY();
    }

    private ImagePlane getImagePlane(PathObject pathObject) {
        var roi = pathObject == null ? null : pathObject.getROI();
        return roi == null ? ImagePlane.getDefaultPlane() : roi.getImagePlane();
    }

    private String getClassificationString(PathObject pathObject) {
        var c = pathObject == null ? null : pathObject.getClassification();
        return c == null ? "" : c;
    }

}