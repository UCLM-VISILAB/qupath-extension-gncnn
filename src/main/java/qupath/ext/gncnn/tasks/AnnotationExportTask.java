/**
 * Copyright (C) 2024 Israel Mateos-Aparicio-Ruiz
 */
package qupath.ext.gncnn.tasks;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.concurrent.Task;
import qupath.ext.gncnn.entities.ProgressListener;
import qupath.ext.gncnn.utils.Utils;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.scripting.QP;

/**
 * Class to export the annotations to images
 */
public class AnnotationExportTask extends Task<Void> {

    private static final Logger logger = LoggerFactory.getLogger(AnnotationExportTask.class);

    private QuPathGUI qupath;

    private List<String> selectedImages;

    private int padding;

    private double downsample;

    private ProgressListener progressListener;

    public AnnotationExportTask(QuPathGUI quPath, List<String> selectedImages, int padding, double downsample,
            ProgressListener progressListener) {
        this.qupath = quPath;
        this.selectedImages = selectedImages;
        this.padding = padding;
        this.downsample = downsample;
        this.progressListener = progressListener;
    }

    @Override
    protected Void call() throws Exception {
        try {
            Project<BufferedImage> project = qupath.getProject();
            String outputBaseDir = Utils.getBaseDir(qupath);
            if (project != null) {
                exportAnnotationsProject(project, outputBaseDir);
            } else {
                ImageData<BufferedImage> imageData = qupath.getImageData();
                if (imageData != null) {
                    exportAnnotations(imageData, outputBaseDir);
                } else {
                    logger.error("No image or project is open");
                }
            }
        } catch (IOException e) {
            logger.error("Error with I/O of files: {}", e.getMessage(), e);
        } catch (InterruptedException e) {
            logger.error("Thread interrupted: {}", e.getMessage(), e);
        }

        return null;
    }

    /**
     * Exports the annotations of a WSI to images
     * 
     * @param imageData
     * @param outputBaseDir
     * @throws InterruptedException
     * @throws IOException
     */
    private void exportAnnotations(ImageData<BufferedImage> imageData, String outputBaseDir)
            throws IOException, InterruptedException {
        ImageServer<BufferedImage> server = imageData.getServer();
        String imageName = server.getMetadata().getName();
        String outputPath = QP.buildFilePath(outputBaseDir, "Temp", "ann-export-output",
                GeneralTools.stripExtension(imageName));

        Collection<PathObject> annotations = imageData.getHierarchy().getAnnotationObjects();
        // Use only 'Glomerulus' annotations
        annotations.removeIf(annotation -> annotation.getPathClass() == null
                || !annotation.getPathClass().getName().equals("Glomerulus"));

        if (annotations.isEmpty()) {
            logger.info("No annotations found for {}", imageName);
            return;
        } else {
            // Create the output folder if it does not exist
            Utils.createFolder(outputPath);
        }

        logger.info("Exporting {} annotations for {}", annotations.size(), imageName);
        for (PathObject annotation : annotations) {
            // TODO: Update progress with each annotation
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }

            ROI roi = annotation.getROI();
            String className = annotation.getPathClass().getName();
            String annotationId = annotation.getID().toString();

            RegionRequest region = RegionRequest.createInstance(server.getPath(), downsample,
                    (int) roi.getBoundsX() - padding, (int) roi.getBoundsY() - padding,
                    (int) roi.getBoundsWidth() + padding * 2, (int) roi.getBoundsHeight() + padding * 2, roi.getZ(),
                    roi.getT());

            String outputName = String.format("%s_%s_%s_%d_%d_%d_%d.png", imageName, className, annotationId,
                    region.getX(), region.getY(), region.getWidth(), region.getHeight());

            BufferedImage img = server.readRegion(region);
            File outputFile = new File(outputPath, outputName);

            ImageIO.write(img, "PNG", outputFile);
        }
        logger.info("Exporting annotations for {} finished", imageName);

        // Update progress
        progressListener.updateProgress();
    }

    /**
     * Export the annotations for each WSI in the project to images
     * 
     * @param project
     * @param outputBaseDir
     * @throws InterruptedException
     * @throws IOException
     */
    private void exportAnnotationsProject(Project<BufferedImage> project, String outputBaseDir)
            throws IOException, InterruptedException {
        List<ProjectImageEntry<BufferedImage>> imageEntryList = project.getImageList();
        logger.info("Exporting annotations for {} images in the project", selectedImages.size());
        // Only process the selected images
        for (ProjectImageEntry<BufferedImage> imageEntry : imageEntryList) {
            ImageData<BufferedImage> imageData = imageEntry.readImageData();
            if (selectedImages.contains(GeneralTools.stripExtension(imageData.getServer().getMetadata().getName()))) {
                exportAnnotations(imageData, outputBaseDir);
            }
        }
        logger.info("Exporting annotations for {} images in the project finished", selectedImages.size());
    }

}
