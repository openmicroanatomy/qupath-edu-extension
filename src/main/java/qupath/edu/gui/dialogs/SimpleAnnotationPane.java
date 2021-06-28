package qupath.edu.gui.dialogs;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.javafx.scene.control.skin.Utils;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.text.TextBoundsType;
import org.controlsfx.control.MasterDetailPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.edu.gui.Browser;
import qupath.edu.util.ReflectionUtil;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.panes.PathObjectListCell;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.DefaultPathObjectComparator;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.objects.hierarchy.events.PathObjectSelectionListener;
import qupath.lib.roi.interfaces.ROI;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.stream.Collectors;

import static qupath.lib.gui.ActionTools.createAction;
import static qupath.lib.gui.ActionTools.createMenuItem;

public class SimpleAnnotationPane implements PathObjectSelectionListener, ChangeListener<ImageData<BufferedImage>>, PathObjectHierarchyListener {

    private final static Logger logger = LoggerFactory.getLogger(SimpleAnnotationPane.class);

    public static final String ANSWER_KEY = "eduAnswer";

    private QuPathGUI qupath;
    private ImageData<BufferedImage> imageData;
    private PathObjectHierarchy hierarchy;
    private BooleanProperty hasImageData = new SimpleBooleanProperty(false);

    private BorderPane pane = new BorderPane();

    /*
     * Request that we only synchronize to the primary selection; otherwise synchronizing to
     * multiple selections from long lists can be a performance bottleneck
     */
    private static boolean synchronizePrimarySelectionOnly = true;

    /*
     * List displaying annotations in the current hierarchy
     */
    private ListView<PathObject> listAnnotations;

    /*
     * Selection being changed by outside forces, i.e. don't fire an event
     */
    private boolean suppressSelectionChanges = false;

    private TextArea slideDescription = new TextArea();

    public void setSlideDescription(String description) {
        slideDescription.setText(description);

        // Empty text areas take up space: setting managed to false will stop rendering it.
        slideDescription.setManaged(!(Strings.isNullOrEmpty(description)));

        slideDescription.setPrefHeight(Utils.computeTextHeight(
            slideDescription.getFont(),
            description,
            slideDescription.getWidth(),
            10,
            TextBoundsType.VISUAL
        ));
    }

    private StringProperty descriptionProperty = new SimpleStringProperty();
    private StringProperty answerProperty = new SimpleStringProperty();
    private StringProperty showAnswerTextProperty = new SimpleStringProperty("No answer defined");

    public ListView<PathObject> getListAnnotations() {
        return listAnnotations;
    }

    public PathObjectHierarchy getHierarchy() {
        return hierarchy;
    }

    /**
     * Constructor.
     * @param qupath current QuPath instance.
     */
    public SimpleAnnotationPane(final QuPathGUI qupath) {
        // TODO: Make toggle between old annotation pane!
        this.qupath = qupath;

        setImageData(qupath.getImageData());

        Pane paneAnnotations = createAnnotationsPane();

        TextArea textDetail = new TextArea();
        textDetail.setWrapText(true);
        textDetail.textProperty().bind(descriptionProperty);

        MasterDetailPane mdPane = new MasterDetailPane();
        mdPane.setMasterNode(paneAnnotations);
        mdPane.setDetailNode(textDetail);
        mdPane.setDetailSide(Side.BOTTOM);
        mdPane.setShowDetailNode(true);
        mdPane.setDividerPosition(0.9);
        mdPane.showDetailNodeProperty().bind(descriptionProperty.isNotEmpty());

        pane.setCenter(mdPane);

        qupath.imageDataProperty().addListener(this);
    }

    private Pane createAnnotationsPane() {
        listAnnotations = new ListView<>();
        hierarchyChanged(null); // Force update

        listAnnotations.setCellFactory(v -> new PathObjectListCell());

        listAnnotations.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        listAnnotations.getSelectionModel().getSelectedItems().addListener(
                (ListChangeListener.Change<? extends PathObject> c) -> synchronizeHierarchySelectionToListSelection()
        );
        listAnnotations.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> synchronizeHierarchySelectionToListSelection());

        listAnnotations.setOnMouseClicked(e -> {
            if (e.getClickCount() > 1 && e.getButton() == MouseButton.PRIMARY) {
                PathObject pathObject = listAnnotations.getSelectionModel().getSelectedItem();
                if (pathObject == null || !pathObject.hasROI())
                    return;
                QuPathViewer viewer = qupath.getViewer();
                ROI roi = pathObject.getROI();

                zoomROI(viewer, roi);
                viewer.centerROI(roi);
            }
        });

        PathPrefs.colorDefaultObjectsProperty().addListener((v, o, n) -> listAnnotations.refresh());

