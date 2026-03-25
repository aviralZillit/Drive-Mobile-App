plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kapt)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.zillit.drive"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zillit.drive"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Encryption keys injected via build config (stored in local.properties or CI secrets)
        buildConfigField("String", "ENCRYPTION_KEY", "\"${project.findProperty("ENCRYPTION_KEY") ?: "Yz2eI81ZLzCxJwf7BjTsMjyx-_PH5op="}\"")
        buildConfigField("String", "ENCRYPTION_IV", "\"${project.findProperty("ENCRYPTION_IV") ?: "Brxd-7fAiRQFYz2e"}\"")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            buildConfigField("String", "DRIVE_BASE_URL", "\"${project.findProperty("DRIVE_BASE_URL_DEV") ?: "https://driveapi-dev.zillit.com/api"}\"")
            buildConfigField("String", "SOCKET_URL", "\"${project.findProperty("SOCKET_URL_DEV") ?: "https://driveapi-dev.zillit.com"}\"")
            buildConfigField("String", "ENVIRONMENT", "\"development\"")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "DRIVE_BASE_URL", "\"${project.findProperty("DRIVE_BASE_URL_PROD") ?: ""}\"")
            buildConfigField("String", "SOCKET_URL", "\"${project.findProperty("SOCKET_URL_PROD") ?: ""}\"")
            buildConfigField("String", "ENVIRONMENT", "\"production\"")
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
        viewBinding = true
        buildConfig = true
        dataBinding = false
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.swiperefresh)
    implementation(libs.androidx.recyclerview)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.livedata)

    // Navigation
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    // Hilt DI
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.codegen)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Paging
    implementation(libs.paging.runtime)

    // Coil (image loading)
    implementation(libs.coil)

    // Media3 (video/audio)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    // WorkManager (background uploads)
    implementation(libs.workmanager)

    // DataStore
    implementation(libs.datastore)

    // Socket.IO
    implementation(libs.socketio) {
        exclude(group = "org.json", module = "json")
    }

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
}
