# PyIDE 2.0 – mini PyCharm for Android (Kotlin + Compose + Chaquopy)

## Build
1. Install Android Studio and Python 3.11 AND 3.8 if you want the `dl` flavor (Chaquopy needs a matching
   Python on the build machine; set `buildPython(...)` in app/build.gradle.kts if it can't find them).
2. Open this folder, let Gradle sync, pick build variant **standardDebug** (Build Variants panel), run on an arm64 device.
   (Add "x86_64" to abiFilters for an Intel emulator.)

## Variants
- standard (Python 3.11): numpy, pandas, scipy, matplotlib, seaborn, scikit-learn, statsmodels, pillow
- dl (Python 3.8, EXPERIMENTAL): same stack plus torch and tflite-runtime. Chaquopy's torch / tensorflow
  wheels are old, so verify at https://chaquo.com/pypi-13.1/torch/ and remove any line that fails.

## Features
- input() / sys.stdin: a text box appears under the console when your script asks for input
- Run / Stop (Stop interrupts the script, including a pending input())
- plt.show() (matplotlib, seaborn, pandas .plot()) renders into the Plots tab
- Autocomplete (Jedi), Problems list (pyflakes), dark/light theme, runtime pip for pure-Python packages
- Samples: input_demo.py, plot_demo.py, ml_demo.py

## Limits
- APK is large (150-300 MB) because of numpy/scipy/pandas; first import of each library is slow.
- Stop can't interrupt a long single native call (e.g. a huge numpy op) until it returns.
- `for line in sys.stdin` never reaches EOF; press Stop to end it.
- Full TensorFlow / latest PyTorch are not available for Python 3.11 on Chaquopy.
