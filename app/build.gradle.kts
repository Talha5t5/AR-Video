plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.arvideo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.arvideo"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.jks")
            storePassword = "password"
            keyAlias = "arvideo"
            keyPassword = "password"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    // Allow assets folder
    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets")
        }
    }

    configurations.all {
        resolutionStrategy {
            force("androidx.core:core:1.13.1")
            force("androidx.core:core-ktx:1.13.1")
            force("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
            force("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
            force("androidx.transition:transition:1.5.0")
            force("androidx.appcompat:appcompat:1.6.1")
            force("androidx.activity:activity-ktx:1.8.2")
            
            eachDependency {
                if (requested.group.startsWith("androidx.compose")) {
                    useVersion("1.7.0")
                }
                if (requested.group == "androidx.lifecycle" && !requested.name.contains("compose")) {
                    useVersion("2.8.4")
                }
            }
        }
    }
}

dependencies {
    // ── ARCore ─────────────────────────────────────────────────────────
    implementation("com.google.ar:core:1.44.0")

    // ── SceneView (Filament-based modern Sceneform replacement) ────────
    implementation("io.github.sceneview:arsceneview:2.3.3")

    // ── ExoPlayer / Media3 ─────────────────────────────────────────────
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-datasource:1.3.1")
    implementation("androidx.media3:media3-common:1.3.1")

    // ── Coroutines ─────────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // ── Lifecycle ──────────────────────────────────────────────────────
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.appcompat:appcompat:1.6.1")

    // ── Activity ───────────────────────────────────────────────────────
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.core:core-ktx:1.12.0")
}
