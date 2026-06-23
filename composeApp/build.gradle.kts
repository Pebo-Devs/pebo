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
    ?: "1.1.0"

// Public OAuth client ids for Pebo's official cloud apps. These are NOT secrets — a client id is a
// public identifier that appears in the consent URL — so they are safe to commit and ship baked into
// the app, which is what lets OneDrive/Google "just work" without each user registering their own app.
val oneDriveClientId = "45447072-b316-420c-914f-23918cca5802"
val googleClientId = "940923472649-usq5q3ntvlqqmpdrqg5qlrh1lb0hpvmc.apps.googleusercontent.com"

// Google's installed-app token exchange additionally needs a client secret. For desktop OAuth clients
// Google does not treat it as confidential, but we still NEVER commit it to this public repo (GitHub
// push-protection would block it and Google auto-revokes leaked secrets). Instead official release
// builds inject it from a CI secret: -PpeboGoogleClientSecret=... or env PEBO_GOOGLE_CLIENT_SECRET.
// When absent (normal local/dev builds) it stays empty and Google simply shows as needing setup.
val googleClientSecret: String =
    ((findProperty("peboGoogleClientSecret") as String?) ?: System.getenv("PEBO_GOOGLE_CLIENT_SECRET"))
        ?.trim().orEmpty()

// Bake the version + GitHub coordinates + public cloud client ids into a generated commonMain source so
// the in-app updater (Settings -> About) knows what it is running and the cloud sync layer has built-in
// OAuth client ids on every target.
val generatePeboBuildConfig by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/peboBuildConfig/commonMain/kotlin")
    val versionValue = appVersion
    val oneDriveId = oneDriveClientId
    val googleId = googleClientId
    val googleSecret = googleClientSecret
    inputs.property("version", versionValue)
    inputs.property("oneDriveClientId", oneDriveId)
    inputs.property("googleClientId", googleId)
    inputs.property("googleClientSecretPresent", googleSecret.isNotBlank())
    outputs.dir(outputDir)
    doLast {
        fun esc(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
        val pkgDir = outputDir.get().asFile.resolve("app/pebo")
        pkgDir.mkdirs()
        pkgDir.resolve("PeboBuildConfig.kt").writeText(
            """
            package app.pebo

            internal object PeboBuildConfig {
                const val VERSION: String = "${esc(versionValue)}"
                const val GITHUB_OWNER: String = "Pebo-Devs"
                const val GITHUB_REPO: String = "pebo"

                // Public OAuth client ids — safe to ship. The secret is injected at release-build time
                // only (empty in dev builds), so it never lives in source control.
                const val ONEDRIVE_CLIENT_ID: String = "${esc(oneDriveId)}"
                const val GOOGLE_CLIENT_ID: String = "${esc(googleId)}"
                const val GOOGLE_CLIENT_SECRET: String = "${esc(googleSecret)}"
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

