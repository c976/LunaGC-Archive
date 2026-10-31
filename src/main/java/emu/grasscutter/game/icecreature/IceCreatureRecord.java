package emu.grasscutter.game.icecreature;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Transient;
import emu.grasscutter.game.world.Position;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** One Ice Creature a player built, saved with the player so it stands again after a relog. */
@Entity
@Getter
@Setter
public class IceCreatureRecord {
    private long guid;
    private int gadgetId;
    private int type;
    private int sceneId;
    private int areaId;
    private int mapLayerId;
    private float posX, posY, posZ;
    private float rotX, rotY, rotZ;
    private int createTime;
    private int likeNum;
    private int cost;
    /** IceCreatureBuildReq field 11, echoed back in the gadget info; its meaning is unknown. */
    private boolean flag;
    /** The serialized beacon/decorate choice from the build request, echoed back as-is. */
    private byte[] buildDetail;
    /** Serialized AbilityScalarValueEntry values the client asked the gadget to start with. */
    private List<byte[]> globalValues = new ArrayList<>();

    /** The live gadget, while one stands in a loaded scene. */
    @Transient private int entityId;

    public Position getPos() {
        return new Position(this.posX, this.posY, this.posZ);
    }

    public Position getRot() {
        return new Position(this.rotX, this.rotY, this.rotZ);
    }

    public void setPos(Position pos) {
        this.posX = pos.getX();
        this.posY = pos.getY();
        this.posZ = pos.getZ();
    }

    public void setRot(Position rot) {
        this.rotX = rot.getX();
        this.rotY = rot.getY();
        this.rotZ = rot.getZ();
    }

    public List<byte[]> getGlobalValues() {
        if (this.globalValues == null) this.globalValues = new ArrayList<>();
        return this.globalValues;
    }
}
