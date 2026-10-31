package emu.grasscutter.game.entity.gadget;

import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq;
import emu.grasscutter.net.proto.OfferingInfoOuterClass.OfferingInfo;
import emu.grasscutter.net.proto.SceneGadgetInfoOuterClass.SceneGadgetInfo;

/**
 * OfferingGadget content: writes offering_info so the client shows the offering interaction key.
 */
public final class GadgetOffering extends GadgetContent {
    private final int offeringId;

    public GadgetOffering(EntityGadget gadget) {
        super(gadget);
        int resolved = OfferingHelper.resolveOfferingId(gadget);
        this.offeringId = resolved > 0 ? resolved : OfferingHelper.OFFERING_ORAIONOKAMI;
    }

    public int getOfferingId() {
        return offeringId;
    }

    @Override
    public boolean onInteract(Player player, GadgetInteractReq req) {
        return !OfferingHelper.tryInteract(player, getGadget());
    }

    @Override
    public void onBuildProto(SceneGadgetInfo.Builder gadgetInfo) {
        gadgetInfo.setOfferingInfo(OfferingInfo.newBuilder().setOfferingId(this.offeringId).build());
        gadgetInfo.setIsEnableInteract(true);
    }
}
