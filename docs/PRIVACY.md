# Privacy

Mosslight Studio works locally. It has no account, analytics, advertising, telemetry, network client, location feature, contacts access, camera access, or microphone access. The source manifest declares no platform, runtime, or network permission. AndroidX adds an app-scoped signature permission for non-exported dynamic receivers to the merged manifest; it grants no access to device or user data.

## Stored data

The app stores one internal JSON file containing only the synthetic decor IDs and their grid coordinates. It does not store caretaker movement, print drafts, sharing history, user text, or external identifiers. Android backup and device-transfer rules exclude the app's private data.

The save reader validates the entire file before using it. A recognized synthetic schema 1 save is migrated to schema 2. Unreadable bytes, unknown item IDs, and files from a future schema are preserved and block ordinary saves so recovery does not silently destroy them. The recovery screen offers retry or an explicit reset; reset replaces the file with the initial synthetic layout.

## Sharing

Sharing happens only after the user presses **Share PNG**. The app renders the current print design into a new PNG under its private cache directory and opens the Android chooser. The intent contains the generated image only. A non-exported `FileProvider` grants the chosen recipient read access to the `cache/prints` subtree; it cannot grant access to the app's persistent files directory.

Recent generated PNGs may remain in app cache until the app's bounded cleanup runs, Android clears cache, or the user clears app storage. Once another app receives the image, that app's storage, network, and retention rules apply. Print marks otherwise live only in memory and disappear when the process ends.

## Accessibility and privacy controls

The scene has a concise description, while movement, arrangement, recovery, print cells, and share actions use native controls or explicit semantic labels. Directional buttons, tap-to-place, and labeled nudge controls provide alternatives to interpreting the pixel scene. Status and placement messages are polite live regions, and primary touch targets use a 48 dp minimum.

The Canvas scene does not expose every decorative pixel or object as a separate accessibility node. Functional placement remains available through the labeled item chips, coordinate readout, nudge buttons, and place action. Large text can require scrolling, and the app is intentionally portrait-oriented. These limits should be checked with TalkBack and the largest supported font setting on the release build.

