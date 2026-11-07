# AutoGuard 1.0.2 verification notes

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
  lets it through.
- **OwnableEntity (26.3)**: `getOwnerUUID()` returns `@Nullable UUID`; `TamableAnimal` implements it.
  AutoGuard's threat detector skips player-owned pets (tamed wolves, cats, etc.) via this API rather
  than relying on `isAlliedTo` (which only works when both sides have teams).
- **Mob.isAggressive() (26.3)**: client-visible via entity data flags; no `ServerLevel` needed.
- **Creeper.getSwell(float)**: removed in 26.3; no client-visible fuse stage remains. Creeper reaction
  relies on proximity + approach/facing and is off by default.
- **EditBox.setFilter**: removed in 26.3; the config screen omits it and falls back to previous values
  on parse errors.

## Not verified

- No Mojang 26.3 client/server jar or decompiled Minecraft source was available in this environment.
  Therefore this project has **not** verified the vanilla implementation of shield block angle,
  raise delay (5 ticks is the commonly cited value but not source-confirmed here), which damage
  types/projectiles are blocked, shield durability loss, knockback, explosion blocking, shield-disable
  cooldown, or exact hand-use/release behavior.
- Whether neutral-mob anger target state is synchronized to a remote client was not verified.
- The mod was not compiled, launched, or tested with Java locally. Java is absent in the workspace;
  Gradle could not start locally. The GitHub Actions workflow is the build check.
- In-game behaviour (shield raise/lower timing, attack priority on mobile, duty-cycle gaps) has NOT
  been tested. These are design fixes based on root-cause analysis, not runtime-verified results.

## Conservative implementation choices / assumptions

- Creeper reaction defaults to **off** because shield mitigation of a creeper blast was not verified.
- Shield raise and release use `MultiPlayerGameMode.useItem(player, hand)` and `releaseUsingItem(player)`
  through the explicit `InteractionHand`. Exact vanilla 26.3 packet/hand semantics require runtime
  confirmation.
- The Mixin `@Redirect` targets `MultiPlayerGameMode.releaseUsingItem` as invoked from
  `Minecraft.handleKeybinds`. If the declared type of `Minecraft.gameMode` differs in the actual 26.3
  build, compilation will fail and the target must be corrected.
- The duty-cycle defaults (max hold 0.8s, cooldown 0.6s, hold after threat gone 0.2s, attack suppress
  0.4s) are heuristic starting points, not verified values. Adjust after in-game testing.
- There is no compile or in-game pass claim until CI passes. Resolve any 26.3 symbol/API mismatches
  and then run the README checklist before distributing a jar.
