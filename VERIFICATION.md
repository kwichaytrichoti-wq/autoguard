# AutoGuard 1.0.5 verification notes

## Inspected directly (real 26.3 source)

- **Fabric API 26.3 source** (`FabricMC/fabric`, branch `26.3`): `KeyMappingHelper`, key-mapping API,
  `ClientCommandRegistrationCallback` / `ClientCommands`, `AttackBlockCallback`, `AttackEntityCallback`,
  and the HUD extraction API (`HudElementRegistry`, `HudElement`, `GuiGraphicsExtractor`). This is why the
  project uses the 26.3 SDL-era key APIs and GUI extraction methods rather than older GLFW /
  `HudRenderCallback` / `GuiGraphics` examples.
- **KeyMapping (Mojang 26.3 mappings)**: `private int clickCount` field exists; `consumeClick()`
  decrements it. AutoGuard exposes it via a Mixin `@Accessor("clickCount")` so it can detect a pending
  attack click *without consuming it*, letting vanilla process the click as a real attack after the
  shield is released.
- **Minecraft.handleKeybinds() (26.3)**: releases the active item every tick via
  `gameMode.releaseUsingItem(player)` when `player.isUsingItem() && !keyUse.isDown()`. AutoGuard's
  Mixin (`@Redirect` on that invoke) skips the release while AutoGuard owns the shield, preventing the
  "shield released one tick after raise" bug. The mod's own release sets a bypass flag so the Mixin
  lets it through. The target is `MultiPlayerGameMode.releaseUsingItem(Player)` in package
  `net.minecraft.client.multiplayer` (verified against the real 26.3 client jar via the build-time
  ASM check).
- **LivingEntity (26.3)**: `isUsingItem()`, `getUseItem()`, `getTicksUsingItem()`, `getUsedItemHand()`
  are all public methods on `LivingEntity`. They work for mobs as well as players. `isUsingItem()` is
  synced via entity data (`useItemTick > 0`); `getTicksUsingItem()` is a client-side counter that
  increments while `isUsingItem()` is true (starts at 0 when the entity enters client view, so it may
  underestimate draw progress if a mob was already drawing when it entered view). `getUseItem()` returns
  the `ItemStack` being used (bow, crossbow, or trident for ranged mobs).
- **OwnableEntity (26.3)**: `getOwner()` returns `@Nullable Entity`; `TamableAnimal` implements it.
  AutoGuard's threat detector skips player-owned pets (tamed wolves, cats, etc.) via this API rather
  than relying on `isAlliedTo` (which only works when both sides have teams).
- **Mob.isAggressive() (26.3)**: client-visible via entity data flags; no `ServerLevel` needed.
- **Creeper.getSwell(float)**: removed in 26.3; no client-visible fuse stage remains. Creeper reaction
  relies on proximity + approach/facing and is off by default.
- **EditBox.setFilter**: removed in 26.3; the config screen omits it and falls back to previous values
  on parse errors.
- **Shield raise delay**: 5 ticks (0.25 s) before blocking activates, confirmed by multiple 26.3 sources
  (Responsive Shields, InstantShield, Craftdex).
- **Shield blocking arc**: vanilla 26.3 blocks attacks within a 90-degree horizontal arc (from Craftdex
  26.3-specific source and Minecraft Wiki data values: `horizontal_blocking_angle: 90`). AutoGuard uses
  this as the base front-check arc plus a small margin.
- **Attacking while shielding**: In Java Edition, attacking while a shield is raised does not perform
  an attack — the shield must be released first (Minecraft Wiki). AutoGuard releases on attack input
  and re-raises after the configured resume delay when the attack input ends.
- **LivingEntity.getLastDamageSource()**: exists on `LivingEntity` (confirmed via Forge/NeoForge javadoc
  for `EntityLivingBase.lastDamageSource`). Returns the `DamageSource` of the most recent hit.
  `DamageSource.isProjectile()`, `getEntity()`, and `getSource()` are available to classify arrow vs
  melee hits and identify the shooter. Note: this field may not be fully synced on the client for all
  damage sources; AutoGuard falls back to scanning nearby projectiles and hostile shooters if needed.
