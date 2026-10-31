package emu.grasscutter.net.proto;

public final class ParsedExtraLevelUpgradeReq {
    private final long avatarGuid;
    private final int targetLevel;
    private final String protoKind;

    public ParsedExtraLevelUpgradeReq(long avatarGuid, int targetLevel) {
        this(avatarGuid, targetLevel, "unknown");
    }

    public ParsedExtraLevelUpgradeReq(long avatarGuid, int targetLevel, String protoKind) {
        this.avatarGuid = avatarGuid;
        this.targetLevel = targetLevel;
        this.protoKind = protoKind == null ? "unknown" : protoKind;
    }

    public long getAvatarGuid() {
        return avatarGuid;
    }

    public int getTargetLevel() {
        return targetLevel;
    }

    public String getProtoKind() {
        return protoKind;
    }
}
