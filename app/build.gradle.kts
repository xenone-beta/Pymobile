plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Injected by CI (see .github/workflows); local builds use the defaults.
val appVersionName: String = System.getenv("APP_VERSION_NAME") ?: "1.0.0"
val appVersionCode: Int = System.getenv("APP_VERSION_CODE")?.toIntOrNull() ?: 1
val releaseKeystorePath: String? = System.getenv("PYMOBILE_KEYSTORE_FILE")

// Must match tools/fetch-python.sh. The official Android build of CPython comes from python.org.
val pythonVersion: String = System.getenv("PYTHON_VERSION") ?: "3.14.10"
val pythonMinor: String = pythonVersion.split(".").take(2).joinToString(".")

android {
    namespace = "dev.xenolab.pymobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.xenolab.pymobile"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        buildConfigField("String", "PYTHON_VERSION", "\"$pythonVersion\"")
        buildConfigField("String", "PYTHON_MINOR", "\"$pythonMinor\"")

        // CPython for Android is only published for arm64 and x86_64. Phones are arm64.
        ndk { abiFilters += "arm64-v8a" }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("PYMOBILE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PYMOBILE_KEY_ALIAS")
                keyPassword = System.getenv("PYMOBILE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = false
        checkDependencies = false
    }

    androidResources {
        // The Python standard library and pip are read with openFd(), which needs uncompressed assets.
        noCompress += listOf("zip", "whl")
    }

    packaging {
        // The Python libraries must exist as real files: CPython opens its extension modules by name.
        jniLibs { useLegacyPackaging = true }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += listOf(
                "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST", "META-INF/LICENSE*", "META-INF/NOTICE*", "plugin.properties",
                "about.html", "OSGI-INF/**",
            )
        }
    }
}

// Fail early, with a useful message, if the Python payload was not downloaded.
val checkPythonPayload by tasks.registering {
    val libs = file("src/main/jniLibs/arm64-v8a/libpython$pythonMinor.so")
    val stdlib = file("src/main/assets/pylib.zip")
    val header = file("src/main/cpp/python/include/python$pythonMinor/Python.h")
    doLast {
        val missing = listOf(libs, stdlib, header).filter { !it.exists() }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Python payload is missing: " + missing.joinToString { it.relativeTo(projectDir).path } +
                    ". Run tools/fetch-python.sh from the repository root first.",
            )
        }
    }
}
tasks.named("preBuild") { dependsOn(checkPythonPayload) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.jgit)
    implementation(libs.slf4j.nop)

    testImplementation(libs.junit)
    // The Android SDK ships only a stub of org.json for local unit tests; use the real one there.
    testImplementation(libs.json)
}
