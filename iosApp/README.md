# Pebo for iOS

This is the SwiftUI host for the **iOS & iPadOS** build of Pebo — a **universal (iPhone + iPad)**
app (`TARGETED_DEVICE_FAMILY = "1,2"`). The entire UI and all logic come from the shared Kotlin
Multiplatform / Compose Multiplatform code in `../composeApp`; this target only embeds the shared
`ComposeApp` framework and shows it. The Compose UI adapts to the size class: one column on iPhone,
a two‑pane list + editor on a compact iPad, and the full three‑pane desktop layout on a large iPad
or in landscape.

```
iosApp/
├─ project.yml              # XcodeGen spec that produces iosApp.xcodeproj
└─ iosApp/
   ├─ iOSApp.swift          # @main App
   ├─ ContentView.swift     # wraps Kotlin MainViewController() in SwiftUI
   ├─ Info.plist
   └─ Assets.xcassets/      # add an AppIcon set here
```

The Kotlin side of the bridge lives in
`../composeApp/src/iosMain/kotlin/app/pebo/MainViewController.kt`.

## Prerequisites

- **Xcode** (full install — Command Line Tools alone do not include the iOS SDK/simulator)
- **JDK 21**
- **[XcodeGen](https://github.com/yonyz/XcodeGen)** to generate the project from `project.yml`:
  `brew install xcodegen` (optional — you can instead open/create the project in Xcode or via the
  Android Studio *Kotlin Multiplatform* plugin)

## Generate & run

```bash
# 1. Generate iosApp.xcodeproj from project.yml
cd iosApp && xcodegen generate && cd ..

# 2. Build the Kotlin framework for the simulator (also done automatically by the
#    "Compile Kotlin Framework" build phase, but useful to verify standalone):
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64

# 3. Build & boot in a simulator
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 15' build
```

Or just open `iosApp/iosApp.xcodeproj` in Xcode, pick a simulator, and press ▶.

## How the framework is wired

`project.yml` adds a **Compile Kotlin Framework** pre-build script that runs
`./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`, and points `FRAMEWORK_SEARCH_PATHS` at
`composeApp/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`. The app links `-framework ComposeApp`
and calls `MainViewControllerKt.MainViewController()`.

## Status

Scaffolded by the Apple parity loop (`apps/parity/`) and **verified end-to-end**: the shared
framework compiles + links, the app builds (`xcodebuild … -sdk iphonesimulator`), and it runs in the
iOS Simulator showing the full Pebo UI. Set a development team in Xcode before running on a device.
