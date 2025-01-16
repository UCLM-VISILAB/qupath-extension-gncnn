"""
From: https://github.com/Nicolik/MESCnn
Modified by: Israel Mateos-Aparicio-Ruiz
Modifications:
    - Removed QuPath path argument
    - Added pixel size argument
    - Modified area computation to use pixel size
    - Added annotations export to GeoJSON
    - Added TorchScript support for Windows/MacOS
"""
import logging
import os
import tqdm
import cv2
import numpy as np
import time
import sys

if 'linux' in sys.platform:
    from detectron2.engine import DefaultPredictor
    from gncnn.detection.model.config import build_model_config, CLI_MODEL_NAME_DICT, set_config, DEFAULT_SEGMENTATION_MODEL
else:
    import torch
    import torchvision.transforms as T

    def preprocess_input(image, device):
        height, width = image.shape[:2]

        transform = T.Compose([
            T.ToPILImage(),
            T.Resize(800),
        ])

        image = np.array(transform(image))
        image = torch.as_tensor(image.astype("float32").transpose(2, 0, 1))
        image.to(device)

        return {"image": image, "height": height, "width": width}

print("Loading local libraries...")
from gncnn.classification.gutils.utils import get_proper_device
from gncnn.definitions import ROOT_DIR
from gncnn.detection.qupath.config import MIN_AREA_GLOMERULUS_UM, DETECTRON_SCORE_THRESHOLD
from gncnn.detection.qupath.utils import get_dataset_dicts_validation, tile2xywh, mask2polygon, get_area_10x
from gncnn.detection.qupath.nms import nms
from gncnn.detection.qupath.download import download_detector
from gncnn.detection.qupath.shapely2geojson import poly2geojson
from gncnn.detection.qupath.mask_ops import paste_masks_in_image
print("Local libraries loaded!")


