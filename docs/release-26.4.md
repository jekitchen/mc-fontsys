# Minecraft 26.4: release-day runbook

How Fontsys moves to Minecraft 26.4 (PRD D61). The goal is Fontsys 1.3.0 on the stores soon after Mojang releases 26.4, and not before. [RELEASING.md](RELEASING.md) covers the release itself; this page covers getting there.

## Where things stand (2026-11-15)

| | Version | Source |
|---|---|---|
| Minecraft | `26.4-snapshot-8` (2026-11-10); no pre-release yet | Mojang version manifest |
| Fabric Loader | `0.20.1` (unchanged) | meta.fabricmc.net |
| Fabric API | `0.166.2+26.4`, built for `26.4-snapshot-8` | Fabric Maven; its `gradle.properties` on GitHub |
| Loom | `1.19.4` (unchanged; 1.20.0 is still alpha) | Fabric Maven |
| Mod Menu | `22.0.0-alpha.4` | Modrinth |
| NeoForge | none for 26.4; NeoForge started 26.2 and 26.3 at their release (`26.x.0.0-beta`) | NeoForged Maven |
| Sodium, Iris | none for 26.4 | Modrinth |

`scripts/next-minecraft.sh check` prints the current list. `bump` writes the newest builds into `next_*` in `gradle.properties`.

**The 26.3 jar doesn't start on 26.4.** Its mixins all apply (every target is unchanged), but the game crashes building its first font: `NoClassDefFoundError: net/minecraft/client/renderer/texture/AbstractTexture`. Compiling against 26.4 gives 37 errors in 10 files. 26.4 reworked GUI rendering (no mouse or partial-tick arguments, `GuiPipeline`) and textures (`AbstractTexture` removed). There's no API common to both versions, so one jar compiled against 26.3 can't cover 26.4. **The owner picks how to proceed** (PRD D61):

