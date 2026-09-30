import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

// Current released version. The GitHub release workflow builds with -P overrides
// and then commits the shipped values back into these two lines, so this file is
// always the record of what is live. Keep each value on its own single line.
val currentVersionName = "1.2"
val currentVersionCode = 3

android {
    namespace = "com.anish.momentum"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.anish.momentum"
        minSdk = 24
        targetSdk = 36
        versionCode = (project.findProperty("momentumVersionCode") as String?)?.toIntOrNull()
            ?: currentVersionCode
        versionName = project.findProperty("momentumVersionName") as String? ?: currentVersionName

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
    lint {
        // New warnings fail the build; everything already in the repo is
        // recorded in lint-baseline.xml so only fresh issues surface.
        abortOnError = true
        checkReleaseBuilds = true
        baseline = file("lint-baseline.xml")
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // OkHttp ships a 37 KB public-suffix (cookie domain) database. It is only
        // reachable from Cookie.parse() and HttpUrl.topPrivateDomain(), and this
        // app sets no CookieJar and calls neither, so it is dead weight. R8 cannot
        // see it — it is a resource, not code.
        resources.excludes += "okhttp3/internal/publicsuffix/*"
    }
    androidResources {
        // Everything raster in this project is already WebP; crunching would
        // only cost build time and re-encode quality.
        noCompress += "webp"
        // Every string this app declares is English, but AppCompat and Material
        // ship translations for ~85 locales, which the resource shrinker keeps
        // because the referenced resources survive. Filtering happens before
        // shrinking, so this is the one place they can be removed.
        // (resConfigs is deprecated in favour of this.)
        localeFilters += "en"
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

