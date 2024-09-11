"""Compares the exported tiles with the tissue annotations (GeoJSON) to remove
tiles with <10% of tissue.

Copyright (C) 2024 Israel Mateos-Aparicio-Ruiz
"""
import argparse
import logging
import os

from shapely.geometry import Polygon

from gncnn.detection.qupath.shapely2geojson import geojson2poly


def is_there_tissue(tile_path, tissue_polygons, threshold=0.1):
    """From the tile path, get coordinates and form a square polygon to check
    if it intersects with any tissue polygon."""
    # 1. Get tile coordinates
    tile_name = os.path.basename(tile_path)
    tile_name = os.path.splitext(tile_name)[0]
    x = int(tile_name.split('x=')[1].split(',')[0])
    y = int(tile_name.split('y=')[1].split(',')[0])
    w = int(tile_name.split('w=')[1].split(',')[0])
    h = int(tile_name.split('h=')[1].split(']')[0])

    # 2. Form polygon
    tile = Polygon([
        (x, y),
        (x + w, y),
        (x + w, y + h),
        (x, y + h),
        (x, y)
    ])


    # 3. Check if tile intersects with any tissue polygon
    for tissue_polygon in tissue_polygons:
        intersection = tile.intersection(tissue_polygon)
        if intersection.area / tile.area > threshold:
            return True
    
    return False


def main():
    parser = argparse.ArgumentParser(description='Remove tiles with no tissue')
    parser.add_argument('-w', '--wsi', type=str, help='path/to/wsi', required=True)
    parser.add_argument('-e', '--export', type=str, help='path/to/export', required=True)
    args = parser.parse_args()

    tile_dir = os.path.join(args.export, 'Temp', 'tiler-output', 'Tiles', args.wsi)
    annotation_dir = os.path.join(args.export, 'Temp', 'threshold-output', 'Annotations', args.wsi)

    geojson_path = os.path.join(annotation_dir, 'annotations.geojson')
    tissue_polygons = geojson2poly(geojson_path)

    for tile in os.listdir(tile_dir):
        tile_path = os.path.join(tile_dir, tile)
        if not is_there_tissue(tile_path, tissue_polygons):
            os.remove(tile_path)
            logging.info(f"Removed tile {tile_path}: No tissue")


if __name__ == '__main__':
    main()
