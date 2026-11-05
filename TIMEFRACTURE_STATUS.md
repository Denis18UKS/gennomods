# TimeFracture 0.6 — playable LOCAL timeline experiments (NeoForge 1.21.1)

This version is an expansion of two separate mods, NOT the complete multiverse described by the original idea.

## Implemented / attempted
- Client artifact screen with actual server player list (from Minecraft connection); server revalidates focused targets using EntityArgument.player
- Unlimited metadata branch ancestry (capped locally to 256 playable snapshot pockets), as in T-1 -> T-2 -> T-3
- Physical snapshots of 21 x 17 x 21 block neighborhoods, copied into distant regions in the *same existing dimension*
- Recursive pocket creation, entrance and return with per-player original position saved across restarts
- Server-side event tracking for tracked player's death, tamed pet death, boss victory, rare block destruction, dimension change
- Vanilla villager-based NPC echoes and observer pathfinding + reactive dialogue; dangerous contact triggers non-destructive evacuation/freeze
- DEV freeze, unfreeze, metadata stabilization, access by UUID
- Opt-in accidental jumps between existing pockets, with /timefracture anomaly on|off|test; no involuntary default teleportation

## Important LIMITATIONS
- NOT fully separate world dimensions, no copies of all chunks in the world, no independent world time or weather
- Each branch is a small explorable block snapshot. The copied region is saved as vanilla world blocks at remote coordinates
- Containers/block entities and mobs are NOT cloned for safety
- Echoes are villagers carrying alternate-player identity; NOT visually correct player clones and NOT neural AI
- A "cosmic collapse" is represented by an evacuation and frozen branching, never damage to the save files
- GUI player list is based on the real client tab-player list; server validates chosen online player before accepting the command
- The FX mod remains the separately built GLSL v0.5, with procedural alternate landscapes, not live portal views into actual cloned regions
- Synced multi-user simultaneous independent timeline copies, view-through rendering, and comprehensive causality model remain TODO

## Commands
```
/give @p timefracture:time_artifact
/timefracture players
/timefracture focus <online-player>
/timefracture branch -
/timefracture branch T-1
/timefracture tree
/timefracture pockets
/timefracture enter T-1
/timefracture return
/timefracture balance status
/timefracture balance freeze
/timefracture balance unfreeze
/timefracture balance restore all
/timefracture dev grant <player>
/timefracture dev revoke <player>
```

## Risks
Make a BACKUP of the world before testing. Snapshot creation loads/copies roughly 7500 blocks in the same server tick and may lag the server. Snapshot pockets are stored at far world coordinates; they are not protected from commands or external mods. Do not run the alpha on a production server.

## Build
`./gradlew -p TimeFractureNeoForge1211 clean build`
`./gradlew -p TimeFractureFXNeoForge1211 clean build`
