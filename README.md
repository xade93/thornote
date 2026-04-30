# ThorNotes

Notebook app for information-heavy games, visual novels, novels, and detective games on dual-screen Android handhelds like the Ayn Thor. The game runs on the top screen; ThorNotes runs on the bottom screen.

## What It Does

**Notebook page** records a browsable stream of captured context:
- Create a separate page for each visual novel, novel, or game.
- Switch pages from the page selector.
- View each page's entry count and storage usage.
- Delete a page from the page menu when you are done with it.
- **Shot** captures the whole screen and stores it in the notebook.
- **OCR** captures the saved fixed region, runs OCR, and stores the recognized text as an editable notebook entry.
- OCR text entries can be corrected in place when recognition is inaccurate.
- Swipe a screenshot or OCR entry sideways to delete it.

**Dictionary page** keeps Japanese word lookup available for moments when a word is hard to remember. Type or paste Japanese text manually, then ThorNotes tokenizes it and shows readings and dictionary meanings.

**Custom OCR region** lets you select a dialogue box or text area once, then reuse it for OCR. Tap Set Region the first time, or long-press OCR to modify it later. Full screenshots always capture the whole screen.

## Tech Stack

- Kotlin + Jetpack Compose
- MediaProjection API with a foreground service for screen capture
- ML Kit Text Recognition v2 for Japanese OCR
- Kuromoji for Japanese tokenization
- Bundled JSON dictionary derived from JMDict-style entries
- SharedPreferences for settings
- App-private file storage for notebook screenshots and metadata

## Setup

### Requirements

- Android SDK, compileSdk 35
- JDK 17 recommended for the current Gradle/Kotlin toolchain

### Build

```bash
echo "sdk.dir=$HOME/Android/sdk" > local.properties
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew installDebug
```

Adjust `sdk.dir` and `JAVA_HOME` for your machine.

## Project Structure

```text
app/src/main/java/com/kanjilens/
├── MainActivity.kt
├── capture/
│   ├── ScreenCaptureManager.kt
│   └── ScreenCaptureService.kt
├── data/
│   ├── NotebookRepository.kt
│   └── models/
│       ├── AppSettings.kt
│       ├── CaptureState.kt
│       └── NotebookEntry.kt
├── ocr/
│   └── TextRecognizer.kt
├── analysis/
│   ├── JapaneseTokenizer.kt
│   └── DictionaryLookup.kt
└── ui/
    ├── screens/
    │   ├── MainScreen.kt
    │   ├── CropScreen.kt
    │   ├── SettingsScreen.kt
    │   └── HelpScreen.kt
    ├── components/
    │   └── WordCard.kt
    └── theme/
        └── Theme.kt
```

## License

MIT
