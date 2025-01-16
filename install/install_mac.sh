#!/bin/sh

# Get QuPath installation path from mac.cfg qupath_path variable
. ./mac.cfg
echo "QuPath installation path: ${qupath_path}"
echo "Extension path: ${extension_path}"

Install the required Python packages
echo "Installing the required Python packages..."
pip install "../gncnn/[mac]" --no-cache-dir
echo "Python packages installed."

# Download the models
echo "Downloading the models..."
python3 ./download_models.py

# Check if the models were downloaded
if [ ! -f "../models/models/detection/model_final.pth" ]; then
    cat <<EOF
*******************************************************
ERROR: The models were not downloaded.
Please check your internet connection and try again.
This could also be due to SSL/corporate security issues.
*******************************************************
EOF
    exit 1
fi
echo "Models downloaded."

# Get model target paths
echo "Copying the models to the target paths..."
gncnn_path=$(python -c "import gncnn; print(gncnn.__path__[0])")

# Check if the gncnn path is empty
if [ -z "$gncnn_path" ]; then
    cat <<EOF
*******************************************************
ERROR: gncnn is not installed or its path could not be determined.
Skipping model copying and related operations.
*******************************************************
EOF
    exit 1
fi

detection_model_dir="${gncnn_path}/detection/logs/cascade_mask_rcnn_R_50_FPN_1x/external-validation/output/"
classification_model_dir="${gncnn_path}/classification/logs/"

# Copy the detection model to the target path
mkdir -p ${detection_model_dir}
cp ../models/models/detection/* ${detection_model_dir}
echo "Detection model copied."

# Copy the classification models to the target path
mkdir -p ${classification_model_dir}
cp -r ../models/models/classification/* ${classification_model_dir}
echo "Classification models copied."

# Remove models directory
rm -rf ../models

# Install the QuPath extension
echo "Installing QuPath extension"
$qupath_path script ./install.groovy --args $extension_path --save
echo "QuPath extension installed."

echo "Installation completed successfully."