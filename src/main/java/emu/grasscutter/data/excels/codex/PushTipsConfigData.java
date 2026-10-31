package emu.grasscutter.data.excels.codex;

import emu.grasscutter.data.*;
import lombok.Getter;

/** One push tip (tutorial) and the watcher / reward / codex tab it belongs to. */
@Getter
@ResourceType(name = "PushTipsConfigData.json")
public class PushTipsConfigData extends GameResource {
    public static final String CODEX_UNRECORDED = "CODEX_UNRECORDED";

    private int pushTipsId;
    private String codexType;
    private String pushTipsType;
    private int watcherId;
    private int rewardId;
    private boolean showImmediately;

    @Override
    public int getId() {
        return this.pushTipsId;
    }

    /** Tips without a (known) codex tab are shown as a pop-up only and never listed in the codex. */
    public boolean isRecordedInCodex() {
        return this.codexType != null && !CODEX_UNRECORDED.equals(this.codexType);
    }
}
