# Architecture

Mosslight Studio is a single-activity Android app. Compose owns the screens and controls, while a custom Android `View` draws the interactive room with `Canvas`. The domain, movement, framing, print composition, and save codec are Kotlin code with no Android UI dependency.

## State and UI flow

`MainActivity` creates a retained `StudioViewModel`. The ViewModel loads the saved layout, owns the `StudioUi` flow, and retains one `StudioSession` across Activity recreation. Compose observes `StudioUi` and switches among the room, arrangement, print, and recovery surfaces.

`StudioSession` holds the live room layout and the caretaker's transient movement state. Opening an overlay neutralizes movement and blocks world updates. Arrangement input selects an item, previews a valid floor cell, and commits the new immutable layout only after the repository reports a successful write. A failed write leaves the session layout unchanged.

The room is rendered by `StudioGameView` and `StudioRenderer`:

- `StudioGameView` leases frame execution from the retained session with a unique token. Only one attached view can own the lease.
- The lease is active only while the view is attached, shown, resumed, focused, visible, and non-empty. Losing any of those conditions removes the frame callback, releases ownership, and clears movement input.
- `Choreographer` supplies frame times. `FixedStepAccumulator` converts them into bounded simulation steps so a long pause cannot create unbounded catch-up work.
- `StudioRenderer` draws generated pixel art into a 240 by 256 off-screen buffer and presents it with an integer-centered fit. Room mode shows the full buffer; arrangement mode shows a floor crop so placement cells remain usable beside the controls.
- The renderer and pointer conversion use the same framing model. Letterboxed space is outside the interactive room.

Compose provides buttons, status, recovery, and print controls around the Canvas scene. The renderer contains no persistence or Activity references. Its bitmaps belong to the attached view and are released when that view detaches.

## Core model

`StudioLayout` is an immutable snapshot keyed by the six synthetic decor items. `RoomRules` validates placement cells, prevents two items from sharing a cell, and projects those placements into the collision world used by movement and placement checks.

`PlayerMotion` performs bounded axis movement with collision checks. `ViewportTransform` and `StudioFraming` contain physical-to-logical coordinate conversion. `PrintDesign` is an immutable six-by-six mark grid with a transient, bounded undo history. `PrintPixels` deterministically turns that model into block-based botanical and astral art.

## Persistence

`StudioApplication` lazily creates one process-wide `StudioRepository`. The repository uses AndroidX typed DataStore with `OkioStorage`, a single I/O scope, and a mutex that orders load, store, migration, and reset operations.

The current JSON format is schema 2. The codec accepts the app's synthetic schema 1 and rewrites it as schema 2 during load. It validates UTF-8, JSON structure, IDs, integer coordinates, geometry, and uniqueness. Unreadable data, unknown IDs, and future schemas become blocked states that retain the original bytes. Ordinary writes cannot replace a blocked state; only the explicit recovery reset does so.

## Print export

Print drafts remain in ViewModel memory and are not part of the room save. Sharing renders only the generated print to an opaque PNG in the app's `cache/prints` directory. A non-exported `FileProvider` grants the selected recipient temporary read access through an `ACTION_SEND` chooser. The sharing intent does not target a package or expose the app's private files directory.

