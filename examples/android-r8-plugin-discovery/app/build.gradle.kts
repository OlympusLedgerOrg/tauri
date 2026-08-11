// Copyright 2019-2024 Tauri Programme within The Commons Conservancy
// SPDX-License-Identifier: Apache-2.0
// SPDX-License-Identifier: MIT

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // Deliberately outside the app.tauri.* namespace: consumer-rules.pro's
    // first rule (`-keep class app.tauri.** { ... }`) would otherwise
    // partially protect this module's own classes and muddy what the test is
    // actually proving. See README.md.
    namespace = "com.example.r8plugindiscovery"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.r8plugindiscovery"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // This module is never distributed -- it only exists to exercise R8
            // output. Signing "release" with the debug key lets `connectedCheck`
            // install this build type on a device/emulator without a real
            // signing config.
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("debug") {
            isMinifyEnabled = false
        }
    }

    // The androidTest below must run against the minified build to mean
    // anything: it is the regression test for
    // crates/tauri/mobile/android/consumer-rules.pro (see app/README.md).
    testBuildType = "release"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}

dependencies {
    // The real library, not a copy or a stub -- see ../settings.gradle.kts.
    implementation(project(":tauri-android"))

    implementation("androidx.appcompat:appcompat:1.7.1")

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("com.fasterxml.jackson.core:jackson-databind:2.15.3")
}
