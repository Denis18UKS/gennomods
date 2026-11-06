# TimeFracture 0.11 (EXPERIMENTAL) — NeoForge 1.21.1

## Delivered
* Physical, player-traversable server-side rift gates in exact world coordinates. Open with `/timefracture gate open T-1` while holding the time artifact (creative users are exempt). The gate never moves with the player's camera.
* Small gates are only placed in air, do not overwrite machines or valuable blocks, expire after five minutes, and do not force-load distant chunks for cleanup.
* TimeFractureFX detects nearby gate blocks every two seconds (bounded client-side scan of loaded chunks) and displays a small world-space rift. The huge sky preview remains a separate original volumetric GLSL effect.
* Gate entry uses the existing LayerWorlds system for separate server dimensions; copying a missing initial chunk is queued in server-tick batches. Returns via `/timefracture return`.
* Opt-in random anomalies now use the REAL playable layer dimensions, not the removed displaced pocket-world API. `/timefracture anomaly on|off|test`.
* A crafting recipe for the time artifact is included.
* FPS guard and adaptive GLSL quality retained from 0.10.
* New developer command `/timefracture gate status`.

## Status — significant remaining work
This is NOT a complete multiverse. The engine still inherits one initial chunk per fork, not the whole generated world. There is no genuine autonomous AI player with a frozen skin, no live alternate-world image through the aperture, no airships transported across layers, no complete new-layer merge through the Create condenser, and no safe full-world DEV history rollback from within a running server. These cannot be replaced by villagers or illustrative particles.

## Test
1. Back up the world and install both new jars (NeoForge 1.21.1, Create 6.0.10).
2. `/give @p timefracture:time_artifact`
3. `/timefracture focus <online-player>`
4. `/timefracture branch -` (use the actual ID returned)
5. `/timefracture gate open T-1`; walk into the new glowing gate.
6. `/timefracture return`; ensure original XYZ and world were restored.
7. `/timefracture anomaly on`; `/timefracture anomaly test`; then `/timefracture anomaly off`.
8. `/tfx preview portal`; `/tfx preview where`; walk away and verify anchor XYZ unchanged.
9. `/tfx preview perf`; adjust `low` or `off` if graphics are too slow.

## World safety
Never test on the only copy of a world. The mod preserves old save regions but it has NOT been tested in an actual interactive client by this build system. GitHub Actions compile and server smoke prove less than real-world multiplayer and GPU integration testing.

## Additional improvement: alternative player avatars (v0.11.1)
- Separate **EchoPlayerEntity** with a humanoid player model. This is NOT a vanilla Villager; it has its own UUID and persists in layer region files.
- When first chunk snapshot finishes, a doppelganger has an 20% spawn chance; meeting yourself is NOT guaranteed.
- Captures current GameProfile texture property at creation and stores it in NBT, rather than following subsequent original-player skin changes. Skin download is asynchronous client-side.
- Basic AI: pathfinding, exploration, looking at players, defense, and fighting hostile mobs. Combat experience stored persistently.
- Contact with the ORIGINAL UUID at 1.5-block distance freezes future branching and attempts an immediate safe return (paradox).
- OP test: `/timefracture dev echo summon <online-player>`.
- **Not a fully intelligent autonomous AI-PLAYER.** Doesn't yet place or mine blocks, use Create, pilot airships, invent technology, or maintain long-term independent plans.
