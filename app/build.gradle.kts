plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.alanmacdougall.mamejibiki"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.alanmacdougall.mamejibiki"
        minSdk = 33
        targetSdk = 37
        versionCode = 2
        versionName = "0.2"
    }

    // The release key and its passwords live outside the repo, in ~/.gradle/gradle.properties.
    // Without them, assembleRelease still builds, but the APK is unsigned.
    val storeFilePath = providers.gradleProperty("mamejibikiStoreFile").orNull
    signingConfigs {
        if (storeFilePath != null) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = providers.gradleProperty("mamejibikiStorePassword").get()
                keyAlias = providers.gradleProperty("mamejibikiKeyAlias").get()
                keyPassword = providers.gradleProperty("mamejibikiKeyPassword").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    lint {
        // The AnkiDroid library ships lint rules meant for AnkiDroid's own codebase.
        disable += "DirectSystemCurrentTimeMillisUsage"
    }
}

kotlin {
    jvmToolchain(21)
}

// Unit tests read the shared normalization vectors from the repo root.
tasks.withType<Test>().configureEach {
    systemProperty("sharedDir", rootProject.file("shared").absolutePath)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ankidroid.api)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
}
