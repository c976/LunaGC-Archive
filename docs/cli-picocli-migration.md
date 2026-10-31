# CLI migration (Picocli)

This branch ports the command parser and all converted built-in commands from
`play/rino` onto the current upstream `main`, without bringing over the
unrelated quest, protocol, resource, combat or server changes.

## Command parsing

- Built-in commands declare their grammar using Picocli arguments, options and subcommands.
- The console uses JLine parsing and Picocli completion.
- Player-targeting commands share the `playerSelector` syntax below. `@` clears the remembered target.
- Commands with positional player selectors (`coop`, `ban`, `unban`, `kick`, `mail send`) keep those arguments in place, while using the same centralized identity parser and resolver as ordinary command targets.
- New account names must not contain `@` or `.`; older stored accounts remain readable.
- The command registry rejects duplicate names or aliases.
- Console feedback continues to reach the web-console output capture.

## Account operations

Console-only commands include:

```text
account create <username> [password] [@UID]
account clone <sourceSelector> <newUsername> [@UID]
account delete <accountSelector>
account resetpassword <accountSelector> <new-password> # alias: account passwd
```

Create accepts a username alone, a username and password, a username and @UID, or all
three (password before @UID). Clone accepts an explicit source selector, a new username,
and an optional trailing @UID. Without a reserved UID, one is allocated automatically.
Without a password, a newly created account stays passwordless until one is set via
resetpassword (alias: passwd).

Console-created and automatically registered accounts receive no implicit administrator
permissions. Both use the same configured `account.defaultPermissions` and explicit
grants. Legacy database documents missing a `permissions` field no longer acquire `*`
on load; existing explicitly stored grants remain unchanged.

`account resetpassword <accountSelector> <new-password>` replaces the account password and
atomically removes the existing `token` and `sessionKey` from the MongoDB
account document. The write is synchronous and performed behind the database
write barrier, so success is reported only after the change is committed.
The currently connected player is then disconnected and must log in again.

The clone operation needs an offline source player and a new account name.
It copies player-owned gameplay collections and uses the database writer barrier
to avoid copying unfinished saves; friendships and shared beatmaps are excluded.
The clone inherits the source account password and permissions.

## Canonical command routes

- `waypoints [areaId...]` (`wp`) preserves full-scene unlocking when no area IDs are supplied; supplying IDs only unlocks matching teleport points and their respective areas, not the entire scene. The 7.1-supported teleport point types use the same filter as the `waypoints` tests. Use `waypoints list` to inspect area IDs.
- `spawn <entityId|name> [options]` retains the short alias `s` but drops the misleading `drop` alias; there is no standalone `drop` command.

- `reload [all|config|language|gacha|shop|combine]` reloads the existing hot-reload components. Bare `reload` and `reload all` keep the previous order: config, language selection, gacha banners, shops, and reliquary decomposition. Each named subcommand reloads only that component. This does **not** reload Excel resources, active Quest JSON, scene scripts, or existing player/world state; restart for those changes. Language resources are bundled and cached, so `reload language` reselects the configured language rather than re-reading modified JSON from disk.
- `stop` (alias `shutdown`) exits with success status 0, invoking Grasscutter's shutdown hook: disable plugins, disconnect players/save worlds, terminate the game thread, then drain database writers within a bounded timeout. The existing 1-second fixed game-shutdown wait and 5-second writer deadline are still present; neither guarantees every pending write will finish if the database stalls. A nonzero exit code is reserved for failures.
- `info` now displays only runtime-derived version/player/server information, with no embedded legacy project attributions or fixed Discord invite. The existing permission-based detailed-information visibility is unchanged.


The Picocli command tree keeps one route for each operation:

