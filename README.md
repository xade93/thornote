# ThorNotes

<img width="1672" height="941" alt="ThorNotes screenshot" src="https://github.com/user-attachments/assets/c203ab70-8c9a-45ad-8067-fdf8fb76f656" />

ThorNotes is a small bottom-screen notebook app made with the Ayn Thor in mind.

When you play lots of games and switch between them, it is easy to forget what was happening in each one. ThorNotes helps by letting you quickly save in-game screenshots or OCR text from a selected screen region, then browse everything later in a clean bottom-screen interface.

It is still rough, but it is already useful for visual novels, detective games, RPGs, and other games where you want to keep track of details, clues, dialogue, names, or memorable moments without leaving the game.

## Download

- GitHub: <https://github.com/xade93/thornote>
- APK releases: <https://github.com/xade93/thornote/releases/>

## Main Features

- Save screenshots into notebook pages.
- OCR a selected screen region and save the recognized text.
- Keep multiple notebook pages, so different games can have separate notes.
- Browse saved screenshots with thumbnails for quick jumping.
- Pin/star important screenshots so they stay easy to find.
- Double-tap the time to turn the bottom screen black for OLED blackout mode, then double-tap again to restore.
- Use an optional floating overlay button to hide or restore ThorNotes while playing.
- Keep notebook data as JSON metadata plus plain image files, so it can be migrated or backed up if needed.
- Use the offline English dictionary page for quick lookup.

## How It Works

ThorNotes uses Android screen capture permission for still screenshots and OCR. Android describes this as "start recording or casting" because MediaProjection uses one generic permission prompt, but ThorNotes only grabs frames when you press Shot or OCR.

For OCR, ThorNotes can use local PP-OCRv5 assets when available and falls back to ML Kit text recognition. Captured text is saved into the active notebook page, alongside screenshots.

The floating overlay button requires Android's "display over other apps" permission. It hides ThorNotes to the background and brings it back without closing the current notebook.

## Data And Backup

Notebook data is stored in app-private internal storage:

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

The important backup target is `files/notebook`. It contains page metadata, entry order, OCR text, and screenshot files. Screenshot paths are relative to the notebook folder, so the notebook directory is intended to be self-contained.

Shared preferences mainly store UI/settings state, such as text size, OCR crop region, and the last opened notebook page. The dictionary database is copied from the APK and does not need to be backed up.

Uninstalling the app deletes its internal data. Android may include it in system app backup because `allowBackup=true`, but that depends on device/account behavior and should not be treated as the main backup path.

For debug builds, `adb run-as` can read the app-private directory:

```bash
adb exec-out run-as com.thornotes tar -C /data/user/0/com.thornotes -cf - files/notebook shared_prefs/thornotes_prefs.xml shared_prefs/notebook.xml > thornotes-backup.tar
```

Restore into an installed debug build:

```bash
adb shell run-as com.thornotes sh -c 'cd /data/user/0/com.thornotes && tar -xf -' < thornotes-backup.tar
```

If `run-as` says the package is not debuggable, Android is blocking shell access to private app data. At that point the practical options are root access, Android's own backup/restore, or adding an in-app export/import flow.

## Build

```bash
echo "sdk.dir=$HOME/Android/sdk" > local.properties
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew installDebug
```

Requires Android SDK compileSdk 35 and JDK 17.

## Credits

ThorNotes started from work based on ThorTranslate. Credit to the ThorTranslate author for the initial base.

## License

MIT
