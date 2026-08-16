package emu.grasscutter.server.packet.send;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.game.entity.EntityClientGadget;
import emu.grasscutter.net.proto.AnimatorParameterValueInfoOuterClass.AnimatorParameterValueInfo;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.net.proto.AnimatorParameterValueInfoPairOuterClass.AnimatorParameterValueInfoPair;
import emu.grasscutter.net.proto.EntityAnimatorPairValueInfoNotifyOuterClass.EntityAnimatorPairValueInfoNotify;
import java.util.Collection;
public class PacketEntityAnimatorPairValueInfoNotify extends BasePacket {
    public PacketEntityAnimatorPairValueInfoNotify(EntityClientGadget ownerGadget) {
        super(PacketOpcodes.EntityAnimatorPairValueInfoNotify);

        EntityAnimatorPairValueInfoNotify proto = EntityAnimatorPairValueInfoNotify.newBuilder()
            .addValueInfoPair(AnimatorParameterValueInfoPair.newBuilder()
                .setNameId(ownerGadget.getNameId())
                .setAnimatorPara(AnimatorParameterValueInfo.newBuilder().setIntVal(202)))
                .setEntityId(ownerGadget.getGadgetId())// Replace with dynamic value if necessary // Adding the valueInfo here
            .build();

        this.setData(proto);
    }

    
}