- `kill` kills the active character; `kill all [sceneId]` kills monsters. The `server.killall` / `server.killall.others` permissions still protect bulk monster removal; no HTTPS keystore password is required. The standalone `killall`, `kill character`, and `suicide` routes are removed.
- `support <pierro|valeriy|blast|all> [on|off]` controls all team-bar support buttons, including Pierro's Ronova-fight support. The former standalone `pierro` and `ronova` commands are removed; all support buttons now use the existing `player.support` / `player.support.others` permissions (not the retired `player.pierro` permissions). The `assist` alias for `support` is retained.
- `restore hp` restores the active team's HP; `restore hp --all` also heals every owned character outside the team (replaces `heal all`). `restore energy` restores active-team energy, and `restore all` restores active-team HP and energy. The old `heal` / `h` entrypoints and `er` / `e` / `energy` aliases are removed.
- `unlock map` unlocks map points and areas without unlocking unrelated features or equipment. `unlock map --force` also includes quest-protected points (use cautiously). Both require `player.unlockall` / `player.unlockall.others`, rather than the retired `prop unlockmap` route under `player.setprop`. `unlock map` now reports newly unlocked waypoints, statues (using the existing statue identification heuristic), other points and region-area flags separately. The full-scene area flag range remains unchanged. `unlock all` retains its broader existing behavior.
- `openstate <name|id> [true|false]` queries or sets an OpenState using a descriptive name, e.g. `openstate gacha`, `openstate multiplayer true`, or `openstate resin false`. `1` and `0` remain accepted. `openstate list [filter] [--page N]` shows up to 20 loaded states per page. Tab completion offers known names actually present in the loaded resources. The legacy name registry is incomplete for newer 7.1 states; these are listed and accessible by numeric ID without inventing names. The former `prop setopenstate` and `prop unsetopenstate` routes/aliases are removed. Writes retain the `player.setprop` permissions and existing save/notify behavior. Some server-enforced states such as resin/45 are reset upon login.
- `sound <sound> [x y z]` plays a scene sound at the current player position or at exactly three coordinates. Incomplete coordinates fail at Picocli parse time. `sound list [filter] [--page N]` shows up to 20 names per page and Tab completion suggests the names observed from scene Lua sound notifications in the current server process. The game resources have no complete sound-name catalog, so the observed list may start empty and must not be presented as exhaustive. Existing `audio` alias is kept.
- `lookup <name...> [--page N]` searches item, avatar, monster and gadget names using the existing relevance ranking and distinct-ID behavior, showing 15 entries per page. `find` and `search` remain aliases.
- `windy` now reports a missing or empty packaged Lua script as an error; it no longer substitutes the UID script or reports a false success.
- `prop <property> [value]` uses Minecraft-style query/set syntax: omit `[value]` to show the current value, or supply it to change the property. `prop set` and the former standalone `setProp` are removed. Map unlocking and open-state changes have separate commands: `unlock map [--force]` and `openstate <name|id> [true|false]`. Existing `player.setprop` and `player.setprop.others` permissions remain unchanged; `help prop` shows the syntax.
- `avatar talent normal|skill|burst|all <level> [--avatar <avatarId>|--all]` and `avatar friendship set <level> [--avatar <avatarId>|--all]` support batch changes to owned characters. Talent `set`/`list` and `extralevel` remain single-character commands; `--all` and `--avatar` are mutually exclusive.
- `avatar stat set <stat> <value> [--avatar <avatarId>]`, `avatar stat lock <stat> [value] [--avatar <avatarId>]`, and `avatar stat unlock <stat> [--avatar <avatarId>]` replace top-level `setStats`, `stats`, and `stat` without aliases. The old `freeze` and `unfreeze` routes remain removed.
- `setSceneTag add <id>`, `remove <id>`, `reset` and `unlock all` are canonical; the old `set`, `del`, `restore` and `unlockall` subcommands are removed.
- `unlock all [@UID]` replaces the standalone `unlockall` command. The existing `player.unlockall` permissions, open-state blacklist and scene-tag exclusion remain unchanged. It is distinct from `tag unlock all`, which operates on scene tags.
- `ban <playerSelector> [endTime] [reason...]` bans an account; `ban <IPv4> [endTime] [reason...]` bans an IP. For IP bans, `endTime` is a future Unix timestamp; omitting it (or using `0`) preserves an indefinite ban, and linked account bans expire with the IP ban. `unban <playerSelector|IPv4>` uses the same parser without subcommands or a keystore key. Account ban/unban requires `server.ban` and, for another account, `server.ban.others`; IP ban/unban requires `server.banip`.
- `kick <playerSelector>` disconnects an online player. It requires `server.kick`, does not accept a keystore key, and no longer has the misleading `restart` alias.
- `announce send <content...>` is the only direct-send route; bare `announce <content...>` is removed. `announce template <templateId>` is the canonical template route; `announce tpl` remains an alias.
- `say <message...>` broadcasts the full literal message to every online player, even if its final word resembles `@UID` or `username@`. `sayto <playerSelector> <message...>` sends only to an online player selected by `@UID`, `username@`, or `username@UID`; targeting someone else checks `server.sendmessage.others`. Both use `server.sendmessage`, neither consumes inline command targets, and Picocli @file expansion remains disabled. The old trailing-selector behavior is removed. The obsolete `sendMessage`, `sendservmsg`, `sendservermessage`, `b`, and `broadcast` aliases remain removed.
- `player list` replaces `list [uid]` and `players`; it displays online players’ nicknames and UIDs. `player info <playerSelector>` also shows account, rank, world level and online state, plus scene/coordinates when online. The console can inspect any player; an in-game user needs `server.playerinfo` to inspect another player. The `--uid` option is not supported.
- `account resetpassword <accountSelector> <new-password>` replaces `account resetpass`; `account passwd` is its sole shortcut. `account delete` and `account clone` also use an explicit selector for the existing account.
- `info` has no `troubleshoot` or `helpme` aliases.
- `battlepass` is the canonical battle-pass command with `bp` as its only alias; `give` retains `g`, `name` retains `rename`, and `namecard` retains `card`. Removed `item`, `giveitem`, `nickname`, and `setnamecard` aliases.
- `coop [guestSelector] <hostSelector>` requires an online host. When the guest is omitted, it uses the current command target (the sender by default in-game); in the console, choose a guest with `target` or provide both selectors. Each explicit selector follows `playerSelector` syntax. Moving another player requires `server.coop.others`.

