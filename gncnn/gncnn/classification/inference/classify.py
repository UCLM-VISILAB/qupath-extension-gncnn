"""
From: https://github.com/Nicolik/MESCnn
Modified by: Israel Mateos-Aparicio-Ruiz
Modifications:
    - Instead of Oxford classification, we use a two-step classification:
        1. Sclerotic vs. Non-Sclerotic
        2. 12 classes (Non-sclerotic), which is optional
    - Added top-3 prediction (instead of top-1) for the 12 classes
    - Added most predicted class to the summary report
    - Removed download of the models (only local models are used)
"""
import logging
import os

import numpy as np
import pandas as pd

from mmpretrain import ImageClassificationInferencer

from gncnn.classification.gutils.utils import get_proper_device
from gncnn.classification.inference.paths import get_logs_path
from gncnn.definitions import ROOT_DIR


def get_most_predicted_topk_classes(scores, topk):
    """Each glomerulus have topk classes predicted. This function returns the topk classes that are most predicted for the whole WSI."""
    scores = scores.sum(axis=0)
    topk_labels = np.argsort(scores)[::-1][:topk]
    return topk_labels


def main():
    import argparse
    parser = argparse.ArgumentParser(description='Classifiers Inference for Glomeruli Task')
    parser.add_argument('-r', '--root-path', type=str, help='Root path', default=ROOT_DIR)
    parser.add_argument('-e', '--export-dir', type=str, help='Directory to export report', required=True)
    parser.add_argument('--netB', type=str, help='Network architecture for Sclerotic vs. Non-Sclerotic', required=True)
    parser.add_argument('--netM', type=str, help='Network architecture for 12 classes (Non-sclerotic)', required=False)
    parser.add_argument('--multi', action='store_true', help='Use 12-class classification after Sclerotic vs. Non-Sclerotic classification', default=False)
    parser.add_argument('--topk', type=int, help='Top k classes to consider for 12-class classification', default=1)
    args = parser.parse_args()

    if args.multi and args.netM is None:
        parser.error("--multi requires --netM")

    net_name_dict = {
        "B": args.netB,
        "M": args.netM if args.multi else None,
    }

    config_name_dict = {
        "convnext": "convnext-base_32xb128_in1k",
        "swin_transformer": "swin-tiny_16xb64_in1k",
    }

    root_path = args.root_path
    export_dir = args.export_dir

    gdc_log_dir = get_logs_path(root_path)
    crop_dir = os.path.join(export_dir, "Temp", "ann-export-output")

    if not os.path.exists(crop_dir):
        logging.warning(f"Directory {crop_dir} does not exist")
        return

    # report_dir = os.path.join(export_dir, "Report")
    report_dir = os.path.join(export_dir, "Report", f"B-{args.netB}_M-{args.netM}")
    os.makedirs(report_dir, exist_ok=True)

    wsi_ids = os.listdir(crop_dir)
    if len(wsi_ids) == 0:
        logging.warning("No WSI IDs found in the export directory")
        return

    wsi_dict = {
        'WSI-ID': [],
        'most-predicted-class': [],
        'ratio-most-predicted-class': [],
    }

    output_file_summary_csv = os.path.join(report_dir, "summary.csv")

    for wsi_id in wsi_ids:
        output_file_csv = os.path.join(report_dir, f"{wsi_id}.csv")
        prediction_dir = os.path.join(crop_dir, wsi_id)

        gdc_dict = {
            'filename': [],
            'predicted-class': [],

            'NoSclerotic-prob': [],
            'Sclerotic-prob': [],

            'ABMGN-prob': [],
            'ANCA-prob': [],
            'C3-GN-prob': [],
            'CryoglobulinemicGN-prob': [],
            'DDD-prob': [],
            'Fibrillary-prob': [],
            'IAGN-prob': [],
            'IgAGN-prob': [],
            'MPGN-prob': [],
            'Membranous-prob': [],
            'PGNMID-prob': [],
            'SLEGN-IV-prob': [],
        }

        # Model 1: Sclerotic vs. Non-Sclerotic
        net_name = net_name_dict["B"]
        net_path = os.path.join(gdc_log_dir, 'binary', net_name, f'{net_name}_B_ckpt.pth')

        device = get_proper_device()
        bin_model = ImageClassificationInferencer(
            model=config_name_dict[net_name],
            pretrained=net_path,
            device=device,
            head=dict(num_classes=2)
        )

        # Model 2: 12 classes
        mult_model = None
        if args.multi:
            net_name = net_name_dict["M"]
            net_path = os.path.join(gdc_log_dir, '12classes', net_name, f'{net_name}_M_ckpt.pth')
            
            mult_model = ImageClassificationInferencer(
                model=config_name_dict[net_name],
                pretrained=net_path,
                device=device,
                head=dict(num_classes=12)
            )
        
        images_list = os.listdir(prediction_dir)
        images_list = [os.path.join(prediction_dir, f) for f in images_list if f.endswith(".png")]
        for image_path in images_list:
            # Forward the sclerotic vs. non-sclerotic model
            scores = bin_model(image_path)[0]["pred_scores"]

            class_idxs = np.argsort(scores)[::-1]
            bin_labels = [
                bin_model.classes[class_idx]
                for class_idx in class_idxs
            ]

            # Collect the predicted class and the scores (scores have shape (num_classes,))
            pred_label = class_idxs[:1]
            pred_class = bin_labels[pred_label[0]][3:]
            gdc_dict['NoSclerotic-prob'].append(scores[0])
            gdc_dict['Sclerotic-prob'].append(scores[1])

            if not args.multi or pred_class == "Sclerotic":
                # Append NaNs for the 12 classes
                gdc_dict['ABMGN-prob'].append(np.nan)
                gdc_dict['ANCA-prob'].append(np.nan)
                gdc_dict['C3-GN-prob'].append(np.nan)
                gdc_dict['CryoglobulinemicGN-prob'].append(np.nan)
                gdc_dict['DDD-prob'].append(np.nan)
                gdc_dict['Fibrillary-prob'].append(np.nan)
                gdc_dict['IAGN-prob'].append(np.nan)
                gdc_dict['IgAGN-prob'].append(np.nan)
                gdc_dict['MPGN-prob'].append(np.nan)
                gdc_dict['Membranous-prob'].append(np.nan)
                gdc_dict['PGNMID-prob'].append(np.nan)
                gdc_dict['SLEGN-IV-prob'].append(np.nan)
            else:
                # Forward the 12 classes model
                scores = mult_model(image_path)[0]["pred_scores"]
                # Collect the predicted class and the scores (scores have shape (num_classes,))
                pred_label = np.argsort(scores)[::-1]
                topk_labels = pred_label[:args.topk]
                pred_class = [mult_model.classes[l][3:] for l in topk_labels]
                gdc_dict['ABMGN-prob'].append(scores[0])
                gdc_dict['ANCA-prob'].append(scores[1])
                gdc_dict['C3-GN-prob'].append(scores[2])
                gdc_dict['CryoglobulinemicGN-prob'].append(scores[3])
                gdc_dict['DDD-prob'].append(scores[4])
                gdc_dict['Fibrillary-prob'].append(scores[5])
                gdc_dict['IAGN-prob'].append(scores[6])
                gdc_dict['IgAGN-prob'].append(scores[7])
                gdc_dict['MPGN-prob'].append(scores[8])
                gdc_dict['Membranous-prob'].append(scores[9])
                gdc_dict['PGNMID-prob'].append(scores[10])
                gdc_dict['SLEGN-IV-prob'].append(scores[11])

                pred_class = " | ".join(pred_class)
            
            gdc_dict['filename'].append(image_path)
            gdc_dict['predicted-class'].append(pred_class)

        gdc_df = pd.DataFrame(data=gdc_dict)
        gdc_df.to_csv(output_file_csv, sep=';', index=False)

        # Save each WSI's results
        if args.topk == 1:
            most_predicted_class = gdc_df['predicted-class'].mode().values[0]
            count_most_predicted_class = gdc_df[gdc_df['predicted-class'] == most_predicted_class].shape[0]
        else:
            scores = gdc_df[['ABMGN-prob', 'ANCA-prob', 'C3-GN-prob', 'CryoglobulinemicGN-prob', 'DDD-prob', 'Fibrillary-prob', 'IAGN-prob', 'IgAGN-prob', 'MPGN-prob', 'Membranous-prob', 'PGNMID-prob', 'SLEGN-IV-prob']].values
            no_sclerotic_prob = gdc_df['NoSclerotic-prob'].values.reshape(-1, 1)
            scores = scores * no_sclerotic_prob
            sclerotic_prob = gdc_df['Sclerotic-prob'].values.reshape(-1, 1)
            scores = np.concatenate((sclerotic_prob, scores), axis=1)
            # Replace NaNs with 0
            scores = np.nan_to_num(scores)
            topk_labels = get_most_predicted_topk_classes(scores, args.topk)

            classes = []
            for label in topk_labels:
                if label == 0:
                    classes.append("Sclerotic")
                else:
                    classes.append(mult_model.classes[label - 1][3:])
            most_predicted_class = " | ".join(classes)
            # Get the count of the most predicted class (the first one in the topk)
            top1_most_predicted_class = classes[0]
            top1_predicted_classes = gdc_df['predicted-class'].apply(lambda x: x.split(" | ")[0])
            count_most_predicted_class = gdc_df[top1_predicted_classes == top1_most_predicted_class].shape[0]

        total_crops = gdc_df.shape[0]

        wsi_dict['WSI-ID'].append(wsi_id)
        wsi_dict['most-predicted-class'].append(most_predicted_class)
        wsi_dict['ratio-most-predicted-class'].append(f'{count_most_predicted_class} | {total_crops}')


    wsi_df = pd.DataFrame(data=wsi_dict)
    wsi_df.to_csv(output_file_summary_csv, sep=';', index=False)


if __name__ == '__main__':
    main()
