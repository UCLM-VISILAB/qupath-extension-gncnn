/**
 * Copyright (C) 2024 Israel Mateos-Aparicio-Ruiz
 */
package qupath.ext.gncnn.tasks;

import qupath.lib.common.GeneralTools;
import qupath.lib.scripting.QP;

/**
 * Class to store the paths used in the different tasks
 */
public class TaskPaths {

    public static final String TMP_FOLDER = "Temp";

    public static final String THRESHOLD_COMMAND = "gncnn-threshold";
    public static final String THRESHOLD_TILE_COMMAND = "gncnn-threshold-tile";
    public static final String SEGMENT_COMMAND = "gncnn-segment";
    public static final String CLASSIFICATION_COMMAND = "gncnn-classify";

    public static final String LOWRES_OUTPUT_FOLDER = "lowres-output";
    public static final String THRESHOLD_OUTPUT_FOLDER = "threshold-output";
    public static final String TILER_OUTPUT_FOLDER = "tiler-output";
    public static final String SEGMENT_OUTPUT_FOLDER = "segment-output";
    private static final String ANN_EXPORT_OUTPUT_FOLDER = "ann-export-output";

    private static final String LOWRES_IMAGES_FOLDER = "Images";
    private static final String THRESHOLD_ANNOTATIONS_FOLDER = "Annotations";
    private static final String TILES_FOLDER = "Tiles";
    private static final String DETECTIONS_FOLDER = "Detections";
    private static final String REPORT_FOLDER = "Report";
    private static final String MODEL_FOLDER = "B-swin_transformer_M-None";

    public static String getLowResOutputDir(String baseDir, String imageName) {
        return QP.buildFilePath(baseDir, TMP_FOLDER, LOWRES_OUTPUT_FOLDER, LOWRES_IMAGES_FOLDER, imageName);
    }

    /**
     * Returns the path to the folder where the thresholded images are stored
     * 
     * @param baseDir
     * @param imageName
     * @return Path to the folder where the thresholded images are stored
     */
    public static String getThresholdOutputDir(String baseDir, String imageName) {
        return QP.buildFilePath(baseDir, TMP_FOLDER, THRESHOLD_OUTPUT_FOLDER, THRESHOLD_ANNOTATIONS_FOLDER, imageName);
    }

    /**
     * Returns the path to the folder where the tiles are stored
     * 
     * @param baseDir
     * @param imageName
     * @return Path to the folder where the tiles are stored
     */
    public static String getTilerOutputDir(String baseDir, String imageName) {
        return QP.buildFilePath(baseDir, TMP_FOLDER, TILER_OUTPUT_FOLDER, TILES_FOLDER, imageName);
    }

    /**
     * Returns the path to the folder where the annotations are stored
     * 
     * @param baseDir
     * @param imageName
     * @return Path to the folder where the annotations are stored
     */
    public static String getAnnotationOutputDir(String baseDir, String imageName) {
        return QP.buildFilePath(baseDir, TMP_FOLDER, ANN_EXPORT_OUTPUT_FOLDER, imageName);
    }

    /**
     * Returns the path to the threshold results
     * 
     * @param baseDir
     * @param imageName
     * @return Path to the threshold results
     */
    public static String getThresholdResultsPath(String baseDir, String imageName) {
        return QP.buildFilePath(baseDir, TMP_FOLDER, THRESHOLD_OUTPUT_FOLDER, THRESHOLD_ANNOTATIONS_FOLDER, imageName,
                "annotations.geojson");
    }

    /**
     * Returns the path to the detection results
     * 
     * @param baseDir
     * @param imageName
     * @return Path to the detection results
     */
    public static String getDetectionResultsPath(String baseDir, String imageName) {
        return QP.buildFilePath(baseDir, TMP_FOLDER, SEGMENT_OUTPUT_FOLDER, DETECTIONS_FOLDER, imageName,
                "detections.geojson");
    }

    /**
     * Returns the path to the classification results
     * 
     * @param baseDir
     * @param imageName
     * @return Path to the classification results
     */
    public static String getClassificationResultsPath(String baseDir, String imageName) {
        return QP.buildFilePath(TMP_FOLDER, REPORT_FOLDER, MODEL_FOLDER,
                GeneralTools.stripExtension(imageName) + ".csv");
    }
}
