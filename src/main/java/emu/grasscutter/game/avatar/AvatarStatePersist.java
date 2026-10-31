/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.game.avatar;

import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.props.FightProperty;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AvatarStatePersist {
    private static final Map<Integer, float[]> PENDING = new ConcurrentHashMap<Integer, float[]>();

    private AvatarStatePersist() {
    }

    public static void stash(Avatar avatar, float f, float f2, float f3) {
        if (avatar == null) {
            return;
        }
        PENDING.put(System.identityHashCode(avatar), new float[]{f, f2, f3});
    }

    public static void afterRecalc(Avatar avatar) {
        if (avatar == null) {
            return;
        }
        float[] fArray = PENDING.remove(System.identityHashCode(avatar));
        if (fArray == null) {
            return;
        }
        try {
            float f;
            float f2;
            float f3 = avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
            float f4 = fArray[0];
            // A recalc that runs before artifacts/weapons are loaded computes a too-small max, so
            // the saved HP has to survive it (that is what the branch below is for). But base HP at
            // the avatar's level is a floor for max HP - equipment only adds to it - so once the
            // computed max has reached that floor the recalc is trustworthy and an above-max saved
            // value is genuine surplus (a max-HP-ratio modifier such as Actor_MaxHPRatio was active
            // when it was saved). Left alone that surplus is persisted forever, and the character's
            // HP bar keeps reading full while damage eats the invisible excess first.
            float baseHp =
                    avatar.getAvatarData() != null
                            ? avatar.getAvatarData().getBaseHp(avatar.getLevel())
                            : 0f;
            boolean maxIsTrustworthy = f3 > 0.0f && (baseHp <= 0.0f || f3 >= baseHp * 0.99f);
            if (f4 > f3 && maxIsTrustworthy) {
                f4 = f3;
            }
            if (f3 > 0.0f && f4 > 0.0f) {
                if (f4 <= 1.01f && f3 > 10.0f) {
                    f2 = f3;
                } else if (f4 > f3) {
                    // 存档HP比“本轮算出的最大HP”还高：这轮recalc发生在
                    // 圣遗物/武器还没加载完的时候（MAX_HP被临时算小了）。
                    // 以前会 Math.min 把血压到临时上限，重登后就只剩裸装HP（例如 6000 多）。
                    // 这里保留存档值，等装备加载完的后续 recalc 再正常计算。
                    f2 = f4;
                } else {
                    f2 = f4;
                    if (f2 < 1.0f && f4 >= 1.0f) {
                        f2 = 1.0f;
                    }
                }
                avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, f2);
                avatar.setCurrentHp(f2);
            }
            if ((f2 = fArray[1]) > 0.0f) {
                avatar.setCurrentEnergy(f2);
            }
            if ((f = fArray[2]) > 0.0f) {
                avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY, f);
                avatar.setNyxValue(f);
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }
}
