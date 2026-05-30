import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.firebase.appdistribution")
}

// Release signing is read from keystore.properties (gitignored). When the file is
// absent (other devs / CI), release falls back to the debug key so the build still works.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKeystore = keystoreProps.getProperty("storeFile") != null

android {
    namespace = "cl.aiep.subtitulos"
    compileSdk = 35

    defaultConfig {
        applicationId = "cl.aiep.subtitulos"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "0.2.0"
    }

    signingConfigs {
        getByName("debug") {
            enableV1Signing = true
            enableV2Signing = true
        }
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName(
                if (hasReleaseKeystore) "release" else "debug",
            )
            isMinifyEnabled = false
            isDebuggable = false

            firebaseAppDistribution {
                appId = "1:37258064848:android:9662f2f53c89174249bfd5"
                groups = "profes"
                releaseNotesFile = "$rootDir/release-notes.txt"
            }
        }
    }

    lint {
        // Instantiatable: the android.print shim is intentional (see CLAUDE.md).
        // InvalidFragmentVersionForActivityResult: false positive — MainActivity is a
        // ComponentActivity using activity-compose, not a Fragment.
        disable += setOf("Instantiatable", "InvalidFragmentVersionForActivityResult")
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")

    implementation(composeBom)
    implementation("androidx.activity:activity:1.9.3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

    debugImplementation(composeBom)
    debugImplementation("androidx.compose.ui:ui-tooling")
}
