# Map fog configuration (7.1)

Map fog is enabled by default. In `config.json`:

```json
{
  "server": {
    "game": {
      "gameOptions": {
        "mapFogEnabled": true
      }
    }
  }
}
```

- `true` (default): show only the areas recorded as explored for this player. Activating a Statue of the Seven unlocks its region through the existing quest/scene-area logic.
- `false`: show every map area on the client without unlocking teleport points, granting rewards, or modifying the player's stored exploration progress.
- The switch is reversible: alternating `true` and `false` restores the same explored regions when fog is enabled again, as long as the underlying exploration state is unchanged.
- Changes to `config.json` take effect after a server restart and a fresh scene-area response (relogin is sufficient).
- Configuration version 18 writes the new field into existing `config.json` files on upgrade. A missing setting also defaults to `true`.

The setting applies consistently to `GetSceneAreaRsp` and `GetScenePointRsp`. Region-access restrictions and world-scene allow-lists are unchanged.

This feature uses the current player's recorded exploration state as-is. It does not migrate, repair, or attempt to infer progress from old saves.

## Starter Statue of the Seven (scene 3, point 7)

The Mondstadt starter area (`scene=3, area=1`) belongs to Statue of the Seven
`(scene=3, point=7)` in the native `scene3_point.json`. When map fog is enabled,
area 1 is visible **only when point 7 has actually been unlocked** and is not
force-locked. An empty scene-area snapshot no longer reveals area 1 by default.

Disabling the quest system never auto-activates point 7. The quest that admits
the player to the open-world scene also no longer counts as exploration of area 1.
Both scene-area and scene-point snapshots apply the same visibility policy.
If a quest or old build stored area 1 early, those saved flags remain untouched
but are hidden until the statue activates; activation sends a fresh area notify.

**Existing saves:** Old versions also added point 7 itself to player unlock
records at login when questing was disabled. Because that record is
indistinguishable from genuine activation, this change will not silently
relock old accounts. Use a fresh account to verify default behavior or inspect
an existing account's saved point and quest status before manually correcting it.
