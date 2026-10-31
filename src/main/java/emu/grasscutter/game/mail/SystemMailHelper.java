/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  emu.grasscutter.Grasscutter
 *  emu.grasscutter.database.DatabaseHelper
 *  emu.grasscutter.database.DatabaseManager
 *  emu.grasscutter.game.mail.Mail
 *  emu.grasscutter.game.mail.Mail$MailContent
 *  emu.grasscutter.game.player.Player
 *  emu.grasscutter.net.packet.BasePacket
 *  emu.grasscutter.server.packet.send.PacketDelMailRsp
 *  emu.grasscutter.server.packet.send.PacketMailChangeNotify
 *  org.bson.types.ObjectId
 */
package emu.grasscutter.game.mail;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.database.DatabaseManager;
import emu.grasscutter.game.mail.Mail;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.server.packet.send.PacketDelMailRsp;
import emu.grasscutter.server.packet.send.PacketMailChangeNotify;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.types.ObjectId;

public final class SystemMailHelper {
    /** Prepended to every system mail's title, and what marks one as a system mail. */
    public static final String TITLE_PREFIX = "[Announcement] ";

    /**
     * The prefix this used to use, still recognised on read.
     *
     * <p>The prefix is not only decoration: it is how a mail is identified as a system mail, which
     * is what stops a player deleting it and what /sysmail matches on. Mails already sitting in
     * players' inboxes carry the old one, so dropping it would make them deletable and invisible to
     * the command. Nothing writes it any more.
     */
    private static final String LEGACY_TITLE_PREFIX = "[\u7cfb\u7edf\u516c\u544a] ";

    public static final long DEFAULT_EXPIRE_SECONDS = 315360000L;

    private SystemMailHelper() {
    }

    /** Whether a title already carries a system prefix, with or without its trailing space. */
    private static boolean hasSystemPrefix(String title) {
        return title.startsWith(TITLE_PREFIX)
                || title.startsWith(TITLE_PREFIX.trim())
                || title.startsWith(LEGACY_TITLE_PREFIX)
                || title.startsWith(LEGACY_TITLE_PREFIX.trim());
    }

    public static boolean isProtected(Mail mail) {
        if (mail == null || mail.mailContent == null || mail.mailContent.title == null) {
            return false;
        }
        return hasSystemPrefix(mail.mailContent.title);
    }

    public static String normalizeTitle(String string) {
        if (string == null || string.isBlank()) {
            return TITLE_PREFIX.trim();
        }
        String string2 = string.trim();
        // An existing prefix is left exactly as it is, so re-titling an old mail does not silently
        // rewrite its prefix and orphan it from whatever else matched on the old one.
        if (hasSystemPrefix(string2)) {
            return string2;
        }
        return TITLE_PREFIX + string2;
    }

    public static Mail newSystemMail() {
        Mail mail = new Mail(new Mail.MailContent(), new ArrayList(), Instant.now().getEpochSecond() + 315360000L, 1, 1);
        mail.expireTime = Instant.now().getEpochSecond() + 315360000L;
        return mail;
    }

    public static int updateContentByTitle(String string, String string2) {
        if (string == null || string.isBlank()) {
            return 0;
        }
        String string3 = string.trim();
        List<Mail> list = DatabaseManager.getGameDatastore().find(Mail.class).stream().filter(mail -> mail.mailContent != null && mail.mailContent.title != null && (mail.mailContent.title.equals(string3) || mail.mailContent.title.contains(string3))).toList();
        int n = 0;
        for (Mail mail2 : list) {
            if (!SystemMailHelper.isProtected(mail2)) continue;
            mail2.mailContent.content = string2 == null ? "" : string2;
            mail2.save();
            SystemMailHelper.notifyOwnerIfOnline(mail2);
            ++n;
        }
        return n;
    }

    public static int updateTitle(String string, String string2) {
        if (string == null || string.isBlank()) {
            return 0;
        }
        String string3 = SystemMailHelper.normalizeTitle(string2);
        List<Mail> list = DatabaseManager.getGameDatastore().find(Mail.class).stream().filter(mail -> mail.mailContent != null && mail.mailContent.title != null && mail.mailContent.title.contains(string.trim())).toList();
        int n = 0;
        for (Mail mail2 : list) {
            if (!SystemMailHelper.isProtected(mail2)) continue;
            mail2.mailContent.title = string3;
            mail2.save();
            SystemMailHelper.notifyOwnerIfOnline(mail2);
            ++n;
        }
        return n;
    }

    public static int deleteByTitle(String string) {
        if (string == null || string.isBlank()) {
            return 0;
        }
        String string2 = string.trim();
        List<Mail> list = DatabaseManager.getGameDatastore().find(Mail.class).stream().filter(mail -> SystemMailHelper.isProtected(mail) && mail.mailContent != null && mail.mailContent.title != null && (mail.mailContent.title.equals(string2) || mail.mailContent.title.contains(string2))).toList();
        return SystemMailHelper.purgeMails(list);
    }

    public static int deleteAllProtected() {
        List<Mail> list = DatabaseManager.getGameDatastore().find(Mail.class).stream().filter(SystemMailHelper::isProtected).toList();
        return SystemMailHelper.purgeMails(list);
    }

    private static int purgeMails(List<Mail> list) {
        if (list.isEmpty()) {
            return 0;
        }
        HashMap<Integer, List<Mail>> hashMap = new HashMap<>();
        for (Mail object : list) {
            hashMap.computeIfAbsent(object.getOwnerUid(), n -> new ArrayList<>()).add(object);
        }
        int n2 = 0;
        for (Map.Entry<Integer, List<Mail>> entry : hashMap.entrySet()) {
            Player player = Grasscutter.getGameServer().getPlayerByUid(entry.getKey(), false);
            ArrayList<Integer> arrayList = new ArrayList<Integer>();
            for (Mail mail : entry.getValue()) {
                if (player != null && player.isOnline()) {
                    ObjectId objectId = mail.getId();
                    List list2 = player.getAllMail();
                    for (int i = 0; i < list2.size(); ++i) {
                        Mail mail2 = (Mail)list2.get(i);
                        if (mail2.getId() == null || !mail2.getId().equals((Object)objectId)) continue;
                        list2.remove(i);
                        arrayList.add(i);
                        break;
                    }
                }
                DatabaseHelper.deleteMail((Mail)mail);
                ++n2;
            }
            if (player == null || !player.isOnline() || arrayList.isEmpty()) continue;
            arrayList.sort(Collections.reverseOrder());
            player.sendPacket((BasePacket)new PacketDelMailRsp(player, arrayList));
            player.sendPacket((BasePacket)new PacketMailChangeNotify(player, null, arrayList));
        }
        return n2;
    }

    private static void notifyOwnerIfOnline(Mail mail) {
        Player player = Grasscutter.getGameServer().getPlayerByUid(mail.getOwnerUid(), false);
        if (player == null || !player.isOnline()) {
            return;
        }
        ObjectId objectId = mail.getId();
        for (Mail mail2 : player.getAllMail()) {
            if (mail2.getId() == null || !mail2.getId().equals((Object)objectId)) continue;
            mail2.mailContent.title = mail.mailContent.title;
            mail2.mailContent.content = mail.mailContent.content;
            mail2.mailContent.sender = mail.mailContent.sender;
            mail2.expireTime = mail.expireTime;
            mail2.importance = mail.importance;
            player.sendPacket((BasePacket)new PacketMailChangeNotify(player, mail2));
            return;
        }
    }
}

