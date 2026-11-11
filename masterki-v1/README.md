# Masterki Economy 1.0.0 — TEST BUILD

NeoForge **1.21.1**, Java **21**. Install the same Masterki JAR on the **client and server**. This is a **development build**.

## Included

- Full custom `ShopScreen` (not a chest or chat UI), brass/copper Create-inspired layout based on the user-supplied screenshot; M coin PNG from the approved GUI reference
- Currency **мастерки (М)**, initial 300 М account; UUID-based persistent balances
- Balance widget by the vanilla inventory, optional temporary main-HUD indicator
- Separate shop/sell/categories, search, filters, price sorting, favorites, item details, quantity choices and confirmation via server-side transaction
- Items from vanilla, optional Create, other loaded mods, editable `config/masterki/products.json`
- Server-validated purchases, sales, bulk discounts, bounded stock, per-day buyback caps, payment history
- 24-hour **real-time** restocking with persisted timestamp; OP forced restock
- 40 production contracts (includes Create parts when installed), maximum 5 completions per real day
- 3 smaller rotating daily tasks, each claimable once per real day per player
- Automated limited payouts from advancement achievements (one-time), first discovered dimensions and biomes (one-time), and hostile mobs (maximum 12 paid kills per real day)
- /money transfers and ranking, /shop history, /contracts
- Economy SavedData in overworld, held across restarts and player deaths
- Disables monetary rewards and sales in Creative mode; verifies inventory and balances on server

## Commands

`/shop`, `/shop history`, `/money`, `/money pay <player> <amount>`,
`/money top`, `/contracts`.

Admin (OP level 2): `/masterki admin restock`, `/masterki admin reload`,
`/masterki admin money add <player> <amount>`, `/masterki admin money set <player> <amount>`.

## What remains out of scope for this test build

The GUI follows the screenshot's **layout and aesthetic**, but is code-rendered rather than a pixel-for-pixel reproduction of the reference. Full survival multiplayer interaction tests and client rendering tests have not been run. Dedicated server startup was exercised in GitHub Actions.

**Not implemented:** physical Create export terminals/kinetic automation, Create Aeronautics delivery routes, building contracts, player auction houses, advanced component-bearing mod item sales, automatic advanced Apotheosis boss-tier valuation and custom original GUI particle animations. These are separate compatibility-heavy features. Please do not treat them as ready.

Shop defaults are a starting economy, not a final balance. Review `config/masterki/products.json` before opening a public server: arbitrary mod items can be valuable or overpowered. Only unmodified stacks are accepted for selling to avoid wiping enchantments, stored inventory contents or custom data.

## Building / testing

JDK 21 + Gradle 9.2.1:
`gradle -p masterki-v1 build`.
Client and server use the same generated JAR.

The included GitHub Actions workflows build the project and start a disposable NeoForge 1.21.1 dedicated server with EULA acceptance **only in CI**. No multiplayer UI click test has been performed. Back up the world before installing the JAR.

## Storage

Overworld SavedData `masterki_economy` contains player balances, purchases, stock, earned advancements/explorations, daily hunt/quest limits, and resupply clock. No database or external service is required.
