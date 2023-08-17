@file:Suppress("UnstableApiUsage")

import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.gradle)
    alias(libs.plugins.nav.safeargs.kotlin)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.flxrs.dankchat"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.flxrs.dankchat"
        minSdk = 21
        targetSdk = 34
        versionCode = 30704
        versionName = "3.7.4"

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("keystore/DankChat.jks").takeIf { it.exists() } ?: File(System.getProperty("user.home") + "/dankchat/DankChat.jks")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDir("src/main/kotlin")
        }
    }
    buildFeatures {
        dataBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            manifestPlaceholders["applicationLabel"] = "@string/app_name"
            signingConfig = signingConfigs.getByName("release")
        }
        getByName("debug") {
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            manifestPlaceholders["applicationLabel"] = "@string/app_name"
        }
        create("dank") {
            initWith(getByName("debug"))
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            manifestPlaceholders["applicationLabel"] = "@string/app_name_dank"
            applicationIdSuffix = ".dank"
        }
    }

    buildOutputs.all {
        (this as? BaseVariantOutputImpl)?.apply {
            val appName = "DankChat-${name}.apk"
            outputFileName = appName
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
        )
    }
}

kotlin {
    jvmToolchain(jdkVersion = 17)
}

dependencies {
// D8 desugaring
    coreLibraryDesugaring(libs.android.desugar.libs)

// Kotlin
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

// AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.emoji2)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.media)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

// Material
    implementation(libs.android.material)
    implementation(libs.android.flexbox)

// Dependency injection
    implementation(libs.hilt.android)
    kapt(libs.hilt.android.compiler)

// Image loading
    implementation(libs.coil)
    implementation(libs.coil.gif)

// HTTP clients
    implementation(libs.okhttp)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

// Other
    implementation(libs.colorpicker.android)

// Test
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlin.test)
}
