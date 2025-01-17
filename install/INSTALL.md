# GNCnn Installation

> [!WARNING]
> This extension **is developed for QuPath 0.5.0 or higher**, and has not been tested with other versions.
>
> If having a NVIDIA GPU, the extension only supports CUDA 11.1. CPU is also supported.

GNCnn was tested on Ubuntu 20.04 and 22.04, Windows 10 and macOS Big Sur 11.4. It requires Python 3.8 or 3.9 (not higher).

**0.** Install Python 3.8 or 3.9 (not higher) on your system.

> [!IMPORTANT]
> When installing Python, make sure to check the option to add Python to the system PATH as in the following image:
>
> ![Python installation](../images/python_path.png)

<!-- **0.** Install on your system the following dependencies:

- Python 3.8 or 3.9 (not higher)
- Git LFS (for downloading the model weights) -->

**1.** Download the `.jar` file for the extension from the [Releases](https://github.com/israelMateos/qupath-extension-gncnn/releases/latest) page.

**2.** This step depends on the platform you are using.

- **Linux**: edit `install/linux.cfg`.
- **Windows**: edit `install/windows.cfg`.
- **macOS**: edit `install/mac.cfg`.

In the configuration file, you should set the following variables:

- `qupath_path`: 
  - For Linux, the path to the QuPath installation directory. It should contain the `bin` directory, in which the `QuPath` executable is located.
  - For Windows and macOS, the path to the QuPath executable. In Windows, it should include the console version of QuPath, _e.g._ `QuPath-0.5.1 (console).exe`. In macOS, it should include the executable buried inside the `.app` directory, _e.g._ `QuPath-0.5.1-x64.app/Contents/MacOS/QuPath-0.5.1-x64`.
- `extension_path`: the path to the `.jar` file downloaded in step 1. It should include the file name.
  
**3.** From the `install` directory, run the following command:

## Troubleshooting

### Installation fails with SSL error

If the installation fails with an SSL error, it may be due to firewall corporate restrictions, not allowing the download of the model weights. To fix this, you can:
- Use a different network.
- Contact your network administrator to allow the installation.
- Contact the main developer, [Israel Mateos-Aparicio](mailto:israel.MateosAparici@uclm.es?subject=[GNCnn]%20Installation%20issue), to get the model weights and manually copy them to the `gncnn` directory in the QuPath extensions directory.