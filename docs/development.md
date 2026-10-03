# Development

## Repository layout

- `app/src/main/java/com/thornotes/`: Android app, grouped by feature.
- `app/src/main/res/`: Android resources.
- `app/src/main/assets/`: bundled dictionary, attribution, and OCR configuration. Paddle OCR models download into app storage when requested.
- `app/src/main/cpp/`: OCR JNI bridge and native processing code.
- `app/src/main/jniLibs/`: packaged PaddleLite and C++ runtime libraries for both supported ARM architectures. CMake links PaddleLite from this same location.
- `app/src/main/paddle/`: vendor PaddleLite headers and OpenCV SDK. OpenCV's CMake configuration references its bundled libraries, including modules not called directly by app code.
- `app/src/androidTest/`: device checks.
- `tools/`: dictionary generation utility.
- `docs/history/`: investigation notes for replaced implementations, including the [old capture freeze](history/screen-capture-freeze.md).

## Build

Use JDK 17 and configure your Android SDK in the ignored `local.properties` file.
The SDK/NDK versions used in CI are listed in [the Android workflow](../.github/workflows/android.yml).

```sh
./gradlew assembleDebug assembleRelease assembleDebugAndroidTest
```

Add `--offline` when all dependencies are already cached. On Arch Linux, prefix the command with `JAVA_HOME=/usr/lib/jvm/java-17-openjdk` if your default Java version differs.

APKs are generated in `app/build/outputs/apk/{debug,release}/` with names such as `ThorNotes-0.4.6-release.apk`. Release signing uses the ignored `keystore.properties`; keep it and the signing key private. Without that configuration, the release APK is unsigned.

Generated output stays in `app/build/`, `app/.cxx/`, `.gradle/`, and `.kotlin/`; these directories are ignored. Use `./gradlew clean` to remove build output when needed. Keep vendor libraries and bundled assets: they are build inputs.

## Device checks

With a compatible debug app and its test APK installed:

```sh
adb shell am instrument -w com.thornotes.test/com.thornotes.DiagnosticsCheck
```

The runner checks settings, archives, floating-button gestures, display selection, and diagnostics using test data. It does not verify actual screen placement. Avoid uninstalling an existing app to resolve signing differences: uninstalling removes its notebook data. Export a backup first and use a compatible signing key or a separate test device.

## Dictionary generation

The bundled dictionary is a runtime asset. Its generator takes an Open English WordNet 2024 ZIP containing the `oewn2024/` directory:

```sh
python3 tools/build_english_dictionary.py /path/to/wordnet.zip /tmp/english_dictionary.db
```

Review the generated database before replacing `app/src/main/assets/english_dictionary.db`; retain its attribution file.
