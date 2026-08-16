package emu.grasscutter.server.packet.send;

import com.google.gson.JsonArray;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.gacha.GachaSystem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.*;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class PacketGetGameplayRecommendationRsp extends BasePacket {

    public PacketGetGameplayRecommendationRsp() {
        super(PacketOpcodes.GetGameplayRecommendationRsp);
this.setData(GetGameplayRecommendationRspOuterClass.GetGameplayRecommendationRsp.newBuilder().build());
    }

  /*   public PacketGetGameplayRecommendationRsp(GetGameplayRecommendationReqOuterClass.GetGameplayRecommendationReq req, GameplayRecommendationSkillRequestOuterClass.GameplayRecommendationSkillRequest skillRequest) {
        super(PacketOpcodes.GetGameplayRecommendationRsp);
        GetGameplayRecommendationRspOuterClass.GetGameplayRecommendationRsp.Builder rsp = GetGameplayRecommendationRspOuterClass.GetGameplayRecommendationRsp.newBuilder();
        String apiUrl = "https://genshin.projektark.xyz/api/gameplaySkillRecommendation?id="+req.getAvatarId();
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Grasscutter.getLogger().info("Data: " + response.body());

                JsonObject jsonObject = JsonParser.parseString(response.body()).getAsJsonObject();
                JsonArray jsonArray = jsonObject.get("ids").getAsJsonArray();
                int depot = jsonObject.get("skillDepot").getAsInt();
                int[] ids = new int[jsonArray.size()];
                for (int i = 0; i < jsonArray.size(); i++) {
                    ids[i] = jsonArray.get(i).getAsInt();
                }
                List<Integer> skillIdList = Arrays.stream(ids).boxed().toList();
                rsp.setSkillResponse(GameplayRecommendationSkillResponseOuterClass.GameplayRecommendationSkillResponse.newBuilder()
                        .addAllSkillIdList(skillIdList).setSkillDepotId(depot).build()).setAvatarId(req.getAvatarId());
            } else {
                Grasscutter.getLogger().error("Error: " + response.statusCode());
            }
        } catch (Exception e) {
            Grasscutter.getLogger().error(e.getMessage());
        }
        setData(rsp);
    }

    public PacketGetGameplayRecommendationRsp(GetGameplayRecommendationReqOuterClass.GetGameplayRecommendationReq req, GameplayRecommendationReliquarySetRequestOuterClass.GameplayRecommendationReliquarySetRequest setRequest) {
        super(PacketOpcodes.GetGameplayRecommendationRsp);
        GetGameplayRecommendationRspOuterClass.GetGameplayRecommendationRsp.Builder rsp = GetGameplayRecommendationRspOuterClass.GetGameplayRecommendationRsp.newBuilder();
        String apiUrl = "https://genshin.projektark.xyz/api/gameplaySetRecommendation?id="+req.getAvatarId();
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Grasscutter.getLogger().info("Data: " + response.body());

                JsonObject jsonObject = JsonParser.parseString(response.body()).getAsJsonObject();
                JsonArray jsonArray = jsonObject.get("ids").getAsJsonArray();
                int[] ids = new int[jsonArray.size()];
                for (int i = 0; i < jsonArray.size(); i++) {
                    ids[i] = jsonArray.get(i).getAsInt();
                }
                List<Integer> skillIdList = Arrays.stream(ids).boxed().toList();

            } else {
                Grasscutter.getLogger().error("Error: " + response.statusCode());
            }
        } catch (Exception e) {
            Grasscutter.getLogger().error(e.getMessage());
        }

        rsp.setReliquarySetResponse(GameplayRecommendationReliquarySetResponseOuterClass.GameplayRecommendationReliquarySetResponse.newBuilder());
        rsp.setAvatarId(req.getAvatarId());
        GameplayRecommendationReliquarySetResponseOuterClass.FCABLBAALDJ reliquarySet = GameplayRecommendationReliquarySetResponseOuterClass.FCABLBAALDJ.newBuilder()
                .setId(15038).build();
        rsp.getReliquarySetResponseBuilder().addOGIKFCEFMNA(reliquarySet).build();
        setData(rsp);
        */
    }
