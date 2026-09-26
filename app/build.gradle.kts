import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.anish.momentum"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.anish.momentum"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // Secrets live in keystore/signing.properties, which is gitignored.
            // gradle.properties is deliberately not used: it is a file that
            // belongs in the repo, and a password there would be committed.
            val signing = rootProject.file("keystore/signing.properties")
                .takeIf { it.exists() }
                ?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }

            storeFile = rootProject.file("keystore/momentum-release.jks")
            storePassword = signing?.getProperty("MOMENTUM_STORE_PASSWORD")
            keyAlias = signing?.getProperty("MOMENTUM_KEY_ALIAS")
            keyPassword = signing?.getProperty("MOMENTUM_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            // Only sign when the keystore and its passwords are actually present,
            // so a fresh clone can still produce an unsigned release build.
            signingConfig = signingConfigs.takeIf {
                rootProject.file("keystore/momentum-release.jks").exists()
            }?.getByName("release")

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
    kotlin {
        jvmToolchain(17)
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    androidResources {
        // Everything raster in this project is already WebP; crunching would
        // only cost build time and re-encode quality.
        noCompress += "webp"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation("com.airbnb.android:lottie:6.6.7")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
}

