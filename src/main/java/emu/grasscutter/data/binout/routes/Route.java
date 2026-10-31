package emu.grasscutter.data.binout.routes;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.net.proto.RouteOuterClass;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Route {
    @SerializedName(value = "localId", alternate = "_localId")
    private int localId;

    @SerializedName(value = "name", alternate = "_name")
    private String name;

    @SerializedName(value = "type", alternate = "_type")
    private RouteType type = RouteType.Unknown;

    @SerializedName(value = "points", alternate = "_points")
    private RoutePoint[] points;

    @SerializedName(value = "arriveRange", alternate = "_arriveRange")
    private float arriveRange; // optional

    @SerializedName(value = "rotType", alternate = "_rotType")
    private RotType rotType; // optional

    @SerializedName(value = "rotAngleType", alternate = "_rotAngleType")
    private RotAngleType rotAngleType; // optional

    public RouteOuterClass.Route toProto() {
        val builder = RouteOuterClass.Route.newBuilder().setRouteType(type.getValue());
        if (points != null) {
            for (var routePoint : points) {
                builder.addRoutePoints(routePoint.toProto().setArriveRange(arriveRange));
            }
        }
        return builder.build();
    }
}
