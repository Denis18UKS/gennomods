# Masterki Economy 1.0.12 — TEST BUILD

NeoForge **1.21.1**, Java **21**. Install the same Masterki JAR on the **client and server**. This is a **development build**.

## Included

- Full custom `ShopScreen` (not a chest or chat UI), brass/copper Create-inspired layout based on the user-supplied screenshot; M coin PNG from the approved GUI reference
- Currency **мастерки (М)**, initial 300 М account; UUID-based persistent balances
- Balance widget below the vanilla inventory (adjusted left and fully scalable), away from JEI, optional temporary main-HUD indicator
- Separate shop/sell/categories, search, filters, price sorting, favorites, item details, quantity choices and confirmation via server-side transaction
- Items from vanilla, optional Create, other loaded mods, editable `config/masterki/products.json`
- Server-validated purchases, sales, bulk discounts, bounded stock, individual per-item buyback caps reset on each Overworld game day, payment history
- 24-hour **real-time** restocking with persisted timestamp; OP forced restock
- 40 individually rotated production contracts (includes Create parts when installed), each paid once per rotation; no daily total cap
- 3 smaller rotating daily tasks, each claimable once per real day per player
- Automated limited payouts from advancement achievements (one-time), first discovered dimensions and biomes (one-time), and hostile mobs (12 ordinary paid kills plus 2 boss paid kills per UTC day)
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
- A specific contract is paid only once per rotation; **there is no daily limit on the number of different contracts** a player may complete. All validation and payouts happen on the server.
- Expired accepted contracts incur a 10% fine, and cancelled accepted contracts a 5% fine, from the reward. No negative balances or fine debt. Fines are recorded in the economy ledger and the persistent fine log.
- The three daily tasks are **not** converted into contracts. They still rotate by UTC day, can be claimed once per day per player, and now show **Выполнено** in the GUI with the daily reset timer.
- Everything persists to `world/data/masterki_contracts.dat` separately from existing balances and shop items.

