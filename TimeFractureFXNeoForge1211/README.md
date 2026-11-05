# TimeFracture 0.9.0 experimental: functional same-coordinate branch entry

**NeoForge 1.21.1** and Create 6.0.10. Builds via GitHub Actions. NOT a fully implemented multiverse.

## Functional layer entry
- Four **pre-registered dimension slots** for each of Overworld, Nether and End (12 independently saved dimension instances). The coordinates stay exactly the same on entry and exit.
- In a fresh world: give artifact, select yourself (real online player), create T-1. A server-thread budgeted block/NBT snapshot around the creation point runs automatically. Query /timefracture layer-status T-1 for progress. Once it says ready, /timefracture enter T-1 teleports you to a truly separate game dimension at the same XYZ. Break/place blocks there and use /timefracture return to restore your original location.
- The 33x49x33 block snapshot preserves block states and attempts block-entity NBT copying (vanilla inventories and Create machines), with an explicit error count if a block entity could not be copied.
- Only the initial local region is captured. Outside that region, the layer has separately generated terrain using the same world seed. This is not a complete historical snapshot of all explored world chunks.
- Other dimensions have independent storage, but are not fully populated from parent builds. Player inventory is NOT automatically cloned/separated.
- Layer T-2 is created from T-1 if the parent is ready; snapshot operations are queued at most 128 blocks/tick, never 53k blocks in a single tick. Pending jobs can resume after server restart.
- Four slots exist by default to limit memory cost of dimensions; older metadata with IDs above T-4 cannot enter through a slot. No artificial islands, no fake villager doubles.
- Because these are separate dimensions, players in different layers can modify the same coordinates independently without overwriting one another.

## Sky shaders
FX now uses a camera-centered dome drawn in the sky pass rather than a finite flat disk or a 3D cone, so no visible disk edge should remain. This is a SKY render technique, not an actual teleport aperture; temporal travel uses /timefracture enter.

## Commands
```
/give @p timefracture:time_artifact
/timefracture focus <real-online-player>
/timefracture branch -
/timefracture branch T-1
/timefracture layers
/timefracture layer-status T-1
/timefracture enter T-1
/timefracture return
/timefracture inspect T-1
/timefracture dev cleanup-legacy-echoes
/tfx preview portal
/tfx preview rift
/tfx preview window
/tfx preview stop
```

## Known missing functionality
Unlimited dynamic layer allocation; complete copy-on-write of all generated chunks and all dimensions; duplicate entities and real AI alternate player with fixed skin; Create contraption airships through live rifts; automated random travel; player inventory isolation; fully safe 100% rewind of a world; seamless naturally varying world history; fully implemented survival Create convergence of new layers. The existing convergence machine can only combine old legacy pocket data and does NOT merge the new independent dimensions.

## Safety
Always back up the entire world before testing. Do not remove or overwrite old region files. Successful compilation does not substitute for testing Minecraft graphics, game FPS/TPS and Create block-entity compatibility. A dimension snapshot is only marked ready after copying completes.
