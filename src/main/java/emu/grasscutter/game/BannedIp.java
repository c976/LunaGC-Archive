package emu.grasscutter.game;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import emu.grasscutter.database.DatabaseHelper;

/** An IP address that is refused at login. */
@Entity(value = "banned_ips", useDiscriminator = false)
public class BannedIp {
    /** The address itself is the document id, so a ban is looked up by primary key. */
    @Id private String ip;

    private String reason;

    /** Epoch seconds. Zero means no expiration, including legacy IP bans. */
    private int endTime;
    private long banTime;

    @Deprecated // Morphia only. Do not use.
    public BannedIp() {}

    public BannedIp(String ip, String reason) {
        this(ip, reason, 0);
    }

    public BannedIp(String ip, String reason, int endTime) {
        this.ip = ip;
        this.reason = reason;
        this.endTime = endTime;
        this.banTime = System.currentTimeMillis() / 1000;
    }

    /** Evaluate expiration without mutating the record; legacy records have endTime=0. */
    public boolean isActiveAt(long now) {
        return endTime == 0 || endTime > now;
    }

    /** The existing account-ban protocol uses a signed int; MAX_VALUE represents no expiry. */
    public int accountBanEndTime() {
        return endTime == 0 ? Integer.MAX_VALUE : endTime;
    }

    public int getEndTime() {
        return endTime;
    }

    public String getIp() {
        return this.ip;
    }

    public String getReason() {
        return this.reason;
    }

    public long getBanTime() {
        return this.banTime;
    }

    public void save() {
        DatabaseHelper.saveBannedIp(this);
    }
}
