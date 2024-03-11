package qupath.ext.ui;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.controlsfx.control.CheckListView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.ImageView;
import qupath.ext.gdcnn.AnnotationExportTask;
import qupath.ext.gdcnn.ClassificationTask;
import qupath.ext.gdcnn.DetectionTask;
import qupath.ext.gdcnn.ThresholdTask;
import qupath.ext.gdcnn.TilerTask;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.common.GeneralTools;
import qupath.lib.common.ThreadTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectSelectionModel;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

public class GDCnnController {

    private static final Logger logger = LoggerFactory.getLogger(GDCnnController.class);

    private QuPathGUI qupath;

    @FXML
    private Button selectAllImgsBtn;
    @FXML
    private Button runAllBtn;
    @FXML
    private Button runDetectionBtn;
    @FXML
    private Button runClassificationBtn;
    @FXML
    private ProgressIndicator progressInd;
    @FXML
    private Label progressLabel;
    @FXML
    private ImageView tickIconImg;
    @FXML
    private Label doneLabel;
    @FXML
    private Button viewResultsBtn;
    @FXML
    private CheckListView<String> imgsCheckList;

    private final ExecutorService pool = Executors
            .newSingleThreadExecutor(ThreadTools.createThreadFactory("GDCnn", true));

    private static final LinkedHashMap<String, String> PROGRESS_MESSAGES = new LinkedHashMap<String, String>() {
        {
            put("ThresholdTask", "Detecting tissue...");
            put("TilerTask", "Tiling images...");
            put("DetectionTask", "Detecting glomeruli... (this may take a while)");
            put("AnnotationExportTask", "Exporting glomerular annotations...");
            put("ClassificationTask", "Classifying glomeruli...");
        }
    };

    @FXML
    /**
     * Initializes the controller
     */
    private void initialize() {
        logger.info("Initializing...");

        this.qupath = QuPathGUI.getInstance();
    }

    @FXML
    /**
     * Runs the detection and classification of the glomeruli
     */
    private void runAll() {
        if (!isImageOrProjectOpen()) {
            Dialogs.showErrorMessage("No image or project open", "Please open an image or project to run the tasks");
            return;
        } else {
            logger.info("Running all tasks");
            try {
                thresholdForeground();
                tileWSIs();
                detectGlomeruli();
                exportAnnotations();
                classifyGlomeruli();
            } catch (IOException e) {
                logger.error("Error running all tasks", e);
                Dialogs.showErrorMessage("Error running all tasks", e);
            }
        }
    }

    @FXML
    /**
     * Runs the detection of the glomeruli
     */
    private void runDetection() {
        if (!isImageOrProjectOpen()) {
            Dialogs.showErrorMessage("No image or project open", "Please open an image or project to run the tasks");
            return;
        } else {
            logger.info("Running detection pipeline");
            try {
                thresholdForeground();
                tileWSIs();
                detectGlomeruli();
            } catch (IOException e) {
                logger.error("Error running detection", e);
                Dialogs.showErrorMessage("Error running detection", e);
            }
        }
    }

    @FXML
    /**
     * Runs the classification of the glomeruli
     */
    private void runClassification() {
        if (!isImageOrProjectOpen()) {
            Dialogs.showErrorMessage("No image or project open", "Please open an image or project to run the tasks");
            return;
        } else {
            logger.info("Running classification pipeline");
            try {
                exportAnnotations();
                classifyGlomeruli();
            } catch (IOException e) {
                logger.error("Error running classification", e);
                Dialogs.showErrorMessage("Error running classification", e);
            }
        }
    }

    @FXML
    // TODO
    private void viewResults() {

    }

    /**
     * Returns true if an image or project is open, false otherwise
     * 
     * @return True if an image or project is open, false otherwise
     */
    private boolean isImageOrProjectOpen() {
        return qupath.getProject() != null || qupath.getImageData() != null;
    }

