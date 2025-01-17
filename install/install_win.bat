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

:: Detect CUDA 11.1 installation
echo Detecting CUDA 11.1 installation...
set "nvidia_cuda_111=false"
if exist "C:\Program Files\NVIDIA GPU Computing Toolkit\CUDA\v11.1\bin\nvcc.exe" set "nvidia_cuda_111=true"
if "%nvidia_cuda_111%"=="false" (
    for /f "delims=" %%P in ('nvcc --version 2^>nul') do (
        echo %%P | findstr "release 11.1" >nul
        if not errorlevel 1 (
            set "nvidia_cuda_111=true"
            goto cuda_detected
        )
    )
)

:: If CUDA 11.1 is not detected, check for cudatoolkit 11.1 in conda
set "nvidia_cuda_111_conda=false"
if "%nvidia_cuda_111_conda%"=="false" (
    for /f "delims=" %%P in ('conda list cudatoolkit --json 2^>nul') do (
        echo %%P | findstr "11.1" >nul
        if not errorlevel 1 (
            set "nvidia_cuda_111_conda=true"
            goto cuda_detected
        )
    )
)

:cuda_detected
echo System-wide CUDA 11.1 detected: %nvidia_cuda_111%
echo Conda CUDA 11.1 detected: %nvidia_cuda_111_conda%

:: Set suffix and mmcv-version based on GPU and CUDA detection
if %nvidia_gpu%==true (
    if %nvidia_cuda_111%==true (
        set "suffix=cu111system"
    ) else if %nvidia_cuda_111_conda%==true (
        set "suffix=cu111conda"
    ) else (
        set "suffix=cu111"
    )
) else (
    set "suffix=cpu"
)
echo suffix: %suffix%


:: Install the required Python packages
echo Installing the required Python packages...
:: Install numpy and cached-property first to avoid errors in Python 3.9
pip install "numpy>=1.24.4,<2" "cached-property>=1.5.2" --no-cache-dir
:: If not installed previously, gncnn cannot be installed (detectron2 dependency)
:: Install pre-built torch and torchvision if CUDA 11.1 is not installed system-wide
:: Otherwise, install the PyPI-indexed version
if %suffix%==cu111 (
    pip install torch==1.8.0+%suffix% torchvision==0.9.0+%suffix% --no-cache-dir -f https://download.pytorch.org/whl/torch_stable.html

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
)
if %suffix%==cpu (
    pip install torch==1.8.0+%suffix% torchvision==0.9.0+%suffix% --no-cache-dir -f https://download.pytorch.org/whl/torch_stable.html

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
)
if %suffix%==cu111system (
    pip install torch==1.8.0 torchvision==0.9.0 --no-cache-dir
)
if %suffix%==cu111conda (
    pip install torch==1.8.0 torchvision==0.9.0 --no-cache-dir
)
:: Install pre-built mmcv-full if CUDA 11.1 is not installed system-wide
:: Otherwise, install the PyPI-indexed version
if %suffix%==cu111 (
    pip install "mmcv-full==1.7.2" --no-cache-dir -f https://download.openmmlab.com/mmcv/dist/%suffix%/torch1.8.0/index.html

    if %errorlevel% neq 0 (
        echo.
        echo *******************************************************
        echo ERROR: Failed to install mmcv-full.
        echo This could be due to SSL/corporate security issues.
        echo Please check your firewall/proxy settings.
        echo *******************************************************
        echo.
        goto eof
    )
)
if %suffix%==cu111conda (
    pip install "mmcv-full==1.7.2" --no-cache-dir -f https://download.openmmlab.com/mmcv/dist/cu111/torch1.8.0/index.html

    if %errorlevel% neq 0 (
        echo.
        echo *******************************************************
        echo ERROR: Failed to install mmcv-full.
        echo This could be due to SSL/corporate security issues.
        echo Please check your firewall/proxy settings.
        echo *******************************************************
        echo.
        goto eof
    )
)
if %suffix%==cpu (
    pip install "mmcv-full==1.7.2" --no-cache-dir -f https://download.openmmlab.com/mmcv/dist/%suffix%/torch1.8.0/index.html

    if %errorlevel% neq 0 (
        echo.
        echo *******************************************************
        echo ERROR: Failed to install mmcv-full.
        echo This could be due to SSL/corporate security issues.
        echo Please check your firewall/proxy settings.
        echo *******************************************************
        echo.
        goto eof
    )
)
if %suffix%==cu111system (
    pip install "mmcv-full==1.7.2" --no-cache-dir
)

if %suffix%==cu111conda (
    set "suffix=cu111system"
)

pip install ..\gncnn\[%suffix%] --no-cache-dir
echo Python packages installed.

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

:: Get model target paths
echo Copying the models to the target paths...
for /f "delims=" %%i in ('python -c "import gncnn; print(gncnn.__path__[0])"') do set gncnn_path=%%i

:: Check if gncnn_path is empty
if "%gncnn_path%"=="" (
    echo.
    echo *******************************************************
    echo ERROR: gncnn is not installed or its path could not be determined.
    echo Skipping model copying and related operations.
    goto eof
)

set detection_model_dir="%gncnn_path%\detection\logs\cascade_mask_rcnn_R_50_FPN_1x\external-validation\output\"
set classification_model_dir="%gncnn_path%\classification\logs\"

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

:: Install the QuPath extension
echo Installing QuPath extension
%qupath_path% script .\install.groovy --args %extension_path% --save
echo QuPath extension installed.

echo Installation completed.

:eof