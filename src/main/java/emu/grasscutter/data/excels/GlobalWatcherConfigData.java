package emu.grasscutter.data.excels;

import emu.grasscutter.data.*;
import lombok.Getter;

/** Global watcher definitions; the push tips (tutorial codex) reference these by watcherId. */
@Getter
@ResourceType(name = "GlobalWatcherConfigData.json")
public class GlobalWatcherConfigData extends GameResource {
    private int id;
    private boolean isDisuse;
    private BattlePassMissionData.TriggerConfig triggerConfig;
    private int progress;

    @Override
    public int getId() {
        return this.id;
    }

    public boolean isUsed() {
        return !this.isDisuse;
    }
}
