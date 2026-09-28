// Android-only build configuration. Applied from build.gradle.kts when the Android target is
// enabled, so that desktop builds don't need the Android Gradle Plugin on the classpath.
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.ManagedVirtualDevice

configure<ApplicationExtension> {
    namespace = "de.teufel.openmynd"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        targetSdk = 34

        applicationId = "de.teufel.openmynd.androidApp"
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    sourceSets["main"].apply {
        manifest.srcFile("src/androidMain/AndroidManifest.xml")
        res.srcDirs("src/androidMain/res")
    }
    //https://developer.android.com/studio/test/gradle-managed-devices
    @Suppress("UnstableApiUsage")
    testOptions {
        managedDevices.allDevices {
            maybeCreate("pixel5", ManagedVirtualDevice::class.java).apply {
                device = "Pixel 5"
                apiLevel = 34
                systemImageSource = "aosp"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}
