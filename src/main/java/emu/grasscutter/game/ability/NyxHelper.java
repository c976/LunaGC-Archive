/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.protobuf.ByteString
 *  com.google.protobuf.CodedInputStream
 *  emu.grasscutter.Grasscutter
 *  emu.grasscutter.data.binout.AbilityData
 *  emu.grasscutter.game.ability.Ability
 *  emu.grasscutter.game.avatar.Avatar
 *  emu.grasscutter.game.entity.EntityAvatar
 *  emu.grasscutter.game.entity.GameEntity
 *  emu.grasscutter.game.player.Player
 *  emu.grasscutter.game.props.FightProperty
 *  emu.grasscutter.net.packet.BasePacket
 *  emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass$AbilityInvokeEntry
 *  emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify
 *  it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap
 */
package emu.grasscutter.game.ability;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.AbilityData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.MavuikaSpiritHelper;
import emu.grasscutter.game.ability.SkirkCunningBridge;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import java.util.Map;

public final class NyxHelper {
    public static final String NYX_KEY = "NyxValue";
    public static final String NYX_MAX_KEY = "NyxValueMax";
    public static final String NYX_MIN_KEY = "NyxValueMin";
    public static final int SKIRK_AVATAR_ID = 10000114;
    private static final float SKIRK_NYX_MAX = 100.0f;

    private NyxHelper() {
    }

    public static boolean isSkirkEntity(GameEntity gameEntity) {
        EntityAvatar entityAvatar;
        if (!(gameEntity instanceof EntityAvatar) || (entityAvatar = (EntityAvatar)gameEntity).getAvatar() == null) {
            return false;
        }
        return entityAvatar.getAvatar().getAvatarId() == 10000114;
    }

