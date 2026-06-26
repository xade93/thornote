# Screen Capture Freeze Notes

This file documents the ThorNotes Shot-button freeze seen on the Ayn Thor device, what evidence we collected, and what mitigations have been tried.

## Symptom

- Pressing `Shot` can freeze the whole Android device, not just ThorNotes.
- During the freeze, the device may become nonresponsive to normal input and may appear to reboot or recover only after some time.
- The failure is most reproducible around screenshot capture, especially after screen-capture permission is granted or when Shot is used repeatedly.

## Capture Path

ThorNotes uses Android `MediaProjection` for screen capture:

1. User taps `Shot`.
2. If no projection exists, Android shows the screen-capture permission UI.
3. `ScreenCaptureService` receives the projection result.
4. `ScreenCaptureManager` creates an `ImageReader`.
5. `ScreenCaptureManager` calls `MediaProjection.createVirtualDisplay(...)`.
6. Frames arrive through `ImageReader`.
7. ThorNotes converts a frame into a `Bitmap` and saves it.

The freezes we have diagnosed happen before bitmap saving, OCR, or notebook writes.

## Most Important Evidence

The useful debug boundary is whether ThorNotes logs `virtual_display_created`.

Known bad log from the 2026-06-27 freeze:

```text
2026-06-27 01:00:31.412 projection_received replacingExisting=false
2026-06-27 01:00:31.415 capture_request ready=true
2026-06-27 01:00:31.417 capture_started
2026-06-27 01:00:31.419 capture_session_creating width=1240 height=1080 density=369
```

There was no app-side `virtual_display_created` after that.

Android system logcat did show system_server starting to add the virtual display:

```text
06-27 01:00:31.425 DisplayDeviceRepository: Display device added: "ThorNotesCapture"
06-27 01:00:31.428 LogicalDisplayMapper: Adding new display: 7 ...
```

Interpretation: Android started creating the virtual display, but `MediaProjection.createVirtualDisplay(...)` did not return cleanly to ThorNotes. This points to a device firmware/display-stack deadlock around virtual display creation, not an ordinary ThorNotes UI freeze.

## What Did Not Solve It

These mitigations helped reduce obvious app-side pressure but did not fully eliminate the device freeze:

- UI-level Shot cooldown only.
- Rejecting overlapping capture requests only.
- Coroutine timeout around capture only.
- Releasing and recreating `ImageReader` / `VirtualDisplay` for each shot.
- Bitmap recycling and notebook write cleanup by themselves.

The important lesson: if the native/display path wedges inside `createVirtualDisplay(...)`, Kotlin timeouts and app-level cleanup may not run soon enough to prevent the device-level failure.

## What Helped

### Persistent Capture Session

The first major improvement was to reuse a persistent `VirtualDisplay + ImageReader` for the active projection instead of recreating them on every Shot.

Why it helped:

- The freeze was strongly associated with virtual-display lifecycle churn.
- Once a `VirtualDisplay` exists, later captures can reuse the existing session.
- Reuse avoids repeated calls to `MediaProjection.createVirtualDisplay(...)`, which is the riskiest operation on this device.

### Latest-Frame Cache

ThorNotes now drains `ImageReader` frames while idle and keeps only the newest cached bitmap.

Why it helped:

- A Shot can usually use an already-available frame.
- The capture path does less work at button-press time.
- The `ImageReader` queue is less likely to back up.

### Bounded Capture Debug Log

ThorNotes now has an opt-in capture debug log in Settings:

```text
/sdcard/Android/data/com.thornotes/files/capture_debug.log
```

The file is capped at 128 KB and trims older lines. It logs lifecycle events such as:

- projection received
- capture request accepted/rejected
- projection settle wait
- capture session create/reuse
- virtual display create success/failure
- pending frame wait
- capture timeout
- cleanup

This is intentionally event-level logging, not frame-by-frame logging.

## Current Avoidance Strategy

The latest mitigation is a projection-settle wait before first session creation.

Reason:

- In the 2026-06-27 failure, screen-capture permission returned and ThorNotes attempted `createVirtualDisplay(...)` about 9 ms later.
- Logcat still showed SystemUI/projection activity immediately before that.
- Creating the virtual display while permission/SystemUI/display state is still settling may trigger the firmware/display-stack deadlock.

Current behavior:

- After a new `MediaProjection` is received, the first capture waits up to 1500 ms before creating the first `VirtualDisplay`.
- Existing reusable sessions skip this delay.
- Debug log records:

```text
projection_settle_wait millis=... elapsed=...
capture_session_creating ... projectionAge=...
```

Expected healthy first-capture log:

```text
projection_received replacingExisting=false
capture_request ready=true
capture_started
projection_settle_wait millis=... elapsed=...
capture_session_creating width=1240 height=1080 density=369 projectionAge=1500
virtual_display_created width=1240 height=1080 density=369
capture_complete source=listener ...
```

If it freezes again and the last line is still `capture_session_creating`, then the settle wait was not enough and the next mitigation should be more drastic.

## Next Escalation If It Still Freezes

If the device still freezes inside first `createVirtualDisplay(...)`, consider changing the interaction model:

1. First tap only requests/prepares screen-capture permission.
2. ThorNotes waits until projection is stable.
3. User taps `Shot` again to perform the actual capture.

This avoids doing `createVirtualDisplay(...)` immediately in the permission-result path.

Another possible escalation is a manual `Prepare Capture` button or status row in Settings, but the simplest user model is likely: first tap grants/prepares, second tap captures.

## Debug Commands

Read the capture debug log:

```bash
adb shell tail -120 /sdcard/Android/data/com.thornotes/files/capture_debug.log
```

Check whether Android still has a MediaProjection active:

```bash
adb shell dumpsys media_projection
```

Check whether the ThorNotes process is still alive:

```bash
adb shell pidof com.thornotes
```

Relevant logcat search:

```bash
adb logcat -d -t 800 | rg -n "ThorNotes|MediaProjection|VirtualDisplay|DisplayDeviceRepository|LogicalDisplayMapper|BufferQueue|ANR|Watchdog|system_server"
```

## Working Hypothesis

This is likely not a normal ThorNotes app crash. The strongest current hypothesis is a firmware/display-stack bug on the device triggered by `MediaProjection.createVirtualDisplay(...)`, especially when called immediately after permission flow or during virtual-display lifecycle churn.

ThorNotes should therefore:

- minimize `createVirtualDisplay(...)` calls,
- reuse the capture session when possible,
- avoid creating the first virtual display immediately after permission,
- keep bounded debug evidence for future failures.
