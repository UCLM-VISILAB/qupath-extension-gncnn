/**
 * Copyright (C) 2024 Israel Mateos-Aparicio-Ruiz
 */
package qupath.ext.gncnn.tasks;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import qupath.ext.gncnn.entities.ProgressListener;
import qupath.ext.gncnn.utils.Utils;
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
import qupath.lib.scripting.QP;

public class TaskManager {

    private static final Logger logger = LoggerFactory.getLogger(TaskManager.class);

    private QuPathGUI qupath;

    private final ExecutorService pool = Executors
            .newSingleThreadExecutor(ThreadTools.createThreadFactory("GNCnn", true));

    private final HashMap<String, String> PROGRESS_MESSAGES = new HashMap<String, String>() {
        {
            put("TissueDetectionTask", "Detecting tissue...");
            put("TilerTask", "Tiling images...");
            put("GlomerulusDetectionTask", "Detecting glomeruli... (this may take a while)");
            put("AnnotationExportTask", "Exporting glomerular annotations...");
            put("ClassificationTask", "Classifying glomeruli...");
        }
    };

    private final ObservableList<Task<?>> currentTasks = FXCollections.observableArrayList();

    private final BooleanProperty runningProperty = new SimpleBooleanProperty(false);

    private final BooleanProperty doneProperty = new SimpleBooleanProperty(false);

    private final DoubleProperty progressProperty = new SimpleDoubleProperty(0);

    private final StringProperty messageProperty = new SimpleStringProperty("");

    private ProgressListener progressListener;

    private double progressStep = 0;

    public TaskManager(QuPathGUI qupath) {
        this.qupath = qupath;

        // Bind the running property to the current tasks
        runningProperty.bind(Bindings.isNotEmpty(currentTasks));

        // Bind the message property to the current task
        messageProperty.bind(Bindings.createStringBinding(() -> {
            String taskName = getCurrentTaskName();
            if (taskName != null) {
                return PROGRESS_MESSAGES.get(taskName);
            }
            return "";
        }, currentTasks));

        // Bind the done property to the progress and running properties
        // The task is done when the progress is greater than or equal to 0.999
        // and there are no running tasks
        doneProperty.bind(Bindings.createBooleanBinding(() -> {
            return progressProperty.get() >= 0.999 && !runningProperty.get();
        }, progressProperty, runningProperty));
    }

    public BooleanProperty doneProperty() {
        return doneProperty;
    }

    public BooleanProperty runningProperty() {
        return runningProperty;
    }

    public final boolean isRunning() {
        return runningProperty.get();
    }

    public DoubleProperty progressProperty() {
        return progressProperty;
    }

    public StringProperty messageProperty() {
        return messageProperty;
    }

    private String getCurrentTaskName() {
        if (currentTasks.isEmpty()) {
            return null;
        }
        return currentTasks.get(0).getClass().getSimpleName();
    }

    /**
     * Submits a task to the thread pool to run in the background
     * 
     * @param task
     */
    private void submitTask(Task<?> task) {
        task.setOnSucceeded(e -> {
            if (task instanceof GlomerulusDetectionTask || task instanceof ClassificationTask) {
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
            }
            logger.info("Task succeeded");
            Dialogs.showInfoNotification("Task succeeded", task.getClass().getSimpleName() + " succeeded");
        });
        task.setOnFailed(e -> {
            logger.error("Task failed", e.getSource().getException());
            Dialogs.showErrorMessage("Task failed", e.getSource().getException());
        });
        task.stateProperty().addListener((Observable o) -> {
            if (task.isDone()) {
                currentTasks.remove(task);
            }
        });
        pool.submit(task);
        currentTasks.add(task);
    }

    /**
     * Cancels all the tasks in the thread pool
     * 
     * @param selectedImages
     * @throws IOException
     */
    public void cancelAllTasks(ObservableList<String> selectedImages) throws IOException {
        logger.info("Cancelling all tasks");
        pool.shutdownNow();
        currentTasks.clear();

        // Clean the temporary files
        logger.info("Cleaning temporary files");
        String outputBaseDir = Utils.getBaseDir(qupath);
        File tempFolder = new File(QP.buildFilePath(outputBaseDir, TaskPaths.TMP_FOLDER));
        if (tempFolder.exists()) {
            Utils.deleteFolder(tempFolder);
        }

        // Clean the temporary annotations, i.e. "Tissue"
        logger.info("Cleaning temporary annotations");
        Project<BufferedImage> project = qupath.getProject();
        if (project != null) {
            // Check for "Tissue" annotations in selected images
            List<ProjectImageEntry<BufferedImage>> imageEntryList = project.getImageList();
            for (ProjectImageEntry<BufferedImage> imageEntry : imageEntryList) {
                String imageName = GeneralTools.stripExtension(imageEntry.getImageName());
                if (selectedImages.contains(imageName)) {
                    ImageData<BufferedImage> imageData = imageEntry.readImageData();

                    // Remove all "Tissue" annotations in the image hierarchy
                    imageData.getHierarchy().getAnnotationObjects().stream()
                            .filter(annotation -> annotation.getPathClass().getName().equals("Tissue"))
                            .forEach(annotation -> imageData.getHierarchy().removeObject(annotation, false));

                    imageEntry.saveImageData(imageData);
                    logger.info("Removed 'Tissue' annotations from {}", imageEntry.getImageName());
                }
            }
        } else {
            // Check for "Tissue" annotations in the current image
            ImageData<BufferedImage> imageData = qupath.getImageData();
            if (imageData != null) {
                // Remove all "Tissue" annotations in the image hierarchy
                imageData.getHierarchy().getAnnotationObjects().stream()
                        .filter(annotation -> annotation.getPathClass().getName().equals("Tissue"))
                        .forEach(annotation -> imageData.getHierarchy().removeObject(annotation, false));
                logger.info("Removed 'Tissue' annotations from {}", imageData.getServer().getMetadata().getName());
            } else {
                logger.error("No project or image is open");
            }
        }
    }

