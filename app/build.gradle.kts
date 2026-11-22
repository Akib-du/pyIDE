plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

android {
    namespace = "com.example.pyide"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.pyide"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "2.0"
        // arm64 only keeps the APK smaller. Add "x86_64" if you test on an Intel/AMD emulator.
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    flavorDimensions += "libs"
    productFlavors {
        create("standard") { dimension = "libs" }                                  // Python 3.11
        create("dl") { dimension = "libs"; applicationIdSuffix = ".dl" }           // Python 3.8 + torch (experimental)
    }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

chaquopy {
    defaultConfig { }
    productFlavors {
        getByName("standard") {
            version = "3.11"
            pip {
                install("jedi==0.19.1")
                install("pyflakes==3.2.0")
                install("numpy")
                install("pandas")
                install("scipy")
                install("matplotlib")
                install("scikit-learn")
                install("statsmodels")
                install("pillow")
                install("seaborn==0.13.2")
            }
        }
        getByName("dl") {
            // EXPERIMENTAL: Chaquopy's torch / tflite-runtime wheels are old, so this flavor uses
            // an older Python. Check https://chaquo.com/pypi-13.1/torch/ for what exists, and
            // delete any line below whose wheel is missing, otherwise the build will fail.
            version = "3.8"
            pip {
                install("jedi==0.19.1")
                install("pyflakes==3.2.0")
                install("numpy")
                install("pandas")
                install("scipy")
                install("matplotlib")
                install("scikit-learn")
                install("pillow")
                install("seaborn==0.11.2")
                install("torch")
                install("tflite-runtime")
            }
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
}
