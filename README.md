<div align="center">

<img src="branding/pebo-logo-256.png" alt="Pebo logo" width="120" height="120" />

# Pebo

### Personal Editable Brain Organizer

**A markdown‑first, local‑first notes app for people who love plain text.**

Write with real Markdown, organize with tags, draw diagrams, and keep everything as
ordinary `.md` files in a folder you control — on your device or your own cloud.

![Kotlin](https://img.shields.io/badge/Kotlin-2.1.21-7F52FF?logo=kotlin&logoColor=white)
![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.8.2-4285F4?logo=jetpackcompose&logoColor=white)
![Desktop](https://img.shields.io/badge/Desktop-Windows%20%7C%20macOS%20%7C%20Linux-2EA043)
![iOS](https://img.shields.io/badge/iOS%20%7C%20iPadOS-15%2B-000000?logo=apple&logoColor=white)
![Android](https://img.shields.io/badge/Android-API%2030%2B-3DDC84?logo=android&logoColor=white)
![Tests](https://img.shields.io/badge/tests-175%2B%20passing-3FB950)
![Storage](https://img.shields.io/badge/files-plain%20.md-555)
![License](https://img.shields.io/badge/license-FSL--1.1--ALv2-blue)

</div>

---

## Why Pebo

Pebo keeps your writing in **plain Markdown files on disk** — no proprietary database, no lock‑in,
no account required to start. Point it at a folder and it just works, online or off. The whole app is
built as a **single Kotlin Multiplatform + Compose codebase**, so the same core ships toward native
desktop and mobile apps instead of a heavy web wrapper.

- 📝 **Your files, your folder.** Every note is a `.md` file with tiny YAML frontmatter. Open them in
  any other editor; nothing is hidden.
- ⚡ **Local‑first & offline.** Notes load and save straight from the filesystem. No server needed.
- 🎨 **Genuinely beautiful.** 37 hand‑tuned themes, a custom typographic scale, and a clean three‑pane
  layout.
- 🧩 **Markdown that does more.** Tables, task lists, fenced code with syntax highlighting, foldable
  headings, and live‑rendered diagrams.

---

## Features

### ✍️ A real Markdown editor

- Full Markdown element set in the live preview: **foldable headings**, paragraphs, \*\*bold / italic /
  inline code / links\*\*, bullet lists, numbered lists, **task lists**, blockquotes, \*\*fenced code
  blocks\*\*, **tables**, images, and horizontal rules.
- **Plain text stays the source of truth.** Toolbar actions (bold, italic, headings, lists, code,
  quote, link…) rewrite the raw Markdown directly, so code fences, tables, and task lists always
  survive round‑trips verbatim.
- **Syntax‑highlighted code snippets** with a compact multi‑language lexer — paste a snippet and it
  lights up.
- **Outline with folding & Focus mode** — structure long notes with headings, collapse the sections
  you aren't working on, and dim everything but the current block.

### 📊 Diagrams, natively

- **Mermaid diagrams rendered inside the app** — flowcharts and sequence diagrams draw directly on a
  Compose canvas in your preview. No browser, no internet round‑trip.

### 🏷️ Tags that pop

- Organize with **inline `#tags`** and a **nested tag tree** in the sidebar.
- **376 named tag icons** to choose from, so your important tags stand out at a glance.
- Pin notes to the top; filter by **All**, **Untagged**, a specific **tag**, or **Trash**.

### 🧠 Fast, keyboard‑driven flow

- **Command palette** (`Ctrl/⌘ + K`) to create notes, switch filters, open settings, and jump around.
- **Inline tag autocomplete** as you type `#`.
- **Right‑click context menus** on notes — open, pin/unpin, add a child note, move to Trash, restore,
  or delete permanently.

### 🎨 35+ themes

37 curated palettes across **Signature, Classics, Nature, Warm, Vibrant, and Light** groups —
including Midnight, Obsidian, Nord, Dracula, Tokyo Night, Gruvbox, Solarized, Catppuccin, Rosé Pine,
Forest, Deep Ocean, Synthwave, Sepia, and more. Each ships a full Material 3 light **and** dark
scheme; switch the app between **Light / Dark / System** independently.

### 📤 Export anywhere

Export any note to **HTML, PDF, DOCX, JPG, or PNG**. Relative image paths are resolved against your
notes folder when rendering the raster formats.

### 🔄 Stays up to date

- **Built‑in updater.** Open **Settings → About** and hit **Check for updates** — Pebo asks the GitHub
  Releases API whether a newer version is out and compares it against the version baked into your build.
- **One‑click install on desktop.** When an update is available, Pebo downloads the right installer for
  your OS (`.msi`/`.exe` on Windows, `.dmg` on macOS, `.deb` on Linux), launches it, and closes so the
  installer can swap the files in place. The About panel shows live **Downloading → Installing** status.
- **No surprises on mobile.** Android and iOS link out to [pebo.app](https://pebo.app) / the store
  listing instead of self‑installing, so platform update rules are respected.

### 💾 Bring your own storage

Choose where your notes live in **Settings → Storage**:

| Backend | Status | Notes |
| --- | --- | --- |
| **On this device** | ✅ Active | Notes saved as `.md` files in a local folder you pick. |
| **Any folder you choose** | ✅ Active | Already have a folder full of `.md`? Pebo scans it **recursively** (every nested subfolder) and **adopts the existing files**, editing them in place. |
| **OneDrive (Microsoft)** | ✅ Built in | The app ships with Pebo's public OneDrive client ID, so you just **sign in with your Microsoft account** — OAuth PKCE, secure token storage, and two‑way sync are all wired. |
| **Google Drive** | ✅ Built in | Ships with Pebo's public Google client ID; **sign in with your Google account**. Google's desktop flow also needs a (non‑confidential) client secret that official release builds bake in. |
| **iCloud Drive** | 🍎 Apple platforms | Reserved for iOS/macOS (requires Apple entitlements). |

> **Already have Markdown?** Point Pebo at a folder that already contains `.md` files and they show up
> immediately — Pebo never moves or rewrites your files behind your back.
> 
> **Big vault?** Pebo walks **every nested subfolder at any depth** and is built to survive a folder
> holding **millions** of `.md` files: it reads only lightweight file metadata while scanning, keeps
> memory bounded by loading just the most‑recently‑modified window of notes (caches and hidden folders
> like `.git`, `.obsidian`, `node_modules` and `.trash` are skipped), and tells you honestly when it's
> *"showing N of M"* so nothing feels silently missing.

---

## Getting started

### Prerequisites

- **JDK 21** (the project is built and run with JDK 21).
- For the **iOS** app: a full **Xcode** install (Command Line Tools alone don't ship the iOS SDK or
  simulator) and, optionally, [XcodeGen](https://github.com/yonyz/XcodeGen) to generate the project.
- Otherwise that's it — the **Gradle wrapper** is included, so you don't need a separate Gradle install.

### Run the desktop app

```bash
# Windows
.\gradlew.bat :composeApp:run

# macOS / Linux
./gradlew :composeApp:run
```

### Run the tests

```bash
./gradlew :composeApp:desktopTest
```

### Run the Android app

The Android app is the **same Compose UI** as the desktop — `App.kt` automatically switches to a
compact, single-pane layout below 840 dp, so the phone gets the identical notes / editor / tag-tree
experience.

```bash
# Build a debug APK -> composeApp/build/outputs/apk/debug/
./gradlew :composeApp:assembleDebug

# Install on a connected device or running emulator
./gradlew :composeApp:installDebug
```

Requires the Android SDK (set `ANDROID_HOME`, or add a `local.properties` with `sdk.dir=...`).
Notes live in the app's external files dir by default; **Storage → Change folder** lets you pick any
folder via the Storage Access Framework. Build a release artifact with
`./gradlew :composeApp:assembleRelease` (APK) or `:composeApp:bundleRelease` (AAB) — sign it with
your own keystore via a `signingConfigs` block (or Android Studio's *Generate Signed Bundle*).

### Build a native installer

```bash
# Builds the installer for whatever OS you're on
./gradlew :composeApp:packageDistributionForCurrentOS
```

This produces a branded, self‑contained installer:

| OS | Format | Task |
| --- | --- | --- |
| Windows | `.msi` | `packageMsi` |
| macOS | `.dmg` | `packageDmg` |
| Linux | `.deb` | `packageDeb` |

> **Packaging on macOS with a Homebrew JDK?** `jpackage` rejects Homebrew's JDK by default. Either use
> a vendor build such as Amazon Corretto 21, or pass
> `-Pcompose.desktop.packaging.checkJdkVendor=false` to the `packageDmg` task.

### Updating the app

Pebo can update itself from **Settings → About → Check for updates**. It queries the GitHub Releases
API for the latest tag and compares it against the version compiled into your build; if a newer release
exists, the desktop app downloads the matching installer (`.msi`/`.exe`/`.dmg`/`.deb`), runs it, and
exits so the files can be replaced. Mobile builds open [pebo.app](https://pebo.app) instead.

The version the running app reports is whatever you pass to the build via `-PappVersion`:

```bash
# Stamp a version into the build (the release workflow passes the git tag here)
./gradlew :composeApp:run -PappVersion=1.2.3
```

When unset it defaults to the value in `composeApp/build.gradle.kts`, so locally built and tagged‑release
binaries both report a sensible version.

### Cutting a release

Releases are produced by the **Release** GitHub Actions workflow (`.github/workflows/release.yml`),
which builds the installers for every platform and attaches them to a `vX.Y.Z` GitHub Release — the
exact thing the in‑app updater looks for. It runs in three ways:

1. **Merge to `main` (the usual way).** Bump `appVersion` in `composeApp/build.gradle.kts` in your PR.
   When it merges, the workflow reads that version and, **if a release for it doesn't already exist**,
   builds the installers and cuts `vX.Y.Z` automatically. Merging a PR that didn't bump the version is
   a no‑op, so you never get duplicate releases.
2. **Push a `v*` tag.** `git tag v1.2.0 && git push origin v1.2.0` releases that exact version.
3. **Manually.** Run the workflow from the Actions tab (optionally typing a version to re‑build).

So the one‑line recipe: **bump `appVersion`, merge to `main`, and the matching release appears** with
Windows/macOS/Linux/Android artifacts attached.

### Run on iOS &amp; iPadOS

The app is **universal (iPhone + iPad)** and reuses the exact same Compose UI as the desktop app.
It adapts to the screen: a single column on iPhone, a two‑pane **list + editor** on a compact iPad
(e.g. an 11"/mini in portrait), and the full three‑pane **sidebar + list + editor** desktop
experience on a large iPad or any iPad in landscape. You need a **full Xcode install** (Command Line
Tools alone don't include the iOS SDK) and **JDK 21**.

```bash
# Generate the Xcode project from iosApp/project.yml (brew install xcodegen)
cd iosApp && xcodegen generate && cd ..

# Open it and run on a simulator…
open iosApp/iosApp.xcodeproj

# …or build the shared framework + app from the command line (iPhone or iPad simulator)
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 15' build
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPad Pro 13-inch (M5)' build
```

See [`iosApp/README.md`](iosApp/README.md) for details on the SwiftUI ↔ Kotlin bridge.

### Cloud sync setup

**You don't need to configure anything** — official Pebo builds ship with Pebo's public OneDrive and
Google Drive client IDs baked in, so cloud sync works out of the box. Just pick OneDrive or Google
Drive in **Settings → Storage** and sign in with your account. Client IDs are public identifiers (not
secrets) and are safe to embed in a native app that uses OAuth with PKCE.

**Google Drive note:** Google's *desktop* OAuth flow additionally requires a `client_secret` in the
token exchange even with PKCE. Google explicitly documents that this desktop secret "is not treated as
a secret," but Pebo still keeps it **out of source**: it is injected at release‑build time from a
GitHub Actions secret. Until that secret is present in a build, Google Drive shows as **"Needs setup."**
OneDrive is a true public client and needs no secret.

**Building official releases (maintainers):** add a repository **Actions secret** named
`PEBO_GOOGLE_CLIENT_SECRET` (the `GOCSPX‑…` value from the Google Cloud Console desktop client). The
release workflow bakes it into the installers. It is never committed to the repo.

**Overriding the built‑in IDs (advanced / your own app registration):** pass your own client IDs (and
the Google secret) at launch — they take precedence over the built‑in defaults:

```bash
# OneDrive
PEBO_ONEDRIVE_CLIENT_ID=<your-app-client-id>

# Google Drive (Desktop OAuth client — needs both id and secret)
PEBO_GOOGLE_CLIENT_ID=<your-app-client-id>
PEBO_GOOGLE_CLIENT_SECRET=<your-app-client-secret>
```

Or, for `./gradlew run`, use `-PpeboOnedriveClientId=…`, `-PpeboGoogleClientId=…`,
`-PpeboGoogleClientSecret=…`.

---

## How notes are stored

Each note is a single Markdown file with a small YAML frontmatter header — readable and editable
anywhere:

```markdown
---
id: 7f3c1a90
created: 2026-06-18T20:14:00Z
modified: 2026-06-18T21:02:33Z
pinned: true
---
# Release checklist

- [x] Tag the build
- [ ] Update the changelog

#work #release
```

- Pebo's own notes live in `<your-folder>/notes/`, with deleted notes in `<your-folder>/.trash/`.
- Any `.md` you keep **anywhere under the chosen folder** (at any nesting depth) is \*\*adopted and edited
  in place\*\* — Pebo scans subfolders recursively and never relocates your files.
- Writes are **atomic** (temp file + rename) so a note is never left half‑written.

---

## Tech stack

| Area | Choice |
| --- | --- |
| Language | **Kotlin 2.1.21** (Multiplatform) |
| UI | **Compose Multiplatform 1.8.2** + Material 3 |
| Async | kotlinx.coroutines 1.10.2 |
| Filesystem | Okio 3.12.0 |
| Rich text | richeditor‑compose 1.0.0‑rc13 |
| Networking / OAuth | Ktor 3.2.0 + kotlinx.serialization 1.9.0 |
| Native integration | JNA 5.17.0 |
| Targets | Desktop (Windows / macOS / Linux) · **Android** (minSdk 30) |
| Build | Gradle (wrapper included), JDK 21 |

The codebase is structured as `commonMain` (shared logic + UI) with thin `desktopMain` (JVM) and
`iosMain` (Kotlin/Native) actual layers, so the same core renders natively on every target.

---

## Project structure

```javascript
pebo/
├─ branding/                       # Logo source (SVG) + generated PNG/ICO/ICNS + installer icons
├─ composeApp/
│  └─ src/
│     ├─ commonMain/kotlin/app/pebo/
│     │  ├─ core/                  # Note model, file format, tags, filters, slugs
│     │  ├─ data/                  # LocalNoteStore (the .md-on-disk backend)
│     │  ├─ ui/                    # Editor, NoteList, Sidebar, Settings, Command palette…
│     │  │  └─ theme/              # 37 palettes + typography
│     │  ├─ export/                # HTML / PDF / DOCX / JPG / PNG export
│     │  ├─ sync/                  # Sync engine + OneDrive / Google Drive remotes
│     │  └─ auth/                  # OAuth PKCE + secure token storage
│     ├─ desktopMain/kotlin/app/pebo/   # Desktop (JVM) entry point + platform actuals
│     ├─ iosMain/kotlin/app/pebo/        # iOS entry (MainViewController) + Kotlin/Native actuals
│     ├─ androidMain/kotlin/app/pebo/   # Android Activity + actuals (SAF storage, export, OAuth)
│     ├─ commonTest/ + desktopTest/      # Shared + desktop tests
│     └─ …
├─ iosApp/                          # SwiftUI host for the iOS/iPadOS app (XcodeGen project.yml)
├─ apps/parity/                     # Apple (iOS+macOS) parity loop: ledger, runner, prompt
├─ tools/android-parity/            # Android parity loop: ledger, runner, screenshots
└─ gradle/                          # Version catalog + wrapper
```

---

## Keyboard shortcuts

| Shortcut | Action |
| --- | --- |
| `Ctrl / ⌘ + K` | Open the command palette |
| `Ctrl / ⌘ + Shift + F` | Toggle Focus mode |
| `Esc` | Close the palette / exit Focus mode |
| Right‑click a note | Open its context menu |

---

## Roadmap

Pebo is built on a shared Kotlin Multiplatform core specifically so it can grow beyond the desktop:

- 📱 **Native iOS / iPadOS** + **macOS** + **Android** from the same codebase — the universal
  iPhone + iPad target and the Android app both build and run today (`iosApp/`, `composeApp` Android);
  macOS ships as a packaged `.dmg`.
- ☁️ **One‑click cloud sign‑in** once public OAuth client IDs are bundled for OneDrive and Google
  Drive.
- 🗂️ **Unbounded vault index** — recursive discovery of every nested `.md` ships today (with a
  memory‑bounded most‑recent window); a persistent on‑disk index is next so even multi‑million‑note
  vaults are fully searchable, and the folder tree can map onto the visual note hierarchy.
- 🔍 **OCR search** inside images and PDFs.
- 🖼️ **Richer media** — inline image rendering with crop/resize and link previews.
- 🔄 **In‑app auto‑update** — ✅ shipped on desktop (Settings → About); background update checks are next.

---

## License

Pebo is **source‑available** under the [**Functional Source License v1.1 (FSL‑1.1‑ALv2)**](LICENSE).

In plain terms:

- ✅ **You can** read the source, run it, self‑host it, modify it, and contribute back.
- ✅ **You can** use it for internal use, non‑commercial education and research, and professional
  services for a licensee.
- 🚫 **You can't** use it for a **Competing Use** — i.e. shipping it (or a derivative) as a commercial
  product or service that substitutes for Pebo.
- 🕓 **It becomes Apache‑2.0** automatically two years after each version is released.

Copyright © 2026 Atul Gupta. "FSL" and the Functional Source License are works of the FSL authors;
see [fsl.software](https://fsl.software).

---

<div align="center">

**Pebo — Personal Editable Brain Organizer**
Made with Kotlin & Compose Multiplatform.

</div>