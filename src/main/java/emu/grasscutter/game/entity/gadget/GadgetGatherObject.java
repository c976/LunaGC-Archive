package emu.grasscutter.game.entity.gadget;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.GatherData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityBaseGadget;
import emu.grasscutter.game.entity.EntityClientGadget;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.EntityItem;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.world.OpenWorldSpawnHelper;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass;
import emu.grasscutter.net.proto.GatherGadgetInfoOuterClass;
import emu.grasscutter.net.proto.InteractTypeOuterClass;
import emu.grasscutter.net.proto.SceneGadgetInfoOuterClass;
import emu.grasscutter.net.proto.VisionTypeOuterClass;
import emu.grasscutter.scripts.data.ScriptArgs;
import emu.grasscutter.server.packet.send.PacketGadgetInteractRsp;
import emu.grasscutter.server.packet.send.PacketSceneEntityDisappearNotify;
import emu.grasscutter.utils.Utils;

/**
 * 7.0 gather + 6.6 GatherInteractHelper gating. Interact uses inventory; ore break uses onMined ground drop.
 */
public final class GadgetGatherObject extends GadgetContent {
    private int itemId;
    private boolean isForbidGuest;
    private boolean minedRewardGiven;

    public GadgetGatherObject(EntityGadget entityGadget) {
        super(entityGadget);
        GatherData gatherData = GatherInteractHelper.resolveGatherData(entityGadget);
        if (entityGadget.getSpawnEntry() != null && entityGadget.getSpawnEntry().getGatherItemId() > 0) {
            this.itemId = entityGadget.getSpawnEntry().getGatherItemId();
        }
        if (gatherData != null) {
            if (this.itemId <= 0) {
                this.itemId = gatherData.getItemId();
            }
            this.isForbidGuest = gatherData.isForbidGuest();
        } else if (this.itemId <= 0) {
            Grasscutter.getLogger().trace("invalid gather object: {}", entityGadget.getConfigId());
        }
        if (GatherInteractHelper.needsDisabledInteractUntilReady(entityGadget)) {
            entityGadget.setInteractEnabled(false);
        }
    }

    public int getItemId() {
        return this.itemId;
    }

    public boolean isForbidGuest() {
        return this.isForbidGuest;
    }

