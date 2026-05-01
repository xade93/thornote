# ThorNotes

ThorNotes is a bottom-screen notebook for Ayn Thor. When playing information-heavy games there is a need to save screenshots as quick as possible, ideally OCR them into text, and fetch them quickly when such need occurs. This app achieves it, while aiming to keep resource usage low and workflow smooth.

## What Matters

- **Shot** captures the current screen and stores a JPEG in the active notebook page.
- **OCR** captures a saved region, runs ML Kit text recognition, and stores editable text.
- Notebook pages are separate streams. The last opened page is remembered.
- The thumbnail rail jumps directly to screenshots or OCR text blocks.
- Dictionary lookup is offline, backed by a bundled Open English WordNet SQLite database.

## Storage Model

Notebook data is stored in app-private internal storage, not public internal storage and not the SD card.

Runtime paths on device:

```text
/data/user/0/com.thornotes/
├── files/
│   ├── notebook/
│   │   ├── pages.json
│   │   └── pages/<page-id>/
│   │       ├── entries.json
│   │       └── images/<entry-id>.jpg
│   └── english_dictionary.db
└── shared_prefs/
    ├── thornotes_prefs.xml
    └── notebook.xml
```

The important backup target is `files/notebook`. It contains page metadata, entry order, OCR text, and screenshot files. Screenshot paths in `entries.json` are relative to `files/notebook`, so the notebook directory is self-contained. Shared preferences only hold UI/settings state such as text size, OCR crop region, and last opened notebook page. `english_dictionary.db` is copied from the APK asset and does not need to be backed up.

Uninstalling the app deletes this internal data. Android may include it in system app backup because `allowBackup=true`, but that is device/account dependent and should not be treated as the main backup path.

## Backup And Restore

For debug builds, `adb run-as` can read the app-private directory:

```bash
adb exec-out run-as com.thornotes tar -C /data/user/0/com.thornotes -cf - files/notebook shared_prefs/thornotes_prefs.xml shared_prefs/notebook.xml > thornotes-backup.tar
```

Restore into an installed debug build:

```bash
adb shell run-as com.thornotes sh -c 'cd /data/user/0/com.thornotes && tar -xf -' < thornotes-backup.tar
```

If `run-as` says the package is not debuggable, Android is blocking shell access to private app data. At that point the practical options are root access, Android's own backup/restore, or adding an in-app export/import flow that writes a zip to a public picker location.

## Capture Cost

Android says "start recording or casting" because MediaProjection has one generic permission prompt. ThorNotes uses it for still captures.

Each Shot/OCR creates a temporary `VirtualDisplay` and `ImageReader`, grabs one RGBA frame, then releases them. The MediaProjection session remains alive so repeated captures do not ask for permission again.

Idle after permission should be low CPU/GPU. Shot briefly uses GPU/compositor work plus JPEG compression. OCR adds bitmap crop work and ML Kit inference, so it is the heavier path. Browsing pages with many screenshots mainly costs RAM and bitmap decode time.

## Build

```bash
echo "sdk.dir=$HOME/Android/sdk" > local.properties
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew installDebug
```

Requires Android SDK compileSdk 35 and JDK 17.

## Tech Notes

- Kotlin + Jetpack Compose
- MediaProjection foreground service for screen capture
- ML Kit Text Recognition v2
- Gson JSON metadata for notebook pages and entries
- SQLite dictionary copied from `app/src/main/assets`

## License

MIT