    /**
     * Sets the progress indicator and label to show that the task is running
     */
    private void setProgressRunning(String taskName) {
        tickIconImg.setVisible(false);
        doneLabel.setVisible(false);
        progressInd.setVisible(true);
        progressLabel.setVisible(true);
        progressLabel.setText(PROGRESS_MESSAGES.get(taskName));
    }

    /**
     * Sets the progress indicator and label to show that the task is done
     */
    private void setProgressDone() {
        progressInd.setVisible(false);
        progressLabel.setVisible(false);
        tickIconImg.setVisible(true);
        doneLabel.setVisible(true);
    }

    /**
     * Puts the project images in the check list
     */
    public void setImgsCheckListElements() {
        ObservableList<String> imgsCheckListItems = FXCollections.observableArrayList();
        Project<BufferedImage> project = qupath.getProject();
        if (project != null) {
            // Add all images in the project to the list
            List<ProjectImageEntry<BufferedImage>> imageEntryList = project.getImageList();

            for (ProjectImageEntry<BufferedImage> imageEntry : imageEntryList) {
                String imageName = GeneralTools.stripExtension(imageEntry.getImageName());
                imgsCheckListItems.add(imageName);
            }
        } else {
            // Add the current image to the list
            ImageData<BufferedImage> imageData = qupath.getImageData();
            if (imageData != null) {
                String imageName = GeneralTools.stripExtension(imageData.getServer().getMetadata().getName());
                imgsCheckListItems.add(imageName);
            } else {
                logger.error("No project or image is open");
            }
        }

        imgsCheckList.setItems(imgsCheckListItems);
    }

    /**
     * Submits a task to the thread pool to run in the background
     * 
     * @param task
     */
    private void submitTask(Task<?> task) {
        task.setOnRunning(e -> {
            logger.info("Task running");
            setProgressRunning(task.getClass().getSimpleName());
        });
        task.setOnSucceeded(e -> {
            if (task instanceof DetectionTask || task instanceof ClassificationTask) {
                // If there is an image selected, select an object from the
                // hierarchy and deselect it to refresh the viewer
                ImageData<BufferedImage> currentImageData = qupath.getViewer().getImageData();
                if (currentImageData != null) {
                    PathObjectHierarchy hierarchy = currentImageData.getHierarchy();
                    Collection<PathObject> objects = hierarchy.getAnnotationObjects();
                    if (!objects.isEmpty()) {
                        PathObject object = objects.iterator().next();
                        PathObjectSelectionModel selectionModel = hierarchy.getSelectionModel();
                        selectionModel.setSelectedObject(object);
                        selectionModel.clearSelection();
                    }
                }
                logger.info("Task succeeded");
                Dialogs.showInfoNotification("Task succeeded", task.getClass().getSimpleName() + " succeeded");
                setProgressDone();
            }
        });
        task.setOnFailed(e -> {
            logger.error("Task failed", e.getSource().getException());
            Dialogs.showErrorMessage("Task failed", e.getSource().getException());
        });
        pool.submit(task);
    }

    /**
     * Apply the threshold to separate the foreground from the background
     * 
     * @throws IOException
     */
    public void thresholdForeground() throws IOException {
        submitTask(new ThresholdTask(qupath, 20, ".jpeg"));
    }

    /**
     * Tiles each WSI and saves them in a temporary folder
     * 
     * @throws IOException // In case there is an issue reading the image
     */
    public void tileWSIs() throws IOException {
        submitTask(new TilerTask(qupath, 4096, 2048, 1, ".jpeg"));
    }

    /**
     * Detects glomeruli in the WSI patches
     * 
     * @throws IOException
     */
    public void detectGlomeruli() throws IOException {
        submitTask(new DetectionTask(qupath, "cascade_R_50_FPN_1x", "external", 1));
    }

    /**
     * Exports the annotations of each WSI to images
     * 
     */
    public void exportAnnotations() {
        submitTask(new AnnotationExportTask(qupath, 300, 1));
    }

    /**
     * Classifies annotated glomeruli
     * 
     * @throws IOException
     */
    public void classifyGlomeruli() throws IOException {
        submitTask(new ClassificationTask(qupath, "swin_transformer"));
    }

}