    /**
     * Runs the detection and classification of the glomeruli
     * 
     * @param selectedImages
     * @param multiclass
     * @throws IOException
     */
    public void runAll(ObservableList<String> selectedImages, Boolean multiclass) throws IOException {
        logger.info("Running all tasks");

        progressStep = 1.0 / (5.0 * selectedImages.size()); // 5 tasks in total

        progressListener = new ProgressListener(progressStep);
        progressProperty.bind(progressListener.progressProperty());

        detectTissue(selectedImages, progressListener);
        tileWSIs(selectedImages, progressListener);
        detectGlomeruli(selectedImages, progressListener);
        exportAnnotations(selectedImages, progressListener);
        classifyGlomeruli(selectedImages, multiclass, progressListener);
    }

    /**
     * Runs the detection of the glomeruli
     * 
     * @param selectedImages
     * @throws IOException
     */
    public void runDetection(ObservableList<String> selectedImages) throws IOException {
        logger.info("Running detection pipeline");

        progressStep = 1.0 / (3.0 * selectedImages.size()); // 3 tasks in total

        progressListener = new ProgressListener(progressStep);
        progressProperty.bind(progressListener.progressProperty());

        detectTissue(selectedImages, progressListener);
        tileWSIs(selectedImages, progressListener);
        detectGlomeruli(selectedImages, progressListener);
    }

    /**
     * Runs the classification of the glomeruli
     * 
     * @param selectedImages
     * @param multiclass
     * @throws IOException
     */
    public void runClassification(List<String> imgsWithGlomeruli, Boolean multiclass) throws IOException {
        logger.info("Running classification pipeline");

        progressStep = 1.0 / (2.0 * imgsWithGlomeruli.size()); // 2 tasks in total

        progressListener = new ProgressListener(progressStep);
        progressProperty.bind(progressListener.progressProperty());

        exportAnnotations(imgsWithGlomeruli, progressListener);
        classifyGlomeruli(imgsWithGlomeruli, multiclass, progressListener);
    }

    /**
     * Apply the threshold to separate the foreground from the background
     * 
     * @param selectedImages
     * @param progressListener
     * @throws IOException
     */
    private void detectTissue(ObservableList<String> selectedImages, ProgressListener progressListener)
            throws IOException {
        submitTask(new TissueDetectionTask(qupath, selectedImages, 20, ".jpeg", progressListener));
    }

    /**
     * Tiles each WSI and saves them in a temporary folder
     * 
     * @param selectedImages
     * @param progressListener
     * @throws IOException // In case there is an issue reading the image
     */
    private void tileWSIs(ObservableList<String> selectedImages, ProgressListener progressListener) throws IOException {
        submitTask(new TilerTask(qupath, selectedImages, 4096, 2048, 0.25, ".jpeg", progressListener));
    }

    /**
     * Detects glomeruli in the WSI patches
     * 
     * @param selectedImages
     * @param progressListener
     * @throws IOException
     */
    private void detectGlomeruli(ObservableList<String> selectedImages, ProgressListener progressListener)
            throws IOException {
        submitTask(new GlomerulusDetectionTask(qupath, selectedImages, "cascade_R_50_FPN_1x", "external", 0.25,
                progressListener));
    }

    /**
     * Exports the annotations of each WSI to images
     * 
     * @param selectedImages
     * @param progressListener
     */
    private void exportAnnotations(List<String> selectedImages, ProgressListener progressListener) {
        submitTask(new AnnotationExportTask(qupath, selectedImages, 300, 1, progressListener));
    }

    /**
     * Classifies annotated glomeruli
     * 
     * @param selectedImages
     * @param multiclass
     * @param progressListener
     * @throws IOException
     */
    private void classifyGlomeruli(List<String> selectedImages, Boolean multiclass, ProgressListener progressListener)
            throws IOException {
        ClassificationTask classificationTask;
        if (multiclass) {
            classificationTask = new ClassificationTask(qupath, selectedImages, "swin_transformer", "convnext",
                    progressListener);
        } else {
            classificationTask = new ClassificationTask(qupath, selectedImages, "swin_transformer", progressListener);
        }
        submitTask(classificationTask);
    }
}
