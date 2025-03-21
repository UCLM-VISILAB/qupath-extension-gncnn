# GNCnn

GNCnn (*G*lomerulo*N*ephritis *C*lassification by *n*eural *n*etwork) is an
extension which integrates a pipeline for glomerular detection and classification
into QuPath. The pipeline is designed to classify glomeruli into sclerotic and
non-sclerotic classes, and to further classify non-sclerotic glomeruli into
12 common common glomerulonephritis diagnoses.

If you find this tool useful for your research, please cite our article in __Computational and Structural Biotechnology Journal__:

```
@article{MATEOSAPARICIORUIZ202535,
title = {GNCnn: A QuPath extension for glomerulosclerosis and glomerulonephritis characterization based on deep learning},
journal = {Computational and Structural Biotechnology Journal},
volume = {27},
pages = {35-47},
year = {2025},
issn = {2001-0370},
doi = {https://doi.org/10.1016/j.csbj.2024.11.049},
url = {https://www.sciencedirect.com/science/article/pii/S2001037024004197},
author = {Israel Mateos-Aparicio-Ruiz and Anibal Pedraza and Jan Ulrich Becker and Nicola Altini and Jesus Salido and Gloria Bueno}
}
```

The pipeline is based on the [MESCnn](https://github.com/Nicolik/MESCnn) 
pipeline, which was developed for the Oxford classification of glomeruli in
IgA nephropathy. The paper for MESCnn can be found at 
[https://www.sciencedirect.com/science/article/pii/S0169260723004807](https://www.sciencedirect.com/science/article/pii/S0169260723004807),
and its citation in BibTeX is the following:

```
@article{ALTINI2023107814,
title = {Performance and Limitations of a Supervised Deep Learning Approach for the Histopathological Oxford Classification of Glomeruli with IgA Nephropathy},
journal = {Computer Methods and Programs in Biomedicine},
pages = {107814},
year = {2023},
issn = {0169-2607},
doi = {https://doi.org/10.1016/j.cmpb.2023.107814},
url = {https://www.sciencedirect.com/science/article/pii/S0169260723004807},
author = {Nicola Altini and Michele Rossini and Sándor Turkevi-Nagy and Francesco Pesce and Paola Pontrelli and Berardino Prencipe and Francesco Berloco and Surya Seshan and Jean-Baptiste Gibier and Anibal Pedraza Dorado and Gloria Bueno and Licia Peruzzi and Mattia Rossi and Albino Eccher and Feifei Li and Adamantios Koumpis and Oya Beyan and Jonathan Barratt and Huy Quoc Vo and Chandra Mohan and Hien Van Nguyen and Pietro Antonio Cicalese and Angela Ernst and Loreto Gesualdo and Vitoantonio Bevilacqua and Jan Ulrich Becker},
}
```

## Dependencies

The extension requires the following dependencies, depending on the version of the extension:

- GNCnn 0.1.x:
  - Python 3.8 or 3.9
  - CUDA 11.1

- GNCnn 0.2.x:
  - Python 3.9
  - CUDA 11.8

## Installation

> [!WARNING]
> This extension **is developed for QuPath 0.5.0 or higher**, and has not been tested with other versions.
>
> If you have a NVIDIA GPU, the extension only supports CUDA 11.1 for v0.1.x or CUDA 11.8 for v0.2.x. The extension will use the GPU by default if it is available. If you do not have a GPU, the extension will use the CPU.

GNCnn was tested on Ubuntu 20.04 and 22.04, Windows 10 and macOS Big Sur 11.4.

**0.** Install:

- For GNCnn 0.1.x:
  - Python 3.8 or 3.9
  - CUDA 11.1 if you are using a NVIDIA GPU

- For GNCnn 0.2.x:
  - Python 3.9
  - CUDA 11.8 if you are using a NVIDIA GPU
 
> [!IMPORTANT]
> When installing Python, make sure to check the option to add Python to the system PATH as in the following image:
>
> ![Python installation](images/python_path.png)

**1.** Download the latest release from the [Releases](https://github.com/israelMateos/qupath-extension-gncnn/releases/latest) page for the platform you are using.

**2.** Edit `install/config.cfg`. You should set the following variables:

- `qupath_path`: 
  - For Linux, the path to the QuPath installation directory. It should contain the `bin` directory, in which the `QuPath` executable is located.
  - For Windows and macOS, the path to the QuPath executable. In Windows, it should include the console version of QuPath, _e.g._ `QuPath-0.5.1 (console).exe`. In macOS, it should include the executable buried inside the `.app` directory, _e.g._ `QuPath-0.5.1-x64.app/Contents/MacOS/QuPath-0.5.1-x64`.
- `extension_path`: the path to the `.jar` file included in the release. It should include the file name.
  
**3.** From the `install` directory, run the following command:

- **Linux**:

```bash
bash install.sh
```

- **Windows**:

```bash
.\install.bat
```

- **macOS**:

```bash
sh install.sh
```

This script will install the Python tool on which the extension depends, and will download the model weights. It will also create a new directory for the GNCnn extension in the QuPath extensions directory. The next time you open QuPath, the extension will be available in the menu.

**4.** Once the extension is installed, you can remove this repository's directory (`qupath-extension-gncnn`) from your system.

## Usage
The extension adds a new menu item to QuPath, called *GNCnn*. This menu item contains the button *Open GNCnn*.

This button opens a dialog window, where you can select the image/s you want to analyze.
You must also select the classification mode: *Sclerotic vs Non-Sclerotic* or *Sclerotic + 12 classes*.

After selecting the image/s and the classification mode, click:

- *Run Detection* to detect glomeruli in the image/s.
- *Run Classification* to classify "Glomerulus" annotations into the selected classes.
- *Run Detection + Classification* to run both detection and classification.

<img src="images/gncnn_main.png" alt="GNCnn Dialog" width="400"/>

The glomeruli are automatically annotated in the corresponding images. An example of the annotations is shown below:

![GNCnn Annotations](images/gncnn_ann.png)

Another button, *View results*, opens a dialog window with the results of the selected image/s.
The results are shown in a table, where each WSI presents:

- The number of glomeruli detected.
- The number of glomeruli for each class.
- The 3 most probable classes for the WSI.

![GNCnn Results](images/gncnn_results.png)

## Building the extension

To build the extension from source, use the following command from the root directory of the repository:

```bash
./gradlew clean shadow
```

The extension `.jar` file will be generated in the `build/libs` directory.

## Troubleshooting

### Installation fails with SSL error

If the installation fails with an SSL error, it may be due to firewall corporate restrictions, not allowing the download of the model weights. To fix this, you can:
- Use a different network.
- Contact your network administrator to allow the installation.
- Contact the main developer, [Israel Mateos-Aparicio](mailto:israel.MateosAparici@uclm.es?subject=[GNCnn]%20Installation%20issue), to get the model weights and manually copy them to the `gncnn` directory in the QuPath extensions directory.

### Bug with Ubuntu

If you are using Ubuntu, you may get `Ubuntu Error 13: Permission denied` when trying to run the extension. As stated in [this issue](https://forum.image.sc/t/could-not-execute-system-command-in-qupath-thanks-to-groovy-script-and-java-processbuilder-class/61629/2?u=oburri), Java's `ProcessBuilder` class is not allowed to run on Ubuntu.

To fix this, QuPath must be [built from source](https://qupath.readthedocs.io/en/stable/docs/reference/building.html) instead of using the installer. This will allow the extension to run without issues.

### Bug with Windows

If you are using Windows, you may get an error when trying to install the extension. In the last step, _i.e._, when the extension is being copied to the QuPath extensions directory, you may get an error saying that the file is being used by another process. This occurs if the extension is already loaded in QuPath.

To fix this, close QuPath before running the installation script, and remove the extension from the QuPath extensions directory if it is already there.

### macOS installation takes too long

In some versions of macOS, the installation script may seem to hang when installing the Python tool. Specifically, this may occur when the script is trying to install the `opencv-python` package. This is a known issue with the `opencv-python` package, and it is not related to the extension.

Although the script may seem to hang, it is still running. The package must be compiled from source, which takes some time. The installation will finish eventually.

### Glomeruli detection takes too long

The glomeruli detection process is the most time-consuming part of the pipeline. In case you are using a CPU, the process may take a long time to finish (depending on the image size, it may take more than 10 minutes). This is because the detection process is based on a two-stage model, which is computationally expensive. If you have a CUDA-compatible GPU, the process will be faster.

Currently, MPS is not supported, so the extension will use the CPU by default in macOS. This may be changed in future versions.

## License

This extension is licensed under the GNU General Public License v3.0. For more information, see the [LICENSE](LICENSE) file.

Also, part of [detectron2](https://github.com/facebookresearch/detectron2) is used in the extension. Detectron2 is licensed under the Apache License 2.0. For more information, see the [LICENSE_DETECTRON2](gncnn/LICENSE_DETECTRON2) file.