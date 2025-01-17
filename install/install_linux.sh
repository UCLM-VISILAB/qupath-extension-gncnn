#!/usr/bin/env bash

# Get QuPath installation path from linux.cfg qupath_path variable
source ./linux.cfg
echo "QuPath installation path: ${qupath_path}"
echo "Extension path: ${extension_path}"

# Detect NVIDIA GPU and system-wide CUDA 11.1 installation
echo "Detecting NVIDIA GPU..."
nvidia-smi > /dev/null
if [ $? -eq 0 ]; then
    echo "NVIDIA GPU detected."
    suffix='cu111'
else
    echo "NVIDIA GPU not detected."
    suffix='cpu'
fi

echo "Detecting system-wide CUDA 11.1 installation..."
nvcc --version | grep "release 11.1"
if [ $? -eq 0 ]; then
    echo "System-wide CUDA 11.1 installation detected."
    suffix='cu111system'
else
    # Check for cudatoolkit 11.1 in conda
    conda list cudatoolkit | grep "11.1"
    if [ $? -eq 0 ]; then
        echo "Conda CUDA 11.1 installation detected."
        suffix='cu111conda'
    else
        echo "System-wide CUDA 11.1 installation not detected."
    fi
fi


# Install the required Python packages
echo "Installing the required Python packages..."
# Install numpy and cached-property first to avoid errors in Python 3.9
pip install "numpy>=1.24.4,<2" "cached-property>=1.5.2" --no-cache-dir
# If not installed previously, gncnn cannot be installed (detectron2 dependency)
# Install pre-built torch and torchvision if CUDA 11.1 is not installed system-wide
# Otherwise, install the PyPI-indexed version
if [ "$suffix" != 'cu111system' ] && [ "$suffix" != 'cu111conda' ]; then
    pip install torch==1.8.0+${suffix} \
        torchvision==0.9.0+${suffix} \
        --no-cache-dir \
        -f https://download.pytorch.org/whl/torch_stable.html
    
    if [ $? -ne 0 ]; then
        cat <<EOF
*******************************************************
ERROR: Failed to install PyTorch and torchvision.
This could be due to SSL/corporate security issues.
Please check your firewall/proxy settings.
*******************************************************
EOF
    fi
else
    pip install torch==1.8.0 torchvision==0.9.0 --no-cache-dir
fi
# Install pre-built mmcv-full if CUDA 11.1 is not installed system-wide
# Otherwise, install the PyPI-indexed version
if [ $suffix != 'cu111system' ]; then
    if [ $suffix == 'cu111conda' ]; then
        aux_suffix='cu111conda'
        suffix='cu111'
    fi
    pip install "mmcv-full==1.7.2" --no-cache-dir -f https://download.openmmlab.com/mmcv/dist/${suffix}/torch1.8.0/index.html

    if [ $? -ne 0 ]; then
        cat <<EOF
*******************************************************
ERROR: Failed to install mmcv-full.
This could be due to SSL/corporate security issues.
Please check your firewall/proxy settings.
*******************************************************
EOF
    fi

    if [ $aux_suffix ]; then
        suffix=$aux_suffix
    fi
else
    pip install "mmcv-full==1.7.2" --no-cache-dir
fi

if [ $suffix == 'cu111conda' ]; then
    suffix='cu111system'
fi

# Install the gncnn package
pip install ../gncnn/[linux-${suffix}] --no-cache-dir
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
_path=$(python -c "import gncnn; print(gncnn.__path__[0])")

# Check if gncnn_path is empty
if [ -z "$_path" ]; then
    cat <<EOF
*******************************************************
ERROR: gncnn is not installed or its path could not be determined.
Skipping model copying and related operations.
*******************************************************
EOF
    exit 1
fi

detection_model_dir="${_path}/detection/logs/cascade_mask_rcnn_R_50_FPN_1x/external-validation/output/"
classification_model_dir="${_path}/classification/logs/"

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
qupath="${qupath_path}/bin/QuPath"
$qupath script ./install.groovy --args $extension_path --save
echo "QuPath extension installed."

echo "Installation completed."