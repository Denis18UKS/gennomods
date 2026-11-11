# Masterki Economy 1.0.0 (development build)

A client/server NeoForge 1.21.1 mod using Java 21. Install the **same JAR on client and dedicated server**.

## Implemented

- Real ShopScreen with brass/copper panels inspired by the user-approved screenshot (not a chest GUI)
- Original Masterki gold/brass M coin asset, embedded from user-provided visual reference
- Inventory balance widget and an optional, brief main HUD indicator
- Server-validated /shop, purchases, sales, bulk discount tiers, stock limits
- /money, /money pay <player> <amount>, /money top
- Live categories, search, sorting, in-stock filter, favourites, history and shop navigation
- Daily server restocking every 24 real hours; uses persisted epoch time
- At most 200 configured items from vanilla, optional Create, and other installed mods
- Up to 40 delivery contracts, 5 completions per real day
- User accounts keyed by UUID, and SavedData-backed balances, stock, daily limits and receipts
- Daily server buyback quotas to limit automated resource farming
- Admin /masterki admin money add/set, restock, reload
- Editable `config/masterki/products.json`, generated automatically on first access

## Limitations (not yet finished)

The screenshot is used as the layout/style reference. The actual GUI is rendered in code, rather than identical embedded pixels. Main shop and inventory display work through the installed client mod. Physical Create terminals, Create Aeronautics cargo, achievement bonuses, hunting rewards, building contracts, player auction houses and advanced item-component trading are NOT implemented yet. These need mod compatibility testing and proper server-side validation.

No full in-game multiplayer QA has been performed. Back up the world and test on a staging server. Do not use as a production economy until exploit testing.

## Commands

`/shop`, `/money`, `/money pay <player> <amount>`, `/money top`, `/contracts`.

Admin (OP level 2): `/masterki admin restock`, `/masterki admin reload`, `/masterki admin money add <player> <amount>`, `/masterki admin money set <player> <amount>`.

## Building

`gradle -p masterki-v1 build` with JDK 21 and Gradle 9.2.1. GitHub Actions workflow included.

## Storage

World-level SavedData: `masterki_economy`. Balances persist across player death and logout. Stock and restock timestamps survive server restart. The UI reads state from signed-in authoritative server packets.
