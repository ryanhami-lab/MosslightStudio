# Mosslight Studio visual design

Mosslight Studio presents a small evening conservatory as a polished 240×256 pixel miniature. The room uses a deep ink-teal frame, moss and mint glasshouse structure, clay and coral accents, and warm ivory/amber task lights. A stepped glazed roof, repeated mullions, stocked potting bench, and individually highlighted floor tiles give the fixed-size room architectural rhythm and depth without requiring a large asset set.

The player avatar is a nonhuman wheeled lamp-bot. Its teal body, amber lamp cap, small status window, side bumpers, and animated wheel highlight remain readable at native resolution. Three wheel states provide a quiet movement cycle. The six decorations have deliberately different silhouettes:

- a loose, asymmetric fern in a clay pot;
- a clustered coral bloom in an ivory pot;
- an upright branching cactus;
- a tall warm floor lantern;
- a compact wooden work stool; and
- a glazed tabletop terrarium with an enclosed plant.

## Rendering and geometry

The renderer composes the scene at the logical room size and presents it with nearest-neighbor bitmap filtering disabled. Whole-number scaling is used whenever the host is at least the logical size; smaller hosts fit the logical frame without cropping. The fixed backdrop is cached once, while the reusable logical frame buffer receives the placement grid, animated motes, depth-sorted objects, preview treatment, and foreground glints.

Every decoration is authored in a 32×32 atlas slot and anchored by its bottom-center pixel to the bottom-center of its exact 16×16 `RoomRules` footprint. Art may overhang the footprint, but the object shadow, placement highlight, and collision inset remain aligned to the logical cell. The lamp-bot is anchored at its player-foot coordinate and participates in the same bottom-edge depth ordering. A moving decoration is omitted from its occupied cell while its preview is rendered, so the preview never shows two copies.

The placement overlay covers columns 2–12 and rows 7–13. Preview fill and corner ticks use the exact tile rectangle; pale mint indicates an available placement and coral indicates rejection. Shadows form a floor contact pass, sprites are ordered by their anchor depth, and greenhouse reflections and edge leaves form a restrained foreground pass.

`StudioRenderer.release()` owns and idempotently recycles the generated atlas, cached backdrop, and logical frame buffer. The renderer does not retain Android activities, views, resources, or external bitmaps.

## Asset provenance

All visual assets in this design are newly authored as native Android drawing code and vector XML. `PixelAtlas.kt` generates one in-memory bitmap atlas from integer rectangle instructions. `StudioRenderer.kt` constructs the architecture, floor, furniture, lighting details, shadows, and animation with Canvas primitives. The launcher mark is defined by the project’s vector XML.

No raster image, photograph, icon pack, font file, sampled artwork, external reference image, network asset, or third-party visual service was imported or used to construct these visuals. The project uses platform text rendering and contains no music or audio asset. The editable Kotlin and XML files are the visual source of record; emulator screenshots document rendered output but are not source assets.
