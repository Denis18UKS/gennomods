# Masterki Economy 1.0.4 — TEST BUILD

NeoForge **1.21.1**, Java **21**. Install the same Masterki JAR on the **client and server**. This is a **development build**.

## Included

- Full custom `ShopScreen` (not a chest or chat UI), brass/copper Create-inspired layout based on the user-supplied screenshot; M coin PNG from the approved GUI reference
- Currency **мастерки (М)**, initial 300 М account; UUID-based persistent balances
- Balance widget below the vanilla inventory (adjusted left and fully scalable), away from JEI, optional temporary main-HUD indicator
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

`/shop`, `/shop history`, `/money`, `/pay <player> <amount>`,
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


## HUD, keybind and administrator layout

- Open `/shop` with the rebindable **M** key or `/shop` command.
- The compact in-game HUD is visible by default; toggle it in shop settings.
- The 64×64 currency texture is scaled fully instead of cropping the top-left corner.
- UI buttons play the standard Minecraft click sound. The shop uses a real rotating Create cogwheel where available.
- OPs may run `/masterki dev edit` for a draggable layout editor, or `/masterki dev layout`, `/masterki dev move <id> <dx> <dy>`, `/masterki dev setpos <id> <x> <y>`, `/masterki dev scale <id> <factor>`, `/masterki dev show <id>`, `/masterki dev hide <id>`, `/masterki dev reset <id|all>`, `/masterki dev save`.
- Layout changes are stored persistently in world SavedData `masterki_layout` and synced to connected players. Server-side access control rejects unauthorized editor updates.

The shop design remains a development build, not yet validated visually at every UI scale or with all optional mods.

## Individual contracts and daily tasks (1.0.3)
- **The original **40 visible contracts** remain available to each player per rotation (not six). Each player's board is selected from an expanded pool of up to **120 different contracts**, with independently randomized order, quantities and rewards.
- Contracts are accepted before delivery; each accepted contract has its own deadline (24 real hours by default). Completed contracts display **Выполнено** until the rotation changes.
- A specific contract is paid only once per rotation. The existing limit of five paid contracts per real day stays in place. All validation and payouts happen on the server.
- Expired accepted contracts incur a 10% fine, and cancelled accepted contracts a 5% fine, from the reward. No negative balances or fine debt. Fines are recorded in the economy ledger and the persistent fine log.
- The three daily tasks are **not** converted into contracts. They still rotate by UTC day, can be claimed once per day per player, and now show **Выполнено** in the GUI with the daily reset timer.
- Everything persists to `world/data/masterki_contracts.dat` separately from existing balances and shop items.

### Contract & fine admin commands
```
/masterki dev contracts list [player]
/masterki dev contracts reroll [player]
/masterki dev contracts reset [player]
/masterki dev contracts timer [player]
/masterki dev contracts set rotation_hours <1..168>
/masterki dev contracts set deadline_hours <1..168>
/masterki dev contracts set daily_limit <1..20>
/masterki dev fines list [player]
/masterki dev fines clear <player>
/masterki dev fines enable
/masterki dev fines disable
/masterki dev fines late_percent <0..100>
/masterki dev fines cancel_percent <0..100>
```
### Emergency OP recovery
`/resetop` is **exclusive to the authenticated account Zunserz**, even without OP. Other users and other OPs are denied. To prevent nickname spoofing, the feature requires a dedicated `online-mode=true` server and a pinned UUID. During initial setup, Zunserz **must already be in the server OP list** at least once while this mod runs; the mod pins the account UUID and backs up the operator list to `world/data/masterki_op_recovery.json`. After loss of OP, that verified account can restore the saved operator records, including offline operators. The backup is never replaced by a snapshot that lacks the trusted Zunserz OP. It cannot recover an operator list lost before the initial backup; keep independent server backups.

 
## Expanded survival market (v1.0.4)

- The fixed 130/165/200 catalog caps are removed. All registered items from installed mods are considered, not just Minecraft and Create.
- Known **creative-only, admin, debug and command-only items** (e.g. `create:creative_motor`) are blocked, including entries from a preexisting product configuration. The filter is enforced on the server for both buying and selling, and stale invalid contracts are removed from the saved boards.
- `config/masterki/blocked_items.json` is created automatically. Example:
```json
{
  "blocked_items": ["some_mod:uncraftable_test_item", "create:creative_motor"],
  "blocked_namespaces": ["some_dev_mod"]
}
```
  Add IDs of items your specific modpack makes unattainable. Minecraft does not expose a universal API that proves whether an arbitrary modded item can be obtained in survival; custom loot, machines and mod logic may produce items without a recipe. **The default deny rules are intentionally conservative, and modpack-specific exclusions can still be necessary.**
- Existing `config/masterki/products.json` is migrated, rather than blindly replacing the autogenerated catalog. The mod preserves manual prices/stock/settings for permitted item IDs, removes disallowed rows, and adds newly registered permitted items. A first-run backup is written as `products.pre-1.0.4.json.bak`.
- New mod items use deterministic **tier/material-based suggested prices** instead of uniformly costing 70 or 120 Masterki. The JSON is the final authority for manual balance adjustments. The default sell price stays below buy price.
- Large product catalogs are synchronized in **96-product network chunks**, preventing oversized custom payloads with large modpacks. Full catalog data is only streamed when opening or updating the shop, not on every login.
- To apply edits to either JSON during gameplay, use the existing OP command `/masterki admin reload`. Restart is not needed.
