# CLI command-wide static audit (2026-10-10)

Scope: all 65 Java files ending in `Command.java` under
`src/main/java/emu/grasscutter/command/commands`, plus `CommandMap`.
This is source review, **not** a claim of compilation, CI success or runtime coverage.

## Shared enforcement

- The dispatcher already enforces the `@Command` permission and target requirement
  before Picocli execution. Do not duplicate those checks in every ONLINE command.
- ONLINE targets now additionally require a non-null, active game session.
  Commands marked NONE or PLAYER may still need operation-level checks.
- The dispatcher has a common Picocli parameter-error and execution-error handler.
  Prefer typed `@Parameters`, `@Option`, and converters over ad hoc parsing.
- Preserve command-local permissions for multi-action commands (`kill`,
  `constellation`, `windy`, `restore`): moving them to one generic root
  permission could expand or silently revoke access.

## Confirmed issues addressed

- `entity`: disambiguate runtime entity ID from config ID, display information
  when no mutation was requested, validate nonnegative properties and target scene,
  clamp current HP to new max, and avoid treating HP increases as damage callbacks.
  The old `--hp 0` mapping to `Float.MAX_VALUE` is removed.
- The common ONLINE requirement now checks active game-session state.

## Follow-up audit findings (no behavior changes made)

1. `group`, `npc`, `quest`, `spawn`, `cutscene` and `debug` access
   a current scene; many already require ONLINE, which does not imply the
   scene is non-null during login/transition. Review command-specific scene guards.
2. `give`, `team`, `npc`, `spawn`, `quest`, `setProp`, `setStats`,
   `tps`, `trialAvatarActivity` and several account commands still contain
   numeric parsing outside Picocli; check whether every parse failure becomes
   a useful command error. Not every parse is necessarily user-facing input.
3. `constellation`, `kill`, `windy`, `restore` and `teleport` use
   operation-specific permission checks. Audit each permission branch before
   consolidation; a shared root permission is **not** automatically equivalent.
4. `se` and `constellation` use scene-transfer-based client refresh.
   Avoid automatically replacing those transfers without a validated packet/ability
   refresh alternative; quest and dungeon state may be disturbed.
5. Some commands perform multiple save calls (`give`, `max`, `teapot`,
   `statue`, `permission`). Trace their callees and persistence semantics
   before removing writes; the database coalescing layer may already mitigate
   repeated saves.
6. `waypoints` reports an obsolete `/tag unlockall` suggestion although the
   canonical command is `tag unlock all`.
7. `EntityCommand` property tests currently cover parsing and validation, not
   runtime Lua callbacks, death behavior, or network packet effects. Add scene
   integration tests before declaring these gameplay effects fully verified.

## Merge gate

Run the full build and unit suite, resolve any failures, and run GitHub CI before
asking for client testing or marking upstream PR #97 ready. The command-wide audit
is deliberately distinct from an exhaustive behavioral test of every command.