- `trial change <scheduleId>`, `trial toggledungeon <index|all>` and `trial togglereward <index|all>` replace the old `trialAvatarActivity` / `taa` entrypoints; the existing `player.trialavataractivity` permissions are retained.
- `dungeon <dungeonId>` is the canonical dungeon command. The former `enter_dungeon` and `enterdungeon` names are removed; the `player.enterdungeon` permission key remains unchanged.
- Removed aliases: `pb`, `levelbreak`, `killCharacter`, `mattrack`, `unlockwp`, `item`, `giveitem`, `nickname`, `setnamecard`, and `suicide`. The old `el` shortcut is also gone after migrating `extralevel` into `avatar`.

## Help and invalid input

- `help` lists commands with their aliases and short descriptions; `help <command>` shows the full command usage. Nested command help is available as `help <command> <subcommand>`, for example `help teleport pos` or `help tp pos`.
- Picocli parse errors, including type-conversion errors, show both the error message and the corresponding command or subcommand syntax.

## Review and validation

- Compare the complete command registry and aliases against upstream before merge.
- Compile and run unit tests, including command parser/registry and database writer tests.
- Ensure game clients are not asked to test before CI has passed.
- Keep the upstream PR as a draft until CI succeeds and compatibility exceptions are documented.


### Achievement reward redemption

An achievement's persisted `rewardClaimed` flag is independent of its current progress
and completion state. Legacy `REWARD_TAKEN` statuses are migrated on load. Revoking a
completed achievement clears its progress but never refunds or resets a previously
claimed reward; completing it again restores `REWARD_TAKEN` rather than making it
claimable. Reward requests require the achievement to be finished and unclaimed, and
reject duplicate IDs. Already claimed achievement-goal rewards and duplicate goal IDs
are also rejected without awarding items.


### Bulk achievement commands

