@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.application") version Versions.agpVersion
    kotlin("android") version Versions.kotlinVersion
    kotlin("kapt") version Versions.kotlinVersion
    kotlin("plugin.parcelize") version Versions.kotlinVersion
    kotlin("plugin.serialization") version Versions.kotlinVersion
    id("com.google.dagger.hilt.android") version Versions.hiltVersion
    id("androidx.navigation.safeargs.kotlin") version Versions.navVersion
}

android {
    namespace = "com.flxrs.dankchat"
    compileSdk = 33
    buildToolsVersion = "33.0.0"

    defaultConfig {
        applicationId = "com.flxrs.dankchat"
        minSdk = 21
        targetSdk = 33
        versionCode = 30500
        versionName = "3.5.0"

        kapt {
            arguments {
                arg("room.schemaLocation", "$projectDir/schemas")
            }
        }
    }

    signingConfigs {
        create("release") {
            val tmpFilePath = System.getProperty("user.home") + "/dankchat/"
            val allFilesFromDir = File(tmpFilePath).listFiles()

            if (allFilesFromDir != null) {
                val keystoreFile = allFilesFromDir.first()
                keystoreFile.renameTo(File("keystore/DankChat.jks"))
            }

            storeFile = file("keystore/DankChat.jks")
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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            manifestPlaceholders["applicationLabel"] = "@string/app_name"
        }
        create("dank") {
            initWith(getByName("debug"))
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            manifestPlaceholders["applicationLabel"] = "@string/app_name_dank"
            applicationIdSuffix = ".dank"
        }
    }

    buildOutputs.all {
        val variantOutputImpl = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
        val variantName: String = variantOutputImpl.name
        val outputFileName = "DankChat-${variantName}.apk"
        variantOutputImpl.outputFileName = outputFileName
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
        )
    }
}

dependencies {
// D8 desugaring
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.0")

// Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${Versions.kotlinVersion}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutinesVersion}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.coroutinesVersion}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.serializationVersion}")

// AndroidX
    implementation("androidx.activity:activity-ktx:${Versions.activityVersion}")
    implementation("androidx.browser:browser:${Versions.browserVersion}")
    implementation("androidx.constraintlayout:constraintlayout:${Versions.constraintVersion}")
    implementation("androidx.core:core-ktx:${Versions.coreVersion}")
    implementation("androidx.emoji2:emoji2:${Versions.emojiVersion}")
    implementation("androidx.exifinterface:exifinterface:${Versions.exifVersion}")
    implementation("androidx.fragment:fragment-ktx:${Versions.fragmentVersion}")
    implementation("androidx.lifecycle:lifecycle-common-java8:${Versions.lifecycleVersion}")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:${Versions.lifecycleVersion}")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:${Versions.lifecycleVersion}")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:${Versions.lifecycleVersion}")
    implementation("androidx.media:media:${Versions.mediaVersion}")
    implementation("androidx.navigation:navigation-fragment-ktx:${Versions.navVersion}")
    implementation("androidx.navigation:navigation-ui-ktx:${Versions.navVersion}")
    implementation("androidx.preference:preference-ktx:${Versions.preferenceVersion}")
    implementation("androidx.recyclerview:recyclerview:${Versions.recyclerviewVersion}")
    implementation("androidx.viewpager2:viewpager2:${Versions.viewpager2Version}")
    implementation("androidx.webkit:webkit:${Versions.webkitVersion}")
    implementation("androidx.room:room-runtime:${Versions.roomVersion}")
    implementation("androidx.room:room-ktx:${Versions.roomVersion}")
    kapt("androidx.room:room-compiler:${Versions.roomVersion}")

// Material
    implementation("com.google.android.material:material:${Versions.materialVersion}")

// Dependency injection
    implementation("com.google.dagger:hilt-android:${Versions.hiltVersion}")
    kapt("com.google.dagger:hilt-android-compiler:${Versions.hiltVersion}")

// Image loading
    implementation("io.coil-kt:coil:${Versions.coilVersion}")
    implementation("io.coil-kt:coil-gif:${Versions.coilVersion}")

// HTTP clients
    implementation("com.squareup.okhttp3:okhttp:${Versions.okhttpVersion}")
    implementation("com.squareup.okhttp3:logging-interceptor:${Versions.okhttpVersion}")
    implementation("io.ktor:ktor-client-core:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-client-okhttp:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-client-logging:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-client-content-negotiation:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-serialization-kotlinx-json:${Versions.ktorVersion}")

    // Test
    testImplementation("org.junit.jupiter:junit-jupiter-api:${Versions.junitVersion}")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:${Versions.junitVersion}")
    testImplementation("io.mockk:mockk:${Versions.mockkVersion}")
    testImplementation("org.jetbrains.kotlin:kotlin-test:${Versions.kotlinVersion}")
}
