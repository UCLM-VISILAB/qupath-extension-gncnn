/**
 * Copyright (C) 2024 Israel Mateos-Aparicio-Ruiz
 */
package qupath.ext.gncnn.tasks;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
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
import qupath.lib.images.writers.TileExporter;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.scripting.QP;

/**
 * Class to tile the WSI into the given size patches and save them in a
 * temporary folder
 * 
 * @author Israel Mateos Aparicio
 */
public class TilerTask extends Task<Void> {

    private static final Logger logger = LoggerFactory.getLogger(TilerTask.class);

    private QuPathGUI qupath;

    private ObservableList<String> selectedImages;

    private int tileSize;

    private int tileOverlap;

    private double downsample;

    private String imageExtension;

    private ProgressListener progressListener;

    public TilerTask(QuPathGUI quPath, ObservableList<String> selectedImages, int tileSize, int tileOverlap,
            double downsample, String imageExtension, ProgressListener progressListener) {
        this.qupath = quPath;
        this.selectedImages = selectedImages;
        this.tileSize = tileSize;
        this.tileOverlap = tileOverlap;
        this.downsample = downsample;
        this.imageExtension = imageExtension;
        this.progressListener = progressListener;
    }

    @Override
    protected Void call() throws Exception {
        try {
            Project<BufferedImage> project = qupath.getProject();
            String outputBaseDir = Utils.getBaseDir(qupath);
            if (project != null) {
                tileWSIProject(project, outputBaseDir);
            } else {
                ImageData<BufferedImage> imageData = qupath.getImageData();
                if (imageData != null) {
                    tileWSI(imageData, outputBaseDir);
                } else {
                    logger.error("No image or project is open");
                }

            // Tissue detections are not needed anymore
            File thresholdOutputFolder = new File(
                    QP.buildFilePath(outputBaseDir, TaskPaths.TMP_FOLDER, TaskPaths.THRESHOLD_OUTPUT_FOLDER));
            if (thresholdOutputFolder.exists())
                Utils.deleteFolder(thresholdOutputFolder);
                }
        } catch (IOException e) {
            logger.error("Error with I/O of files: {}", e.getMessage(), e);
        } catch (InterruptedException e) {
            logger.error("Thread interrupted: {}", e.getMessage(), e);
        }

        return null;
    }

    /**
     * Tiles the image data and saves the tiles
     * 
     * @param imageData
     * @param outputBaseDir
     * @throws IOException
     * @throws InterruptedException
     */
    private void tileWSI(ImageData<BufferedImage> imageData, String outputBaseDir)
            throws IOException, InterruptedException {
        String imageName = GeneralTools.stripExtension(imageData.getServer().getMetadata().getName());
        String outputPath = TaskPaths.getTilerOutputDir(outputBaseDir, imageName);

        // Check if the thread has been interrupted before starting the tiling
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }

        // Create the output folder if it does not exist
        Utils.createFolder(outputPath);
        logger.info("Tiling {} [size={},overlap={}]", imageName, tileSize, tileOverlap);
        new TileExporter(imageData)
                .tileSize(tileSize)
                .imageExtension(imageExtension)
                .overlap(tileOverlap)
                .downsample(downsample)
                .annotatedTilesOnly(true)
                .writeTiles(outputPath);
        logger.info("Tiling of {} finished: {}", imageName, outputPath);

        // Remove all 'Tissue' annotations in the image hierarchy
        imageData.getHierarchy().getAnnotationObjects().stream()
                .filter(annotation -> annotation.getPathClass().getName().equals("Tissue"))
                .forEach(annotation -> imageData.getHierarchy().removeObject(annotation, false));

        // Check if the thread has been interrupted after tiling the image
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }

        // Remove non-tissue tiles
        VirtualEnvironment venv = new VirtualEnvironment(this.getClass().getSimpleName(), progressListener);

        // This is the list of commands after the 'python' call
        List<String> arguments = Arrays.asList(TaskPaths.THRESHOLD_TILE_COMMAND, "--wsi", imageName, "--export",
                QP.buildFilePath(outputBaseDir));
        venv.setArguments(arguments);

        // Check if the thread has been interrupted before starting the process
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }

        // Run the command
        logger.info("Removing non-tissue exported tiles for {}", imageName);
        venv.runCommand();
        logger.info("Finished removing non-tissue exported tiles for {}", imageName);

        // Update progress
        progressListener.updateProgress();
    }

    /**
     * Tiles each WSI in a project and saves them in corresponding temporary folders
     * 
     * @param project
     * @param outputBaseDir
     * @throws IOException
     * @throws InterruptedException
     */
    private void tileWSIProject(Project<BufferedImage> project, String outputBaseDir)
            throws IOException, InterruptedException {
        List<ProjectImageEntry<BufferedImage>> imageEntryList = project.getImageList();

        logger.info("Tiling {} images in the project [size={},overlap={}]",
                selectedImages.size(), tileSize, tileOverlap);
        // For each image, tile it and save the tiles in a temporary folder
        // Only process the selected images
        for (ProjectImageEntry<BufferedImage> imageEntry : imageEntryList) {
            ImageData<BufferedImage> imageData = imageEntry.readImageData();
            if (selectedImages.contains(GeneralTools.stripExtension(imageData.getServer().getMetadata().getName()))) {
                tileWSI(imageData, outputBaseDir);
                imageEntry.saveImageData(imageData);
            }
        }

        logger.info("Tiling {} images in the project finished", selectedImages.size());
    }

}
