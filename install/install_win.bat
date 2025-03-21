:: Load configuration
echo Loading configuration...
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
        goto gpu_detected
    )
)
:gpu_detected
echo NVIDIA GPU detected: %nvidia_gpu%


:: Detect CUDA 11.8 system-wide installation
echo Detecting CUDA 11.8 installation...
set "nvidia_cuda_118=false"
for /f "delims=" %%P in ('nvcc --version 2^>nul') do (
    echo %%P | findstr "release 11.8" >nul
    if not errorlevel 1 (
        set "nvidia_cuda_118=true"
        goto cuda_detected
    )
)

:: If CUDA 11.8 is not detected, check for cudatoolkit 11.8 in conda
if "%nvidia_cuda_118%"=="false" (
    for /f "delims=" %%P in ('conda list cudatoolkit --json 2^>nul') do (
        echo %%P | findstr "11.8" >nul
        if not errorlevel 1 (
            set "nvidia_cuda_118=true"
            goto cuda_detected
        )
    )
)

:cuda_detected
echo CUDA 11.8 detected: %nvidia_cuda_118%

:: Set suffix based on CUDA 11.8 detection
if %nvidia_cuda_118%==true (
    set "suffix=cu118"
) else (
    set "suffix=cpu"
)
echo suffix: %suffix%

:: Uninstall (if) existing (maybe incompatible) packages
echo Uninstalling existing packages that might be incompatible...
echo Uninstalling gncnn...
pip uninstall -y gncnn || echo gncnn was not installed.

echo Uninstalling mmcv-full...
pip uninstall -y mmcv-full || echo mmcv was not installed.

echo Uninstalling mmcv...
pip uninstall -y mmcv || echo mmcv was not installed.

echo Uninstalling mmcls...
pip uninstall -y mmcls || echo mmcls was not installed.

echo Uninstalling torchvision...
pip uninstall -y torchvision || echo torchvision was not installed.

echo Uninstalling torch...
pip uninstall -y torch || echo torch was not installed.

:: Install the required Python packages
echo Installing the required Python packages...
:: Install numpy and cached-property first to avoid errors in Python 3.9
pip install "numpy>=1.24.4,<2" "cached-property>=1.5.2" --no-cache-dir
:: If not installed previously, gncnn installation could raise an error
pip install torch==2.4.1 torchvision==0.19.1 --no-cache-dir --index-url https://download.pytorch.org/whl/%suffix%

if %errorlevel% neq 0 (
    echo.
    echo *******************************************************
    echo ERROR: Failed to install PyTorch and torchvision.
    echo This could be due to SSL/corporate security issues.
    echo Please check your firewall/proxy settings.
    echo *******************************************************
    echo.
    goto eof
)
:: Install mmcv+mmpretrain via openmim
pip install -U openmim --no-cache-dir && mim install "mmcv==2.2.0" "mmpretrain==1.2.0"

if %errorlevel% neq 0 (
    echo.
    echo *******************************************************
    echo ERROR: Failed to install mmcv.
    echo This could be due to SSL/corporate security issues.
    echo Please check your firewall/proxy settings.
    echo *******************************************************
    echo.
    goto eof
)

pip install ..\gncnn\[%suffix%] --no-cache-dir
echo Python packages installed.

:: Check if models are already in their target paths
echo Checking if models are already in their target paths...
for /f "delims=" %%i in ('python -c "import gncnn; print(gncnn.__path__[0])"') do set gncnn_path=%%i

:: Check if gncnn_path is empty
if "%gncnn_path%"=="" (
    echo.
    echo *******************************************************
    echo ERROR: gncnn is not installed or its path could not be determined.
    echo Skipping model checking and related operations.
    goto download_models
)

set detection_model_dir="%gncnn_path%\detection\logs\cascade_mask_rcnn_R_50_FPN_1x\external-validation\output\"
set classification_model_dir="%gncnn_path%\classification\logs\"

:: Verify if detection model exists
if exist "%detection_model_dir%\model_final.pth" (
    echo Detection model already exists in the target path.
    set "detection_model_exists=true"
) else (
    set "detection_model_exists=false"
)

:: Verify if classification models exist
if exist "%classification_model_dir%" (
    echo Classification models already exist in the target path.
    set "classification_model_exists=true"
) else (
    set "classification_model_exists=false"
)

:: Skip download and copy if all models exist
if "%detection_model_exists%"=="true" if "%classification_model_exists%"=="true" (
    echo All models are already in their target paths. Skipping download and copy process.
    goto models_checked
)

:download_models
:: Download the models
echo Downloading the models...
python .\download_models.py

:: Check if the models were downloaded
if not exist ..\models\models\detection\model_final.pth (
    echo.
    echo *******************************************************
    echo ERROR: The models were not downloaded successfully.
    echo Please check your internet connection and try again.
    echo It could also be due to SSL/corporate security issues.
    echo *******************************************************
    echo.
    goto eof
)
echo Models downloaded.

:: Copy the models to the target paths
echo Copying the models to the target paths...

:: Copy the detection model to the target path
if not exist %detection_model_dir% mkdir %detection_model_dir%
xcopy /E /I /Y "..\models\models\detection\*" %detection_model_dir%
echo Detection model copied.

:: Copy the classification models to the target path
if not exist %classification_model_dir% mkdir %classification_model_dir%
xcopy /E /I /Y "..\models\models\classification\*" %classification_model_dir%
echo Classification models copied.

:: Remove models directory
rmdir /S /Q ..\models

:models_checked

:: Install the QuPath extension
echo Installing QuPath extension
%qupath_path% script .\install.groovy --args %extension_path% --save
echo QuPath extension installed.

echo Installation completed.

:eof