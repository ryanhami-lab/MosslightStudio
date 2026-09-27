# Testing

The project uses JDK 17, Android SDK 36, JUnit 4, kotlinx-coroutines-test, and AndroidX instrumentation tests.

## Local JVM checks

On Windows:

```powershell
.\gradlew.bat testDebugUnitTest --console=plain
.\gradlew.bat lintDebug assembleDebug --console=plain
```

On Linux or macOS:

```bash
./gradlew testDebugUnitTest --console=plain
./gradlew lintDebug assembleDebug --console=plain
```

Useful focused runs are:

```powershell
.\gradlew.bat testDebugUnitTest --tests 'com.ryanhami.mosslightstudio.core.*' --tests 'com.ryanhami.mosslightstudio.data.*' --console=plain
.\gradlew.bat testDebugUnitTest --tests 'com.ryanhami.mosslightstudio.StudioViewModelTest' --console=plain
```

The JVM suite covers immutable model boundaries, placement and collision geometry, frame accumulation, viewport and crop transforms, print composition and undo, strict save decoding, migration, blocked-byte preservation, ordered DataStore writes, ViewModel persistence, and failure-without-commit behavior.

## Android checks

Start an API 36 emulator, then run:

```powershell
.\gradlew.bat connectedDebugAndroidTest --console=plain
```

Or on Linux or macOS:

```bash
./gradlew connectedDebugAndroidTest --console=plain
```

The instrumentation suite exercises Activity recreation and frame-lease replacement, stopping input when focus is lost, print-panel lifecycle behavior, exactly-once share claiming, arrangement touch-coordinate mapping, Android bitmap output, and cache-only `FileProvider` access. Restart persistence is a separate manual device check.

Manual release review should cover room movement, edge collisions, every arrangement cell, occupied and caretaker-blocked placements, restart persistence, recovery from a deliberately invalid save in a development install, print undo and clear, chooser cancellation, large text, TalkBack labels, keyboard/D-pad input, rotation policy, and foreground/background transitions.

## Current evidence

Current Windows evidence for the frozen main source is:

- All 38 JVM tests passed.
- `lintDebug` and `lintRelease` completed with zero errors.
- Debug, instrumentation, and unsigned release APK assembly completed successfully.
- All 4 Android instrumentation tests passed against the exact final debug APK on an API 36 emulator.
- A manual emulator restart check persisted a moved item as schema 2, survived a force-stop and relaunch, and reconstructed the saved selection correctly.
- A manual recovery check injected a synthetic future schema into the new app only. Load, retry, and keep-layout paths preserved the exact file hash; the two-step replace action restored the six-item initial room.
- Actual screens were reviewed at 1080×2160 / 420 dpi and 720×1280 / 360 dpi, including font scale 2.0. Narrow arrangement actions remained reachable by scrolling; horizontal print scrolling exposed and accepted taps on both edge columns.
- A dedicated release signature was verified and the signed release installed successfully on the same emulator. No pre-existing application signer was used.

Remaining lint warnings concern pinned dependency updates, the intentional portrait orientation, the custom View's absence of an XML-editor constructor, and optional Kotlin extension syntax. They are not suppressed as proof of correctness and should be reassessed when dependencies or platform policy change.

The connected run used one API 36 emulator; display-size checks reused that isolated instance. No physical-device, alternate API-level, OEM, low-memory, foldable, tablet, end-to-end TalkBack, or recipient-app matrix has been completed, so behavior across those environments remains unverified. The default Gradle release output is unsigned unless signing variables are supplied. Future published upgrades need their own continuity checks.

