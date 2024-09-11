/**
 * Copyright (C) 2024 Israel Mateos-Aparicio-Ruiz
 */
package qupath.ext.gncnn.utils;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.ImageView;
import qupath.ext.gncnn.entities.ImageResult;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.regions.RegionRequest;
import qupath.lib.scripting.QP;

/**
 * Utility class to provide some useful methods
 * 
 * @author Israel Mateos Aparicio
 */
public class Utils {

    private static final Logger logger = LoggerFactory.getLogger(Utils.class);

    /**
     * Deletes a folder and all its content
     * 
     * @param folder
     */
    public static void deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteFolder(f);
                } else {
                    f.delete();
                }
            }
        }
        folder.delete();
    }

    /**
     * Creates a folder if it does not exist, otherwise it deletes it and creates
     * 
     * @param path
     */
    public static void createFolder(String path) {
        File folder = new File(path);
        if (!folder.exists()) {
            folder.mkdirs();
        } else {
            deleteFolder(folder);
            folder.mkdirs();
        }
    }

    /**
     * Returns the base directory of the project or the image
     * 
     * @param qupath
     * @return Base directory
     */
    public static String getBaseDir(QuPathGUI qupath) {
        Project<BufferedImage> project = qupath.getProject();
        String baseDir = QP.PROJECT_BASE_DIR;
        if (project == null) {
            ImageData<BufferedImage> imageData = qupath.getImageData();
            if (imageData != null) {
                baseDir = Paths.get(imageData.getServer().getPath()).toString();
                // Take substring from the first slash after file: to the last slash
                baseDir = baseDir.substring(baseDir.indexOf("file:") + 5,
                        baseDir.lastIndexOf("/"));
            }
        }
        return baseDir;
    }

    /**
     * Checks if there are "Glomerulus" annotations in the selected images, and
     * returns the images with "Glomerulus" annotations
     * 
     * @param qupath
     * @param selectedImages
     * @return The images with "Glomerulus" annotations
     * @throws IOException
     */
    public static List<String> getImgsWithGlomeruli(QuPathGUI qupath, ObservableList<String> selectedImages)
            throws IOException {
        List<String> imgsWithGlomeruli = new ArrayList<String>();

        Project<BufferedImage> project = qupath.getProject();
        if (project != null) {
            // Check for glomerulus annotations in the selected images
            List<ProjectImageEntry<BufferedImage>> imageEntryList = project.getImageList();
            for (ProjectImageEntry<BufferedImage> imageEntry : imageEntryList) {
                String imageName = GeneralTools.stripExtension(imageEntry.getImageName());
                if (selectedImages.contains(imageName)) {
                    ImageData<BufferedImage> imageData = imageEntry.readImageData();
                    PathObjectHierarchy hierarchy = imageData.getHierarchy();
                    Collection<PathObject> annotations = hierarchy.getAnnotationObjects();

                    for (PathObject annotation : annotations) {
                        if (annotation.getPathClass() != null
                                && annotation.getPathClass().getName().equals("Glomerulus")) {
                            imgsWithGlomeruli.add(imageName);
                            break;
                        }
                    }
                }
            }
        } else {
            // Check for glomerulus annotations in the current image
            ImageData<BufferedImage> imageData = qupath.getImageData();
            if (imageData != null) {
                String imageName = GeneralTools.stripExtension(imageData.getServer().getMetadata().getName());
                PathObjectHierarchy hierarchy = imageData.getHierarchy();
                Collection<PathObject> annotations = hierarchy.getAnnotationObjects();

                for (PathObject annotation : annotations) {
                    if (annotation.getPathClass() != null
                            && annotation.getPathClass().getName().equals("Glomerulus")) {
                        imgsWithGlomeruli.add(imageName);
                        break;
                    }
                }
            } else {
                logger.error("No project or image is open");
            }
        }

        return imgsWithGlomeruli;
    }

    /**
     * Returns a thumbnail of the image
     * 
     * @param imageData
     * @param desiredSize
     * @return The thumbnail of the image
     * @throws IOException
     */
    private static BufferedImage getThumbnail(ImageData<BufferedImage> imageData, double desiredSize)
            throws IOException {
        ImageServer<BufferedImage> server = imageData.getServer();

        int width = server.getWidth();
        int height = server.getHeight();

        // Calculate downsample factor depending on the desired size
        double downsample = Math.max(width / desiredSize, height / desiredSize);

        RegionRequest request = RegionRequest.createInstance(imageData.getServerPath(), downsample, 0, 0, width,
                height);

        BufferedImage img = server.readRegion(request);

        return img;
    }

    public static String getTopkMostPredictedClass(HashMap<String, Double> diseaseProbs, int k) {
        // Check if any value for any key which is not 'Non-sclerotic' or 'Sclerotic' is greater than 0
        boolean removeNonSclerotic = false;
        for (Map.Entry<String, Double> entry : diseaseProbs.entrySet()) {
            String key = entry.getKey();
            double value = entry.getValue();
            if (!key.equals("Non-sclerotic") && !key.equals("Sclerotic") && value > 0) {
                removeNonSclerotic = true;
                break;
            }
        }
        
        // Get the top k most predicted classes, i.e., the classes with the highest probabilities,
        // removing the 'Non-sclerotic' and 'Sclerotic' classes if necessary
        List<Map.Entry<String, Double>> sortedProbs = new ArrayList<>(diseaseProbs.entrySet());
        if (removeNonSclerotic) {
            sortedProbs.removeIf(entry -> entry.getKey().equals("Non-sclerotic"));
            sortedProbs.removeIf(entry -> entry.getKey().equals("Sclerotic"));
        }
        sortedProbs.sort((entry1, entry2) -> entry2.getValue().compareTo(entry1.getValue()));
        List<String> topkClasses = new ArrayList<>();
        int topk = removeNonSclerotic ? k : 1;
        for (int i = 0; i < topk && i < sortedProbs.size(); i++) {
            topkClasses.add(sortedProbs.get(i).getKey());
        }

        return String.join(" | ", topkClasses);
    }

    /**
     * Returns the results of the detection and classification of the glomeruli,
     * including all images in the project (or the current image if no project
     * exists)
     * 
     * @param qupath
     * @param selectedImages
     * @return The results of the detection and classification of the glomeruli
     * @throws IOException
     */
    public static ObservableList<ImageResult> getResults(QuPathGUI qupath, ObservableList<String> selectedImages)
            throws IOException {
        ObservableList<ImageResult> results = FXCollections.observableArrayList();
        Project<BufferedImage> project = qupath.getProject();
        if (project != null) {
            // Check for glomerulus annotations in the selected images
            List<ProjectImageEntry<BufferedImage>> imageEntryList = project.getImageList();
            for (ProjectImageEntry<BufferedImage> imageEntry : imageEntryList) {
                String imageName = GeneralTools.stripExtension(imageEntry.getImageName());
                if (selectedImages.contains(imageName)) {
                    ImageData<BufferedImage> imageData = imageEntry.readImageData();
                    PathObjectHierarchy hierarchy = imageData.getHierarchy();
                    Collection<PathObject> annotations = hierarchy.getAnnotationObjects();

                    int nGlomeruli = 0;
                    HashMap<String, Integer> diseaseCounts = new HashMap<String, Integer>() {
                        {
                            put("Non-sclerotic", 0);
                            put("Sclerotic", 0);
                            put("ABMGN", 0);
                            put("ANCA", 0);
                            put("C3-GN", 0);
                            put("CryoglobulinemicGN", 0);
                            put("DDD", 0);
                            put("Fibrillary", 0);
                            put("IAGN", 0);
                            put("IgAGN", 0);
                            put("MPGN", 0);
                            put("Membranous", 0);
                            put("PGNMID", 0);
                            put("SLEGN-IV", 0);
                            put("Non-classified", 0);
                        }
                    };
                    HashMap<String, Double> diseaseProbs = new HashMap<String, Double>() {
                        {
                            put("Non-sclerotic", 0.0);
                            put("Sclerotic", 0.0);
                            put("ABMGN", 0.0);
                            put("ANCA", 0.0);
                            put("C3-GN", 0.0);
                            put("CryoglobulinemicGN", 0.0);
                            put("DDD", 0.0);
                            put("Fibrillary", 0.0);
                            put("IAGN", 0.0);
                            put("IgAGN", 0.0);
                            put("MPGN", 0.0);
                            put("Membranous", 0.0);
                            put("PGNMID", 0.0);
                            put("SLEGN-IV", 0.0);
                            put("Non-classified", 0.0);
                        }
                    };

                    for (PathObject annotation : annotations) {
                        PathClass pathClass = annotation.getPathClass();
                        if (pathClass != null) {
                            String className = pathClass.getName();
                            // Adapt the class names to the ones in the report
                            className = className.replace("Glomerulus", "Non-classified");
                            className = className.replace("NoSclerotic", "Non-sclerotic");
                            className = className.split(" ")[0];
                            diseaseCounts.put(className, diseaseCounts.get(className) + 1);
                            nGlomeruli++;

                            // Get the probabilities of the detected classes
                            Map<String, Number> measurements = annotation.getMeasurements();
                            Double noScleroticProb = measurements.getOrDefault("NoSclerotic-prob", 0.0).doubleValue();

                            // All the probabilities except the "NoSclerotic" and "Sclerotic"
                            // must be multiplied by the "NoSclerotic" probability, as they
                            // are sub-classes of "NoSclerotic"
                            for (Map.Entry<String, Number> entry : measurements.entrySet()) {
                                String key = entry.getKey();
                                Double value = entry.getValue().doubleValue();
                                if (!key.equals("NoSclerotic-prob") && !key.equals("Sclerotic-prob")) {
                                    diseaseProbs.put(key.replace("-prob", ""), diseaseProbs.get(key.replace("-prob", "")) + value * noScleroticProb);
                                } else {
                                    diseaseProbs.put(key.replace("-prob", "").replace("NoSclerotic", "Non-sclerotic"), diseaseProbs.get(key.replace("-prob", "").replace("NoSclerotic", "Non-sclerotic")) + value);
                                }
                            }
                        }
                    }

                    // If all the probabilities are 0, the class is empty
                    boolean empty = true;
                    for (Map.Entry<String, Double> entry : diseaseProbs.entrySet()) {
                        if (entry.getValue() > 0) {
                            empty = false;
                            break;
                        }
                    }

                    // Get the top 3 most predicted classes
                    String mostPredictedClass = "";
                    if (empty) {
                        // If there are non-classified glomeruli, the most predicted class is "Non-classified"
                        if (diseaseCounts.get("Non-classified") > 0) {
                            mostPredictedClass = "Non-classified";
                        }
                    } else {
                        mostPredictedClass = getTopkMostPredictedClass(diseaseProbs, 3);
                    }

                    ImageView thumbnail = new ImageView(SwingFXUtils.toFXImage(getThumbnail(imageData, 200), null));
                    results.add(new ImageResult(thumbnail, imageName, mostPredictedClass, nGlomeruli, diseaseCounts));
                }
            }
        } else {
            // Check for glomerulus annotations in the current image
            ImageData<BufferedImage> imageData = qupath.getImageData();
            if (imageData != null) {
                String imageName = GeneralTools.stripExtension(imageData.getServer().getMetadata().getName());
                PathObjectHierarchy hierarchy = imageData.getHierarchy();
                Collection<PathObject> annotations = hierarchy.getAnnotationObjects();

                int nGlomeruli = 0;
                HashMap<String, Integer> diseaseCounts = new HashMap<String, Integer>() {
                    {
                        put("Non-sclerotic", 0);
                        put("Sclerotic", 0);
                        put("ABMGN", 0);
                        put("ANCA", 0);
                        put("C3-GN", 0);
                        put("CryoglobulinemicGN", 0);
                        put("DDD", 0);
                        put("Fibrillary", 0);
                        put("IAGN", 0);
                        put("IgAGN", 0);
                        put("MPGN", 0);
                        put("Membranous", 0);
                        put("PGNMID", 0);
                        put("SLEGN-IV", 0);
                        put("Non-classified", 0);
                    }
                };
                HashMap<String, Double> diseaseProbs = new HashMap<String, Double>() {
                    {
                        put("Non-sclerotic", 0.0);
                        put("Sclerotic", 0.0);
                        put("ABMGN", 0.0);
                        put("ANCA", 0.0);
                        put("C3-GN", 0.0);
                        put("CryoglobulinemicGN", 0.0);
                        put("DDD", 0.0);
                        put("Fibrillary", 0.0);
                        put("IAGN", 0.0);
                        put("IgAGN", 0.0);
                        put("MPGN", 0.0);
                        put("Membranous", 0.0);
                        put("PGNMID", 0.0);
                        put("SLEGN-IV", 0.0);
                        put("Non-classified", 0.0);
                    }
                };

                for (PathObject annotation : annotations) {
                    PathClass pathClass = annotation.getPathClass();
                    if (pathClass != null) {
                        String className = pathClass.getName();
                        // Adapt the class names to the ones in the report
                        className = className.replace("Glomerulus", "Non-classified");
                        className = className.replace("NoSclerotic", "Non-sclerotic");
                        className = className.split(" ")[0];
                        diseaseCounts.put(className, diseaseCounts.get(className) + 1);
                        nGlomeruli++;

                        // Get the probabilities of the detected classes
                        Map<String, Number> measurements = annotation.getMeasurements();
                        Double noScleroticProb = measurements.getOrDefault("NoSclerotic-prob", 0.0).doubleValue();

                        // All the probabilities except the "NoSclerotic" and "Sclerotic"
                        // must be multiplied by the "NoSclerotic" probability, as they
                        // are sub-classes of "NoSclerotic"
                        for (Map.Entry<String, Number> entry : measurements.entrySet()) {
                            String key = entry.getKey();
                            Double value = entry.getValue().doubleValue();
                            if (!key.equals("NoSclerotic-prob") && !key.equals("Sclerotic-prob")) {
                                diseaseProbs.put(key.replace("-prob", ""), diseaseProbs.get(key.replace("-prob", "")) + value * noScleroticProb);
                            } else {
                                diseaseProbs.put(key.replace("-prob", "").replace("NoSclerotic", "Non-sclerotic"), diseaseProbs.get(key.replace("-prob", "").replace("NoSclerotic", "Non-sclerotic")) + value);
                            }
                        }
                    }
                }

                // If all the probabilities are 0, the class is empty
                boolean empty = true;
                for (Map.Entry<String, Double> entry : diseaseProbs.entrySet()) {
                    if (entry.getValue() > 0) {
                        empty = false;
                        break;
                    }
                }

                // Get the top 3 most predicted classes
                String mostPredictedClass = "";
                if (empty) {
                    // If there are non-classified glomeruli, the most predicted class is "Non-classified"
                    if (diseaseCounts.get("Non-classified") > 0) {
                        mostPredictedClass = "Non-classified";
                    }
                } else {
                    mostPredictedClass = getTopkMostPredictedClass(diseaseProbs, 3);
                }

                ImageView thumbnail = new ImageView(SwingFXUtils.toFXImage(getThumbnail(imageData, 200), null));
                results.add(new ImageResult(thumbnail, imageName, mostPredictedClass, nGlomeruli, diseaseCounts));
            } else {
                logger.error("No project or image is open");
            }
        }

        return results;
    }

    /**
     * Filters a list of strings by a given filter
     * 
     * @param list
     * @param filter
     * @return The filtered list
     */
    public static ObservableList<String> filterList(ObservableList<String> list, String filter) {
        ObservableList<String> filteredList = FXCollections.observableArrayList();
        for (String item : list) {
            if (item.toLowerCase().contains(filter.toLowerCase())) {
                filteredList.add(item);
            }
        }
        return filteredList;
    }

    /**
     * Filters a list of ImageResults by a given filter
     * 
     * @param results
     * @param filter
     * @return The filtered list of ImageResults
     */
    public static ObservableList<ImageResult> filterResults(ObservableList<ImageResult> results, String filter) {
        ObservableList<ImageResult> filteredResults = FXCollections.observableArrayList();
        for (ImageResult result : results) {
            if (result.getName().toLowerCase().contains(filter.toLowerCase())
                    || result.getMostPredictedClass().toLowerCase().contains(filter.toLowerCase())) {
                filteredResults.add(result);
            }
        }
        return filteredResults;
    }
}
