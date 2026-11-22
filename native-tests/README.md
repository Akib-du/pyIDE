# Native ML and geospatial stack acceptance tests

This directory contains tests for the planned ARM64 / Python 3.10 runtime additions:

- XGBoost: train a small CPU model, predict, save to JSON, and reload it.
- GDAL Python bindings: create and write a GeoTIFF with EPSG:4326 metadata.
- Rasterio: read the GDAL-created file, create a second GeoTIFF, and verify GDAL can read it.

## Run on the Android device

After the native wheels are integrated into the standard PyIDE flavor, open
`native_stack_smoke_test.py` in the PyIDE editor and run it. All checks execute
in one Python process, which also catches native-library loading conflicts.

A successful run ends with:

```text
ALL NATIVE STACK TESTS PASSED
```

These tests are acceptance criteria, not proof that the packages are already
bundled. The app's existing Gradle/Python configuration is intentionally not
changed until compatible Android ARM64 wheels and their native dependencies
have been built and verified.