- **A.** One multi-version jar: per-version classes compiled against each Minecraft, picked at run time.
- **B.** Two jars from the same code, `+26.3` and `+26.4` (D32's fallback).
- **C.** 1.3.0 for 26.4 only; 26.3 stays on 1.2.x (D15). This is the recommendation.

## Tools

| Command | What it does |
|---|---|
| `scripts/next-minecraft.sh check` | Newest Minecraft 26.4, Fabric API (and the build it targets), Mod Menu and NeoForge, compared with `gradle.properties`. Exit 0 = up to date, 3 = newer builds out. |
| `scripts/next-minecraft.sh bump` | The same, then updates `next_minecraft_version`, `next_fabric_api_version`, `next_modmenu_version`. |
| `scripts/next-minecraft.sh compile` | Compiles the mod and the game tests against the next version (the port's to-do list). |
| `./gradlew runNextClientGameTest` | Boots the built jar on a production-style client of `next_minecraft_version` with its Fabric API and runs the full game-test suite. The jar is a test copy whose `fabric.mod.json` declares `next_fabric_minecraft_range`, so Fabric Loader accepts it; the classes are the built ones. Results are in `build/run/nextClientGameTest/`. |
| `next-minecraft` workflow | The three checks above on CI: weekly, on demand (Actions → next-minecraft → Run workflow) and on pushes to `mc-26.4`. Not a required check. |

## Until 26.4 is released

1. After each snapshot or pre-release: `scripts/next-minecraft.sh bump`, then `compile` and `./gradlew runNextClientGameTest`. Note any new breakage in D61.
2. Port the code once the owner has chosen A, B or C and the 1.3.0 feature PRs (D59 toggle key, D60 classic shadow) have merged; they change the same screens. The port's to-do list, from snapshot 8:
   - Screens (`FontConfigScreen`, `AdvancedFontScreen`, `KeepOrRevertScreen`, `UnappliedChangesScreen`): `extractRenderState(graphics)` without mouse or partial ticks; the mouse is `graphics.mouseX()` / `mouseY()`, partial ticks `graphics.partialTicks()`.
   - The font list: `extractWidgetRenderState(graphics)`; entries implement `extractContent(GuiGraphicsExtractor, boolean hovered)`.
   - `AbstractWidget.visible` is protected: `setVisible(...)`.
   - `RenderPipelines.GUI_TEXTURED` → `GuiPipeline.TEXTURED` (`PreviewPanel`); `RenderPipeline` → `GuiPipeline` in `MipmappedSheetGlyph`.
   - `MipmappedGlyphPage(s)`: `AbstractTexture` and `Dumpable` are gone. Hold a `TextureResources` and register it with `textureManager.registerProvider(id, new ExternalTextureProvider(resources))`, released in `close()`, as 26.4's `FontTexture` does.
   - Uploads: `nativeImage.writeToGpuTexture(encoder, texture, mip, layer, x, y)` instead of `CommandEncoder.writeToTexture(texture, image, ...)` (`ColorAtlasGlyph`, `MipmappedGlyphPage`).
   - `Mth.clamp(double, double, double)` is gone: `Math.clamp` (`ValueSlider`).
   - Then the game tests in `src/gametest`, which don't compile yet because the mod doesn't.
3. The draft PR on branch `mc-26.4` carries the release-day metadata. Keep it rebased on `main`.

## Release day

Start when `scripts/next-minecraft.sh check` shows Minecraft `26.4` (type `release`) and a Fabric API built for `26.4`. NeoForge usually follows within a day or two as `26.4.0.0-beta` and later builds. Check https://maven.neoforged.net and the NeoForge 26.4 MDK (NeoForgeMDKs on GitHub) for its ModDevGradle version.

### 1. Versions

On the `mc-26.4` branch:

```sh
scripts/next-minecraft.sh bump        # next_* -> 26.4 and its Fabric API and Mod Menu
```

Then set in `gradle.properties` (all options):

- `neoforge_version`, `moddev_version`: from NeoForged's Maven and the 26.4 MDK.
- `sodium_version`, `iris_version`, and the NeoForge Sodium and Iris lines: 26.4 builds from Modrinth if they exist. If they don't, the `-PwithSodiumIris` runs can't pass yet; ask the owner whether to release without them, as for 26.4 snapshots.
- `quilt_*`: from https://meta.quiltmc.org/v3/versions/loader/26.4 if Quilt has a 26.4 profile.
- Record every version and the date in PRD D61 (or a new decision row), as D7 does.

Then, depending on the owner's choice:

| | A: one jar | B: two jars | C: 26.4 only (recommended) |
|---|---|---|---|
| `minecraft_version` | `26.3` (the 26.3 build); 26.4 classes built against `26.4` | one build per version | `26.4` |
| `fabric_api_version`, `modmenu_version` | unchanged for the 26.3 build | per build | 26.4's |
| `fabric_minecraft_range` | `>=26.3 <26.5-` | `~26.3` and `~26.4` | `~26.4` |
| `neoforge_minecraft_range` | `[26.3,26.5)` | `[26.3,26.4)` and `[26.4,26.5)` | `[26.4,26.5)` |
| `minecraft_label` | `26.3-26.4` | `26.3` and `26.4` | `26.4` |
| `publish_game_versions` | `26.3,26.4` | `26.3` and `26.4` | `26.4` |

The draft PR already holds column A. For C, change those lines as in column C. In C, `next_*` and `runNextClientGameTest` are redundant once `minecraft_version=26.4`; leave them pointing at 26.4 until 26.5 snapshots start.

### 2. Suites

All must pass on 26.4 (PRD D32: a version enters the jar's range only after both game-test suites pass on it):

```sh
./gradlew build
./gradlew runClientGameTest
./gradlew runClientGameTest -PwithSodiumIris          # needs Sodium and Iris for 26.4
./gradlew runProductionClientGameTest
./gradlew runNextClientGameTest                       # A only: the jar on a 26.4 client
./gradlew :neoforge:neoforgeSmokeTest
./gradlew :neoforge:neoforgeSmokeTest -PwithSodium
./gradlew runQuiltClientGameTest                      # if Quilt has a 26.4 profile
scripts/linux-vm.sh test
```

In CI, the draft PR's `client-game-test (production-jar-26.4)` job is non-blocking while 26.4 is a snapshot. On release day remove its `continue-on-error`, and the owner adds it to the `main` ruleset's required checks (A only; in C the regular jobs already run on 26.4).

### 3. Hand off to the release

1. Mark the `mc-26.4` PR ready for review, with the suite results. The owner runs the manual checks (RELEASING.md, "Before the release") on 26.4 and merges it.
2. Release PR, as in RELEASING.md: `mod_version=1.3.0`, the `CHANGELOG.md` heading `## [1.3.0] - <date>`, PRD status and change log. In C, note in the store description that 26.3 players stay on 1.2.0.
3. After the merge, tag `v1.3.0`. The jar is `build/libs/fontsys-1.3.0+<minecraft_label>.jar`; the `release` and `publish` workflows take the label and game versions from `gradle.properties`.
