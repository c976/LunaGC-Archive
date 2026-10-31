# Reconnecting out of a dungeon

## The problem

A dungeon scene is deregistered as soon as its last player leaves (`Scene.removePlayer` →
`World.deregisterScene`, unless `dontDestroyWhenEmpty`), but `players.sceneId` still names that
dungeon. The next login therefore rebuilds an **empty shell**: `World.getSceneById()` creates the
scene, and nothing puts a `DungeonManager` on it - `DungeonManager` is only ever constructed by
`DungeonSystem.enterDungeon`/`handoffDungeon`/`restartDungeon`.

`HandlerPostEnterSceneReq` then takes its `SCENE_DUNGEON` branch, which starts a dungeon only
`if (dungeonManager != null)`. So the player stands in a dungeon that is not a dungeon: no
`startDungeon()`, no settle, no trial team, and no way back to the world except quitting to the
main menu and re-entering.

`fa0b5c0` fixed exactly this for TPS dungeons by redirecting such a login to Teyvat. Every other
dungeon was still restored into the shell.

## Why it strands a quest

Plot dungeons gate their steps on dungeon events, and the recovery path for a step that should
restart is a **fail condition on returning to the world**. Subquest 35722 (dungeon 2004, scene
20020) is typical:

```
finishCond  QUEST_CONTENT_LUA_NOTIFY  "220020001"   (damage the group's monster below 20% HP)
failCond    QUEST_CONTENT_ENTER_MY_WORLD  [3, 0]    (back in Mondstadt)
failExec    QUEST_EXEC_ROLLBACK_QUEST  ["35721"]
isRewind    true
```

Landing in the shell means `ENTER_MY_WORLD` is never queued, so `GameQuest.fail()` never runs and
the rollback never happens - the segment is stuck. Landing in the world fires it all by itself:

`HandlerPostEnterSceneReq` (`SCENE_WORLD`) → `QUEST_CONTENT_ENTER_MY_WORLD` →
`GameMainQuest.tryFailSubQuests` → `ContentEnterMyWorld` (`sceneId == 3`) → `GameQuest.fail()` →
`QUEST_EXEC_ROLLBACK_QUEST` → `GameQuest.rewind()` clears 35722 back to `UNSTARTED` and re-starts
35721, whose `finishCond` is `QUEST_CONTENT_ENTER_DUNGEON [2004, 67]`. Walking into the entrance
again replays the segment cleanly.

**No quest logic was added.** The rollback already lives in the quest data; the login redirect is
what lets the existing chain run.

## The fix

- `Player.onLogin()`: next to the TPS and home sanitizers, a third one - if the saved scene is a
  `SCENE_DUNGEON`, log into the world the player entered the dungeon from instead.
- `DungeonSystem.enterDungeon()`: that origin used to be recorded only on the `Scene`, which does
  not survive the logout, so it is now also recorded on the `Player`. It gets its own
  `prevSceneForDungeon` / `prevPosForDungeon` pair rather than reusing `prevScene`, because
  entering a home overwrites `prevScene` (`HomeWorldMPSystem`) and the two would then come from
  different scenes - which would place the player at coordinates that belong elsewhere. Only world
  scenes are recorded, so a home, activity or dungeon-to-dungeon handoff cannot overwrite a good
  pair with an unusable one.

## Reproducing it

Standing in a dungeon, force-kill the server (`taskkill /F` - a graceful shutdown saves
`sceneId` as the world and hides the bug), then start it again and let the client reconnect.

| step | `sceneId` | 35721 | 35722 |
| --- | --- | --- | --- |
| saved before the kill | 20020 | FINISHED | UNFINISHED |
| login, before this fix | 20020 (shell) | FINISHED | UNFINISHED |
| login, after this fix | 3 | UNFINISHED, progress cleared | UNSTARTED |

## Not covered

The story party a plot dungeon asks for is still not restored, because there is nothing to
restore it from: `trialAvatarList` is empty on all 33217 quest rows, and `beginExec` carries a
`type` on only 10 of them (`acceptCond` 33216, `finishExec` 12327, `failExec` 3895). Whatever the
client sets up on a first entry is therefore the only path that can produce it - this is a
resource-data gap, not a code bug. What this change guarantees is that the segment restarts
instead of dead-ending.
