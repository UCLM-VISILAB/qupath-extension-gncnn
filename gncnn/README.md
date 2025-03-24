# GNCnn

GNCnn (*G*lomerulo*N*ephritis *C*lassification by *n*eural *n*etwork) is an
extension which integrates a pipeline for glomerular detection and classification
into QuPath. The pipeline is designed to automatically segment and classify glomeruli
into sclerotic and non-sclerotic classes, and to further classify non-sclerotic
glomeruli into 12 common glomerulonephritis diagnoses.

If you find this tool useful for your research, please cite our article in __Computational and Structural Biotechnology Journal__ ([10.1016/j.csbj.2024.11.049](https://doi.org/10.1016/j.csbj.2024.11.049)):

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

## Installation

To install this tool, which is a local Python package, you need to have Python 3.9 installed on your system.
The package is offered in different versions, according to the platform you are using, which are:
- `linux-cpu`: for Linux without GPU support
- `linux-cu118`: for Linux with CUDA 11.8 support
- `cpu`: for Windows without GPU support
- `cu118`: for Windows with CUDA 11.8 support
- `mac`: for macOS with an Intel CPU

Then, you can install the package by running the following command in the terminal, outside this folder:

```bash
pip install ./gncnn/[{version}]
```

## Usage

This tool is thought to be used from a QuPath extension, which will provide a
user-friendly interface to run the pipeline on whole slide images.

## License

This extension is licensed under the GNU General Public License v3.0. For more information, see the [LICENSE](LICENSE) file.

Also, part of [detectron2](https://github.com/facebookresearch/detectron2) is used in the extension. Detectron2 is licensed under the Apache License 2.0. For more information, see the [LICENSE_DETECTRON2](LICENSE_DETECTRON2) file.
