import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlinx.serialization)
}

val appVersion = "1.0.0"

// Android is opt-in (see settings.gradle.kts); iOS can only be built on macOS hosts.
val androidEnabled = gradle.extra["openmynd.android"] as Boolean
val iosEnabled = System.getProperty("os.name").startsWith("Mac")

if (androidEnabled) {
    apply(plugin = "com.android.application")
    apply(from = "android.gradle.kts")
}

kotlin {
    if (androidEnabled) {
        androidTarget {
            //https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-test.html
            @OptIn(ExperimentalKotlinGradlePluginApi::class)
            instrumentedTestVariant {
                sourceSetTree.set(KotlinSourceSetTree.test)
                // String configuration names: AGP is applied imperatively, so its
                // configuration accessors aren't generated for this script.
                dependencies {
                    add("debugImplementation", libs.androidx.testManifest)
                    add("implementation", libs.androidx.junit4)
                }
            }
        }
    }

    if (iosEnabled) {
        listOf(
            iosX64(),
            iosArm64(),
            iosSimulatorArm64()
        ).forEach {
            it.binaries.framework {
                baseName = "ComposeApp"
                isStatic = true
            }
        }
    }

    // Linux desktop client
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // `mobileMain` holds code shared by Android and iOS only (BlueFalcon/Kable BLE
    // backends, runtime permissions), which has no JVM/Linux implementation.
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("mobile") {
                withAndroidTarget()
                group("ios")
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(libs.icons)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.voyager.navigation)
            implementation(libs.voyager.navigation.tabs)
            implementation(libs.lottie)
            implementation(libs.coil)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.serialization)
            implementation(libs.kermit)
            implementation(libs.buffer)
            implementation(libs.uuid)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlin.reflect)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            @OptIn(ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
            implementation(libs.kotlinx.coroutines.test)
        }

        if (androidEnabled || iosEnabled) {
            maybeCreate("mobileMain").dependencies {
                implementation(libs.blue.falcon)
                implementation(libs.kable.core)
                implementation(libs.permissions)
                implementation(libs.permissions.compose)
                implementation(libs.permissions.bluetooth)
                implementation(libs.permissions.location)
            }
        }

        if (androidEnabled) {
            androidMain.dependencies {
                implementation(compose.uiTooling)
                implementation(libs.androidx.activityCompose)
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.koin.android)
            }
        }

        if (iosEnabled) {
            iosMain.dependencies {
                implementation(libs.stately.concurrent.collections)
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.dbus.java.core)
                implementation(libs.dbus.java.transport.native.unixsocket)
                runtimeOnly(libs.slf4j.simple)
            }
        }
    }
}

tasks.named<Test>("desktopTest") {
    // dbus-java requires a D-Bus machine ID even for the tests' private bus. Containers and
    // clean build chroots (e.g. makepkg in a devtools chroot) often have no /etc/machine-id,
    // so point dbus-java at a fixed one.
    environment("DBUS_MACHINE_ID_LOCATION", file("src/desktopTest/dbus-machine-id").absolutePath)
    testLogging {
        exceptionFormat = TestExceptionFormat.FULL
    }
}

compose.desktop {
    application {
        mainClass = "de.teufel.openmynd.MainKt"
        // Lets main() set the X11 WM_CLASS so KDE/GNOME match the window to its .desktop file.
        jvmArgs += listOf("--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED")

        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "openmynd"
            packageVersion = appVersion
            description = "Bluetooth LE controller for the Teufel MYND speaker"
            vendor = "OpenMynd contributors"
            licenseFile.set(rootProject.file("LICENSE"))
            includeAllModules = true

            linux {
                iconFile.set(rootProject.file("packaging/linux/icons/512x512/openmynd.png"))
                menuGroup = "AudioVideo;Audio"
                appCategory = "sound"
                rpmLicenseType = "MIT"
                debMaintainer = "openmynd@users.noreply.github.com"
                shortcut = true
            }
        }
    }
}
