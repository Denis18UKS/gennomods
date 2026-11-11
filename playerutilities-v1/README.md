# Player Utilities 1.0.0 (server-side)

NeoForge 1.21.1 / Java 21. Install only on the dedicated server (or in single-player). No client-side mod required.

## Commands

- `/tpa <player>`: send teleport request
- `/tpaccept`: accept the incoming request (teleports requester to recipient)
- `/tpdeny`: decline
- `/death list [page]`: list all deaths, 7 entries per page
- `/death last`: show most recent death
- `/death remove <number>`: remove exactly one entry by number
- `/death clear confirm`: permanently delete all personal death history

All commands available without OP. The `/tpa` request expires after 60 seconds and has a 5-second sender cooldown. Chat accept/deny buttons are clickable.

Death history stores server-local date, HH:mm:ss, world dimension, coordinates, and localized cause of death for each entry. No TTL and no periodic cleanup. Saved in overworld SavedData `playerutilities_deaths`. Click coordinates to copy them.

## Building

`gradle -p playerutilities-v1 build` with Java 21 and Gradle 9.2.1. GitHub Actions available.

**Caution**: compile-verified but not fully tested in a live multiplayer world. Back up before deploying.