        ContextMenu menuAnnotations = GuiTools.populateAnnotationsMenu(qupath, new ContextMenu());
        menuAnnotations.getItems().add(3,
            createMenuItem(createAction(() -> EditAnnotationAnswerDialog.openDialog(this), "Set answer properties"))
        );

        listAnnotations.setContextMenu(menuAnnotations);

        // Add the main annotation list
        BorderPane panelObjects = new BorderPane();

        // Add show answer button
        Button btnShowAnswer = new Button();
        btnShowAnswer.textProperty().bind(showAnswerTextProperty);
        btnShowAnswer.setOnAction(e -> {
            PathObjectHierarchy objectHierarchy = qupath.getImageData().getHierarchy();
            if (objectHierarchy == null)
                return;

            PathObject pathObject = objectHierarchy.getSelectionModel().getSelectedObject();

            if (pathObject != null && ReflectionUtil.retrieveMetadataValue(pathObject, ANSWER_KEY) != null) {
                if (isQuiz((String) ReflectionUtil.retrieveMetadataValue(pathObject, ANSWER_KEY))) {
                    showQuizDialog(pathObject);
                } else{
                    showAnswerDialog(pathObject);
                }
            }
        });

        btnShowAnswer.disableProperty().bind(answerProperty.isNull());
        btnShowAnswer.prefWidthProperty().bind(panelObjects.widthProperty());

        slideDescription.maxHeightProperty().bind(pane.heightProperty().divide(3).multiply(2));
        slideDescription.setMinHeight(0);
        slideDescription.setWrapText(true);
        slideDescription.setDisable(true);
        slideDescription.setStyle("-fx-opacity: 1.0;"); // Disabled text fields are gray because of lower opacity

