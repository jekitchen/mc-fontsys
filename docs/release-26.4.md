# Minecraft 26.4: release-day runbook

How Fontsys moves to Minecraft 26.4 (PRD D61). [RELEASING.md](RELEASING.md) covers a release itself; this page covers getting there.

## The plan (owner, 2026-11-15)

- **1.3.0 ships for 26.3** with the toggle key (D59) and classic shadow (D60), once they merge. It doesn't wait for 26.4.
- **1.4.0 is the 26.4 port, for 26.4 only.** It's prepared now on branch `mc-26.4-port` (a draft PR) against the 26.4 snapshots, so release day is the version bump and the tests.
  - Fabric first.
  - NeoForge follows as **1.4.1** once NeoForge publishes 26.4 builds (see "NeoForge follow-up" below).
  - 26.3 stays on 1.3.x; D15 (latest release only) applies.
- **One jar per Minecraft version from 26.4 on**, built from the same code at different points in history (replaces D32).
  - `main` moves to 26.4 when the port merges, on release day.
  - Just before that merge, cut `mc/26.3` from main's last 26.3 commit, for critical 1.3.x fixes only.

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

The 26.3 jar doesn't start on 26.4: 26.4 reworked GUI rendering and textures. The mixins are fine. Details are in D61.

**Vulkan:** Fontsys 1.2.0 crashes on Vulkan (`VK_ERROR_DEVICE_LOST`), on 26.3 and on 26.4. That's an existing bug, tracked and fixed separately in D62, and it blocks both 1.3.0 and 1.4.0. It matters more on 26.4, whose default setting picks Vulkan on about half of launches.

## Tools

| Command | What it does |
|---|---|
| `scripts/next-minecraft.sh check` | Newest Minecraft 26.4, Fabric API (and the build it targets), Mod Menu and NeoForge, compared with `gradle.properties`. Exit 0 = up to date, 3 = newer builds out. |
| `scripts/next-minecraft.sh bump` | The same, then updates `next_minecraft_version`, `next_fabric_api_version`, `next_modmenu_version`. |
| `scripts/next-minecraft.sh adopt` | Builds for the next version. It copies `next_*` into `minecraft_version`, `fabric_api_version` and `modmenu_version`, and sets `minecraft_label`, `publish_game_versions` and both ranges to it: Fabric `~26.4-` for a snapshot, `~26.4` for the release, and NeoForge `[26.4,26.5)`. **Port branch only.** |
| `scripts/next-minecraft.sh compile` | Compiles the mod and the game tests against the next version. |
| `-PgraphicsBackend=opengl` / `=vulkan` | Pins the graphics backend for the client runs. Without it, 26.4 snapshots pick at random on each launch. |
| `./gradlew runNextClientGameTest` | Boots the built jar on a production-style client of `next_minecraft_version` with its Fabric API and runs the full game-test suite. |
| `next-minecraft` workflow | `check`, `compile` and `runNextClientGameTest` on CI: weekly, on demand, and on pushes to `mc-26.4-port`. Not a required check. |

## Until 26.4 is released

1. **1.3.0 for 26.3** comes from `main` as usual (RELEASING.md), once D59 and D60 merge.
2. **After each 26.4 snapshot or pre-release**, on `mc-26.4-port`:
   ```sh
   scripts/next-minecraft.sh bump && scripts/next-minecraft.sh adopt
   ./gradlew build
   ./gradlew runClientGameTest -PgraphicsBackend=opengl
   ./gradlew runProductionClientGameTest -PgraphicsBackend=opengl
   ./gradlew runProductionClientGameTest -PgraphicsBackend=vulkan
   ```
   Fix what the new snapshot breaks, verifying against `./gradlew genSources`. Record the versions in D61.
3. **Rebase the port** onto `main` whenever D59, D60 or anything else touching the screens merges. The port keeps its screen changes mechanical (render signatures only) so these rebases stay small.

## Release day: 1.4.0 for 26.4 (Fabric)

Start when `scripts/next-minecraft.sh check` shows Minecraft `26.4` (type `release`) and a Fabric API built for `26.4`. 1.3.0 should already be out.

1. **Versions**, on `mc-26.4-port`:
   ```sh
   scripts/next-minecraft.sh bump && scripts/next-minecraft.sh adopt
   ```
   - `adopt` sets `fabric_minecraft_range=~26.4`, `neoforge_minecraft_range=[26.4,26.5)`, `minecraft_label=26.4` and `publish_game_versions=26.4`.
   - Leave `neoforge_enabled=false` unless NeoForge already has a 26.4 build (then see the NeoForge follow-up, below, and ship both in 1.4.0).
   - Sodium and Iris for 26.4: if they exist, set `sodium_version` and `iris_version`; if not, `-PwithSodiumIris` stays skipped. Ask the owner whether to release without that check.
   - Quilt: try `-PquiltLoaderVersion=<newest>` if Quilt publishes a 26.4 profile; not listed either way (D57).
   - Record every version and the date in D61.
