package emu.grasscutter.data.binout.routes;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.proto.RoutePointOuterClass;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoutePoint {
    @SerializedName(value = "pos", alternate = "_pos")
    private Position pos;

    @SerializedName(value = "speedLevel", alternate = "_speedLevel")
    private int speedLevel; // optional

    @SerializedName(value = "waitTime", alternate = "_waitTime")
    private float waitTime; // optional

    @SerializedName(value = "targetVelocity", alternate = "_targetVelocity")
    private float targetVelocity; // optional

    @SerializedName(value = "hasReachEvent", alternate = "_hasReachEvent")
    private boolean hasReachEvent; // optional

    @SerializedName(value = "rotRoundLeaveDir", alternate = "_rotRoundLeaveDir")
    private Position rotRoundLeaveDir;

    @SerializedName(value = "reachStop", alternate = "_reachStop")
    private boolean reachStop;

    // rotRoundReachDir //optional Pos with optional values
    // rotRoundLeaveDir //optional Pos with optional values

    public RoutePointOuterClass.RoutePoint.Builder toProto() {
        val builder = RoutePointOuterClass.RoutePoint.newBuilder().setPosition(pos.toProto());
        if (waitTime != 0) {
            builder.setTime(waitTime);
        } else if (targetVelocity != 0) {
            builder.setVelocity(targetVelocity);
        }

        return builder;
    }
}
