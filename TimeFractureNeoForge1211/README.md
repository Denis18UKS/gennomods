# TimeFracture 0.9 EXPERIMENTAL — NeoForge 1.21.1

## Implemented in v0.9
- Real, individually persisted **dimension slots**, not fake distant islands. Slots 1–8 for the Overworld, Nether and End (24 predefined dimensions).
- /timefracture enter T-1 .. T-8 transports the player to the corresponding real ServerLevel **without changing X/Y/Z**.
- Upon first branch snapshot, a **single 16×16 chunk column** is copied at the original X/Z on the server main thread at **256 blocks maximum per tick**. Blocks and block-entity NBT (including eligible Create block entities) are copied. Copy starts at branch creation or first entry. A player requesting entry before completion is teleported when the snapshot finishes.
- Snapshot progress and completion markers persisted under world/data/timefracture/copied_chunks.txt; snapshot never marked done before all block positions have been processed.
- Each layer dimension saves independent terrain, blocks, block entities and entities naturally to vanilla dimension region files.
- Artifact GUI now uses /timefracture enter for its "Войти" button; /timefracture return restores source world and coordinates.
- Sky portal is an anchored 3D spherical shell with a raymarched 3D cloud/nebula shader. No flat sky disk; it uses world-relative camera and center uniforms.
- /tfx preview where prints the WORLD coordinates for each preview effect; moving the camera cannot change them.

## Major limitations (NOT a completed multiverse)
- **Only one chunk column** (16×16 horizontal, full build height) is copied at each initial timeline anchor. The rest of that dimension uses world generation with the same seed, but arbitrary player-built structures beyond the snapshot are **not** inherited. This is NOT full lazy copy-on-write chunk layering.
- No copy of mobs / entities, complex ownership associations, villages from live original world, timelines in other mods' dimensions, or independent offline AI simulation.
- Runtime Create power networks should be tested after copying block entities; network recalculation and complex moving contraptions are not guaranteed.
- 8 fixed slots; creating more than 8 playable branches is refused to avoid unisolated same-world snapshots.
- Existing T-* metadata from older versions remains. Old pocket worlds are not deleted automatically.
- Inter-dimension portals and airships are not yet gameplay-functional. Sky effect is visual only. Raymarch shader should be tested for FPS, GPU and graphic-mod compatibility.
- Full pre-split DEV rollback of arbitrary worlds, multiple-world Create merging, autonomous AI player replicas are NOT implemented.

## Commands
/give @p timefracture:time_artifact
/timefracture players
/timefracture focus <online-player>
/timefracture branch -
/timefracture branch T-1
/timefracture layers
/timefracture inspect T-1
/timefracture enter T-1
/timefracture return
/tfx preview portal
/tfx preview where
/tfx preview rift
/tfx preview window
/tfx preview close
/tfx preview collapse
/tfx preview stop

## Safety and performance
Back up entire world before installing. The mod creates 24 predefined dimensions; server memory and world startup may increase. The chunk snapshot copies 256 blocks per server tick (NOT thousands in one tick), but the first chunk load may still stall momentarily. It is strongly recommended to test in a separate world first. Server startup and runtime testing has NOT been completed here: GitHub Actions only verifies compilation and packaging.
