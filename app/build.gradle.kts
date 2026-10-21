plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.security.aetherdns"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.security.aetherdns"
        minSdk = 28 // Android 9 (Pie) introduces native Private DNS (DoT)
        targetSdk = 35
        versionCode = 16
        versionName = "1.13.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Penandatanganan rilis dari environment (dipakai workflow .github/workflows/release.yml).
    // Tanpa variabel ini, assembleRelease tetap menghasilkan APK unsigned seperti sebelumnya.
    val releaseKeystore = System.getenv("AETHERDNS_KEYSTORE_PATH")
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("AETHERDNS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("AETHERDNS_KEY_ALIAS")
                keyPassword = System.getenv("AETHERDNS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            // Data BouncyCastle yang tidak dipakai (post-quantum, pesan CertPath) — menghemat ±1,5 MB
            excludes += setOf("org/bouncycastle/pqc/**", "org/bouncycastle/x509/*.properties", "org/bouncycastle/pkix/*.properties")
        }
    }

    testOptions {
        // Robolectric (screenshot UI opsional) butuh resource Android
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            // Opsional: mirror untuk unduhan android-all Robolectric bila Maven Central membatasi (429)
            System.getenv("ROBOLECTRIC_REPO_URL")?.let { test.systemProperty("robolectric.dependency.repo.url", it) }
        }
    }

    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.ext)
    implementation(libs.kotlinx.coroutines.android)

    // Shizuku API (Rootless Permission Granter via Wireless Debugging)
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // Aktivasi bawaan tanpa Shizuku: pairing + koneksi ke adbd perangkat sendiri via Wireless debugging (Android 11+).
    // libadb-android dipakai di bawah lisensi Apache-2.0 (dual GPL-3.0-or-later / Apache-2.0).
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
    // TLS 1.3 + exportKeyingMaterial untuk protokol pairing (API publik, tanpa hidden API platform)
    implementation("org.conscrypt:conscrypt-android:2.5.3")
    // Sertifikat X.509 self-signed untuk kunci ADB sekali pakai (versi sama dengan bcprov milik libadb)
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.81")

    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.kotlinx.coroutines.test)
    // Screenshot UI opsional (AETHERDNS_SCREENSHOTS=1), lihat ScreenshotTest
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.40.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.40.1")
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation(libs.androidx.compose.ui.tooling)
}
