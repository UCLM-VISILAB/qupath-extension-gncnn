"""Apply thresholding to an image and save the result as a GeoJSON file.

Copyright (C) 2024 Israel Mateos-Aparicio-Ruiz
"""
import argparse
import logging
import os

import cv2
import shapely.geometry

from gncnn.detection.qupath.config import MIN_AREA_GLOMERULUS_UM
from gncnn.detection.qupath.shapely2geojson import poly2geojson

def thresholding(img_path):
    # 1. Median filtering
    img = cv2.imread(img_path)
    img = cv2.medianBlur(img, 11)

    # 2. Get saturation channel of HSV
    img = cv2.imread(img_path)
    img = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
    img = cv2.split(img)[1]

    # 3. Otsu's thresholding
    _, img = cv2.threshold(img, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)

    # 4. Closing
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (11, 11))
    img = cv2.morphologyEx(img, cv2.MORPH_CLOSE, kernel)

    return img


def get_original_contours(contours, downsample):
    """Get original contours from downsampled contours."""
    original_contours = []
    for contour in contours:
        contour = contour.squeeze()
        contour = contour * downsample
        contour = contour.astype(int)
        original_contours.append(contour)
    return original_contours


def contours2geojson(contours, pixel_size, output_path):
    """Convert contours to a GeoJSON file."""
    # 1. Convert contours to polygons
    polygons = []
    for contour in contours:
        contour = contour.squeeze()
        polygon = shapely.geometry.Polygon(contour)
        polygons.append(polygon)
    
    # 2. Remove small polygons
    final_polygons = []
    for polygon in polygons:
        if polygon.area * pixel_size**2 < MIN_AREA_GLOMERULUS_UM:
            logging.info(f'Removing small polygon with area {polygon.area * pixel_size**2} um^2')
            continue
        final_polygons.append(polygon)

    # 3. Convert polygons to GeoJSON
    logging.info(f'Saving {len(final_polygons)} tissue polygons to {output_path}')
    poly2geojson(final_polygons, 'Tissue', [255, 0, 0], output_path)


def main():
    parser = argparse.ArgumentParser(description='Segment Tissue from WSI')
    parser.add_argument('--undersampling', type=int, help='Undersampling factor of tiles', default=20)
    parser.add_argument('-w', '--wsi', type=str, help='path/to/wsi', required=True)
    parser.add_argument('-e', '--export', type=str, help='path/to/export', required=True)
    parser.add_argument('--pixel-size', type=float, help='Pixel size of the WSI', default=0.5)
    args = parser.parse_args()

    lowres_dir = os.path.join(args.export, 'Temp', 'lowres-output', 'Images', args.wsi)
    path_to_threshold_output = os.path.join(args.export, 'Temp', 'threshold-output')

    annotation_dir = os.path.join(path_to_threshold_output, 'Annotations', args.wsi)
    os.makedirs(annotation_dir, exist_ok=True)

    for img_path in os.listdir(lowres_dir):
        img_path = os.path.join(lowres_dir, img_path)
        img = thresholding(img_path)

        # Find contours
        contours, _ = cv2.findContours(img, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        contours = get_original_contours(contours, downsample=args.undersampling)

        # Remove contours with less than 3 points
        final_contours = []
        for contour in contours:
            if len(contour) < 3:
                logging.warning(f'Contour with less than 3 points found in {img_path}')
                continue
            final_contours.append(contour)

        # Save as GeoJSON for QuPath
        path_to_geojson = os.path.join(annotation_dir, 'annotations.geojson')
        contours2geojson(final_contours, args.pixel_size, path_to_geojson)


if __name__ == '__main__':
    main()
