# Smart Aim 1.3.0 (Minecraft 26.3)

Client-side Fabric assist for your own projectiles. It retains the 1.2.3 target rules, categories, per-entity overrides, presets, Mod Menu pages, commands, config migration, projectile list, and fishing bobber behavior. It does not add melee/PvP, modify damage, teleport projectiles, or replace vanilla collision, enchantment, effect, or kill-credit handling. Velocity is still synchronized through the existing vanilla path.

> **Baseline provenance:** no Smart Aim source project or source archive was present in the connected GitHub account or the `mod minecraft` Drive folder. I therefore started from the Drive file `smartaim-1.2.3.jar`, decompiled its classes with Vineflower, and used that as the 1.2.3 baseline. The files in `src/main/java` are decompiled baseline sources, not the author's original source checkout. The MIT license from the jar is retained.

## Configuration

The 1.2.3 configuration surface is retained; the tracking rows are new. `smartaim.json` is created under Fabric's config directory.

| Config key | Default | Validated range / meaning |
|---|---:|---|
| `enabled` | `true` | Global assist switch. |
| `range` | `24` | 1–64 blocks; legacy cone-search range. |
| `maxTurnDegrees` | `10` | 0–45 degrees per tick, before the existing speed-scaled cap. |
| `coneAngle` | `20` | 1–89 degrees. |
| `leadTarget` | `true` | Predict target motion. |
| `debugParticles` | `false` | Draw target markers for debugging. |
| `categories.hostile` | `true` | Hostile mobs. |
| `categories.neutral` | `false` | Neutral mobs. |
| `categories.friendly` | `false` | Friendly mob types. |
| `categories.tamedPets` | `false` | Tamed/owned pets. |
| `entityOverrides` | `{}` | Per-entity `allow`/`deny` actions; always-excluded player/non-mob rules still apply. |
| `projectiles` | projectile defaults | Per-projectile enablement; absent entries use the 1.2.3 defaults. |
| `configVersion` | `3` | Updated automatically during load. |

## Persistent target tracking

Persistent tracking is a new opt-in setting and defaults to **off**. With it off, target acquisition still uses the 1.2.3 forward cone search, the same lead prediction, drag/gravity-aware aiming, and capped turn math. With it on, the first target selected by that normal scan is locked by projectile UUID and target UUID. The registry stores UUIDs and scalar snapshots only; it does not retain entity instances. A valid lock skips subsequent cone scans and keeps steering toward the same entity, using the same capped-turn and ballistic/lead calculation.

A lock is released and the usual cone search resumes when the target is dead, removed, unloaded or not in the projectile's dimension; target rules or overrides no longer allow it; a solid block blocks line of sight; it exceeds `trackingRange`; the angle from the projectile's heading exceeds `trackingMaxAngle`; distance grows for `trackingGiveUpTicks`; its position changes by more than `trackingTeleportDistance` in one tick; `trackingMaxTicks` is exceeded; or the projectile is stuck/in ground, returning toward its owner as a trident, hooked as a fishing bobber, or too slow. Removing a projectile also clears its UUID entry. An absent target from the server-level UUID lookup is treated as unavailable, including unloaded and cross-dimension targets.

| Config key | Default | Validated range | Description |
|---|---:|---:|---|
| `trackingEnabled` | `false` | boolean | Enables persistent locking. |
| `trackingRange` | `32` | 1–64 blocks | Maximum projectile-to-target distance while locked. |
| `trackingMaxTicks` | `100` | 1–400 ticks | Maximum lock duration (the menu shows seconds; 20 ticks = 1 second). |
| `trackingMaxAngle` | `100` | 1–170 degrees | Release when the target moves behind the allowed turn arc. |
| `trackingGiveUpTicks` | `15` | 1–200 ticks | Consecutive ticks of increasing target distance before release. |
| `trackingTeleportDistance` | `6` | 0.1–64 blocks | Release threshold for target movement in one tick. |

All new keys are optional when reading older 1.2.x `smartaim.json` files; missing keys use the defaults above. Existing settings, categories, projectile flags, and overrides continue to be normalized and clamped as before. Config version is advanced to 3.

