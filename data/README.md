# AstaPS data directory

This directory contains **server-defined datasets**. It is not the game's extracted
client resource pack and is not the owner's main gameplay configuration directory.

## How files are loaded

Most `DataLoader.load*(...)` calls select a local file from `data/` first,
falling back to `src/main/resources/defaults/data/` inside the JAR when absent.
A few explicit `loadBundled*(...)` calls deliberately prefer the packaged version.
When changing a table, check its actual loader rather than assuming every file
has the same precedence.

Files that are **exact copies of a bundled default** should not be checked into
`data/`. The packaged default provides them already; operators can add a local
file if a supported loader allows overrides.

The main datasets currently maintained as local files include:

- **Drops:** `ChestDrop.json`, `DungeonDrop.json`; monster drops use the
  packaged `MonsterDrop.json` unless an operator supplies a local override.
- **Spawns and exploration:** `Spawns.json`, `GadgetSpawns.json`,
  `*_explore_points.json`, `region_explore_points.json`, and
  `specialty_materials_points.json`.
- **Gacha and shop data:** `GachaEpitomized*.json` and `Shop.json`.
  Extra shop examples and the historical banner-name reference are archived at
  `docs/reference/data/`, not loaded at runtime.
- **Other server data:** `AvatarBaseStats.json`, `boss-drop-tags.json`,
  and client dispatch/version configuration under `version/`.

This is an inventory of existing datasets, not an endorsement of the format or
the runtime authority of every file. A later reward/drop refactor must audit
and remove its old read path explicitly.

## Runtime state is not source data

These files are written while the server is running and are **gitignored**:

- `artifact_transmuter_state.json`
- `investigation_elite_respawn.json`
- `nodkrai_explore_claimed.json`
- `nodkrai_guard_respawn.json`
- `snezhnaya_explore_claimed.json`
- `snezhnaya_guard_respawn.json`

They remain in `data/` for now so their current readers/writers need no path
migration. They are intentionally absent from the repository; a missing file
is handled by its owning system. Do not reset or delete real server state
when switching deployments.

## What does not belong here

- `resources/`: external 7.1 resource pack, including `Server/`,
  `ExcelBinOutput/`, scene points, and scripts. In particular, AstaPS reads
  `Server/DropTableExcelConfigData.json` **from the resource pack**, not
  `data/Server/`.
- `rewards/`: operator-editable LootCSS and YAML reward rules. They own
  chest, scene-point, ley-line, and boss payout policy; `data/` must not
  introduce a competing reward configuration.
- `config/`: other server settings, separate from drop and reward tables.
- `src/main/resources/defaults/data/`: defaults maintained by the server
  source and included in the JAR.

The obsolete `_sakura_reward_5x.flag` was a manually recorded edit marker;
it has no consumer in the server and must not be used as a runtime authority.
