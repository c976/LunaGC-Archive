# PR #71: Mondstadt prologue fixes against upstream main

## Baseline

This pull request is rebuilt on **MeChen618/AstaPS main** (initial integration
point `532cbd8`), including merged server PR #87 and Resource PR #13. It does
not use `play/rino` as a base. The old PR branch is retained as one merge
parent for review history, but its overlapping runtime changes have been
replaced by the corresponding upstream implementations.

## Source precedence

- `QuestExcelConfigData.json` is the loaded subquest table. Its populated
  action and reward lists remain authoritative.
- `BinOutput/Quest/<mainId>.json` is a **materialized resource representation**.
  It may include reviewed compatibility prerequisites and begin actions.
  These fields are **not** proof that ordinary 7.1 native Quest records contain
  `acceptCond` or `beginExec`. The independent `Genshin-Reverse` native
  decoder is a distinct source.
- If an Excel action or reward list is empty, fall back to the corresponding
  valid BinOutput list. Do not insert BinOutput-only subquest rows.
- The reviewed 351 and 363 accept conditions override stale flattened
  prerequisites, with old acceptance index entries removed before reindexing.
  `35100 FINISHED -> 35101`, `35202 FINISHED -> 36301`. Do not display
  Chapter 1001 START before the hilltop talk.

For multi-condition finish/fail objectives, if flattened QuestExcel has
`LOGIC_NONE` or no meaningful combinator, a reviewed explicit BinOutput
`LOGIC_AND` or `LOGIC_OR` restores its intended meaning. This applies to
30901's three-dungeon AND and the alternative battle fail routes. Explicit
meaningful Excel logic takes precedence; single-condition rows remain intact.

The first world-entry welcome modal is suppressed to prevent an
`AntiAddictNotify` from interrupting the 7.1 birth cinematic. Notices for
returning players after a server update and active announcements remain.

The 35302 begin action activates scene 3 group 133003002 suite 2, which
contains tutorial slime config 439. The Traveler must keep Anemo abilities
during the 353 tutorial. `35404` notifies group 133003439 for the bow target.
The formal Amber encounter comes after the 355 forest sequence.

## Generic progress corrections

Finish/fail objective flags are cumulative until the quest's normal reset.
Previously a later nonmatching event wrote zero over earlier satisfied
predicates. This made 30901's three distinct dungeon clears under
`LOGIC_AND` impossible to accumulate. Repeated matches now avoid redundant
client progress notifications.

Quest-state accept gates (`STATE_EQUAL` and `STATE_NOT_EQUAL`) are different:
they are live conditions and are re-evaluated for a candidate state event.
This prevents a stale NOT_EQUAL result from accepting an excluded branch.
On saved-quest rebind, persisted finish/fail progress arrays with an obsolete
condition count are reset to the loaded row's current shape.

## Resource companion

The upstream materialized 35301 had a nonnative
`GRANT_TRIAL_AVATAR(1)`, introduced in resource commit `c98b896710`.
The invalid action existed in **both** BinOutput and the 51 MB flattened
QuestExcel. [Resource PR #14](https://github.com/MeChen618/AstaPS-Resource/pull/14)
fixes both representations on current upstream Resource main and restores
`35402.gainItems = [{itemId:1021,count:1}]` after the formal Amber encounter.
The dedicated Resource CI checks both representations and the surrounding
slime, forest, and target actions.

## Resource deployment

[Resource PR #14](https://github.com/MeChen618/AstaPS-Resource/pull/14) is merged into upstream Resource `main` (`97bd823`). Use the latest upstream Resource checkout in `resources/`; no fork-only resource pin is required. Existing local test installs can keep their reviewed PR #14 checkout until the next resource update.

## Validation and limits

Server JUnit tests cover fallback precedence, reviewed 351/363 gate selection,
the 353 slime/354 target actions, the 35402 reward, cumulative three-dungeon
objectives, live acceptance state gates, and saved progress shape changes.
The paired Resource CI covers the relevant JSON/Lua assets.

Successful CI does not verify cinematic timing, in-world NPC/talk actors,
map-marker duplication, scene spawning, full Act II/III gameplay, Dvalin
combat visuals, or the client-side weather/scene handshake. Those need a
continuous 7.1 client regression run. Do not force quest completion, create
fake quest events, or insert time delays to conceal remaining defects.
