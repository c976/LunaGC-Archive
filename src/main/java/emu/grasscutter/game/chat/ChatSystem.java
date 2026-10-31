package emu.grasscutter.game.chat;

import static emu.grasscutter.config.Configuration.GAME_INFO;

import emu.grasscutter.GameConstants;
import emu.grasscutter.command.CommandMap;
import emu.grasscutter.game.dps.DPSMeter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.ChatInfoOuterClass.ChatInfo;
import emu.grasscutter.server.event.player.PlayerChatEvent;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.Utils;
import java.util.*;
import java.util.regex.Pattern;

public class ChatSystem implements ChatSystemHandler {
    static final String PREFIXES = "[/!]";
    static final Pattern RE_PREFIXES = Pattern.compile(PREFIXES);
    static final Pattern RE_COMMANDS = Pattern.compile("\n" + PREFIXES);

    // We store the chat history for ongoing sessions in the form
    //    user id -> chat partner id -> [messages]
    private final Map<Integer, Map<Integer, List<ChatInfo>>> history = new HashMap<>();

    private final GameServer server;

    public ChatSystem(GameServer server) {
        this.server = server;
    }

    public GameServer getServer() {
        return server;
    }

    private boolean tryInvokeCommand(Player sender, Player target, String rawMessage) {
        if (rawMessage.isEmpty()) return false;
        // The DPS test is prefix-free: sending "dps30" in chat starts it. See DPSMeter.
        if (DPSMeter.handleChat(sender, rawMessage)) return true;
        if (!RE_PREFIXES.matcher(rawMessage.substring(0, 1)).matches()) return false;
        for (String line : rawMessage.substring(1).split("\n[/!]"))
            CommandMap.getInstance().invoke(sender, target, line);
        return true;
    }

    /********************
     * Chat history handling
     ********************/
    private synchronized void putInHistory(int uid, int partnerId, ChatInfo info) {
        this.history
                .computeIfAbsent(uid, x -> new HashMap<>())
                .computeIfAbsent(partnerId, x -> new ArrayList<>())
                .add(info);
    }

    public synchronized void clearHistoryOnLogout(Player player) {
        this.history.remove(player.getUid());
    }

    /** Copies one conversation so packets never iterate the live list. */
    private synchronized List<ChatInfo> copyHistory(int uid, int partnerId) {
        return new ArrayList<>(
                this.history.getOrDefault(uid, Map.of()).getOrDefault(partnerId, List.of()));
    }

    public void handlePullPrivateChatReq(Player player, int partnerId) {
        player.sendPacket(new PacketPullPrivateChatRsp(copyHistory(player.getUid(), partnerId)));
    }

    public void handlePullRecentChatReq(Player player) {
        this.ensureServerConversation(player);
    }

    /********************
     * Sending messages
     ********************/
    public void sendPrivateMessageFromServer(int targetUid, String message) {
        sendPrivateMessageFromBot(GameConstants.SERVER_CONSOLE_UID, targetUid, message);
    }

    public void sendPrivateMessageFromServer(int targetUid, int emote) {
        // Get target.
        Player target = getServer().getPlayerByUid(targetUid);
        if (target == null) {
            return;
        }

        // Create chat packet and put in history.
        var packet = new PacketPrivateChatNotify(GameConstants.SERVER_CONSOLE_UID, targetUid, emote);
        putInHistory(targetUid, GameConstants.SERVER_CONSOLE_UID, packet.getChatInfo());

        // Send.
        target.sendPacket(packet);
    }

    public void sendPrivateMessageFromBot(int fromUid, int targetUid, String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        if (!GameConstants.isServerBotUid(fromUid)) {
            fromUid = GameConstants.SERVER_CONSOLE_UID;
        }

        Player target = getServer().getPlayerByUid(targetUid);
        if (target == null) {
            return;
        }

        var packet = new PacketPrivateChatNotify(fromUid, targetUid, message);
        putInHistory(targetUid, fromUid, packet.getChatInfo());
        target.sendPacket(packet);
    }

