/**
 * Copyright (C) 2024 Israel Mateos-Aparicio-Ruiz
 */
package qupath.ext.gncnn.tasks;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import qupath.ext.gncnn.entities.ProgressListener;
import qupath.ext.gncnn.env.VirtualEnvironment;
import qupath.ext.gncnn.utils.Utils;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.ImageData;
import qupath.lib.io.PathIO;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.scripting.QP;

/**
 * Class to detect glomeruli in the WSI patches, and add the detected objects to
 * the image hierarchy
 */
public class GlomerulusDetectionTask extends Task<Void> {

    private static final Logger logger = LoggerFactory.getLogger(GlomerulusDetectionTask.class);

    private QuPathGUI qupath;

    private ObservableList<String> selectedImages;

    private String modelName;

    private String trainConfig;

    private double desiredPixelSize;

    private ProgressListener progressListener;

    public GlomerulusDetectionTask(QuPathGUI quPath, ObservableList<String> selectedImages, String modelName,
            String trainConfig, double desiredPixelSize, ProgressListener progressListener) {
        this.qupath = quPath;
        this.selectedImages = selectedImages;
        this.modelName = modelName;
        this.trainConfig = trainConfig;
        this.desiredPixelSize = desiredPixelSize;
        this.progressListener = progressListener;
    }

    @Override
    protected Void call() throws Exception {
        try {
            Project<BufferedImage> project = qupath.getProject();
            String outputBaseDir = Utils.getBaseDir(qupath);
            if (project != null) {
                detectGlomeruliProject(project, outputBaseDir);
            } else {
                ImageData<BufferedImage> imageData = qupath.getImageData();
                if (imageData != null) {
                    detectGlomeruli(imageData, outputBaseDir);
                } else {
                    logger.error("No image or project is open");
                }
            }

            // Tiles are not needed anymore
            File tilerOutputFolder = new File(
                    QP.buildFilePath(outputBaseDir, TaskPaths.TMP_FOLDER, TaskPaths.TILER_OUTPUT_FOLDER));
            if (tilerOutputFolder.exists())
                Utils.deleteFolder(tilerOutputFolder);

            // Detections are already added to the image hierarchy, so they are not needed
            File segmentOutputFolder = new File(
                    QP.buildFilePath(outputBaseDir, TaskPaths.TMP_FOLDER, TaskPaths.SEGMENT_OUTPUT_FOLDER));
            if (segmentOutputFolder.exists())
                Utils.deleteFolder(segmentOutputFolder);
        } catch (IOException e) {
            logger.error("Error with I/O of files: {}", e.getMessage(), e);
        } catch (InterruptedException e) {
            logger.error("Thread interrupted: {}", e.getMessage(), e);
        }

        return null;
    }

    /**
     * Detects glomeruli in the WSI and adds the detected objects to the image
     * hierarchy
     * 
     * @param imageData
     * @param outputBaseDir
     * @throws InterruptedException
     * @throws IOException
     */
    private void detectGlomeruli(ImageData<BufferedImage> imageData, String outputBaseDir)
            throws IOException, InterruptedException {
        String imageName = GeneralTools.stripExtension(imageData.getServer().getMetadata().getName());
        VirtualEnvironment venv = new VirtualEnvironment(this.getClass().getSimpleName(), progressListener);

        double pixelSize = imageData.getServer().getPixelCalibration().getAveragedPixelSizeMicrons();
        double undersampling = desiredPixelSize / imageData.getServer().getPixelCalibration().getAveragedPixelSize().doubleValue();

        // This is the list of commands after the 'python' call
        List<String> arguments = Arrays.asList(TaskPaths.SEGMENT_COMMAND, "--wsi", imageName, "--export",
                QP.buildFilePath(outputBaseDir),
                "--model",
                modelName, "--train-config", trainConfig, "--undersampling", Double.toString(undersampling),
                "--pixel-size", Double.toString(pixelSize));
        venv.setArguments(arguments);

        // Check if the thread has been interrupted before starting the process
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }

        // Run the command
        logger.info("Running detection for {}", imageName);
        venv.runCommand();
        logger.info("Detection for {} finished", imageName);

        // Read the annotations from the GeoJSON file
        String geoJSONPath = TaskPaths.getDetectionResultsPath(outputBaseDir, imageName);
        List<PathObject> detectedObjects = PathIO.readObjects(Paths.get(geoJSONPath));

        // Check if the thread has been interrupted before adding the detected
        // objects to the image hierarchy
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }

        // Add the detected objects to the image hierarchy
        PathObjectHierarchy hierarchy = imageData.getHierarchy();
        hierarchy.addObjects(detectedObjects);
        logger.info("Added {} detected objects to {}", detectedObjects.size(), imageName);

        // Update progress
        if (progressListener.getProgress() >= 0.99) {
            progressListener.updateProgress();
        }
    }

    /**
     * Detects glomeruli in the WSIs in a project and adds the detected objects to
     * the each image hierarchy
     * 
     * @param project
     * @param outputBaseDir
     * @throws InterruptedException
     * @throws IOException
     */
    private void detectGlomeruliProject(Project<BufferedImage> project, String outputBaseDir)
            throws IOException, InterruptedException {
        List<ProjectImageEntry<BufferedImage>> imageEntryList = project.getImageList();
        logger.info("Running detection for {} images", selectedImages.size());
        // Only process the selected images
        for (ProjectImageEntry<BufferedImage> imageEntry : imageEntryList) {
            ImageData<BufferedImage> imageData = imageEntry.readImageData();
            if (selectedImages.contains(GeneralTools.stripExtension(imageData.getServer().getMetadata().getName()))) {
                detectGlomeruli(imageData, outputBaseDir);
                imageEntry.saveImageData(imageData);
            }
        }
        logger.info("Detection for {} images in the project finished", selectedImages.size());
    }

}
