plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// Redirect build directory out of OneDrive to prevent Windows file-lock & sync latency
val customBuildDir = System.getenv("GRADLE_BUILD_DIR") ?: (
    if (System.getProperty("user.name") == "TOOLS UBH") "C:/Users/TOOLS UBH/AppData/Local/Temp/gradle_build" else null
)

if (customBuildDir != null) {
    allprojects {
        layout.buildDirectory.set(file("$customBuildDir/AetherDNS/${project.name}"))
    }
}