`achievement grant all` and `achievement revoke all` now call the achievement
manager's batched operations. Grant-all visits every used achievement ID directly,
sets each unfinished stage to its own required progress, and applies its individual
completion transition and event. It does not use the parent marker or synchronize
progress across stages. Revoke-all iterates each persisted, valid achievement ID and
resets completed states independently. Both preserve reward-claim history and
status-transition counts, recompute the completed count once, queue one achievement
document save, and group unique changed achievements into update packets of at
most 128 entries. Single-ID grant, revoke and progress retain linked-stage behavior.

`achievement revoke all` does not use stage-group membership: every completed
achievement ID is reset directly, even if the final stage marked `isParent` has
not been completed. It does not erase progress in achievements that are already
unfinished. Already claimed rewards remain non-redeemable after reset.

### Battle-pass purchases and paid state

`battlepass buy <levels> [@UID]` (alias: `bp buy`) and the client's `BuyBattlePassLevelReq` now both
use `BattlePassManager.buyLevels`. The manager validates requested levels,
caps the purchase at the remaining levels up to the BP maximum, computes the
price from `BATTLE_PASS_LEVEL_PRICE`, and rejects insufficient funds or a
failed primogem debit without changing BP level. Successful purchases save
both the player's primogem balance and the battle-pass level, retain fractional
BP progress, and send both regular and beyond BP schedule updates.

`battlepass paid [true|false] [@UID]` (alias: `bp paid`) uses the persisted `paid` field for
both state changes and queries. The ordinary 7.1 schedule derives its unlock
status and platform flags from that field. The compatibility helper also
uses the manager setter rather than reflection. New players default to a
free battle pass; previously persisted paid grants remain unchanged.

### Inventory clear preview

`clear <all|weapons|artifacts|materials> [--level N] [--refinement N]
[--rarity N] [--dry-run] [@UID]` supports `--dry-run` to preview the
matching inventory entries and their total stack quantities. For example,
`clear all --dry-run @10001` reports how many stacks and individual items
would be deleted without changing the inventory. The preview applies the
same category, rarity, level, refinement, locked and equipped filters as
the destructive path; `all` also includes furniture, display and virtual
inventory items. Without `--dry-run`, deletion behavior is unchanged.

### Decoupled bulk command selectors

The standalone `achievement grantall` / `achievement revokeall` routes have been
replaced by `achievement grant all` / `achievement revoke all`. The same
`grant` / `revoke` route also accepts a numeric achievement ID; batch manager
operations and localized success keys are unchanged.

`tag unlock all` (also `setSceneTag unlock all`) replaces `tag unlockall`.
`unlock all` replaces the standalone `unlockall` command while retaining its
current permission keys and behavior. These commands deliberately do different
things: `unlock all` leaves scene tags unchanged, but `tag unlock all` enables
scene tags and can create incompatible quest terrain states. Legacy concatenated
commands are removed rather than kept as duplicate aliases. Other uses of
`all` (such as `restore all` and `clear all`) keep their existing meanings.

### Target-aware JLine console prompt

The JLine prompt is `asta> ` when the console has no remembered target.
After selecting `rino@` (or `target rino@`), it displays the account name and UID,
e.g. `rino@10001> `. Entering `@` (or `target`) clears the selection and
restores `asta> `. The prompt uses the same target state as command execution;
per-command inline target overrides do not change that remembered target.
The account name is cached when selecting the target, so rendering the prompt
never loads offline players or queries the database.

### Player selectors

**`playerSelector`:** `@UID` (player UID), `username@` (exact account username), or
`username@UID` (both must match).

Use `playerSelector` for general command targets, `coop` guests and hosts, `ban` / `unban` accounts,
`kick` targets, and `mail send` recipients. The `ban` and `unban` commands also accept IPv4 addresses;
it can resolve accounts with reserved UIDs before character creation. `mail send all`
broadcasts to all players. `@` clears the remembered target, and
`target 10001` selects UID 10001.

