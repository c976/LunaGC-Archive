/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  emu.grasscutter.Grasscutter
 *  emu.grasscutter.net.proto.ProfilePictureOuterClass$ProfilePicture
 *  emu.grasscutter.net.proto.ProfilePictureOuterClass$ProfilePicture$Builder
 *  emu.grasscutter.utils.FileUtils
 *  emu.grasscutter.utils.JsonUtils
 */
package emu.grasscutter.game.player;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.proto.ProfilePictureOuterClass;
import emu.grasscutter.utils.FileUtils;
import emu.grasscutter.utils.JsonUtils;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ProfilePictureHelper {
    private static final Object LOCK = new Object();
    private static volatile boolean loaded;
    private static List<Integer> allIds;
    private static Map<Integer, Integer> avatarToPic;
    private static Map<Integer, Integer> picToAvatar;

    private ProfilePictureHelper() {
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void ensureLoaded() {
        if (loaded) {
            return;
        }
        Object object = LOCK;
        synchronized (object) {
            if (loaded) {
                return;
            }
            try {
                Path path = FileUtils.getExcelPath((String)"ProfilePictureExcelConfigData.json");
                if (path == null || !Files.exists(path, new LinkOption[0])) {
                    path = FileUtils.getResourcePath((String)"ExcelBinOutput/ProfilePictureExcelConfigData.json");
                }
                List<Map> rawList = JsonUtils.loadToList((Path) path, Map.class);
                ArrayList<Integer> arrayList = new ArrayList<Integer>();
                HashMap<Integer, Integer> hashMap = new HashMap<Integer, Integer>();
                HashMap<Integer, Integer> hashMap2 = new HashMap<Integer, Integer>();
                if (rawList != null) {
                    for (Map mapRaw : rawList) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> map = (Map<String, Object>) mapRaw;
                        int n;
                        if (map == null || map.get("id") == null) continue;
                        int n2 = ((Number) map.get("id")).intValue();
                        arrayList.add(n2);
                        Object v = map.get("unlockParam");
                        if (!(v instanceof Number) || (n = ((Number) v).intValue()) < 10000000) continue;
                        hashMap.putIfAbsent(n, n2);
                        hashMap2.put(n2, n);
                    }
                }
                Collections.sort(arrayList);
                allIds = List.copyOf(arrayList);
                avatarToPic = Map.copyOf(hashMap);
                picToAvatar = Map.copyOf(hashMap2);
                Grasscutter.getLogger().info("ProfilePictureHelper loaded {} pictures (avatar maps {})", (Object)allIds.size(), (Object)avatarToPic.size());
            }
            catch (Throwable throwable) {
                Grasscutter.getLogger().warn("ProfilePictureHelper load failed: {}", (Object)throwable.toString());
                allIds = List.of(Integer.valueOf(1), Integer.valueOf(2));
                avatarToPic = Map.of(10000005, 1, 10000007, 2);
                picToAvatar = Map.of(1, 10000005, 2, 10000007);
            }
            loaded = true;
        }
    }

    public static List<Integer> allProfilePictureIds() {
        ProfilePictureHelper.ensureLoaded();
        return allIds;
    }

    public static ProfilePictureOuterClass.ProfilePicture.Builder fillFromHeadImage(ProfilePictureOuterClass.ProfilePicture.Builder builder, int n) {
        ProfilePictureHelper.ensureLoaded();
        if (builder == null) {
            builder = ProfilePictureOuterClass.ProfilePicture.newBuilder();
        }
        if (n >= 10000000) {
            builder.setAvatarId(n);
            Integer n2 = avatarToPic.get(n);
            if (n2 != null) {
                builder.setProfilePictureId(n2.intValue());
            }
        } else if (n > 0) {
            builder.setProfilePictureId(n);
            Integer n3 = picToAvatar.get(n);
            if (n3 != null) {
                builder.setAvatarId(n3.intValue());
            }
        } else {
            builder.setProfilePictureId(1).setAvatarId(10000005);
        }
        return builder;
    }

    public static ProfilePictureOuterClass.ProfilePicture toProto(int n) {
        return ProfilePictureHelper.fillFromHeadImage(ProfilePictureOuterClass.ProfilePicture.newBuilder(), n).build();
    }

    static {
        allIds = List.of();
        avatarToPic = Map.of();
        picToAvatar = Map.of();
    }
}