    public void sendPrivateMessage(Player player, int targetUid, String message) {
        // Sanity checks.
        if (message == null || message.length() == 0) {
            return;
        }

        // Get target.
        var target = getServer().getPlayerByUid(targetUid);
        if (target == null && !GameConstants.isServerBotUid(targetUid)) {
            return;
        }

        // Invoke the chat event.
        var event = new PlayerChatEvent(player, message, target);
        event.call();
        if (event.isCanceled()) return;

        // Fetch the new target.
        if (!GameConstants.isServerBotUid(targetUid)) {
            targetUid = event.getTargetUid();
            if (targetUid == -1) return;
        }

        // Fetch the new message.
        message = event.getMessage();
        if (message == null || message.length() == 0) return;

        // Create chat packet.
        var packet = new PacketPrivateChatNotify(player.getUid(), targetUid, message);

        // Send and put in history.
        player.sendPacket(packet);
        putInHistory(player.getUid(), targetUid, packet.getChatInfo());

        // DPS commander: handles DPS only and replies with usage for anything else
        if (targetUid == GameConstants.SERVER_DPS_UID) {
            if (!DPSMeter.handleChat(player, message)) {
                sendPrivateMessageFromBot(
                        GameConstants.SERVER_DPS_UID,
                        player.getUid(),
                        "Usage: send dps30 (or dps60) to start a run; send dpsstop to end it early.");
            }
            return;
        }

        // Check if command
        var isCommand = tryInvokeCommand(player, target, message);

        if (target != null && !isCommand) {
            target.sendPacket(packet);
            this.putInHistory(targetUid, player.getUid(), packet.getChatInfo());
        }
    }

    public void sendPrivateMessage(Player player, int targetUid, int emote) {
        // Get target.
        var target = getServer().getPlayerByUid(targetUid);
        if (target == null && !GameConstants.isServerBotUid(targetUid)) {
            return;
        }

        // Invoke the chat event.
        var event = new PlayerChatEvent(player, emote, target);
        event.call();
        if (event.isCanceled()) return;

        // Fetch the new target.
        if (!GameConstants.isServerBotUid(targetUid)) {
            targetUid = event.getTargetUid();
            if (targetUid == -1) return;
        }
        // Fetch the new emote.
        emote = event.getMessageAsInt();
        if (emote == -1) return;

        // Create chat packet.
        var packet = new PacketPrivateChatNotify(player.getUid(), targetUid, emote);

        // Send and put is history.
        player.sendPacket(packet);
        this.putInHistory(player.getUid(), targetUid, packet.getChatInfo());

        if (target != null) {
            target.sendPacket(packet);
            this.putInHistory(targetUid, player.getUid(), packet.getChatInfo());
        }
    }

    public void sendTeamMessage(Player player, int channel, String message) {
        // Sanity checks
        if (message == null || message.length() == 0) {
            return;
        }

        // Check if command
        if (this.tryInvokeCommand(player, null, message)) {
            return;
        }

        // Invoke the chat event.
        var event = new PlayerChatEvent(player, message, channel);
        event.call();
        if (event.isCanceled()) return;

        // Fetch the new message.
        message = event.getMessage();
        if (message == null || message.length() == 0) return;
        // Fetch the new channel.
        channel = event.getChannel();
        if (channel == -1) return;

        // Create and send chat packet
        player.getWorld().broadcastPacket(new PacketPlayerChatNotify(player, channel, message));
    }

    public void sendTeamMessage(Player player, int channel, int icon) {
        // Invoke the chat event.
        var event = new PlayerChatEvent(player, icon, channel);
        event.call();
        if (event.isCanceled()) return;

        // Fetch the new icon.
        icon = event.getMessageAsInt();
        if (icon == -1) return;
        // Fetch the new channel.
        channel = event.getChannel();
        if (channel == -1) return;

        // Create and send chat packet
        player.getWorld().broadcastPacket(new PacketPlayerChatNotify(player, channel, icon));
    }

    /********************
     * Welcome messages
     ********************/
    private void sendServerWelcomeMessages(Player player) {
        var joinOptions = GAME_INFO.joinOptions;

        if (joinOptions.welcomeEmotes != null && joinOptions.welcomeEmotes.length > 0) {
            this.sendPrivateMessageFromServer(
                    player.getUid(),
                    joinOptions.welcomeEmotes[Utils.randomRange(0, joinOptions.welcomeEmotes.length - 1)]);
        }

        if (joinOptions.welcomeMessage != null && joinOptions.welcomeMessage.length() > 0) {
            this.sendPrivateMessageFromServer(player.getUid(), joinOptions.welcomeMessage);
        }
    }

    /**
     * Sends the console conversation (welcome messages on first use) so the bot chat is there right
     * after login, not only once the client pulls it after a teleport.
     */
    public void ensureServerConversation(Player player) {
        var serverHistory = copyHistory(player.getUid(), GameConstants.SERVER_CONSOLE_UID);
        if (serverHistory.isEmpty()) {
            this.sendServerWelcomeMessages(player);
            serverHistory = copyHistory(player.getUid(), GameConstants.SERVER_CONSOLE_UID);
        }

        if (serverHistory.isEmpty()) {
            return;
        }

        int historyLength = serverHistory.size();
        var recentMessages = serverHistory.subList(Math.max(historyLength - 3, 0), historyLength);
        player.sendPacket(new PacketPullRecentChatRsp(recentMessages));
        player.sendPacket(new PacketPullPrivateChatRsp(serverHistory));
    }
}
