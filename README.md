# PyIDE 2.0 – mini PyCharm for Android (Kotlin + Compose + Chaquopy)

## Python runtime
- CPython 3.13 on Android ARM64 (arm64-v8a).
- Chaquopy Gradle plugin 17.2.0.
- The build machine must have Python 3.13 available because Chaquopy buildPython major/minor must match the app runtime.

## Build
1. Install Android Studio and Python 3.13.
2. Open this folder, let Gradle sync, pick build variant standardDebug, and run on an ARM64 device.
3. GitHub Actions builds the ARM64 standard APK with Python 3.13.

## Variants
- standard: Python 3.13 with Jedi, pyflakes, NumPy, pandas, SciPy, Matplotlib, scikit-learn, Pillow and seaborn, subject to Android wheel availability.
- dl: Python 3.13 with the same scientific base. PyTorch and TensorFlow are not declared until compatible Android ARM64 CPython wheels are built and verified.

## Features
- input() / sys.stdin: a text box appears under the console when your script asks for input
- Run / Stop (Stop interrupts the script, including a pending input())
- plt.show() (matplotlib, seaborn, pandas .plot()) renders into the Plots tab
- Autocomplete (Jedi), Problems list (pyflakes), dark/light theme, runtime pip for pure-Python packages
- Samples: input_demo.py, plot_demo.py, ml_demo.py

## Limits
- Native scientific packages must have Android ARM64 wheels matching CPython 3.13.
- Full TensorFlow and PyTorch Python wheels are separate build projects; their inclusion is not claimed by this runtime upgrade.