### Contract & fine admin commands
```
/masterki dev contracts list [player]
/masterki dev contracts reroll [player]
/masterki dev contracts reset [player]
/masterki dev contracts timer [player]
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

## Hunting fix (v1.0.5)

- The hunting caps are **independent per player**: up to **12 paid ordinary hostile kills** per UTC calendar day at **25 M** each; up to **2 paid vanilla boss kills** (Wither, Warden and Ender Dragon) at **450 M** each.
- **Ender Dragon payouts are fixed**: it is classified as a boss explicitly, before testing the `Enemy` marker, which does not apply to the dragon.
- **Wither Skeleton is not a boss** and correctly earns only the ordinary 25 M reward. No credit for mobs killed without a player-attributed damage source; no hunting payouts in Creative mode.
- The two daily counters are persisted separately within `masterki_economy.dat` as `Hunts` / `HuntDay` and new `BossHunts` / `BossHuntDay`. Existing balances, 12-kill count, inventory, shop and contract data remain intact during upgrade.
- Both reset at **00:00 UTC**, not 24 hours after the last kill. The new plain-player command `/hunt` displays both counters plus the remaining time to reset.
- Payout messages include progress (e.g. `3/12`, `1/2`); a separate warning appears on the kill that fills each cap, not on every later unpaid kill.

## Contract anti-abuse correction (v1.0.6)

- Removed the mistaken total daily limit of **5 paid contracts**. Players can complete every distinct contract available in their personal board, including all 40 in one day.
- Anti-exploit is now **per contract and per rotation**, based on the persistent contract state. The server only rewards a contract while its state is `accepted` and its deadline has not passed. A successful turn-in transitions to `completed`, consumes the resources once, records the ledger entry and prevents all further payments for that contract ID until the next rotation.
- Removed the obsolete `/masterki dev contracts set daily_limit` setting and no longer increments the legacy per-day contract counter. Older counter values in world data are harmless and do not constrain rewards.
- Keep other controls untouched: each of the 3 daily tasks still pays once per task per day; hunting limits stay **12 ordinary mobs / 2 bosses per UTC day**.
- Existing account balances, historical logs, saved individual contract status, deadlines, fines and other world data persist across the upgrade.

## Unified 05:00 daily contracts (v1.0.7)

- A single server-wide calendar cutoff replaces each player's previous `now + 24h` rotation. Default is **05:00 Asia/Yekaterinburg (Ufa UTC+5)**, i.e. 00:00 UTC. Different players still receive their own 40 randomly chosen contracts from the larger eligible pool, but their boards renew at the same time. Login and server restarts do not restart or extend a cycle.
- The 40 issued contracts are classified using quantity and actual configured market buy price. A **normal** order expires at the *next* 05:00 cutoff; a **difficult** order expires at 05:00 *two calendar days after issuance*, never after that. Deadline is established at issuance, **not** when the player accepts.
- The player can accept a difficult contract and keep it through the first renewal, while 40 fresh offers appear alongside it. A carried-over order is labeled as such in the client GUI. Unaccepted old offers vanish without a fine, and each accepted contract that reaches its own deadline becomes overdue with the usual 10% fine. Cancelling an accepted order retains the 5% fine. Fines are applied at most once, including while players are offline.
- Contracts remain per-player and per-rotation: every distinct order can be paid once, with **no total daily paid-contract limit**. Existing completed records cannot be turned in again.
- Each order row shows its difficulty, calendar deadline in the configured server time zone, remaining real time and carryover status. Accepting an order with less than two hours remaining requires a second click within six seconds, as a warning.
- **Migration**: v1.0.6 boards get a cycle key without wiping entries. Accepted contracts with time left are extended to the nearest 05:00 boundary at or after their old deadline (no early expiration due solely to migration); completed records, balances, historical records and fines remain untouched.
- Also uses the configured cutoff for the three daily shop tasks and the independent daily hunting caps (12 regular enemies + 2 bosses). At the default Ufa setting, this exactly matches their prior 00:00 UTC reset.

### Configuring the reset time / timezone

On first load, the mod creates `config/masterki/contract_schedule.json` on the server:

```json
{
  "timezone": "Asia/Yekaterinburg",
  "reset_hour": 5
}
```

The timezone must be a Java/IANA ZoneId and `reset_hour` an integer 0-23. Changes are picked up by `/masterki admin reload` or a server restart. Do not change the clock during an active contract cycle unless players accept that schedules will be recalculated. The older commands `/masterki dev contracts set rotation_hours` and `... deadline_hours` are removed because all contracts now use the shared daily calendar boundary.

## Individual buyback caps on the Minecraft day/night cycle (v1.0.8)

- The old **server-wide** daily buyback counter is replaced with independent **UUID + item-ID** counters. Another player selling the same resource does not consume your quota. Different items are tracked independently for each player.
- **No per-item cap values or prices changed.** Existing formula remains `offer.daily > 0 ? offer.daily * 2 : 8` (computed in long arithmetic). The cap restricts sales **to the shop**, not purchases.
- The quota resets when the **Overworld's dayTime crosses to another 24,000-tick calendar day**, not after 24 real hours. Sleeping the night advances this clock and can reset the quota; server downtime and player reconnection do not advance the day clock or reset the counter. The Overworld controls the cycle for all dimensions.
- Player-specific counters are stored in the existing world SavedData `masterki_economy.dat` under `BuybackGameDay` and `BuybackUsers`. A server restart preserves the current game's per-player quota.
- Upgrade from 1.0.7: the old shared `Buybacks` count cannot be attributed fairly to particular UUIDs, so it is not migrated; everyone's **buyback quota alone** starts fresh on their first attempt to sell in 1.0.8. Existing balances, historical transactions, stocks, contracts, daily tasks, hunting data, configs and unrelated economy fields are retained.
- Only the buyback quota's scope and reset clock change. Contract renewal, daily tasks and hunting use the previously configured real-world 05:00 server time, unchanged.

## v1.0.9 — quick sales, contract HUD, individual hunt economy, Apotheosis tiers

- The `Магазин` tab displays a single **Buy** button, and the `Продажа` tab displays only **Sell 1** and **Sell N**. Transaction controls do not appear in other tabs. The current tab's subtotal is shown before the server validates and processes the transaction. Sell rewards have no bulk discount; purchase bulk discounts remain unchanged.
- A new `hud_contracts` indicator shows the first three active contracts, resource counts **calculated from the player's actual 36 main inventory slots each frame**, a progress bar, current fixed payout, and deadline. More accepted contracts are counted below. The client notifies when all resources are present; the server remains authoritative on completion. The indicator is movable, scalable, and toggleable in existing `/masterki dev edit` and related `/masterki dev layout` commands. Server state snapshots sync every five seconds and on economy actions.
- Hunting caps are now per **UUID and species key** (with charged creepers counted as their own variant). Vanilla examples: zombie 15M/12 daily, skeleton 20M/10, creeper **40M/5**, charged creeper **80M/5**, witch 50M/5; each boss has independent limits (Wither 450M/2, Warden 650M/1, Ender Dragon 550M/1). Most unlisted hostile mobs receive 20M/8; all payouts multiply by the actual killer's Apotheosis tier.
- **Optional Apotheosis 1.21 integration** reads the player's real `apotheosis:world_tier` NeoForge data attachment through the registry: Haven x1, Frontier x1.15, Ascent x1.30, Summit x1.50, Pinnacle x1.75. No classes from Apotheosis are linked, so the module remains usable without it (x1). Values are deliberately moderate and configurable in future versions.
- Tier multipliers affect completed hunts, contracts, three daily jobs, first-time achievements, and exploration rewards. Contract reward is **frozen when accepted** and persisted to avoid switching tiers immediately before turning in a valuable contract; existing accepted contracts with no frozen reward preserve their original 1.0.8 amount. Shop item prices remain identical for all players.
- Per-species hunt statistics persist in `masterki_economy` SavedData and reset at the **existing real-world 05:00 Ufa** boundary, independent of the 24,000-tick personal shop sell limits. Old aggregate hunt counters remain stored for compatibility, but do not affect the new species-separated caps.
- This is still a development build. Run client/server multiplayer trials with **the matching Apotheosis 1.21.1 release** to confirm the tier registry from a full modpack.

## v1.0.10 — one-click purchase, balanced contract payouts and compact HUD

- The **Магазин** tab now has **Купить 1 предмет** and **Купить N**. Buying one unit is a real server-validated transaction with a `count=1` packet, independent of the selected quantity. The **Продажа** tab still has **Продать 1** and **Продать N**. Purchase and sell controls are scoped strictly to their matching tabs.
- **Contracts:** the old global **1250M ceiling is removed**. New payouts use the actual configured per-item sell price times the randomized final quantity. Rare Nether Stars and Elytra get a 150% contract premium over the normal sell value; Create parts 130%; regular items 120%. Apotheosis world-tier coefficients apply afterward. This makes a Nether Star or Elytra contract far more valuable than a simple anvil contract, without making buying from the shop and turning in the contract profitable under normal prices.
- Newly generated rare contracts request fewer items: Nether Stars typically 2 (variant 1–3), Elytra typically 1 (variant 1–2); other high-value offers also have reduced quantities. Existing **accepted** contracts keep their exact frozen payout and deadline. **Only available/unaccepted** contracts are repriced on first v1.0.10 world load, with a persistent price-schema marker. Completed records, fines, bank balances and rotation progress remain intact. No user data is reset.
- Contract rows display the total reward and approximate **reward per unit**. Contracts, daily jobs, history and settings now use a full-width panel without irrelevant item catalogue categories, right-side selection panel, or trade buttons.
- **Contract HUD:** translucent smaller layout and a compact/expanded display mode. The default layout scale is 70% for new players; existing server-assigned custom sizes/positions remain intact. Compact mode displays two active orders, expanded up to three, with item names, live resource progress, payout, and deadline. Additional active contracts are counted and all accepted contracts can still trigger ready notifications. Local mode/enable preferences survive client restarts via `config/masterki/client_hud.json`. The existing dev layout editor still controls screen position/scale/visibility.
- **Balance diagnostics:** the existing `masterki_economy` overworld SavedData serializer/deserializer already persists balances by UUID and remains unchanged. For investigating a displayed 0 M, OPs can run `/masterki admin money inspect <player>` to view the persisted value, UUID, transaction-history size and most recent entry. This command **does not alter the balance**. A 0 M screenshot alone does not prove a save regression.
- All species-specific hunting limits, Apotheosis tier payouts, calendar-rotation deadlines, per-player game-day sell limits, and old admin commands remain unchanged.

**Deployment:** install exactly the same v1.0.10 JAR on both client and server, replacing v1.0.9; do not delete saved world files. Full multiplayer GUI tests, balance persistence after repeated server restarts, and integration with a particular installed Apotheosis modpack still need in-game verification.

## v1.0.11 — Contract paging regression hotfix

- Fixed the contract GUI unexpectedly jumping back to page 2 during periodic server snapshots. Previously, `ShopScreen.filter()` clamped **all tabs** to the number of *filtered shop product pages*, even when the player was browsing pages of contracts. Contract pages now use the actual contract count and 5 rows/page; daily-task pages use 8 rows/page; shop/sell tabs use 15 items/page.
- Page navigation and incoming snapshot refreshes share the same tab-specific `maxPageForCurrentTab()` boundary. The current page is preserved while browsing unless its content genuinely disappears.
- No changes to player balances, SavedData, contract rotation, trade prices, hunter payouts, or Apotheosis integration. Replace the existing client **and server** v1.0.10 JAR with the matching v1.0.11 JAR.

## v1.0.12 — reliable daily contracts, 3-day difficult orders and whole-board bonus

- Every schedule boundary at **05:00 Asia/Yekaterinburg** produces a FRESH batch of up to **40** orders. Only still-valid **accepted** orders survive subsequent daily rotations. They appear after the new orders. Expired available offers, completed, canceled, or overdue orders leave the active board and move into a persistent contract archive; overdue accepted orders are fined once.
- New **difficult** and rare contracts expire at the **third** daily schedule boundary after issue (3 days, e.g., issued Nov 17, deadline Nov 20 at 05:00). Regular offers expire at the very next daily boundary. Existing accepted contracts keep their frozen deadlines and payouts.
- **Bonus for completing an entire 40-order batch:** one-time additional **15% of the sum of the original batch's order payouts minus 13% shop commission on the bonus alone**, net `floor(sum * 1305 / 10000)`. For 100,000 Masterki across a batch, the bonus is **13,050 Masterki**, total **113,050 Masterki** including contract payouts. Actual Apotheosis-adjusted accepted payouts contribute to the stored batch total. Every contract must be completed successfully; any canceled, expired or overdue order forfeits that batch's bonus. A batch may remain eligible when its accepted difficult orders carry over for up to three days; incoming daily rotations do NOT reset its progress.
- Batch completion, cancellation, fines, frozen payout totals, the paid flag, and up to 160 archived contract outcomes per player are persisted inside `masterki_contracts` SavedData, with backward-compatible default values for older worlds. A player with 40 already-issued orders from v1.0.11 has the current board's progress recovered without double-paying an already-completed batch. Historical orders from prior versions that no longer exist in the world cannot be retroactively reconstructed.
- Contract GUI shows the completion counter, net bonus and failure/paid status for a batch. Click the displayed batch summary to cycle among the three most recent tracked batches. The **История** tab now displays both financial transactions and an archive of completed, expired, canceled or overdue contracts.
- Admin-only **`/masterki admin contracts refresh`** removes ONLY expired/overdue contracts from all stored player boards, archiving them and applying any overdue fine only once. It doesn't reroll, doesn't produce more contracts, doesn't change today's unexpired issued offers, doesn't shorten accepted difficult contracts, and doesn't change the scheduled 05:00 rotation. (Players already online may independently be naturally rotated by their normal periodic sync after 05:00.)
- The existing developer `/masterki dev contracts reroll` remains an explicit cheat/test command, separate from safe admin cleanup.
- No changes to trading, per-player sell limits, hunt species caps, balances or difficulty coefficients. Install the same 1.0.12 JAR on both client/server; **back up the world first**, and do not delete the world or config directory.
- Compilation and server smoke testing by GitHub Actions do not replace an in-game verification of GUI rendering, three-day rotation behavior, and multiplayer persistence.
