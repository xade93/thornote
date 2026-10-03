# ThorNotes
[![Android](https://github.com/xade93/thornote/actions/workflows/android.yml/badge.svg)](https://github.com/xade93/thornote/actions/workflows/android.yml)

ThorNotes is a small bottom-screen notebook app with dual screen handheld in mind.

When you play lots of games and switch between them, it is easy to forget what was happening in each one. ThorNotes helps by letting you quickly save in-game screenshots or OCR text from a selected screen region, then browse everything later in a clean bottom-screen interface.

It is still rough, but I find it already useful for puzzle games, visual novels, RPGs, and other games where you want to keep track of maps, clues, dialogue, names, or memorable moments without leaving the game.

On device photo:
<img width="1657" height="573" alt="image" src="https://github.com/user-attachments/assets/2ffa52d3-0f83-4447-88d2-aade5d125a15" />

Screenshots:
<img width="2454" height="2170" alt="merged-image-2026-06-21T17-10-04" src="https://github.com/user-attachments/assets/477cceb0-cf88-45e8-a554-45ec1711e3ff" />

## Main Features

- **Save screenshots, text notes** into notebook pages; **Pin/star** important screenshots.
- **OCR** a selected screen region and save the recognized text. Two methods are provided, both are fully offline. Multilingual support.
- Keep **multiple notebook pages**, so different games can have separate notes; Browse saved screenshots with thumbnails for quick jumping.
- Double-tap the time to turn the bottom screen black for **OLED blackout mode**, then double-tap again to restore.
- Optional **floating overlay button** to hide or restore ThorNotes while playing, to use other second screen app. Coexist well with other floating apps e.g. DS Overlay.
- Set the floating button’s **double-tap action** to capture the top screen or do nothing. Enable ThorNotes in Android Accessibility settings to allow screen capture. A green ✓ confirms the screenshot was saved to your current page; a red ! means it failed.
- **Backup and restore** notebook from Settings.
- Use the offline English **dictionary** page for quick lookup.

## Screen capture

ThorNotes and its floating button prefer an available secondary presentation display (the bottom screen on dual-screen handhelds), falling back to the main screen when unavailable. If several secondary screens are connected, Android’s presentation-display order decides the target.

Requires Android 11 or newer. Tap Shot or OCR to open the capture setup prompt, enable ThorNotes in Android Accessibility settings, then return and tap capture again. The accessibility service takes an on-demand screenshot of the default (top) display; screenshots and OCR text stay on your device. Disable the service in Accessibility settings to revoke access. Apps that protect their screen content cannot be captured.

## Data and Backup

- Notebook data is stored in app-private internal storage. Uninstalling the app usually deletes its internal data. Use `Settings -> Notebook Backup` to backup or restore a ThorNotes ZIP backup.
    - `Export` writes the notebook folder and relevant shared preferences into a zip file.
    - `Import` adds pages from the selected backup into the current notebook **without replacing existing pages**. Pages that already exist are skipped.
- Import is designed to keep existing notebook data intact if a restore fails partway through. Its not fully atomic but is usually safe. Still, please try to keep ThorNotes open while backup or restore process is running.

## Build

```bash
echo "sdk.dir=$HOME/Android/sdk" > local.properties
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew installDebug
```

## Credits

ThorNotes started from work based on ThorTranslate. Credit to ThorTranslate (ie ThorLens) author for the initial program.

Disclosure: LLM is used in this work extensively (Codex).

## License

MIT
