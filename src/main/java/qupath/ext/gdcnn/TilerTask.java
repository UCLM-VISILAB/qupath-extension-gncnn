package qupath.ext.gdcnn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.IOException;
import javafx.concurrent.Task;

import java.util.List;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.writers.TileExporter;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

/**
 * Class to tile the WSI into the given size patches and save them in a
 * temporary folder
 * 
 * @author Israel Mateos Aparicio
 */
public class TilerTask extends Task<Void> {

    private static final Logger logger = LoggerFactory.getLogger(TilerTask.class);

    private QuPathGUI qupath;

    private int tileSize;

    private int tileOverlap;

    private int downsample;

    private String imageExtension;

    private String gdcnnPath;

    public TilerTask(QuPathGUI quPath, int tileSize, int tileOverlap, int downsample, String imageExtension, String gdcnnPath) {
        this.qupath = quPath;
        this.tileSize = tileSize;
        this.tileOverlap = tileOverlap;
        this.downsample = downsample;
        this.imageExtension = imageExtension;
        this.gdcnnPath = gdcnnPath;
    }

    @Override
    protected Void call() throws Exception {
        Project<BufferedImage> project = qupath.getProject();
        if (project != null) {
            tileWSIProject(project);
        } else {
            ImageData<BufferedImage> imageData = qupath.getImageData();
            if (imageData != null) {
                tileWSI(imageData);
            } else {
                logger.error("No image or project is open");
            }
        }
        return null;
    }

    /**
     * Tiles the image data and saves the tiles
     * 
     * @param imageData
     * @throws IOException
     */
    private void tileWSI(ImageData<BufferedImage> imageData) throws IOException {
        String imageName = imageData.getServer().getMetadata().getName();
        // If gdcnnPath does not end with a slash, add it
        if (!gdcnnPath.endsWith("/")) {
            gdcnnPath += "/";
        }
        String outputPath = gdcnnPath + "Temp/tiler-output/Tiles/" + GeneralTools.stripExtension(imageName);
        // Create the output folder if it does not exist
        Utils.createFolder(outputPath);
        logger.info("Tiling {} [size={},overlap={}]", imageName, tileSize, tileOverlap);
        new TileExporter(imageData)
                .tileSize(tileSize)
                .imageExtension(imageExtension)
                .overlap(tileOverlap)
                .downsample(downsample)
                .annotatedTilesOnly(false)
                .writeTiles(outputPath);
        logger.info("Tiling of {} finished: {}", imageName, outputPath);
    }

    /**
     * Tiles each WSI in a project and saves them in corresponding temporary folders
     * 
     * @param project
     * @throws IOException
     */
    private void tileWSIProject(Project<BufferedImage> project) throws IOException {
        List<ProjectImageEntry<BufferedImage>> imageEntryList = project.getImageList();

        logger.info("Tiling {} images in the project [size={},overlap={}]",
                imageEntryList.size(), tileSize, tileOverlap);
        // For each image, tile it and save the tiles in a temporary folder
        for (ProjectImageEntry<BufferedImage> imageEntry : imageEntryList) {
            ImageData<BufferedImage> imageData = imageEntry.readImageData();
            tileWSI(imageData);
        }

        logger.info("Tiling images in the project finished");
    }

}
