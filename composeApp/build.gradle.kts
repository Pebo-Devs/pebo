import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// Single source of truth for the app version. Override at build time with -PappVersion=1.2.3
// (the release workflow passes the git tag); falls back to the baseline below for local dev.
val appVersion: String = (findProperty("appVersion") as String?)
    ?.removePrefix("v")
    ?.trim()
    ?.takeIf { Regex("""\d+(\.\d+){0,2}""").matches(it) }
    ?: "1.0.0"

// Bake the version + GitHub coordinates into a generated commonMain source so the in-app updater
// (Settings -> About) knows what it is running and which repo's releases to check.
val generatePeboBuildConfig by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/peboBuildConfig/commonMain/kotlin")
    val versionValue = appVersion
    inputs.property("version", versionValue)
    outputs.dir(outputDir)
    doLast {
        val pkgDir = outputDir.get().asFile.resolve("app/pebo")
        pkgDir.mkdirs()
        pkgDir.resolve("PeboBuildConfig.kt").writeText(
            """
            package app.pebo

            internal object PeboBuildConfig {
                const val VERSION: String = "$versionValue"
                const val GITHUB_OWNER: String = "Pebo-Devs"
                const val GITHUB_REPO: String = "pebo"
            }
            """.trimIndent() + "\n"
        )
    }
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
        versionName = appVersion
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

// Forward OAuth public client ids to the desktop run task without committing them to source.
// Usage: ./gradlew :composeApp:run -PpeboOnedriveClientId=<id> [-PpeboGoogleClientId=<id> -PpeboGoogleClientSecret=<secret>]
// Google's installed-app flow additionally requires a client secret (not confidential for desktop apps).
tasks.withType<JavaExec>().configureEach {
    mapOf(
        "peboOnedriveClientId" to "pebo.onedrive.clientId",
        "peboGoogleClientId" to "pebo.google.clientId",
        "peboGoogleClientSecret" to "pebo.google.clientSecret",
    ).forEach { (gradleProp, systemProp) ->
        (findProperty(gradleProp) as String?)?.takeIf { it.isNotBlank() }?.let {
            systemProperty(systemProp, it)
        }
    }
}

// Wire the generated PeboBuildConfig into commonMain so every target compiles it.
kotlin.sourceSets.named("commonMain") {
    kotlin.srcDir(generatePeboBuildConfig)
}

