# Domain reward claim receipts (7.1)

This branch records resin claim attempts with `RewardGrantJournal`.
It does **not** introduce an atomic transaction across payment, inventory and
receipt collections.

The journal identity is `DOMAIN : UID : dungeonId.runUuid`. A
`DungeonManager` creates its UUID at construction and rotates it in
`restartDungeon()`. It does not depend on a client-supplied interaction ID
or the dungeon ID alone. Two independent successful challenges cannot
accidentally share a receipt.

The claim is synchronized on `DungeonManager` and inventory. It:

1. Validates the reward source, materializes the full batch and preflights bag
   capacity and payment availability.
2. Creates a `PREPARED` receipt before payment.
3. Consumes resin or the selected alternate resource.
4. Marks the player rewarded for the current run and advances the journal to
   `CONFIRMED`.
5. Adds the items.
6. Marks the receipt `COMPLETED` after successful delivery.

The check in step 1 is a preflight: if funds change before step 3, the
receipt may remain `PREPARED` even without payment. Conversely, a crash may
occur after an irreversible operation but before its next journal write.
Do not interpret the stage as conclusive proof of a charge.

## Manual reconciliation

Run this query against the game MongoDB database:

```javascript
db.reward_grant_receipts.find(
  { source: "DOMAIN", stage: { $in: ["PREPARED", "CONFIRMED"] } },
  { _id: 1, uid: 1, operationId: 1, stage: 1, rewards: 1, createdAt: 1 }
).sort({ createdAt: 1 })
```

Inspect the player's inventory, resin/material/primogem balances, claim
logs and dungeon identity before making corrections. Receipt reward arrays
contain intended item quantities, not an automatic replay instruction.
Some partial grants may have persisted; never blindly refund or duplicate
the listed rewards. Clearing a receipt is not a substitute for inspection.

The in-memory `rewardedPlayers` guard still prevents repeated claims
within one dungeon run. `restartDungeon()` creates a new run identity.
Repeated entry and crash recovery across restarts still require client
testing; a process crash does not resume the prior DungeonManager.
Native 7.1 drop rates are unaffected by this change.

A clean `payCost() == false` now marks its attempt `ABORTED` and rotates
the run's claim token. This permits another attempt without reopening the
same receipt. An exception or unknown database result must **not** be
aborted. Expected `debits` are audit data, not verified charges.

## Reward source validation (reviewed in PR #120)

An existing native drop root is not sufficient evidence that its loot belongs to
this dungeon. The server traverses all reachable positive-weight leaves and
compares the appropriate artifact/material families with a validated compatibility
pool (or with preview metadata when no such pool exists). An old set under a
reused root, such as legacy artifact families instead of 4706's 475/485 families,
invalidates the native source. A structurally and semantically validated
`DungeonDrop.json` pool is then used if present. Some other proxies still lack
independent drop-rate verification; successful validation does not prove exact
official probabilities.

Reward previews **never** produce payable items. When neither source passes
validation, the claim is rejected before creating a receipt or deducting resin;
the log records the dungeon ID and rejected sources. Test coverage includes
the known 4706 mismatch and the existing malformed upstream proxy pools.

The scene restart entry point and the grant commit share the DungeonManager
monitor. The old manager's claim marker and run token are cleared or rotated
under that lock; stale requests cannot pay after a scene switch. Client tests
are still required for replay and scene lifecycle behavior.

## Fontaine weapon-domain compatibility coverage

The 7.1 source join identifies twelve released Fontaine weapon dungeons with
absent native server drop roots `4470–4473`, `4500–4507`. For example,
dungeon `4471` requests native root `83152600`, absent from the pinned
`Server/DropTableExcelConfigData`, and has no prior `DungeonDrop.json` pool.
The service correctly denied the claim before charging resin.

To make these dungeons playable, `data/DungeonDrop.json` now includes
**explicit static compatibility pools** for the twelve IDs. Their reward
**item identities** are checked against the 7.1 Fontaine weapon entry
`DungeonEntryExcelConfigData.id=25`:
`114049–114052`, `114053–114056`, and `114057–114060`.
The AR EXP, Mora and friendship reward amounts match the corresponding
7.1 dungeon previews (100 EXP; Mora 1125/1550/1850/2200 by tier;
friendship 10/15/15/20). The **material count/weight distributions**
are copied from existing AstaPS compatibility pools for Mondstadt's
same-tier weapon domains `4330–4333`; these are **not independently
validated native 7.1 Fontaine rates**. As with other proxies, the preview
only corroborates the static pool; claim-time rewards are generated by
`DomainDropRoller`, never converted from previews.

Tests require positive payable rewards for every supported tier and
roll count, constrain item families, and reject native graphs with a
cycle in any positive-weight branch (even if a second branch has a valid
item). A valid shared subtable remains allowed.

Before merging, client-test at least `4471` across normal/condensed
claims, repeated runs, insufficient resin and bag-full rejection.
This does not solve the 7.1 missing native-source drop-rate investigation
and must not be reported as official-rate restoration.