        panelObjects.setTop(slideDescription);
        panelObjects.setCenter(listAnnotations);
        panelObjects.setBottom(btnShowAnswer);
        return panelObjects;
    }

    /**
     * Tries to focus the viewer into a ROI.
     *
     * @param viewer Viewer to zoom
     * @param roi ROI to zoom into
     */
    private void zoomROI(QuPathViewer viewer, ROI roi) {
        double areaROI;
        ImageServer<BufferedImage> server = viewer.getServer();

        if (roi.isLine()) {
            // Estimate area as an square
            areaROI = Math.pow(
                roi.getScaledLength(
                    server.getPixelCalibration().getPixelWidthMicrons(),
                    server.getPixelCalibration().getPixelHeightMicrons()
                ), 2
            );
        } else {
            areaROI = roi.getScaledArea(
                server.getPixelCalibration().getPixelWidthMicrons(),
                server.getPixelCalibration().getPixelHeightMicrons()
            );
        }

        double pixelCalibration = server.getPixelCalibration().getPixelHeightMicrons() * server.getMetadata().getMagnification();
        double areaViewer = (viewer.getView().getHeight() * pixelCalibration) * (viewer.getView().getWidth() * pixelCalibration);

        double magnification = Math.min(60, Math.sqrt(areaViewer / (areaROI * 2)));

        viewer.setMagnification(magnification);
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

    void setImageData(ImageData<BufferedImage> imageData) {
        if (this.imageData == imageData)
            return;

        // Deal with listeners for the current ImageData
        if (this.hierarchy != null) {
            hierarchy.removePathObjectListener(this);
            hierarchy.getSelectionModel().removePathObjectSelectionListener(this);
        }
        this.imageData = imageData;
        if (this.imageData != null) {
            hierarchy = imageData.getHierarchy();
            hierarchy.getSelectionModel().addPathObjectSelectionListener(this);
            hierarchy.addPathObjectListener(this);
            PathObject selected = hierarchy.getSelectionModel().getSelectedObject();
            listAnnotations.getItems().setAll(hierarchy.getAnnotationObjects());
            hierarchy.getSelectionModel().setSelectedObject(selected);
        } else {
            listAnnotations.getItems().clear();
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

        if (pathObjectSelected instanceof PathAnnotationObject) {
            PathAnnotationObject annotation = (PathAnnotationObject) pathObjectSelected;

            if (annotation.getDescription() != null && ReflectionUtil.retrieveMetadataValue(annotation, ANSWER_KEY) == null) {
                descriptionProperty.set(annotation.getDescription());
                answerProperty.set(null);
            } else {
                descriptionProperty.set(null);
                answerProperty.set((String) ReflectionUtil.retrieveMetadataValue(annotation, ANSWER_KEY));
            }
        } else {
            answerProperty.set(null);
        }

        answerProperty.addListener(((observable, oldValue, newValue) -> {
            if (newValue == null) {
                showAnswerTextProperty.set("No answer defined");
            } else {
                if (isQuiz(newValue)) {
                    showAnswerTextProperty.set("Show quiz");
                } else {
                    showAnswerTextProperty.set("Show answer");
                }
            }
        }));

        if (suppressSelectionChanges)
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


    /**
     * Quizzes are stored as JSON, which begin with <code>[{</code>
     */
    public static boolean isQuiz(String string) {
        return string != null && string.startsWith("[{");
    }

    private void showQuizDialog(PathObject pathObject) {
        try {
            List<MultichoiceOption> choices = List.of(new Gson().fromJson((String) ReflectionUtil.retrieveMetadataValue(pathObject, ANSWER_KEY), MultichoiceOption[].class));
            List<MultichoiceOption> answers = choices.stream().filter(MultichoiceOption::getIsAnswer).collect(Collectors.toList());

            MultichoiceOption result = (MultichoiceOption) Dialogs.showChoiceDialog("Select correct choice", pathObject.getName(), choices.toArray(), choices.get(0));

            if (result != null) {
                String message = result.getIsAnswer() ? "Right answer!" : "Wrong answer!";

                if (answers.size() > 1 || !(result.getIsAnswer())) {
                    message += "\n\n";
                    message += "All the right answers are: " + answers.toString().replaceAll("\\[|\\]", "");
                }

                String description = ((PathAnnotationObject) pathObject).getDescription();
                if (description != null) {
                    message += "\n\n";
                    message += description;
                }

                // TODO: Add support for retrying wrong answers

                Dialogs.showPlainMessage("Answer", message);
            }
        } catch (JsonSyntaxException ex) {
            logger.error("Error while parsing answer JSON", ex);
            showAnswerDialog(pathObject);
        }
    }

    private void showAnswerDialog(PathObject pathObject) {
        Dialogs.showMessageDialog(pathObject.getName(), (String) ReflectionUtil.retrieveMetadataValue(pathObject, ANSWER_KEY));
    }

    // todo: rework variable names & remember to update table property references
    public static class MultichoiceOption {

        private String choice;
        private Boolean isAnswer;

        public MultichoiceOption() {
            this("", false);
        }

        public MultichoiceOption(String choice) {
            this(choice, false);
        }

        public MultichoiceOption(String choice, boolean isAnswer) {
            this.choice = choice;
            this.isAnswer = isAnswer;
        }

        public String getChoice() {
            return choice;
        }

        public void setChoice(String choice) {
            this.choice = choice;
        }

        public boolean getIsAnswer() {
            return isAnswer;
        }

        public void setIsAnswer(boolean isAnswer) {
            this.isAnswer = isAnswer;
        }

        public Boolean getAnswer() {
            return isAnswer;
        }

        public void setAnswer(Boolean answer) {
            this.isAnswer = answer;
        }

        @Override
        public String toString() {
            return choice;
        }
    }


    @Override
    public void changed(ObservableValue<? extends ImageData<BufferedImage>> source, ImageData<BufferedImage> imageDataOld, ImageData<BufferedImage> imageDataNew) {
        setImageData(imageDataNew);

        if (imageDataNew == null) {
            setSlideDescription(null);
        } else {
            setSlideDescription(qupath.getProject().getEntry(imageDataNew).getDescription());
        }
    }

    @Override
    public void hierarchyChanged(PathObjectHierarchyEvent event) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> hierarchyChanged(event));
            return;
        }

        if (hierarchy == null) {
            listAnnotations.getItems().clear();
            return;
        }

        Collection<PathObject> newList = hierarchy.getObjects(new HashSet<>(), PathAnnotationObject.class);
        // If the lists are the same, we just need to refresh the appearance (because e.g. classifications or measurements now differ)
        // For some reason, 'equals' alone wasn't behaving nicely (perhaps due to ordering?)... so try a more manual test instead
//		if (newList.equals(listAnnotations.getItems())) {
        if (newList.size() == listAnnotations.getItems().size() && newList.containsAll(listAnnotations.getItems())) {
            // Don't refresh unless there is good reason to believe the list should appear different now
            // This was introduced due to flickering as annotations were dragged
            // TODO: Reconsider when annotation list is refreshed

//			listAnnotations.setStyle(".list-cell:empty {-fx-background-color: white;}");

//			if (event.getEventType() == HierarchyEventType.CHANGE_CLASSIFICATION || event.getEventType() == HierarchyEventType.CHANGE_MEASUREMENTS || (event.getStructureChangeBase() != null && event.getStructureChangeBase().isPoint()) || PathObjectTools.containsPointObject(event.getChangedObjects()))
            if (!event.isChanging())
                listAnnotations.refresh();
            return;
        }
        // If the lists are different, we need to update accordingly - but we don't want to trigger accidental selection updates
//		listAnnotations.getSelectionModel().clearSelection(); // Clearing the selection would cause annotations to disappear when interactively training a classifier!
        boolean lastChanging = suppressSelectionChanges;
        suppressSelectionChanges = true;
        listAnnotations.getItems().setAll(newList);
        suppressSelectionChanges = lastChanging;
    }
}