    @Override
    public boolean onInteract(Player player, GadgetInteractReqOuterClass.GadgetInteractReq req) {
        if (!GatherInteractHelper.isGatherInteractAllowed(this.getGadget())) {
            GatherInteractHelper.logBlockedInteract(this.getGadget());
            return false;
        }
        ItemData itemData = GameData.getItemDataMap().get(this.getItemId());
        if (itemData == null) {
            return false;
        }
        this.minedRewardGiven = true;
        int yield = resolveGatherYield(this.getItemId());
        player.getInventory().addItem(new GameItem(itemData, yield), ActionReason.Gather);
        ScriptArgs scriptArgs = new ScriptArgs(this.getGadget().getGroupId(), 20, this.getGadget().getConfigId());
        if (this.getGadget().getMetaGadget() != null) {
            scriptArgs.setEventSource(this.getGadget().getMetaGadget().config_id);
        }
        this.getGadget().getScene().getScriptManager().callEvent(scriptArgs);
        this.getGadget()
                .getScene()
                .broadcastPacket(
                        new PacketGadgetInteractRsp(
                                (EntityBaseGadget) this.getGadget(),
                                InteractTypeOuterClass.InteractType.InteractType_INTERACT_GATHER));
        try {
            OpenWorldSpawnHelper.onGathered(this.getGadget(), this.getItemId());
            try {
                emu.grasscutter.game.world.NodKraiExploreSpawnHelper.markClaimed(this.getGadget());
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
        return true;
    }

    @Override
    public void onBuildProto(SceneGadgetInfoOuterClass.SceneGadgetInfo.Builder builder) {
        if (!GatherInteractHelper.isGatherInteractAllowed(this.getGadget())) {
            return;
        }
        builder.setGatherGadget(
                GatherGadgetInfoOuterClass.GatherGadgetInfo.newBuilder()
                        .setItemId(this.getItemId())
                        .setIsForbidGuest(this.isForbidGuest())
                        .build());
    }

    public void onMined(int killerId) {
        if (this.minedRewardGiven) {
            return;
        }
        this.ensureItemId();
        if (this.itemId <= 0) {
            Grasscutter.getLogger()
                    .warn(
                            "OreGather skip: no itemId gadgetId={} cfg={} pointType={}",
                            this.getGadget().getGadgetId(),
                            this.getGadget().getConfigId(),
                            this.getGadget().getPointType());
            return;
        }
        Player player = this.resolvePlayer(killerId);
        if (player == null) {
            return;
        }
        ItemData itemData = GameData.getItemDataMap().get(this.itemId);
        if (itemData == null) {
            Grasscutter.getLogger().warn("OreGather skip: missing ItemData id={}", this.itemId);
            return;
        }
        this.minedRewardGiven = true;
        try {
            Scene scene = this.getGadget().getScene();
            // Official: ore and crystal breaks become ground pickups. useOnGain items apply on collect.
            int count = itemData.isUseOnGain() ? 1 : Utils.randomRange(1, 2);
            for (int i = 0; i < count; ++i) {
                EntityItem drop =
                        new EntityItem(
                                scene,
                                player,
                                itemData,
                                this.getGadget().getPosition().nearby2d(1.0f).addY(1.0f),
                                1,
                                true);
                scene.addEntity(drop);
            }
            OpenWorldSpawnHelper.onGathered(this.getGadget(), this.getItemId());
            try {
                emu.grasscutter.game.world.NodKraiExploreSpawnHelper.markClaimed(this.getGadget());
            } catch (Throwable ignored) {
            }
            this.forceClientDespawn();
            Grasscutter.getLogger()
                    .info(
                            "OreGather drop item={} x{} useOnGain={} gadgetId={} cfg={} uid={}",
                            this.itemId,
                            count,
                            itemData.isUseOnGain(),
                            this.getGadget().getGadgetId(),
                            this.getGadget().getConfigId(),
                            player.getUid());
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("OreGather drop failed: {}", t.toString());
            this.minedRewardGiven = false;
        }
    }

    /** Resolve gather item when spawn lacked pointType / GatherData at construct time. */
    private void ensureItemId() {
        if (this.itemId > 0) {
            return;
        }
        try {
            GatherData gatherData = GatherInteractHelper.resolveGatherData(this.getGadget());
            if (gatherData != null && gatherData.getItemId() > 0) {
                this.itemId = gatherData.getItemId();
                return;
            }
        } catch (Throwable ignored) {
        }
        // Scarlet Quartz / Dulins Blood (Dragonspine)
        if (this.getGadget() != null && this.getGadget().getGadgetId() == 70590025) {
            this.itemId = 101005;
        }
    }

    private void forceClientDespawn() {
        try {
            EntityGadget gadget = this.getGadget();
            if (gadget == null || gadget.getScene() == null) {
                return;
            }
            gadget.getScene()
                    .broadcastPacket(
                            new PacketSceneEntityDisappearNotify(
                                    gadget, VisionTypeOuterClass.VisionType.VisionType_VISION_REMOVE));
        } catch (Throwable ignored) {
        }
    }

    public void dropItems(Player player) {
        if (player == null) {
            return;
        }
        this.onMined(player.getTeamManager().getCurrentAvatarEntity().getId());
        Scene scene = this.getGadget().getScene();
        if (scene != null) {
            scene.killEntity(this.getGadget(), player.getTeamManager().getCurrentAvatarEntity().getId());
        }
    }

    private Player resolvePlayer(int entityId) {
        try {
            EntityGadget gadget = this.getGadget();
            if (gadget == null || gadget.getScene() == null) {
                return null;
            }
            if (entityId > 0) {
                GameEntity e = gadget.getScene().getEntityById(entityId);
                if (e instanceof EntityAvatar) {
                    return ((EntityAvatar) e).getPlayer();
                }
                if (e instanceof EntityClientGadget) {
                    EntityClientGadget cg = (EntityClientGadget) e;
                    if (cg.getOwner() != null) {
                        return cg.getOwner();
                    }
                }
            }
            if (gadget.getScene().getWorld() != null && gadget.getScene().getWorld().getHost() != null) {
                return gadget.getScene().getWorld().getHost();
            }
            if (gadget.getScene().getPlayers() != null && !gadget.getScene().getPlayers().isEmpty()) {
                return gadget.getScene().getPlayers().get(0);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * Official specialty plants that grant more than 1 item per interact.
     * e.g. Lumidouce Bell = 3 per plant.
     */
    private static int resolveGatherYield(int itemId) {
        switch (itemId) {
            case 101235: // Lumidouce Bell
                return 3;
            default:
                return 1;
        }
    }
}
