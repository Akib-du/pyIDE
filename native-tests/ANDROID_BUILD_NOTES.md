# Android native build constraints (Python 3.10 / arm64-v8a)

This file records constraints verified during dependency research. It is not a
claim that the native packages or APK have been built.

## Current app baseline

- Chaquopy plugin: 15.0.1
- Android Gradle Plugin: 8.5.2
- Python: 3.10 in the standard flavor
- ABI: arm64-v8a
- minSdk: 24

Do not change these versions until the candidate wheels have been built and
tested. Chaquopy 15 supports Python 3.10 and Android Gradle Plugin 8.5.2.

## XGBoost

Chaquopy's source tree contains an Android build recipe for XGBoost 1.7.3 and
a Chaquopy-specific patch. The public Chaquopy wheel index also has older
Android wheels, but the previously identified published wheel is tagged for
CPython 3.8 and cannot be installed into this app's CPython 3.10 runtime.
Build a CPython 3.10 / Android arm64 wheel from the recipe and run the
on-device train/predict/save/load test before integrating it.

## GDAL

GDAL documents Android installation through vcpkg using the
`arm64-android` triplet. However, the current vcpkg GDAL port explicitly
configures `BUILD_PYTHON_BINDINGS=OFF`. A successful vcpkg build therefore
provides the native GDAL library, not `from osgeo import gdal`.

The Python bindings must be built separately against the exact same GDAL
headers and library, then packaged with every required shared library and
GDAL data resource. The GDAL Python-binding documentation lists libgdal,
development headers, NumPy and SWIG as relevant build requirements.

## Rasterio

Rasterio's current documentation states that Rasterio 1.5 and later require
Python 3.12 or newer and GDAL 3.8 or newer. This app currently uses Python
3.10, so do not add Rasterio 1.5+ to the current flavor.

For the Python 3.10 baseline, evaluate a compatible older Rasterio release
(e.g. the 1.3 series) and pin it to the exact GDAL ABI used by the Android
native build. Compile its extension modules against that same libgdal.
Do not bundle an unrelated PyPI Linux wheel.

## Required release gates

1. Build XGBoost, GDAL Python bindings, and Rasterio for CPython 3.10 /
   Android arm64-v8a.
2. Audit every ELF shared object for ARM64 architecture, unresolved
   dependencies, correct SONAMEs, and Android-compatible load alignment.
3. Include required GDAL data files and driver support.
4. Install all wheels and native libraries into the standard flavor without
   changing the existing app source or the main branch.
5. Build the APK.
6. Run `native_stack_smoke_test.py` on a physical ARM64 Android device.
7. Only publish the APK after every release gate passes.

## Current status

Acceptance test source is committed. No Android-native XGBoost, GDAL Python,
or Rasterio wheel has yet passed these gates. No updated APK is available yet.
