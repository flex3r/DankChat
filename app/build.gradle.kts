@file:Suppress("UnstableApiUsage")

import com.android.build.gradle.internal.PropertiesValueSource
import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.StringReader
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.nav.safeargs.kotlin)
    alias(libs.plugins.ksp)
    alias(libs.plugins.about.libraries)
}

android {
    namespace = "com.flxrs.dankchat"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.flxrs.dankchat"
        minSdk = 21
        targetSdk = 34
        versionCode = 31108
        versionName = "3.11.8"
    }

    androidResources {
        generateLocaleConfig = true
    }

    val localProperties = gradleLocalProperties(rootDir, providers)
    signingConfigs {
        create("release") {
            storeFile = file("keystore/DankChat.jks").takeIf { it.exists() } ?: File(System.getProperty("user.home") + "/dankchat/DankChat.jks")
            storePassword = localProperties.getProperty("SIGNING_STORE_PASSWORD") ?: System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias = localProperties.getProperty("SIGNING_KEY_ALIAS") ?: System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = localProperties.getProperty("SIGNING_KEY_PASSWORD") ?: System.getenv("SIGNING_KEY_PASSWORD")
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDir("src/main/kotlin")
        }
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/versions/9/previous-compilation-data.bin"
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
            isDefault = true
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

    //noinspection WrongGradleMethod
    androidComponents {
        beforeVariants {
            sourceSets.named("main") {
                java.srcDir(File("build/generated/ksp/${it.name}/kotlin"))
            }
        }
    }
}

ksp {
    arg("room.schemaLocation", "${layout.projectDirectory}/schemas")
    arg("KOIN_CONFIG_CHECK", "true")
    arg("KOIN_DEFAULT_MODULE", "false")
    arg("KOIN_USE_COMPOSE_VIEWMODEL", "true")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(jdkVersion = 17)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=kotlin.uuid.ExperimentalUuidApi",
            "-opt-in=kotlin.time.ExperimentalTime",
            "-Xnon-local-break-continue",
            "-Xwhen-guards",
        )
    }
}

dependencies {
// D8 desugaring
    coreLibraryDesugaring(libs.android.desugar.libs)

// Kotlin
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.json.okio)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.immutable.collections)

// AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.emoji2)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.transition.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.media)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.datastore.core)
    implementation(libs.androidx.datastore.android)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

// Compose
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    "dankImplementation"(libs.compose.ui.tooling)
    implementation(libs.compose.icons.core)
    implementation(libs.compose.icons.extended)
    implementation(libs.compose.unstyled)

// Material
    implementation(libs.android.material)
    implementation(libs.android.flexbox)

// Dependency injection
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.koin.annotations)
    implementation(libs.koin.ksp.compiler)
    ksp(libs.koin.ksp.compiler)

// Image loading
    implementation(libs.coil)
    implementation(libs.coil.gif)
    implementation(libs.coil.ktor)
    implementation(libs.coil.cache.control)
    implementation(libs.coil.compose)

// HTTP clients
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

// Other
    implementation(libs.colorpicker.android)
    implementation(libs.process.phoenix)
    implementation(libs.autolinktext)
    implementation(libs.aboutlibraries.compose.m3)

// Test
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlin.test)
}

fun gradleLocalProperties(projectRootDir: File, providers: ProviderFactory): Properties {
    val properties = Properties()
    val propertiesContent =
        providers.of(PropertiesValueSource::class.java) {
            parameters.projectRoot.set(projectRootDir)
        }.get()

    StringReader(propertiesContent).use { reader ->
        properties.load(reader)
    }

    return properties
}
