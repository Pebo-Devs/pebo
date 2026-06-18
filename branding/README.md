# Pebo brand assets

Pebo's logo: a brand-gradient "page" tile with a dog-ear fold (a notes/document cue) and a clean
white **P** monogram whose bowl is a full circle — a friendly, pebble-like head that makes the
letter ownable rather than a default typeface "P".

`P` · `E` · `B` · `O` = **P**ersonal **E**dit **B**oard **O**nline.

## Source of truth

The mark is defined **once** as geometry and rendered everywhere from it, so nothing can drift:

| File | Role |
| --- | --- |
| [`pebo-logo.svg`](pebo-logo.svg) | Vector source (48×48 viewBox). Hand-authored; matches the in-app `ImageVector` exactly. |
| `composeApp/.../ui/PeboLogo.kt` | The in-app `peboLogo()` `ImageVector` (sidebar, window icon, Settings → About). Same numbers as the SVG. |
| [`GenerateLogo.java`](GenerateLogo.java) | Pure-JDK renderer that rasterises the SVG geometry to the PNG / ICO / ICNS files below. |

Brand colours (fixed — the mark does **not** recolour with the UI theme):

- Gradient top-left `#5B8CFF` (azure indigo) → bottom-right `#7C5CFF` (violet)
- Monogram `#FFFFFF` · fold shade `#000000` @ 20%

## Generated files

| File | Use |
| --- | --- |
| `pebo-logo-256.png`, `-512.png`, `-1024.png` | Square transparent renders (store listings, web, social). |
| `pebo-icon.ico` | Windows app/installer icon — 16/32/48/64/128/256 px, PNG-compressed. Wired into the MSI via `compose.desktop { … windows { iconFile } }`. |
| `pebo-logo.icns` | macOS app/installer icon — wired into the DMG via `macOS { iconFile }`. |

The Linux DEB icon uses `pebo-logo-512.png` directly.

## Regenerating

From the repo root (requires only a JDK):

```sh
javac -d branding/out branding/GenerateLogo.java
java  -cp branding/out GenerateLogo branding
```

If you change the mark, edit the geometry in **both** `pebo-logo.svg` and `PeboLogo.kt` (they share
the same coordinates), then re-run the generator.
