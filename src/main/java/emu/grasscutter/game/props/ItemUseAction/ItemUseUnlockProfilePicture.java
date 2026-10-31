package emu.grasscutter.game.props.ItemUseAction;

import emu.grasscutter.game.props.ItemUseOp;

/**
 * [v35] 头像解锁券（材料 320001/320002/320003）。
 * useParam[0] = 要解锁的头像 id。客户端按“已拥有该券”判定解锁，服务端返回成功即可。
 */
public class ItemUseUnlockProfilePicture extends ItemUseAction {
    private final int profilePictureId;

    public ItemUseUnlockProfilePicture(String[] useParam) {
        int id = 0;
        try {
            if (useParam != null && useParam.length > 0) {
                id = Integer.parseInt(useParam[0].trim());
            }
        } catch (Exception ignored) {
        }
        this.profilePictureId = id;
    }

    @Override
    public ItemUseOp getItemUseOp() {
        return ItemUseOp.ITEM_USE_UNLOCK_PROFILE_PICTURE;
    }

    @Override
    public boolean useItem(UseItemParams params) {
        return true;
    }
}