def main():
    import argparse
    parser = argparse.ArgumentParser(description='Segment Glomeruli with Detectron2 from WSI')
    parser.add_argument('-w', '--wsi', type=str, help='path/to/wsi', required=True)
    parser.add_argument('-e', '--export', type=str, help='path/to/export', required=True)
    parser.add_argument('-m', '--model', type=str, help='Model to use for inference', default="cascade_R_50_FPN_1x")
    parser.add_argument('-c', '--train-config', type=str, help='I=Internal/E=External/A=All', default="external")
    parser.add_argument('--undersampling', type=float, help='Undersampling factor of tiles', default=4)
    parser.add_argument('--pixel-size', type=float, help='Pixel size of the WSI', default=0.5)

    args = parser.parse_args()

    print("undersampling: ", args.undersampling)
    print("pixel size: ", args.pixel_size)

    if 'linux' in sys.platform:
        if args.model not in CLI_MODEL_NAME_DICT:
            logging.warning(f"Model '{args.model}' not present, default to {DEFAULT_SEGMENTATION_MODEL}!")
            args.model = DEFAULT_SEGMENTATION_MODEL

        train_config = args.train_config
        config_file, model_name = CLI_MODEL_NAME_DICT[args.model]

        cfg = build_model_config(config_file)
        config_dir = set_config(cfg, train_config)
    else:
        model_name = "cascade_mask_rcnn_R_50_FPN_1x"
        config_dir = 'external-validation'

    undersampling = args.undersampling

    tool_dir = os.path.join(ROOT_DIR, 'gncnn')
    model_folder = os.path.join(tool_dir, 'detection', 'logs', model_name, config_dir)
    logs_dir = os.path.join(model_folder, 'output')

    device = str(get_proper_device())
    platform = sys.platform
    # If platform is other than Linux, use torchscript
    if not 'linux' in platform:
        if device == "cuda":
            path_to_weights = os.path.join(logs_dir, "model_final_cuda.ts")
        elif device == "cpu":
            path_to_weights = os.path.join(logs_dir, "model_final_cpu.ts")
        else:
            raise ValueError(f"Unsupported device: {device}")
    else:
        path_to_weights = os.path.join(logs_dir, "model_final.pth")


    if not os.path.exists(path_to_weights):
        print(f"Model weights not found: {path_to_weights}!")
        model_path = download_detector(model_name, config_dir, tool_dir)
        print(f"Downloaded: {model_path}")
    else:
        print(f"Model weights found: {path_to_weights}!")

    if not 'linux' in platform:
        predictor = torch.jit.load(path_to_weights)
        predictor.eval()
    else:
        cfg.MODEL.WEIGHTS = path_to_weights
        cfg.MODEL.ROI_HEADS.SCORE_THRESH_TEST = DETECTRON_SCORE_THRESHOLD
        predictor = DefaultPredictor(cfg)

    tile_dir = os.path.join(args.export, 'Temp', 'tiler-output', 'Tiles', args.wsi)
    path_to_segment_output = os.path.join(args.export, 'Temp', 'segment-output')

    detection_dir = os.path.join(path_to_segment_output, 'Detections', args.wsi)
    os.makedirs(detection_dir, exist_ok=True)

    print(f"Attempting to build dataset dict from {tile_dir}")
    dataset_dicts = get_dataset_dicts_validation(tile_dir)

    masks_wsi = []
    bboxes_wsi = []
    scores_wsi = []
    offset_wsi = []

    counts = 0
    for dd, d in enumerate(tqdm.tqdm(dataset_dicts)):
        filename = d["file_name"]
        base_name = os.path.basename(filename)
        x1_off, y1_off, _, _ = tile2xywh(filename)

        logging.info(f"Basename: {base_name}, x_off: {x1_off}, y_off: {y1_off}")

        im = cv2.imread(filename)
        start_time = time.time()
        if 'linux' not in platform:
            lib = "TorchScript"
            # Disable gradient computation during inference
            with torch.no_grad():
                inputs = preprocess_input(im, device)  # Preprocess input image if needed
                image = inputs["image"]
                outputs = predictor(image)
            
            # keys=['pred_boxes', 'pred_classes', 'pred_masks', 'scores']
            boxes = outputs[0]
            scores = outputs[3].cpu().numpy()
            masks = outputs[2][:, 0, :, :]
            
            if outputs[2].shape[0] > 0:
                scale_factor = inputs["width"] / image.shape[2] # Images are square
                boxes *= scale_factor
                masks = paste_masks_in_image(masks, boxes, im.shape[:2])
            mask_array = masks.cpu().numpy()
        else:
            lib = "Detectron2"
            outputs = predictor(im)
            classes = outputs["instances"].get("pred_classes").cpu().numpy()
            scores = outputs["instances"].get("scores").cpu().numpy()

            mask_array = outputs['instances'].to("cpu").pred_masks.numpy()

        mask_array = mask_array.astype(np.uint8)
        end_time = time.time()
        elapsed_time = end_time - start_time
        logging.info(f"[{lib}] Elapsed Time (sec): {elapsed_time:.2f}")

        for m, mask in enumerate(mask_array):
            logging.info(f"Mask ({m}) - shape: {mask.shape}, dtype: {mask.dtype}, sum: {mask.sum()}")

            contours, heirarchy = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
            bounding_boxes = [cv2.boundingRect(contour) for contour in contours]

            if bounding_boxes:
                x1, y1 = bounding_boxes[0][0]*undersampling + int(x1_off), bounding_boxes[0][1]*undersampling + int(y1_off)
                w, h = bounding_boxes[0][2]*undersampling, bounding_boxes[0][3]*undersampling
                x2, y2 = x1 + w, y1 + h
                bboxes_wsi.append((x1, y1, x2, y2))
                scores_wsi.append(scores[m])
                masks_wsi.append(mask*255)
                offset_wsi.append((x1_off, y1_off))
                counts = counts + 1

    if len(bboxes_wsi) != 0:
        print(f"Before NMS: {len(bboxes_wsi)}")
        idxs = nms(bboxes_wsi, scores_wsi, threshold_iou=0.4, threshold_iom=0.4, return_idxs=True)
        print(f"After  NMS: {len(idxs)}")
    else:
        print("No detections found!")
        idxs = []

    picked_boxes = [bboxes_wsi[i] for i in idxs]
    picked_score = [scores_wsi[i] for i in idxs]
    picked_masks = [masks_wsi[i] for i in idxs]
    picked_offset = [offset_wsi[i] for i in idxs]

    list_polygons = []
    glomerular_areas = []

    for glomerulus_roi, single_glomerulus_mask, xy_offset in zip(picked_boxes, picked_masks, picked_offset):
        x1_roi, y1_roi, x2_roi, y2_roi = glomerulus_roi
        w_roi, h_roi = x2_roi - x1_roi, y2_roi - y1_roi

        single_glomerulus_mask[single_glomerulus_mask>0] = 255
        single_glomerulus_mask = single_glomerulus_mask.astype(np.uint8)

        polygon = mask2polygon(single_glomerulus_mask)
        area_um = get_area_10x(polygon) * (args.pixel_size * undersampling) ** 2
        glomerular_areas.append(area_um)

        polygon_large = np.array([[point[0]*undersampling + xy_offset[0],
                                   point[1]*undersampling + xy_offset[1]] for point in polygon])

        if area_um > MIN_AREA_GLOMERULUS_UM:
            list_polygons.append(polygon_large)
        else:
            logging.warning(f"Area: {area_um} below min area of {MIN_AREA_GLOMERULUS_UM}!")

    logging.info(f"BBoxes before NMS: {len(bboxes_wsi)} / after NMS: {len(picked_boxes)}")
    
    # Save as GeoJSON for QuPath
    path_to_geojson = os.path.join(detection_dir, 'detections.geojson')
    poly2geojson(list_polygons, 'Glomerulus', [0, 0, 255], path_to_geojson)


if __name__ == '__main__':
    main()
