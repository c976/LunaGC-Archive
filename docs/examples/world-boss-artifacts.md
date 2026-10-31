# World Boss artifact configuration

The artifact section of `src/main/resources/rewards/world-boss.css` configures
artifacts for 48 world Boss identities, using six parameterized templates and one fixed pool in
`src/main/resources/rewards/loot.yaml`. The obsolete two-Boss example files
have been removed; the bundled resources are the configuration to inspect.

## Shared definitions

| Native rarity | Pool definitions | Actual candidate pools |
| --- | ---: | ---: |
| 1 star | 1 | 3 |
| 2 stars | 1 | 3 |
| 3 stars | 1 | 7 |
| 4 stars | 1 | 6 |
| 5 stars, including special native rows | 3 | 6 |
| Total | 7 | 25 |

Definitions use structured `genshin.artifact` entries. Ordinary one- to
four-star pools select from `<SETS...>`, inferring available native slots.
The ordinary five-star pool fixes Gladiator's Finale / Wanderer's Troupe as
its two equally weighted sets. Special five-star rows use an Instructor-only
plume/goblet weighted pool (17:33, also 34:66),
with substat counts attached to each slot; an Instructor-only goblet template
accepts the initial substat count. The single-quality gem pool retains dynamic
element arguments while fixing its copy count at 3. Candidate probabilities, rarities and initial substat
counts remain unchanged.

The 48 complete Boss programs form nine source groups. Each group
declares its own level rules with explicit Boss `tag` selectors and
artifact-set template arguments. The 22-Boss Instructor program and
Setekh Wenut retain their distinct native distributions. Open-ended
ranges continue the final native row beyond level 120.

## Independent rolls and linked properties

Each source artifact branch rolls independently. When two branches resolve
to the same candidate pool, repeating its CSS property would overwrite one
branch. Instead, the complete independent execution-count distributions are
convolved, then each resulting execution rolls that candidate pool afresh.
This preserves the entire resulting item-count distribution.

For example, one source branch executing 1/2 times at 95%/5% and another
executing 0/1 times at 79%/21% become 1/2/3 executions at
75.05%/23.9%/1.05%. No expected-value approximation is used.

The unusual native rows for Frostnight Scion and similar Bosses are retained
literally. A fixed slot remains fixed. Where slot and initial-substat count
are linked, weighted entries keep them together. Zero-substat five-star
variants are selected explicitly when the source requires them. These rows
are not replaced with ordinary five-slot artifact drops.

## Original-source comparison

Native roots come from this repository's `data/ChestDrop.json`, expanded
through `src/test/resources/native-snapshots/DropTableExcelConfigData.json`. Referenced subtables and
native artifact attributes come from `RinoPaw/AstaPS-Resource` at
`c0213668889ed05cbeb86660f424c3daf18bfacd`:

- `Server/DropSubTableExcelConfigData.json`
- `ExcelBinOutput/ReliquaryExcelConfigData.json`

The bundled CSS and YAML were read back and compared using exact rational
arithmetic against native item IDs, candidate probabilities, and complete
execution-count distributions. Coverage is levels 1–200, 1000, and
2147483647 for every configured Boss: 9494 Boss/level combinations. The
comparison includes all 25 candidate pools and 197 native artifact variants.

Before the Precision Clockwork fallback, a separate manual parser inspection
used the actual `ArtifactResolver`,
`YamlLootTables`, generic expansion, and `CssRewards` implementations, backed
by 4352 native artifact metadata rows. All 9494 configured combinations
matched native probabilities within 1e-11, including slot/substat correlations
and merged execution counts. At that point the 202 Precision Clockwork
combinations had no artifact declarations; the fallback below supersedes that
exclusion. Non-artifact item services were
isolated for this inspection; it does not verify full server startup or real
inventory delivery. No test suite or CI was run.

## Precision Clockwork fallback

Precision Clockwork references 542901, 542902, 542903, 542904, 542905,
542906, 542908, and 542909. Their definitions remain missing from the examined
primary tables, subtables, and item resources. The original candidate pools
and their internal weights have not been recovered.

By explicit user choice, Precision Clockwork now joins artifact group 2 with
Pyro Regisvine. No generic definitions, candidate pools, or CSS rules are added.
The original 47-Boss source comparison above remains valid; Precision Clockwork
is an additional inferred fallback, not another exact source reconstruction.

The fallback is supported by two separate comparisons:

- All 25 native levels (1 through 120) have identical artifact branch execution
  counts, root probabilities, and child count/weight shapes for Precision
  Clockwork and Pyro Regisvine. The last row continues above level 120.
- In `AstaPS-Resource` at the commit cited above, client reward preview 15209
  contains material 113080 and is labelled `超重型巡陆艇_90级`. Its nonempty
  artifact display-item list matches Pyro Regisvine preview 15140 exactly.
  `DisplayItemExcelConfigData.json` resolves those entries to the same set IDs
  and rarities: Adventurer (3), Berserker (3/4), Instructor (3/4), Prayers for
  Illumination (3/4), Gladiator's Finale (4/5), and Wanderer's Troupe (4/5).

Reward previews do not establish internal selection weights, initial substat
counts, or the low-level one-/two-star pool. All of these are explicitly borrowed
from Pyro Regisvine. This replaces the five unresolved branch pools by role;
it is not a one-to-one remapping of missing IDs. In particular, native ID 542901
is reused by both the first branch and the 66% child of the second branch,
whereas Pyro Regisvine uses separate IDs 540500 and 540505. The fallback does
not claim to preserve that unresolved alias.

The resulting configuration covers all 48 Bosses with the same twelve generic
definitions and 25 candidate pools. Material, gem, Mora, and EXP rules are
unchanged. No test suite or CI was run for this fallback.

## Loading issue and scope

The separately identified material/gem name-resolution failure remains a
bundled configuration loading issue; these artifact changes do not repair it.
Ordinary chest artifacts for cities 1-4 are now configured in the chest section;
weekly Bosses remain outside this configuration.
