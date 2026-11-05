# TimeFracture 0.8 — NeoForge 1.21.1: stability & architecture repair

This is an EXPERIMENTAL progress build. It deliberately does NOT claim to implement independent full-world time layers or true AI player clones.

## Fixed this release
* The sky portal in TimeFractureFX is now a huge, world-anchored, GLSL-filled sky aperture drawn at AFTER_SKY, **not a purple cone** rendered with thousands of transparent walls. Single 96-triangle sky surface, radius 420 blocks, preview placed 96 blocks above the player.
* Preview limit: one portal, one rift, one window, instead of stacking many copies every command.
* Ordinary timeline creation does NOT make 21x17x21 displaced islands or cause thousands of synchronous setBlock operations anymore.
* Ordinary timeline creation does NOT spawn Villagers pretending to be a real player.
* Recursive lineage, all dimension registry IDs and original X/Y/Z are stored in `layers.tsv`, scoped to the save. This is **metadata only**; isolated chunk & block-entity state and simultaneous per-player views remain unimplemented.
* Player can inspect metadata with `/timefracture layers` and `/timefracture inspect T-1`. Entry to metadata-only timelines and random intrusion are blocked to avoid falsely teleporting users into a pocket.
* DEV operator cleanup command `/timefracture dev cleanup-legacy-echoes` removes tagged old Villager proxies from loaded chunks, without touching ordinary villagers.
* Artifact makes intermittent quiet clock-tick attempts when held (after first GUI open), using built-in Minecraft mechanical sounds.
* Optional JEI plugin registers a new recipe category with **rotating real block models**, cyclic 3D layer-by-layer guide to the Create chronal condenser structure. Needs JEI installed (19.57.x+ 1.21.1 API).

## World safety and migration
Previously created legacy displaced pockets are kept for recovery. Original world and region files are **not automatically deleted**. Existing legacy Create convergence is left available for archived old pocket data only; the newly indexed world-wide layers cannot be merged until an isolated chunk backend is implemented.

## Commands
```
/give @p timefracture:time_artifact
/give @p timefracture:chronal_core
/timefracture players
/timefracture focus <online player>
/timefracture branch -
/timefracture branch T-1
/timefracture tree
/timefracture layers
/timefracture inspect T-1
/timefracture dev cleanup-legacy-echoes
/tfx preview info
/tfx preview portal
/tfx preview rift
/tfx preview window
/tfx preview close
/tfx preview collapse
/tfx preview stop
```

## Not yet completed
True world-wide, fully simulated divergent chunk/entity inventories at SAME coordinates; per-player isolated block/world states in multiplayer; skin-preserving player-like AI entities; actual airships across rifts; portal remote-world camera; full pre-split rollback; automatic survival convergence of new world-wide layers. These require substantial new game architecture and cannot be honestly promised as finished in this build.

## Verification
GitHub Actions tests compilation & packaged resources. Actual in-game NeoForge client, JEI interaction, Create integration, Iris/Oculus and performance profiling still need player verification. Keep a world backup and remove older versions of the two mods.
