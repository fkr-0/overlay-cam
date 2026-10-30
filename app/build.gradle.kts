import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
}

// Release signing inputs come from ANDROID_KEYSTORE_* env vars (CI) or a git-ignored
// keystore.properties (local). Without them the release variant is debug-signed and
// unofficial; -PrequireReleaseSigning=true turns that fallback into a build failure.
val keystoreProperties = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.isFile }?.inputStream()?.use(::load)
}

fun signingInput(env: String, property: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() }
        ?: keystoreProperties.getProperty(property)?.takeIf { it.isNotBlank() }

val releaseSigningInputs = mapOf(
    "storeFile" to signingInput("ANDROID_KEYSTORE_FILE", "storeFile"),
    "storePassword" to signingInput("ANDROID_KEYSTORE_PASSWORD", "storePassword"),
    "keyAlias" to signingInput("ANDROID_KEY_ALIAS", "keyAlias"),
    "keyPassword" to signingInput("ANDROID_KEY_PASSWORD", "keyPassword"),
)
val missingSigningInputs = releaseSigningInputs.filterValues { it == null }.keys
val releaseSigningConfigured = missingSigningInputs.isEmpty()
val requireReleaseSigning = providers.gradleProperty("requireReleaseSigning").orNull == "true"

if (requireReleaseSigning && !releaseSigningConfigured) {
    throw GradleException("Release signing is required but missing: ${missingSigningInputs.sorted().joinToString()}")
}
if (releaseSigningConfigured && !file(releaseSigningInputs.getValue("storeFile")!!).isFile) {
    throw GradleException("Release keystore not found: ${releaseSigningInputs.getValue("storeFile")}")
}
if (!releaseSigningConfigured && missingSigningInputs.size < releaseSigningInputs.size) {
    throw GradleException("Incomplete release signing configuration; missing: ${missingSigningInputs.sorted().joinToString()}")
}

android {
    namespace = "com.example.overlaycam"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.fkr.overlaycam"
        minSdk = 24
        targetSdk = 34
        versionCode = 6
        versionName = "0.4.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(releaseSigningInputs.getValue("storeFile")!!)
                storePassword = releaseSigningInputs.getValue("storePassword")
                keyAlias = releaseSigningInputs.getValue("keyAlias")
                keyPassword = releaseSigningInputs.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (releaseSigningConfigured) "release" else "debug")
        }
    }

    buildFeatures { compose = true }
    composeOptions {
        // matches Kotlin 1.9.x
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += setOf("META-INF/AL2.0","META-INF/LGPL2.1")
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.08.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3:1.3.0")

    // XML theme parent
    implementation("com.google.android.material:material:1.12.0")

    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation(kotlin("test"))
}
