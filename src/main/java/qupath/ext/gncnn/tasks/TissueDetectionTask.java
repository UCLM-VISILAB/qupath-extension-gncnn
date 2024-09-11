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
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.writers.ImageWriterTools;
import qupath.lib.io.PathIO;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.regions.RegionRequest;
import qupath.lib.scripting.QP;

/**
 * Class to process the images using a thresholding method in order to separate
 * foreground from background
 */
public class TissueDetectionTask extends Task<Void> {

    private static final Logger logger = LoggerFactory.getLogger(TissueDetectionTask.class);

    private QuPathGUI qupath;

    private ObservableList<String> selectedImages;

    private int downsample;

    private String imageExtension;

    private ProgressListener progressListener;

    public TissueDetectionTask(QuPathGUI quPath, ObservableList<String> selectedImages, int downsample,
            String imageExtension, ProgressListener progressListener) {
        this.qupath = quPath;
        this.selectedImages = selectedImages;
        this.downsample = downsample;
        this.imageExtension = imageExtension;
        this.progressListener = progressListener;
    }

    @Override
    protected Void call() throws IOException, InterruptedException {
        try {
            Project<BufferedImage> project = qupath.getProject();
            String outputBaseDir = Utils.getBaseDir(qupath);
            if (project != null) {
                detectTissueProject(project, outputBaseDir);
            } else {
                ImageData<BufferedImage> imageData = qupath.getImageData();
                if (imageData != null) {
                    detectTissue(imageData, outputBaseDir);
                } else {
                    logger.error("No image or project is open");
                }
            }

            // Low-resolution images are not needed anymore
            File lowresOutputFolder = new File(
                    QP.buildFilePath(outputBaseDir, TaskPaths.TMP_FOLDER, TaskPaths.LOWRES_OUTPUT_FOLDER));
            if (lowresOutputFolder.exists())
                Utils.deleteFolder(lowresOutputFolder);
        } catch (IOException e) {
            logger.error("Error with I/O of files: {}", e.getMessage(), e);
        } catch (InterruptedException e) {
            logger.error("Thread interrupted: {}", e.getMessage(), e);
        }

        return null;
    }

    private void exportLowResolutionImage(ImageData<BufferedImage> imageData, String outputBaseDir) throws IOException {
        ImageServer<BufferedImage> server = imageData.getServer();
        String imageName = GeneralTools.stripExtension(server.getMetadata().getName());
        String outputPath = TaskPaths.getLowResOutputDir(outputBaseDir, imageName);
        // Create the output folder if it does not exist
        Utils.createFolder(outputPath);

        logger.info("Exporting low-res {} [downsample={}]", imageName, downsample);
        RegionRequest request = RegionRequest.createInstance(server, downsample);
        outputPath += "/" + imageName + imageExtension;
        ImageWriterTools.writeImageRegion(server, request, outputPath);
        logger.info("Low-res exporting of {} finished: {}", imageName, outputPath);
    }

    /**
     * Applies the thresholding algorithm to the image, separating the
     * foreground from the background, and adds the foreground objects as
     * annotations to the image hierarchy
     * 
     * @param imageData
     * @param outputBaseDir
     * @throws InterruptedException
     * @throws IOException
     */
    private void detectTissue(ImageData<BufferedImage> imageData, String outputBaseDir)
            throws IOException, InterruptedException {

        // Check if the thread has been interrupted before exporting the
        // low-resolution image
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }

        exportLowResolutionImage(imageData, outputBaseDir);

        String imageName = GeneralTools.stripExtension(imageData.getServer().getMetadata().getName());
        VirtualEnvironment venv = new VirtualEnvironment(this.getClass().getSimpleName(), progressListener);

        double pixelSize = imageData.getServer().getPixelCalibration().getAveragedPixelSizeMicrons();

        // This is the list of commands after the 'python' call
        List<String> arguments = Arrays.asList(TaskPaths.THRESHOLD_COMMAND, "--wsi", imageName, "--export",
                QP.buildFilePath(outputBaseDir), "--undersampling", Integer.toString(downsample), "--pixel-size",
                Double.toString(pixelSize));
        venv.setArguments(arguments);

        // Check if the thread has been interrupted before starting the process
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }

        // Run the command
        logger.info("Running thresholding algorithm for {}", imageName);
        venv.runCommand();
        logger.info("Thresholding algorithm for {} finished", imageName);

        // Read the annotations from the GeoJSON file
        String geoJSONPath = TaskPaths.getThresholdResultsPath(outputBaseDir, imageName);
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
        progressListener.updateProgress();
    }

    /**
     * Applies the thresholding algorithm to each image in the project, separating
     * the foreground from the background, and adds the foreground objects as
     * annotations to each image hierarchy
     * 
     * @param project
     * @param outputBaseDir
     * @throws InterruptedException
     * @throws IOException
     */
    private void detectTissueProject(Project<BufferedImage> project, String outputBaseDir)
            throws IOException, InterruptedException {
        List<ProjectImageEntry<BufferedImage>> imageEntryList = project.getImageList();
        logger.info("Running tissue detection for {} images", selectedImages.size());
        // Only process the selected images
        for (ProjectImageEntry<BufferedImage> imageEntry : imageEntryList) {
            ImageData<BufferedImage> imageData = imageEntry.readImageData();
            if (selectedImages.contains(GeneralTools.stripExtension(imageData.getServer().getMetadata().getName()))) {
                detectTissue(imageData, outputBaseDir);
                imageEntry.saveImageData(imageData);
            }
        }

        logger.info("Tissue detection for {} images in the project finished", selectedImages.size());
    }

}
