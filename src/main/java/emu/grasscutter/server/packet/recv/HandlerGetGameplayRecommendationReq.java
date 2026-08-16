package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetGameplayRecommendationReqOuterClass;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketGetGameplayRecommendationRsp;
@Opcodes(PacketOpcodes.GetGameplayRecommendationReq)
public class HandlerGetGameplayRecommendationReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        GetGameplayRecommendationReqOuterClass.GetGameplayRecommendationReq req = GetGameplayRecommendationReqOuterClass.GetGameplayRecommendationReq.parseFrom(payload);
       // if(req.hasSkillRequest()){
       //     session.send(new PacketGetGameplayRecommendationRsp(req,req.getSkillRequest()));
      //  }else if(req.hasReliquarySetRequest()){
          //  session.send(new PacketGetGameplayRecommendationRsp(req,req.getReliquarySetRequest()));
        }

   // }
}