2. **Suites**, all on 26.4:
   ```sh
   ./gradlew build
   ./gradlew runClientGameTest -PgraphicsBackend=opengl
   ./gradlew runClientGameTest -PgraphicsBackend=vulkan
   ./gradlew runProductionClientGameTest -PgraphicsBackend=opengl
   ./gradlew runProductionClientGameTest -PgraphicsBackend=vulkan
   ./gradlew runClientGameTest -PwithSodiumIris -PgraphicsBackend=opengl   # only if Sodium and Iris exist for 26.4
   scripts/linux-vm.sh test
   ```
   - CI runs the same on the port PR, on OpenGL, plus a non-blocking `client-game-test (production-jar-vulkan)` job. Once the Vulkan fix (D62) lands, make that job blocking: drop its `experimental` flag, and the owner adds it to the required checks.
   - The NeoForge jobs report "skipped: Fabric-only build".
3. **Cut `mc/26.3`** from main's last 26.3 commit, just before the port merges:
   ```sh
   git fetch origin
   git push origin origin/main:refs/heads/mc/26.3
   ```
4. **Merge the port.** Mark the `mc-26.4-port` PR ready with the suite results. The owner runs the manual checks (RELEASING.md, "Before the release") on 26.4 and merges it. `main` is now 26.4.
5. **Release 1.4.0** from `main` as in RELEASING.md: `mod_version=1.4.0`, the `CHANGELOG.md` heading `## [1.4.0] - <date>`, PRD status and change log, then tag `v1.4.0`.
   - The jar is `fontsys-1.4.0+26.4.jar`.
   - `publish` lists it for the loaders whose metadata the jar carries (Fabric only) and the game versions in `publish_game_versions` (26.4).
   - In the store description, say that 26.3 players stay on 1.3.x and that NeoForge for 26.4 follows.

## Fabric-only builds

While NeoForge has no build for the target Minecraft, `gradle.properties` has `neoforge_enabled=false`:

- `settings.gradle` leaves out the `neoforge` subproject, so nothing compiles against NeoForge.
- The jar has no `neoforge` package and no `META-INF/neoforge.mods.toml`.
- `checkSharedBytecode` isn't registered.
- `checkModJar` checks the Fabric half, and fails if any NeoForge part is in the jar.
- CI's NeoForge jobs check the setting first and skip their steps, so the required check names stay green.
- `publish` reads the loaders from the jar.

`-Pneoforge_enabled=true` turns NeoForge back on for one build.

## NeoForge follow-up: 1.4.1

Once NeoForge publishes 26.4 builds (watch https://maven.neoforged.net or `scripts/next-minecraft.sh check`):

1. On a branch from `main`:
   - set `neoforge_enabled=true`;
   - set `neoforge_version` and `moddev_version` (from NeoForged's Maven and the NeoForge 26.4 MDK);
   - set the NeoForge Sodium line if Sodium has a 26.4 NeoForge build.
2. Check the shared mixins against NeoForge's patched 26.4 sources (`neoforge/build/moddev/artifacts/`), then run:
   ```sh
   ./gradlew build                                   # checkSharedBytecode and checkModJar for both loaders
   ./gradlew :neoforge:neoforgeSmokeTest
   ./gradlew :neoforge:neoforgeSmokeTest -PwithSodium  # if Sodium has a 26.4 NeoForge build
   ./gradlew runProductionClientGameTest
   ```
3. Release **1.4.1**: the one jar for both loaders again (D54), listed for Fabric and NeoForge. The changelog says "Adds NeoForge for Minecraft 26.4".

**Why a new version, not NeoForge added to 1.4.0:** the 1.4.0 jar has no NeoForge metadata, so it can't run on NeoForge. Adding NeoForge to 1.4.0 would mean a second, different file under the same version number. CurseForge files can't change once uploaded, and Modrinth would show two jars that differ for the same version. That breaks D54's "one file for both loaders" and confuses anyone comparing files. A new patch version keeps one file per version, and Fabric players just see a small update.

## 1.3.x fixes for 26.3

Critical fixes only, on `mc/26.3`:

1. Branch from `mc/26.3`, open the PR against `mc/26.3`, bump `mod_version` to `1.3.x`.
2. Tag the merge commit `v1.3.x`. The `release` and `publish` workflows run from the tag, so they work on any branch; the jar is `+26.3` and lists 26.3.
3. If the fix also applies to 26.4, port it to `main` in its own PR.
