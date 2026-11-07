# AutoGuard 1.0.2

Client-side Fabric mod for Minecraft Java 26.3. AutoGuard attempts to raise a held vanilla shield shortly before a nearby, front-facing threat reaches the player, then releases it after the configured hold period. It uses the client game mode's ordinary `useItem(player, InteractionHand)` interaction and never changes inventory slots or swaps hands.

> **Verification status:** This project was authored in an environment without Java/Gradle and without access to Mojang's Minecraft artifacts. It has not been compiled or run here, and the vanilla 26.3 implementation of shield blocking, damage types, hand use, cooldowns, and sync behavior could not be inspected. Treat it as an unverified development build until the checks in the final report are completed. In particular, threat-to-shield-blockability and exact API compatibility need validation against the actual 26.3 client.

## Features

- Client-only; targets Fabric Loader 0.20.1+, Fabric API 0.165.2+26.3, Java 25, and Minecraft 26.3.
- Defaults to single-player/integrated worlds. Remote multiplayer is opt-in.
- Supports shields in either hand and chooses the off-hand if both hands contain shields.
- Bounded nearby-entity scan for hostiles, angry neutral mobs, optional creepers (off by default until 26.3 explosion blocking is verified), and inbound projectiles. It uses visible movement/facing cues rather than a mob's AI target.
- Three presets: Cautious, Normal, and Aggressive.
- Shield durability threshold, HUD, key binding (unbound by default), client commands, and config screen through optional Mod Menu (no Cloth Config dependency).
- `autoguard.json` includes a schema version; missing, invalid, and out-of-range values fall back to safe defaults.
- English and Thai language resources.

## Commands

- `/autoguard on`
- `/autoguard off`
- `/autoguard toggle`
- `/autoguard status`
- `/autoguard preset cautious|normal|aggressive`
- `/autoguard reload`

The key binding is listed as **Toggle AutoGuard** and is unbound initially. Use `/autoguard` in chat for control on launchers without a convenient keyboard toggle.

## Settings

Open the configuration from Mod Menu when installed. The screen provides toggles and number fields (not sliders). Advanced values and per-entity ID allow/deny lists are stored in `.minecraft/config/autoguard.json`.

- Presets set early margin, front arc, and hold time.
- Numeric values include early margin (blocks), front arc (degrees), hold after threat gone (seconds), max continuous hold (seconds), cooldown after release (seconds), attack suppress (seconds), projectile arrival limit (ticks), and minimum remaining shield durability.
- Duty cycle: the shield is not held indefinitely. It is released after the threat is gone (plus a short hold), forced down after max continuous hold, and suppressed from re-raising after any attack or during cooldown.
- `alwaysReactTo` and `neverReactTo` accept registry IDs such as `minecraft:zombie`. The never list takes precedence.
- Remote servers are disabled by default. Enable `allowRemoteServers` only if you accept the server-specific consequences.

## How it works and limitations

The detector runs once at the end of each client tick and queries entities in a small bounding box around the player. Projectile arrival is estimated from current position and velocity; melee prediction uses reach plus the preset margin and observable movement, facing, or swing activity. The controller tracks whether AutoGuard itself initiated shield use and only attempts to release its own use. Player attack/block callbacks and the attack key yield priority to attacks/mining. Existing item use is left alone by default.

The controller does not apply its own damage reduction and sends no custom packets: it requests the ordinary vanilla use-item action. Vanilla use of one item may pause the other hand's use animation/action while the shield is raised; the other-hand stack is not moved or swapped. Detection is heuristic and cannot anticipate every modded or server-side attack. Exact vanilla blockability, shield raise delay, axe-disable timing, and behavior with modded entities have not been source-verified or runtime-tested in this environment.

Some servers may regard automated blocking or other client assistance as an unfair advantage and prohibit it. Check server rules before enabling remote-server use.

## Build

Requires JDK 25 and Gradle 9.8.2 (Fabric Loom 1.19.3 for unobfuscated Minecraft 26.3):

```sh
./gradlew clean build
```

The mod jar and source jar are written to `build/libs/`. A GitHub Actions workflow in `.github/workflows/build.yml` builds and uploads both jars on pushes and pull requests.

## Manual test checklist

Test in a disposable single-player world first. This is a checklist, not a claim that these cases have passed:

- Zombie approaches from the front and from behind.
- Skeleton fires arrows; also test a missed arrow and, if applicable, piercing arrows.
- Creeper begins swelling in front; check that the shield actually blocks the explosion in 26.3 before relying on this behavior.
- Hostile enemy hits with an axe; check shield-disable cooldown and recovery.
- Eat or drink while an enemy approaches.
- Attack a nearby enemy while AutoGuard wants to block; verify the attack happens and blocking resumes only afterward if a threat remains.
- Check the hand matrix below; verify item identity, slot, and action are unchanged after each case.

### Hand matrix

For each main-hand item, test with the shield in the off-hand. Repeat relevant cases with the shield in the main hand and an item in the off-hand. Also repeat with the player main arm set to left and right; the implementation uses `MAIN_HAND` and `OFF_HAND`, never arm-side assumptions.

| Main-hand item | Check |
| --- | --- |
| Sword | Attack takes priority; shield resumes if danger remains |
| Axe | Attack takes priority |
| Pickaxe | Mining is not blocked by AutoGuard |
| Block | Placement input lowers the off-hand shield |
| Food | Eating is not interrupted by default |
| Bow | Drawing is not interrupted by default |
| Crossbow | Drawing/loading is not interrupted by default |
| Trident | Charging/throwing is not interrupted by default |
| Bucket | Use/placement remains available |
| Empty hand | Off-hand shield raises through vanilla interaction |
| Shield in main hand + item in off-hand | Check correct `MAIN_HAND` use and preserve the off-hand item |

## Development caveat

This repository contains source and a CI build definition, but an installable jar and a successful CI run are not included in this checkout. The source should be compiled against the actual 26.3 Fabric toolchain and exercised in-game before distribution. See [`VERIFICATION.md`](VERIFICATION.md) for exactly what 26.3 APIs were inspected and which vanilla mechanics remain unverified.
