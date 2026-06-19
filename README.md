<div align="center">

<img src="branding/pebo-logo-256.png" alt="Pebo logo" width="120" height="120" />

# Pebo

### Personal Edit Board Online

**A markdown‑first, local‑first notes app for people who love plain text.**

Write with real Markdown, organize with tags, draw diagrams, and keep everything as
ordinary `.md` files in a folder you control — on your device or your own cloud.

![Kotlin](https://img.shields.io/badge/Kotlin-2.1.21-7F52FF?logo=kotlin&logoColor=white)
![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.8.2-4285F4?logo=jetpackcompose&logoColor=white)
![Desktop](https://img.shields.io/badge/Desktop-Windows%20%7C%20macOS%20%7C%20Linux-2EA043)
![Tests](https://img.shields.io/badge/tests-175%2B%20passing-3FB950)
![Storage](https://img.shields.io/badge/files-plain%20.md-555)

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

- Full Markdown element set in the live preview: **foldable headings**, paragraphs, **bold / italic /
  inline code / links**, bullet lists, numbered lists, **task lists**, blockquotes, **fenced code
  blocks**, **tables**, images, and horizontal rules.
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

### 💾 Bring your own storage

Choose where your notes live in **Settings → Storage**:

| Backend | Status | Notes |
| --- | --- | --- |
| **On this device** | ✅ Active | Notes saved as `.md` files in a local folder you pick. |
| **Any folder you choose** | ✅ Active | Already have a folder full of `.md`? Pebo **adopts the existing files** and edits them in place. |
| **OneDrive (Microsoft)** | 🔧 Built — needs a client ID | OAuth PKCE + secure token storage + two‑way sync engine are implemented; set `PEBO_ONEDRIVE_CLIENT_ID` to enable. |
| **Google Drive** | 🔧 Built — needs a client ID | Same sync engine; set `PEBO_GOOGLE_CLIENT_ID` to enable. |
| **iCloud Drive** | 🍎 Apple platforms | Reserved for iOS/macOS (requires Apple entitlements). |

> **Already have Markdown?** Point Pebo at a folder that already contains `.md` files and they show up
> immediately — Pebo never moves or rewrites your files behind your back.

---

## Getting started

### Prerequisites

- **JDK 21** (the project is built and run with JDK 21).
- That's it — the **Gradle wrapper** is included, so you don't need a separate Gradle install.

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

### Enable cloud sync (optional)

Cloud backends use OAuth with PKCE and never store a client secret. Provide a public OAuth client ID
via environment variable before launching:

```bash
# OneDrive
PEBO_ONEDRIVE_CLIENT_ID=<your-app-client-id>

# Google Drive
PEBO_GOOGLE_CLIENT_ID=<your-app-client-id>
```

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
- Any `.md` you keep directly in the chosen folder is **adopted and edited in place**.
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
| Build | Gradle (wrapper included), JDK 21 |

The codebase is structured as `commonMain` (shared logic + UI) with a thin `desktopMain` actual
layer, so platform targets can be added without rewriting the core.

---

## Project structure

```
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
│     ├─ desktopMain/kotlin/app/pebo/   # Desktop entry point + platform actuals
│     └─ desktopTest/              # Desktop-specific tests
└─ gradle/                         # Version catalog + wrapper
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

- 📱 **Native iOS, Android, and macOS app targets** from the same codebase.
- ☁️ **One‑click cloud sign‑in** once public OAuth client IDs are bundled for OneDrive and Google
  Drive.
- 🗂️ **Recursive vault import** — map a deep folder tree of `.md` into the note hierarchy.
- 🔍 **OCR search** inside images and PDFs.
- 🖼️ **Richer media** — inline image rendering with crop/resize and link previews.

---

## License

Not yet decided. Until a license file is added, all rights are reserved by the author.

---

<div align="center">

**Pebo — Personal Edit Board Online**
Made with Kotlin & Compose Multiplatform.

</div>
