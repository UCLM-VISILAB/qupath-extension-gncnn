:: Get paths from windows.cfg variables
for /f "usebackq tokens=1* delims==" %%a in ("windows.cfg") do (
    if "%%a"=="qupath_path" set qupath_path=%%b
    if "%%a"=="extension_path" set extension_path=%%b
)
echo QuPath installation path: %qupath_path%
echo Extension path: %extension_path%

:: Detect NVIDIA GPU
echo Detecting NVIDIA GPU...
set "nvidia_gpu=false"
for /f "usebackq tokens=1* delims=" %%a in (`wmic path win32_VideoController get name`) do (
    echo %%a | findstr /i "NVIDIA" >nul
    if not errorlevel 1 (
        set "nvidia_gpu=true"
        goto :break
    )
)
:break
echo NVIDIA GPU detected: %nvidia_gpu%

:: Set suffix and mmcv-version based on nvidia_gpu value
if %nvidia_gpu%==true (
    set "suffix=cu111"
) else (
    set "suffix=cpu"
)
echo suffix: %suffix%


:: Install the required Python packages
echo Installing the required Python packages...
:: Install numpy and cached-property first to avoid errors in Python 3.9
pip install "numpy>=1.24.4,<2" "cached-property>=1.5.2" --no-cache-dir
:: Install torch and torchvision pre-built with CUDA 11.1 if NVIDIA GPU is detected
:: Otherwise, install the CPU version
:: If not installed previously, gncnn cannot be installed (detectron2 dependency)
pip install torch==1.8.0+%suffix% torchvision==0.9.0+%suffix% --no-cache-dir -f https://download.pytorch.org/whl/torch_stable.html
:: Install pre-built mmcv-full to avoid errors when compiling from source
pip install "mmcv-full==1.7.2" --no-cache-dir -f https://download.openmmlab.com/mmcv/dist/%suffix%/torch1.8.0/index.html

pip install ..\gncnn\[%suffix%] --no-cache-dir
echo Python packages installed.

:: Download the models
echo Downloading the models...
python .\download_models.py
echo Models downloaded.

:: Get model target paths
echo Copying the models to the target paths...
for /f "delims=" %%i in ('python -c "import gncnn; print(gncnn.__path__[0])"') do set gncnn_path=%%i
set detection_model_dir="%gncnn_path%\detection\logs\cascade_mask_rcnn_R_50_FPN_1x\external-validation\output\"
set classification_model_dir="%gncnn_path%\classification\logs\"

:: Copy the detection model to the target path
if not exist %detection_model_dir% mkdir %detection_model_dir%
xcopy /E /I "..\models\models\detection\*" %detection_model_dir%
echo Detection model copied.

:: Copy the classification models to the target path
if not exist %classification_model_dir% mkdir %classification_model_dir%
xcopy /E /I "..\models\models\classification\*" %classification_model_dir%
echo Classification models copied.

:: Remove models directory
rmdir /S /Q ..\models

:: Install the QuPath extension
echo Installing QuPath extension
%qupath_path% script .\install.groovy --args %extension_path% --save
echo QuPath extension installed.