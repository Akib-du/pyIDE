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
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    flavorDimensions += "libs"
    productFlavors {
        create("standard") { dimension = "libs" }
        create("dl") { dimension = "libs"; applicationIdSuffix = ".dl" }
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
            version = "3.13"
            pip {
                install("jedi")
                install("pyflakes")
                install("numpy")
                install("pandas")
                install("scipy")
                install("matplotlib")
                install("scikit-learn")
                install("pillow")
                install("seaborn")
            }
        }
        getByName("dl") {
            version = "3.13"
            pip {
                install("jedi")
                install("pyflakes")
                install("numpy")
                install("pandas")
                install("scipy")
                install("matplotlib")
                install("scikit-learn")
                install("pillow")
                install("seaborn")
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