In `account create` and `account clone`, a separate trailing `@UID` argument reserves
an optional UID for the new account. Existing account selectors use `@UID`, `username@`, or `username@UID`,
including accounts with a reserved UID but no character save. Username/UID matching
is exact.

### GM cutscene playback safety

`cutscene list [search]` is a resource lookup and no longer requires an online
target. The results are ordered by numeric cutscene ID, with the existing 30
result cap. `cutscene <id>` still requires an online player; an ID absent from
`CutsceneExcelConfigData` is rejected unless `--force` is supplied. Invalid
non-positive IDs are always rejected. `--force` only bypasses the Excel lookup,
not online or gameplay-safety checks.

Manually initiated cutscenes are tracked per game session. Their finish
notifications still receive the normal client acknowledgement, but never
complete pending scene scripts or trigger the Spiral Abyss half-time team
swap. Manual playback of an ID already awaited by scene scripts or the Abyss
transition is refused. Pending manual IDs are cleared upon acknowledgement
or after a bounded timeout; disconnecting ends the session-scoped tracking.
This prevents manual GM playback from being mistaken for a quest or tower
completion, but does not claim to make unknown client-side IDs playable.

### Daily commission resource diagnostics

`dailytask support` (or `dt support`) reports commission definition and Lua
resource coverage by city. It only inspects loaded game resources and no longer
requires an online player or a daily-task database record; it works from the
server console. Other `dailytask` operations (`list`, `load`, `reset`, `city`,
`finish`, and `bonus`) still require an online player and reject a missing
or offline target before loading any daily-task state. The root command can
print help without a selected player.


### Avatar command consolidation

Character-specific commands now share one `avatar` command group:

```text
avatar list
avatar constellation set <0-6> [--avatar <avatarId> | --all]
avatar constellation reset [--avatar <avatarId> | --all]
avatar talent set <talentId> <level> [--avatar <avatarId>]
avatar talent normal <level> [--avatar <avatarId> | --all] # alias: n
avatar talent skill <level> [--avatar <avatarId> | --all]  # alias: e
avatar talent burst <level> [--avatar <avatarId> | --all]  # alias: q
avatar talent all <level> [--avatar <avatarId> | --all]
avatar talent list [--avatar <avatarId>]
avatar stat set <stat> <value> [--avatar <avatarId>]
avatar stat lock <stat> [value] [--avatar <avatarId>]
avatar stat unlock <stat> [--avatar <avatarId>]
avatar friendship set <level> [--avatar <avatarId> | --all]
avatar extralevel [--avatar <avatarId>]
avatar max [--avatar <avatarId> | --all]
```

`--avatar` selects a character owned by the target player by avatar ID. Without it,
single-character operations use the currently active character. `--all` selects
every owned character, and is mutually exclusive with `--avatar`. The
`talent all` operation means all skills of one character, not every character.
`avatar stat` has no shorthand alias. Stat operations keep `player.setstats[.others]`
permissions. `set` changes the current in-memory combat value; `lock` uses a
transient fight-property override; `unlock` removes that override. For selected
off-team avatars, `set` sends an avatar fight-property update instead of an entity
scene packet. The override is not stored in the character save.
The standalone `constellation`, `talent`, `extralevel` (`el`),
`setFetterLevel`, and `max` commands and their aliases were removed.

Permissions are enforced per operation. Constellation set/reset retain distinct
`player.setconstellation[.others]` and `player.resetconstellation[.others]`
permissions. Talents use `player.settalent[.others]`; friendship uses
`player.setfetterlevel[.others]`; extra-level upgrades use `player.give[.others]`;
max uses `player.max[.others]`. The `avatar list` operation currently
requires `player.give[.others]`.

The target must be online. Specifying `--avatar` does not allow modification of
unowned characters. Extra-level upgrades still consume their required materials;
max retains its level-90 cap. `Avatar.forceConstellationLevel` saves each changed
avatar. The existing scene transfer when lowering constellation levels or
changing all characters remains in place until the client refresh behavior
is verified; this is not a resolved scene-transition issue.
