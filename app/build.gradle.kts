import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing: reads keystore.properties (git-ignored, repo root) or env vars
// BINAURAL_KEYSTORE_FILE / BINAURAL_KEYSTORE_PASSWORD / BINAURAL_KEY_ALIAS / BINAURAL_KEY_PASSWORD.
// Without either, release builds are left unsigned so debug/CI builds still work.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(propKey: String, envKey: String): String? =
    (keystoreProps.getProperty(propKey) ?: System.getenv(envKey))?.takeIf { it.isNotBlank() }
val releaseStoreFile = signingValue("storeFile", "BINAURAL_KEYSTORE_FILE")

android {
    namespace = "com.miferstlab.binauralbeats"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.miferstlab.binauralbeats"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "1.6.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = signingValue("storePassword", "BINAURAL_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "BINAURAL_KEY_ALIAS") ?: "binaural-upload"
                keyPassword = signingValue("keyPassword", "BINAURAL_KEY_PASSWORD")
                    ?: signingValue("storePassword", "BINAURAL_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (releaseStoreFile != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.android.billing)
    // Billing pulls an ancient androidx.fragment:1.1.0 transitively; ActivityResult APIs need >= 1.3.
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    testImplementation(libs.junit)
}
