# Player Utilities 1.0.0 — TEST BUILD

NeoForge **1.21.1** / Java **21**. Standalone server-side mod; unrelated to Masterki and Create.

## Commands

- `/tpa <player>` — request teleport to player; no OP needed
- `/tpaccept` — accept request, teleports requester to the accepting player
- `/tpdeny` — decline request
- `/death list [page]` — all permanent records (7 per page)
- `/death last` — latest death
- `/death remove <number>` — remove one death record manually
- `/death clear confirm` — remove the player's records, with two-step confirmation

Teleport requests expire after 60 seconds; a 5-second sender cooldown and one pending request per recipient prevent spam. Accept/deny chat options are clickable.

Death history has **no TTL, no limit, and no automatic cleanup**, and records server-local date, time to the second, dimension, xyz coordinates and cause. Entries are stored in world SavedData `playerutilities_deaths`. Coordinates can be clicked to copy.

## Building / status

`gradle -p playerutilities-v1 build` with JDK 21 and Gradle 9.2.1. GitHub Actions also starts a disposable NeoForge dedicated server to check mod loading.

**Caution:** full in-game multiplayer teleport and death-record save/reload scenarios have not been manually verified. Use on a backed-up world first.
