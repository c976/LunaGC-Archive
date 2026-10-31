# Runtime configuration ownership

Server operators own gameplay policy. The default configuration is bundled in
the JAR but **must not overwrite operator edits**.

```text
AstaPS/
├── config.json                  # Network, database, and global game options
├── rewards/
│   ├── registry.tsv             # Resource names to native item IDs
│   ├── loot.yaml                # Reward pools and roll definitions
│   ├── transpoints.lcss         # First activation of map points
│   └── *.lcss                  # Other source-specific reward rules
├── data/                        # Other server tables and runtime data
└── resources/                   # Supported client-extracted game resources
```

## Authority

- `resources/` provides source identity, native item and scene IDs, and
  quest/scene metadata. It does not silently dictate operator reward amounts.
- `rewards/` provides the configurable loot policy: the external
  `loot.yaml`, `registry.tsv`, and all direct `*.lcss` files.
  `RewardConfiguration` compiles all files before publishing an updated
  configuration. A failed `reload rewards` retains the current compiled
  configuration.
- `data/` contains other server tables, plus some runtime data. Its individual
  loaders have their own documented precedence rules; do not silently blend
  their values with LootCSS.
- The player database and `reward_grant_receipts` store progress, pending
  activations, and delivery evidence. They are not editable reward policy.

At first startup, AstaPS copies packaged defaults into `rewards/`.
Subsequent startup and reload operations preserve that directory. See
`docs/rewards/external-configuration.md` for editing and reload commands.

## Boundaries between reward sources

- Teleport waypoints, Statues of the Seven, and manually activated dungeon
  entrances select first-activation rewards through `transpoints.lcss`.
  Reward failures must not withdraw progression. See
  `docs/trans-point-rewards.md`.
- Ordinary world chests select LootCSS reward rules according to their
  supported chest attributes and native scene metadata. Failure to admit a
  reward batch preserves the unclaimed chest.
- Ley-line blossoms and resin-paid boss rewards remain tied to their payment
  and claim admission. A failed preflight must not charge resin.
- Dungeon completion rewards and other independent progression systems keep
  their source-specific admission and receipts.

The bundled reward values have not all been verified against the Global 7.1
client. Treat them as server-owned defaults, not authoritative official drop
rates. See `docs/rewards/source-provenance.md`.

Do not reintroduce legacy reward file paths or an implicit native reward-table
fallback. Unknown attributes, malformed selectors, and invalid item references
must cause an explicit configuration error.
