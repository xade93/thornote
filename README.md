# ThorNotes

ThorNotes is a small bottom-screen notebook app with dual screen handheld in mind.

When you play lots of games and switch between them, it is easy to forget what was happening in each one. ThorNotes helps by letting you quickly save in-game screenshots or OCR text from a selected screen region, then browse everything later in a clean bottom-screen interface.

It is still rough, but I find it already useful for visual novels, detective games, RPGs, and other games where you want to keep track of details, clues, dialogue, names, or memorable moments without leaving the game.

On device photo:
<img width="1657" height="573" alt="image" src="https://github.com/user-attachments/assets/2ffa52d3-0f83-4447-88d2-aade5d125a15" />

Screenshots:
<img width="2454" height="2170" alt="merged-image-2026-06-21T17-10-04" src="https://github.com/user-attachments/assets/477cceb0-cf88-45e8-a554-45ec1711e3ff" />

## Main Features

- **Save screenshots** into notebook pages.
- **OCR** a selected screen region and save the recognized text. Two methods are provided, both are fully offline. Multilingual support.
- Keep multiple notebook pages, so different games can have separate notes; Browse saved screenshots with thumbnails for quick jumping.
- **Pin/star** important screenshots so they stay easy to find.
- Double-tap the time to turn the bottom screen black for **OLED blackout mode**, then double-tap again to restore.
- Use optional **floating overlay button** to hide or restore ThorNotes while playing, to use other second screen app. Coexist well with other floating apps e.g. DS Overlay.
- **Backup and restore** notebook from Settings.
- *TBD* Use the offline English dictionary page for quick lookup.

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

Requires Android SDK compileSdk 35 and JDK 17.

## CI and releases

[Android CI](.github/workflows/android.yml) builds debug and release variants and
runs Android lint on pushes to `main`, pull requests, and manual runs from the
Actions tab. Download the debug APK from the run's artifacts. CI does not run
device/instrumentation tests.

For automatic signed releases, add these repository secrets under **Settings →
Secrets and variables → Actions → New repository secret**:

- `ANDROID_KEYSTORE_BASE64`: Base64-encoded contents of the existing release
  keystore (use `base64 -w0 /absolute/path/to/your-release.keystore`).
- `ANDROID_KEYSTORE_PROPERTIES`: The full contents of your local
  `keystore.properties`, including `storePassword`, `keyAlias`, and `keyPassword`.
  CI overrides `storeFile` to point to its temporary copy of the keystore.

Use the existing signing key so users can update installed releases. Never commit
either secret. Signing secrets are used only on tag pushes, not pull requests.

To release:

1. Update `versionName` and increase `versionCode` in `app/build.gradle.kts`.
2. Commit the release changes and push them to your repository.
3. Tag that commit with `v` followed by `versionName`, then push the tag. For
   example, for version `0.4.4` and this checkout's `thornote` remote:

   ```bash
   git tag -a v0.4.4 -m 'ThorNotes 0.4.4'
   git push thornote v0.4.4
   ```

CI verifies that the tag matches the built APK version and that the APK is signed,
then creates a draft GitHub Release with the APK attached and generated notes.
Review the draft and publish it. Missing signing secrets fail the tag build.
An existing release for the tag causes draft creation to fail rather than replacing
its APK; use a new version/tag for a corrected release.

## Credits

ThorNotes started from work based on ThorTranslate. Credit to ThorTranslate (ie ThorLens) author for the initial program.

## License

MIT
