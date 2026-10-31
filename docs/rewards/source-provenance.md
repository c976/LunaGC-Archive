# Reward source provenance

## Acceptance boundary

The reward language and settlement implementation are independent of the origin
of the configured values. Passing compiler, recursion, coverage or payout
regression tests does not establish that those values reproduce official rewards.
The current defaults have not met the requirement to exclude unverified edits
in inherited private-server data. They must not be presented as an authenticated
original-game baseline or as ready for acceptance on that basis.

A source must record its version, original acquisition route, immutable identity,
and known transformations before it is accepted as original data. An immutable
Git revision makes a reference reproducible; it does not authenticate the bytes.
Independently chosen reward policies need explicit review as our own design.
Missing evidence is not permission to infer a replacement value or classification.

## Sources inspected on 2026-10-21

| Source | Evidence | Status |
| --- | --- | --- |
| AstaPS `data/ChestDrop.json` | Introduced by `c6fd412d2d71b259745674fc4dcb3545168921f7`; the commit explicitly says it is a squashed Chiori import without the individual prior commits | Inherited server reference; original reward provenance unresolved |
| AstaPS `src/test/resources/native-snapshots/DropTableExcelConfigData.json` | Same squashed import and history boundary | Inherited server reference; original reward provenance unresolved |
| AstaPS-Resource Gadget Excel | Snapshot `c0213668889ed05cbeb86660f424c3daf18bfacd`, blob `9b32db003dcc37468a3824c8de41d14b7d69cd93`; its only recorded file change is import commit `67e2fdf5185ff6661e69505b1098d5f53374fccd` | Exact observed fields; original package identity and extraction chain not authenticated |
| AstaPS-Resource scene Lua | Read directly from `Scripts/Scene` at the same resource snapshot; the sample shrine group was introduced by `b925f3437767d8667aa9956e0500815475a802c1` | Exact observed script fields; no blanket assertion that every script is unmodified original data |
| Imported exploration point JSON | Supplies community marker kinds such as `chest_puzzle` | Derived metadata, never proof of a native type or official reward |

The scene scan linked explicit gadget entries to Excel rows with literal
`type: Chest`: 14,127 configuration entries, 9,063 with a literal `drop_tag`,
147 distinct tag strings, 1,339 entries with `chest_drop_id`. These are counts
within this resource snapshot, not official completeness or provenance claims.
The observed Excel has 309 `Chest` definitions with 148 distinct `jsonName`
values, including book-page objects. Neither those observations nor a Chinese
tag string identify who originally authored the record.

## Current configuration dependencies

| Rules | Current basis | Consequence |
| --- | --- | --- |
| Regional chest Mora, EXP books, talent books and artifact distributions | Historical ChestDrop/server-table references, expanded with resource mappings | Their derivation is reproducible, but official authenticity is unresolved |
| World Boss fixed payouts and distribution structure | The same inherited reference tables; 1,200 rows are compared in regression tests | Agreement proves consistency with the reference, not agreement with an authenticated original source |
| Precision Clockwork artifact candidates and empty puzzle-table replacements | Explicit configuration choices borrowing other configured pools | Design choices; never recovered missing official data |
| Later-region generic chest pools and inherited weapon/ore defaults | Existing provisional defaults and placeholders | Require review or replacement before source-based acceptance |
| Chest Adventure EXP and regional Primogem overrides | Existing independent configuration policies | Must be reviewed as our own policies; not justified by the inherited table comparison |
| Blossom and transpoint payouts | Existing configuration values; no per-value original-source evidence recorded by this audit | Unverified; do not infer authenticity from plausible values or passing tests |
| CSS chest `type` and `grade` | Server classification using imported metadata, Gadget IDs and prefab names | Derived selector metadata; not a native inheritance hierarchy |

## Next verification

Establish a source baseline with acquisition evidence and hashes, then compare
the exact fields needed by each rule. Keep raw observed values distinct from
derived attributes and explicitly designed reward policies. Rebuild or approve
the affected configuration only after that evidence is available. Do not add
`drop_tag`-based defaults or certify the existing numeric rewards from the
maintained resource repository alone. Compiler and settlement verification can
continue independently; official reward accuracy remains unproven.