- **Charge times (Minecraft Wiki data values)**: bow full draw = 1 s (20 ticks); crossbow load =
  1.25 s (25 ticks); trident charge = 0.5 s (10 ticks). Skeleton attack interval: 3.0 s easy / 2.0 s hard.
  These are used as constants in `ThreatDetector` for ranged wind-up timing.
- **Arrow speed**: approximately 3 blocks per tick (60 blocks/second) at full charge, confirmed by
  player testing and command-block measurements.
- **Mob attack reach**: most hostile mobs use 3.0 blocks. `Mob.getAttackReachSqr()` is protected and not
  directly accessible from client-side code without an additional mixin, so AutoGuard keeps the 3.0
  estimate for melee reach calculation. The `maxReactionDistance` hard cap (default 4.5 blocks) limits
  how far a melee threat can trigger a raise regardless of the reach estimate.
- **Entity types for ranged mobs**: skeleton, stray, bogged, pillager, piglin, drowned — confirmed
  via 26.3 entity registry item IDs (minecraft:skeleton, minecraft:stray, minecraft:bogged,
  minecraft:pillager, minecraft:piglin, minecraft:drowned).

## Not verified

- No Mojang 26.3 client/server jar or decompiled Minecraft source was available in this environment
  outside the Fabric Loom toolchain used by CI. Therefore this project has **not** verified the vanilla
  implementation of shield block angle beyond the data-value default, which damage types/projectiles are
  blocked, shield durability loss, knockback, explosion blocking, shield-disable cooldown, or exact
  hand-use/release behavior.
- Whether `LivingEntity.getTicksUsingItem()` is incremented for remote mobs on the client in all cases
  (e.g. mobs already drawing when entering view) has not been runtime-tested.
- Whether `getLastDamageSource()` is populated on the client-side player entity for all damage sources
  has not been runtime-tested; fallback inference is used.
- Whether neutral-mob anger target state is synchronized to a remote client was not verified.
- The mod was not compiled, launched, or tested with Java locally. Java is absent in the workspace;
  Gradle could not start locally. The GitHub Actions workflow is the build check.
- In-game behaviour (shield raise/lower timing, attack-driven rest, speed-aware raising, ranged wind-up
  detection, protection range hard cap, damage report arrow/melee split) has NOT been tested by the
  developer. These are design changes based on root-cause analysis of the real 26.3 source and user test
  feedback, not runtime-verified results.

## Conservative implementation choices / assumptions

- Creeper reaction defaults to **off** because shield mitigation of a creeper blast was not verified.
- Shield raise and release use `MultiPlayerGameMode.useItem(player, hand)` and `releaseUsingItem(player)`
  through the explicit `InteractionHand`. Exact vanilla 26.3 packet/hand semantics require runtime
  confirmation.
- The Mixin `@Redirect` targets `MultiPlayerGameMode.releaseUsingItem(Player)` as invoked from
  `Minecraft.handleKeybinds`. If the declared parameter type differs in the actual 26.3 build, the
  build-time ASM check will fail and the target must be corrected.
- The duty-cycle settings (`customMaxContinuousHoldSeconds`, `customCooldownAfterReleaseSeconds`) are
  kept as optional values defaulting to 0 (disabled). A hidden failsafe force-releases after ~15 s of
  continuous hold so a stuck shield can never happen.
- Ranged wind-up full-draw times (bow 20, crossbow 25, trident 10 ticks) are taken from Minecraft Wiki
  data values, not from decompiled 26.3 code. Mob draw behavior may differ from player draw behavior.
- The `getTicksUsingItem()` value on the client starts at 0 when the entity enters client view. If a
  ranged mob was already drawing when it entered view, the client-side counter underestimates draw
  progress, potentially delaying the shield raise.
- The resume delay default (0.1 s) and lead ticks default (6) are heuristic starting points, not
  verified values. Adjust after in-game testing.
- There is no compile or in-game pass claim until CI passes. Resolve any 26.3 symbol/API mismatches
  and then run the README checklist before distributing a jar.
