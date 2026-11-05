# TimeFracture 0.7 Create Convergence — NeoForge 1.21.1

## Build status
This is an **experimental local-pocket** implementation, not a fully independent multiverse. It integrates directly with **Create 6.0.10** kinetic mechanics and uses configured maximum RPM (`AllConfigs.server().kinetics.maxRotationSpeed`, default 256).

## Survival: merge branches through a real Create machine (NO merge command)
1. Install both TimeFracture and Create 6.0.10 on both client and server; FX mod is optional for visuals.
2. Create two or more localized branches using the time artifact or `/timefracture branch -`.
3. Craft **Chronal Core**: 4 Create Brass Casings, 4 Create Precision Mechanisms, one Minecraft Clock (pattern `BPB / PCP / BPB`).
4. Place the **Chronal Core** at the center of a 3×3 level.
5. Place **one Create Brass Casing** to the north, south, east and west at the SAME Y level.
6. Place a Create **Shaft** directly BELOW the core, on the **Y axis**. Connect the shaft to a real Create rotational network capable of the maximum configured rotation speed.
7. Place a Minecraft **Amethyst Block** directly ABOVE the core.
8. Right-click the core with **one Nether Star** to prime it. Nether Star is consumed in survival.
9. Supply the **actual server-configured maximum RPM** for 600 consecutive ticks (30 seconds). Use Create Speed Controller / large cogwheel and sufficient stress capacity. A stalled or overstressed network counts as 0 RPM.
10. Once fully charged, the server starts an incremental convergence job and creates a NEW merged pocket per dimension. It may take more time to process all saved branches. The original pockets are archived **without deleting their saved blocks**.

If RPM drops after valid charging began, charging restarts from zero, and a ONE-TIME 1-in-12 failure chance applies to that interruption. The failure produces a small world explosion (no block destruction) and cancels the operation. No kick, account punishment or ban.

### Check machine
Right-click core with empty hand to see actual RPM, configured maximum and charge. The process can only be triggered by this physically powered Create machine.
```
/timefracture converge status
```
There is deliberately **NO** `/timefracture converge` command to launch a survival merge.

### Conflict policy
- Existing pocket snapshots are never overwritten.
- The merged result is a new saved local area for each Minecraft dimension represented by the inputs.
- Non-air blocks are added where the base snapshot is empty.
- If two branches have different blocks at the same coordinate, the first branch's block is retained and the conflict is counted and reported; manual reconciliation may be necessary.
- Block entities (Create machines, inventories), entities and player inventories are NOT merged; this is a conservative preview of a future timeline-layer engine.
- Original branches become archived and are not normally selectable after completion, but their remote world regions remain on disk.

## DEV restore and safety
```
/timefracture dev rollback preview
/timefracture dev rollback origin
/timefracture dev rollback archive-pockets confirm
/timefracture dev rollback unarchive
/timefracture balance freeze
/timefracture balance restore all
```
The first two DEV commands are GUARDED: **a full world-history rewind is refused unless a verified full pre-split backup is available**. Current builds do not create such full backups. Archiving local pockets cannot bring back dead pets, repair inventories, or rewind an entire save. Restore a complete backup manually while the server is OFF.

### Other commands
```
/give @p timefracture:time_artifact
/give @p timefracture:chronal_core
/timefracture players
/timefracture focus <online_player>
/timefracture branch -
/timefracture branch T-1
/timefracture tree
/timefracture pockets
/timefracture enter T-1
/timefracture return
/timefracture anomaly on
/timefracture anomaly test
/timefracture anomaly off
```
The artifact has a new animated medieval broken-clock texture. FX remains the independently built TimeFractureFX 0.5.0 (two world-space GLSL effects, not yet showing actual real timeline snapshots).

## WARNING
Back up your world before testing. This experimental module copies block volumes to far-away coordinates and loads chunks. It has not been launched inside an actual Minecraft client in this development environment; successful GitHub Actions compilation is not equivalent to tested gameplay.