    public static boolean isSkirkAbility(Ability ability) {
        AbilityData abilityData;
        if (ability == null) {
            return false;
        }
        try {
            String string;
            abilityData = ability.getData();
            if (abilityData != null && abilityData.abilityName != null && ((string = abilityData.abilityName).contains("Skirk") || string.contains("SKIRK"))) {
                return true;
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        GameEntity owner = ability.getOwner();
        return NyxHelper.isSkirkEntity(owner);
    }

    public static float getNyx(GameEntity gameEntity) {
        if (gameEntity == null) {
            return 0.0f;
        }
        try {
            Float f = (Float)gameEntity.getGlobalAbilityValues().get(NYX_KEY);
            if (f != null && !Float.isNaN(f.floatValue())) {
                return f.floatValue();
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        try {
            float f = gameEntity.getNyxValue();
            return Float.isNaN(f) ? 0.0f : f;
        }
        catch (Throwable throwable) {
            return 0.0f;
        }
    }

    public static float clampNyx(GameEntity gameEntity, float f) {
        float f2 = 100.0f;
        if (!NyxHelper.isSkirkEntity(gameEntity)) {
            Float f3 = null;
            try {
                f3 = (Float)gameEntity.getGlobalAbilityValues().get(NYX_MAX_KEY);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            if (f3 != null && f3.floatValue() > 0.0f) {
                f2 = f3.floatValue();
            }
        }
        if (Float.isNaN(f)) {
            return 0.0f;
        }
        if (f < 0.0f) {
            return 0.0f;
        }
        if (f > f2) {
            return f2;
        }
        return f;
    }

    public static void ensureSkirkNyxBounds(GameEntity gameEntity) {
        EntityAvatar entityAvatar;
        if (!NyxHelper.isSkirkEntity(gameEntity)) {
            return;
        }
        Map map = gameEntity.getGlobalAbilityValues();
        map.put(NYX_MAX_KEY, Float.valueOf(100.0f));
        map.put(NYX_MIN_KEY, Float.valueOf(0.0f));
        gameEntity.setFightProperty(FightProperty.FIGHT_PROP_MAX_SPECIAL_ENERGY, 100.0f);
        gameEntity.setFightProperty(FightProperty.FIGHT_PROP_START_SPECIAL_ENERGY, 50.0f);
        if (gameEntity instanceof EntityAvatar && (entityAvatar = (EntityAvatar)gameEntity).getAvatar() != null) {
            Avatar avatar = entityAvatar.getAvatar();
            avatar.setFightProperty(FightProperty.FIGHT_PROP_MAX_SPECIAL_ENERGY, 100.0f);
            avatar.setFightProperty(FightProperty.FIGHT_PROP_START_SPECIAL_ENERGY, 50.0f);
        }
    }

    public static void setNyxForInvoke(Player player, GameEntity gameEntity, float f) {
        if (gameEntity == null) {
            return;
        }
        float oldNyx = NyxHelper.getNyx(gameEntity);
        float f2 = NyxHelper.clampNyx(gameEntity, f);
        if (f2 < oldNyx) {
            emu.grasscutter.game.ability.NightsoulStaminaExempt.markNyxCostActive(gameEntity);
        }
        Player hookPlayer = player;
        if (hookPlayer == null && gameEntity.getScene() != null) {
            hookPlayer = gameEntity.getScene().getHost();
        }
        try {
            MavuikaSpiritHelper.beforeGlobalFloatPut(hookPlayer, gameEntity, NYX_KEY, f2);
        } catch (Throwable ignored) {
        }
        gameEntity.getGlobalAbilityValues().put(NYX_KEY, Float.valueOf(f2));
        gameEntity.onAbilityValueUpdate();
        if (player != null) {
            player.sendPacket((BasePacket)new PacketServerGlobalValueChangeNotify(gameEntity, NYX_KEY, f2));
        } else if (gameEntity.getScene() != null && gameEntity.getScene().getHost() != null) {
            gameEntity.getScene().getHost().sendPacket((BasePacket)new PacketServerGlobalValueChangeNotify(gameEntity, NYX_KEY, f2));
        }
    }

    public static Object2FloatOpenHashMap<String> buildContext(Ability ability, GameEntity gameEntity) {
        Object2FloatOpenHashMap object2FloatOpenHashMap = new Object2FloatOpenHashMap();
        if (gameEntity != null) {
            for (FightProperty fightProperty : FightProperty.values()) {
                object2FloatOpenHashMap.put((Object)fightProperty.name(), gameEntity.getFightProperty(fightProperty));
            }
            for (Map.Entry entry : gameEntity.getGlobalAbilityValues().entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) continue;
                object2FloatOpenHashMap.put((Object)((String)entry.getKey()), ((Float)entry.getValue()).floatValue());
            }
        }
        if (ability != null && ability.getAbilitySpecials() != null) {
            object2FloatOpenHashMap.putAll((Map)ability.getAbilitySpecials());
        }
        return object2FloatOpenHashMap;
    }

    public static void applyNyx(GameEntity gameEntity, float f, boolean bl, Ability ability, Player player) {
        if (gameEntity == null) {
            return;
        }
        float oldNyx = NyxHelper.getNyx(gameEntity);
        float f2 = NyxHelper.clampNyx(gameEntity, f);
        if (f2 < oldNyx) {
            emu.grasscutter.game.ability.NightsoulStaminaExempt.markNyxCostActive(gameEntity);
        }
        Player hookPlayer = player;
        if (hookPlayer == null && ability != null) {
            hookPlayer = ability.getPlayerOwner();
        }
        if (hookPlayer == null && gameEntity.getScene() != null) {
            hookPlayer = gameEntity.getScene().getHost();
        }
        try {
            MavuikaSpiritHelper.beforeGlobalFloatPut(hookPlayer, gameEntity, NYX_KEY, f2);
        } catch (Throwable ignored) {
        }
        gameEntity.getGlobalAbilityValues().put(NYX_KEY, Float.valueOf(f2));
        gameEntity.onAbilityValueUpdate();
        if (bl) {
            Player player2 = player;
            if (player2 == null && ability != null) {
                player2 = ability.getPlayerOwner();
            }
            if (player2 == null && gameEntity.getScene() != null) {
                player2 = gameEntity.getScene().getHost();
            }
            if (player2 != null) {
                player2.sendPacket((BasePacket)new PacketServerGlobalValueChangeNotify(gameEntity, NYX_KEY, f2));
            }
        }
    }

    public static boolean shouldNotifyClient(GameEntity gameEntity) {
        return true;
    }

    public static boolean shouldExecuteNyxSet(Ability ability) {
        return true;
    }

    public static boolean shouldExecuteNyxAdd(Ability ability, float f) {
        return true;
    }

    public static boolean shouldExecuteNyxAction(Ability ability) {
        return true;
    }

    public static void addNyx(Ability ability, GameEntity gameEntity, float f) {
        if (gameEntity == null || f == 0.0f) {
            return;
        }
        if (NyxHelper.isSkirkEntity(gameEntity) && gameEntity instanceof EntityAvatar) {
            Player player;
            Player player2 = player = ability != null ? ability.getPlayerOwner() : null;
            if (player == null && gameEntity.getScene() != null) {
                player = gameEntity.getScene().getHost();
            }
            SkirkCunningBridge.onNyxAdd(player, (EntityAvatar)gameEntity, f);
            return;
        }
        NyxHelper.ensureSkirkNyxBounds(gameEntity);
        NyxHelper.applyNyx(gameEntity, NyxHelper.getNyx(gameEntity) + f, NyxHelper.shouldNotifyClient(gameEntity), ability, null);
    }

    public static void syncFromSpecialEnergyInvoke(Player player, GameEntity gameEntity, float f) {
        if (player == null || gameEntity == null || f == 0.0f || !NyxHelper.isSkirkEntity(gameEntity)) {
            return;
        }
        if (gameEntity instanceof EntityAvatar) {
            SkirkCunningBridge.applyDelta(player, (EntityAvatar)gameEntity, f);
        }
    }

    public static void initSkirkNyxDisplay(Player player, EntityAvatar entityAvatar) {
        SkirkCunningBridge.onEnterScene(player, entityAvatar);
    }

    public static void handleChangeNyxValue(Player player, AbilityInvokeEntryOuterClass.AbilityInvokeEntry abilityInvokeEntry) {
        if (player == null || abilityInvokeEntry == null || player.getScene() == null) {
            return;
        }
        GameEntity gameEntity = player.getScene().getEntityById(abilityInvokeEntry.getEntityId());
        if (gameEntity == null) {
            return;
        }
        float f = NyxHelper.getNyx(gameEntity);
        float f2 = 0.0f;
        if (gameEntity instanceof EntityAvatar) {
            f2 = ((EntityAvatar)gameEntity).getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY);
        }
        float f3 = f > f2 ? f : f2;
        float f4 = Float.NaN;
        try {
            float[] fArray = NyxHelper.parseChangeNyxFloats(abilityInvokeEntry.getAbilityData());
            float f5 = fArray[0];
            float f6 = fArray[1];
            if (f5 != 0.0f) {
                f4 = NyxHelper.clampNyx(gameEntity, f5);
            } else if (f6 != 0.0f) {
                f4 = NyxHelper.clampNyx(gameEntity, f3 + f6);
            }
        }
        catch (Throwable throwable) {
            Grasscutter.getLogger().warn("ChangeNyxValue parse failed: {}", (Object)throwable.toString());
            return;
        }
        if (Float.isNaN(f4)) {
            return;
        }
        NyxHelper.ensureSkirkNyxBounds(gameEntity);
        if (NyxHelper.isSkirkEntity(gameEntity) && gameEntity instanceof EntityAvatar) {
            SkirkCunningBridge.syncFromClient(player, (EntityAvatar)gameEntity, f4);
        } else {
            NyxHelper.setNyxForInvoke(player, gameEntity, f4);
        }
        Grasscutter.getLogger().info("ChangeNyxValue entity=" + abilityInvokeEntry.getEntityId() + " old=" + f + " special=" + f2 + " base=" + f3 + " target=" + f4);
    }

    private static float[] parseChangeNyxFloats(ByteString byteString) throws Exception {
        float f = 0.0f;
        float f2 = 0.0f;
        if (byteString == null || byteString.isEmpty()) {
            return new float[]{f, f2};
        }
        CodedInputStream codedInputStream = byteString.newCodedInput();
        while (!codedInputStream.isAtEnd()) {
            int n = codedInputStream.readTag();
            int n2 = n >>> 3;
            int n3 = n & 7;
            if (n3 == 5) {
                float f3 = codedInputStream.readFloat();
                if (n2 == 1) {
                    f = f3;
                    continue;
                }
                if (n2 != 2) continue;
                f2 = f3;
                continue;
            }
            if (n3 == 0) {
                codedInputStream.readRawVarint64();
                continue;
            }
            if (n3 == 1) {
                codedInputStream.readRawLittleEndian64();
                continue;
            }
            if (n3 == 2) {
                codedInputStream.readBytes();
                continue;
            }
            codedInputStream.skipField(n);
        }
        return new float[]{f, f2};
    }
}

