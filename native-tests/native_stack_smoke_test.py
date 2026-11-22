"""On-device acceptance test for PyIDE's native ML/geospatial stack.

Run this from the PyIDE editor after the XGBoost, GDAL Python bindings, and
Rasterio wheels have been bundled into the standard ARM64 flavor.
This deliberately tests all three packages in one Python process.
"""

import os
import tempfile
import traceback

import numpy as np


def test_xgboost():
    import xgboost as xgb

    X = np.array(
        [[0.0, 0.0], [0.0, 1.0], [1.0, 0.0], [1.0, 1.0],
         [0.2, 0.1], [0.9, 0.8], [0.1, 0.9], [0.8, 0.2]],
        dtype=np.float32,
    )
    y = np.array([0, 0, 1, 1, 0, 1, 0, 1], dtype=np.int32)

    model = xgb.XGBClassifier(
        n_estimators=8,
        max_depth=2,
        learning_rate=0.3,
        objective="binary:logistic",
        eval_metric="logloss",
        tree_method="hist",
        n_jobs=1,
        verbosity=0,
    )
    model.fit(X, y)
    predictions = model.predict(X)
    assert predictions.shape == y.shape
    assert set(np.unique(predictions)).issubset({0, 1})

    with tempfile.TemporaryDirectory(prefix="pyide_xgb_") as tmp:
        model_path = os.path.join(tmp, "smoke-model.json")
        model.save_model(model_path)
        restored = xgb.XGBClassifier()
        restored.load_model(model_path)
        restored_predictions = restored.predict(X)
        assert np.array_equal(predictions, restored_predictions)

    return "XGBoost %s: train / predict / JSON save-load OK" % xgb.__version__


def test_gdal_and_rasterio():
    from osgeo import gdal, osr
    import rasterio
    from rasterio.transform import from_origin

    values = np.arange(64, dtype=np.uint16).reshape((8, 8))
    transform = from_origin(90.0, 24.0, 0.01, 0.01)

    spatial_ref = osr.SpatialReference()
    spatial_ref.ImportFromEPSG(4326)
    projection = spatial_ref.ExportToWkt()

    with tempfile.TemporaryDirectory(prefix="pyide_geo_") as tmp:
        gdal_path = os.path.join(tmp, "created_by_gdal.tif")
        rasterio_path = os.path.join(tmp, "created_by_rasterio.tif")

        driver = gdal.GetDriverByName("GTiff")
        dataset = driver.Create(gdal_path, 8, 8, 1, gdal.GDT_UInt16)
        assert dataset is not None, "GDAL GTiff driver unavailable"
        dataset.SetGeoTransform((90.0, 0.01, 0.0, 24.0, 0.0, -0.01))
        dataset.SetProjection(projection)
        dataset.GetRasterBand(1).WriteArray(values)
        dataset.FlushCache()
        dataset = None

        # Rasterio must read a GeoTIFF created by GDAL.
        with rasterio.open(gdal_path) as src:
            assert src.width == 8 and src.height == 8
            assert src.count == 1
            assert src.crs.to_epsg() == 4326
            assert np.array_equal(src.read(1), values)

        # GDAL must read a GeoTIFF created by Rasterio.
        profile = {
            "driver": "GTiff",
            "height": 8,
            "width": 8,
            "count": 1,
            "dtype": "uint16",
            "crs": "EPSG:4326",
            "transform": transform,
        }
        with rasterio.open(rasterio_path, "w", **profile) as dst:
            dst.write(values, 1)

        check = gdal.Open(rasterio_path, gdal.GA_ReadOnly)
        assert check is not None, "GDAL could not open Rasterio output"
        assert check.RasterXSize == 8 and check.RasterYSize == 8
        assert np.array_equal(check.GetRasterBand(1).ReadAsArray(), values)
        check = None

    return "GDAL %s + Rasterio %s: cross-read/write GeoTIFF and EPSG:4326 OK" % (
        gdal.VersionInfo("--version"),
        rasterio.__version__,
    )


def main():
    checks = [
        ("XGBoost", test_xgboost),
        ("GDAL + Rasterio", test_gdal_and_rasterio),
    ]
    failures = []
    print("PyIDE native stack acceptance test")
    print("NumPy", np.__version__)

    for label, check in checks:
        try:
            print("[PASS]", check())
        except Exception:
            failures.append(label)
            print("[FAIL]", label)
            traceback.print_exc()

    if failures:
        raise RuntimeError("Native stack checks failed: " + ", ".join(failures))

    print("ALL NATIVE STACK TESTS PASSED")


if __name__ == "__main__":
    main()
