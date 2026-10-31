# Login OpenState policy (7.1)

This change is based on the current upstream mainline. Gameplay settings live in **config.json**
(not a separate game.json on this upstream baseline).

Set `server.game.gameOptions.unlockAllOpenStatesOnLogin`:

```json
{
  "server": {
    "game": {
      "gameOptions": {
        "unlockAllOpenStatesOnLogin": false
      }
    }
  }
}
```

The default is **false**. OpenStates use the resource's actual defaults and
supported quest/level prerequisites. Changing
`server.game.gameOptions.questing.enabled` no longer mass-unlocks features.

Set the new option to **true** to opt into the previous bulk unlock for
sandbox testing, whether questing is enabled or not. The previous
questing-disabled starter Mondstadt map area/point handling remains intact.
Previously saved unlocks are not reset by switching this off; use a genuinely
new account for clean-state testing.

The 7.1 resource spells the OpenState condition field `cond_type`.
It previously deserialized as null (`condType` in Java), leaving empty
conditions that made quest- and level-gated features unlock at login. The
field mapping is corrected with backward-compatible camelCase support.
Non-default empty/unknown-condition states remain locked. Unsupported
offering and reputation conditions no longer count as fulfilled.

The change does not adjust the native Amber compulsory tutorial or force
`quest_remind` (OpenState 7).

The automatic unlock scan checks the player's stored OpenState map by integer OpenState ID, not by resource object.