The General menu page has the tracking switch plus sliders for range, maximum duration (seconds), and angle. Give-up and teleport thresholds are config/command-only. Presets set tracking explicitly: **Mild off**, **Normal off**, **Strong on**.

## Commands

- `/smartaim status` — current assist, aim, target, projectile, and tracking settings.
- `/smartaim tracking on|off` — enable or disable persistent tracking.
- `/smartaim set trackingEnabled true|false`
- `/smartaim set trackingRange <1..64>`
- `/smartaim set trackingMaxTicks <1..400>`
- `/smartaim set trackingMaxAngle <1..170>`
- `/smartaim set trackingGiveUpTicks <1..200>`
- `/smartaim set trackingTeleportDistance <0.1..64>`

The existing `/smartaim set` keys and target/category/projectile commands remain available.

## Build and tests

Requires JDK 25. Run `./gradlew build` from this directory. The build uses Minecraft 26.3, Fabric Loader 0.20.1, Fabric API 0.165.2+26.3, Mod Menu 21.0.0 (compile-only), and Gradle 9.8.2. `build` runs the unit tests and creates `build/distributions/smartaim-1.3.0-src.zip` containing source and project files without build output. GitHub Actions runs the same build/test task on pushes and pull requests and uploads the mod jar and source archive as artifacts.

Unit tests cover each lock-release condition, lock identity/history and registry cleanup, plus 1.2.x config migration, new defaults and clamping. These unit tests do not substitute for an in-game integration test. **This sandbox build has not passed:** `./gradlew test` could not download the Gradle 9.8.2 distribution because the network handshake to `services.gradle.org` was blocked. Consequently, the GitHub Actions workflow has not run and the source has not been compiler-verified here.

## Requested gameplay measurements and verification limits

No Minecraft client, headless server, or game-test runner was available here, so **none of the requested gameplay trials were run**. No hit counts or tick/distance measurements below are invented:

1. **Enderman:** teleport-survival outcome and observed tracking duration: not measured. The configured lock cap is **100 ticks** (5 seconds), but the normal teleport threshold is **6 blocks per tick**, so an enderman teleport beyond that releases the lock and causes a fresh cone scan rather than preserving a continuous lock. Whether it reaches the full cap was not tested. The exact Minecraft 26.3 `Enderman` damage implementation was not available in this workspace; arrow damage/immunity is therefore unverified here.
2. **Bat:** 20-shot/throw hit counts with tracking off and on: not measured (0 trial runs in each condition).
3. **Fish/squid and water:** whether tracking continues after entering water and the ending point: not tested. Projectile slowing in water and real collision ordering need client/server verification.
4. **Unreachable/evasive target:** configured maximum lock distance is **32 blocks** and duration cap **100 ticks** (5 seconds); the greatest observed distance/duration and actual release reason are not measured.
5. **Circling:** no in-game orbit trial was run. The angle, increasing-distance, range and duration guards are implemented, but practical orbit prevention needs verification at Minecraft 26.3's live projectile speeds/turn limits.

Also not verified in a real client: mixin application against the exact 26.3 runtime, Mod Menu layout at all resolutions, commands in multiplayer permission contexts, client velocity synchronization under server latency, villager/tamed-wolf exclusions under each config/override combination, and the full set of named projectile variants. Review the build and run the checklist in-game before distributing as a tested release.

## Changelog

### 1.3.0
- Added opt-in persistent per-projectile target locks with UUID-only state, lock validation, cleanup, and fallback to cone search on release.
- Added tracking settings, migration defaults and bounds, menu controls, `/smartaim tracking`, `/smartaim set` support, status output, and preset behavior.
- Added unit tests for lock rules/cleanup and old-config migration.
- Gameplay measurements and real-client verification remain outstanding as stated above.

### 1.2.3 baseline
- Retained the original target categories and exclusions, per-entity overrides, projectile toggles, target acquisition cone, capped turns, prediction, config recovery/migration, presets, menu, commands, and bobber logic.
