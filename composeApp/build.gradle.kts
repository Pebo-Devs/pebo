import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm("desktop")

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.okio)
            implementation(libs.richeditor.compose)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.browser)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.core.splashscreen)
            implementation(libs.androidx.security.crypto)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.okhttp)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.cio)
            implementation(libs.jna.platform)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
        }
    }
}

android {
    namespace = "app.pebo"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.pebo"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
}

val appVersion: String = (findProperty("appVersion") as String?)
    ?.removePrefix("v")
    ?.trim()
    ?.takeIf { Regex("""\d+(\.\d+){0,2}""").matches(it) }
    ?: "1.0.0"

compose.desktop {
    application {
        mainClass = "app.pebo.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
            packageName = "Pebo"
            packageVersion = appVersion
            windows {
                iconFile.set(rootProject.file("branding/pebo-icon.ico"))
                menu = true
                menuGroup = "Pebo"
                shortcut = true
                dirChooser = true
                perUserInstall = false
                upgradeUuid = "21d745eb-b400-4903-8856-4f5eeacc3748"
            }
            macOS {
                iconFile.set(rootProject.file("branding/pebo-logo.icns"))
                bundleID = "app.pebo"
                dockName = "Pebo"
                appCategory = "public.app-category.productivity"
            }
            linux {
                iconFile.set(rootProject.file("branding/pebo-logo-512.png"))
            }
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "app.pebo.resources"
    generateResClass = always
}
