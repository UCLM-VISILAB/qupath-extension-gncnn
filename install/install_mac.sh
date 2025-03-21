#!/bin/sh

# Get QuPath installation path from mac.cfg qupath_path variable
. ./mac.cfg
echo "QuPath installation path: ${qupath_path}"
echo "Extension path: ${extension_path}"

echo "Installing the required Python packages..."

# Install mmcv+mmpretrain via openmim
pip install -U openmim --no-cache-dir && mim install "mmcv==2.2.0" "mmpretrain==1.2.0"

if [ $? -ne 0 ]; then
    cat <<EOF
*******************************************************
ERROR: Failed to install mmcv.
This could be due to SSL/corporate security issues.
Please check your firewall/proxy settings.
*******************************************************
EOF
    fi

# Uninstall (if) existing (maybe incompatible) packages
echo "Uninstalling existing packages that might be incompatible..."
echo "Uninstalling gncnn..."
if pip show gncnn > /dev/null 2>&1; then
    pip uninstall -y gncnn
    echo "gncnn was uninstalled."
else
    echo "gncnn was not installed previously."
fi

echo "Uninstalling mmcls..."
if pip show mmcls > /dev/null 2>&1; then
    pip uninstall -y mmcls
    echo "mmcls was uninstalled."
else
    echo "mmcls was not installed previously."
fi

echo "Uninstalling mmcv"
if pip show mmcv > /dev/null 2>&1; then
    pip uninstall -y mmcv
    echo "mmcv was uninstalled."
else
    echo "mmcv was not installed previously."
fi

echo "Uninstalling torchvision"
if pip show torchvision > /dev/null 2>&1; then
    pip uninstall -y torchvision
    echo "torchvision was uninstalled."
else
    echo "torchvision was not installed previously."
fi

echo "Uninstalling torch"
if pip show torch > /dev/null 2>&1; then
    pip uninstall -y torch
    echo "torch was uninstalled."
else
    echo "torch was not installed previously."
fi

# Install the gncnn package
pip install "../gncnn/[mac]" --no-cache-dir

echo "Python packages installed."

# Check if the models are already in their target paths
echo "Checking if models are already in their target paths..."
gncnn_path=$(python -c "import gncnn; print(gncnn.__path__[0])")

# Check if the gncnn path is empty
if [ -z "$gncnn_path" ]; then
    cat <<EOF
*******************************************************
ERROR: gncnn is not installed or its path could not be determined.
Skipping model checking and related operations.
*******************************************************
EOF
    exit 1
fi

detection_model_dir="${gncnn_path}/detection/logs/cascade_mask_rcnn_R_50_FPN_1x/external-validation/output/"
classification_model_dir="${gncnn_path}/classification/logs/"

if [ -f "${detection_model_dir}/model_final.pth" ] && [ "$(ls -A ${classification_model_dir})" ]; then
    echo "Models are already in their target paths. Skipping download and copy process."
else
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

    # Copy the models to the target paths
    echo "Copying the models to the target paths..."
    mkdir -p ${detection_model_dir}
    cp ../models/models/detection/* ${detection_model_dir}
    echo "Detection model copied."

    mkdir -p ${classification_model_dir}
    cp -r ../models/models/classification/* ${classification_model_dir}
    echo "Classification models copied."

    # Remove models directory
    rm -rf ../models
fi

# Install the QuPath extension
echo "Installing QuPath extension"
$qupath_path script ./install.groovy --args $extension_path --save
echo "QuPath extension installed."

echo "Installation completed successfully."