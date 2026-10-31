package emu.grasscutter.game.dps;

import emu.grasscutter.GameConstants;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.dungeons.challenge.trigger.ChallengeTrigger;
import emu.grasscutter.game.dungeons.challenge.trigger.KillMonsterTrigger;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass.PropChangeReason;
import emu.grasscutter.server.packet.send.PacketAvatarFightPropUpdateNotify;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Entry point for the DPS test.
 *
 * <p>Send "dps30" in any chat to start a 30 second run; "dpsstop" ends it early.
 * The {@code /dps} command routes here too, so both paths behave identically.
 */
public final class DPSMeter {

    public static final int DEFAULT_SECONDS = 60;
    public static final int MIN_SECONDS = 1;
    public static final int MAX_SECONDS = 120;
    public static final int MAX_TARGETS = 10;

    /** Hilichurl brute. Without a weapon its AI never starts, so it stands still and acts as a target. */
    private static final int TARGET_MONSTER_ID = 21020201;

    private static final int TARGET_LEVEL = 90;

    /** Prefix-free triggers: dps30 / dps30s. The number is the duration in seconds. */
    private static final Pattern CHAT_START =
            Pattern.compile("^dps(\\d{1,3})s?$", Pattern.CASE_INSENSITIVE);

    private static final Pattern CHAT_STOP =
            Pattern.compile("^dps(?:stop)$", Pattern.CASE_INSENSITIVE);

    private DPSMeter() {}

    /** Replies through the friend DPS commander, falling back to a console whisper. */
    public static void reply(Player player, String message) {
        if (player == null) {
            CommandHandler.sendMessage(null, message);
            return;
        }
        try {
            var chat = player.getServer().getChatSystem();
            if (chat != null) {
                chat.sendPrivateMessageFromBot(
                        GameConstants.SERVER_DPS_UID, player.getUid(), message);
                return;
            }
        } catch (Throwable t) {
            Grasscutter.getLogger().debug("DPS reply via bot failed, fallback", t);
        }
        CommandHandler.sendMessage(player, message);
    }

    /**
     * Tries to handle a chat message as a DPS command.
     *
     * @return true when consumed as a command, in which case it must not also be broadcast as chat
     */
    public static boolean handleChat(Player sender, String rawMessage) {
        if (sender == null || rawMessage == null) return false;

        var normalized = normalize(rawMessage);
        if (!normalized.startsWith("dps")) return false;

        if (CHAT_STOP.matcher(normalized).matches()) {
            stop(sender);
            return true;
        }

        var matcher = CHAT_START.matcher(normalized);
        if (!matcher.matches()) return false;

        start(sender, Integer.parseInt(matcher.group(1)), 1);
        return true;
    }

    /** Strips whitespace, folds full-width digits to ASCII and lowercases, so input typed with a CJK IME
     * still matches. */
    private static String normalize(String message) {
        var builder = new StringBuilder(message.length());
        for (var i = 0; i < message.length(); i++) {
            var c = message.charAt(i);
            if (c >= '\uFF10' && c <= '\uFF19') c -= 0xFEE0; // full-width digits
            if (c == '\u3000' || Character.isWhitespace(c)) continue; // ideographic space
            builder.append(Character.toLowerCase(c));
        }
        return builder.toString();
    }

    /**
     * Starts a DPS test run.
     *
     * @param seconds duration, clamped to [{@value MIN_SECONDS}, {@value MAX_SECONDS}] seconds
     * @param targetCount number of targets, clamped to [1, {@value MAX_TARGETS}]
     */
    public static void start(Player player, int seconds, int targetCount) {
        if (player == null) {
            CommandHandler.sendMessage(null, "A DPS test can only be started by a player.");
            return;
        }

        var timeLimit = Math.min(MAX_SECONDS, Math.max(MIN_SECONDS, seconds));
        var count = Math.min(MAX_TARGETS, Math.max(1, targetCount));

        var scene = player.getScene();
        var running = scene.getChallenge();
        if (running != null && running.inProgress()) {
            reply(
                    player,
                    running instanceof DPSChallenge
                            ? "A DPS test is already running. Send \"dpsstop\" to end it early."
                            : "Another challenge is running in this scene, so a DPS test cannot start.");
            return;
        }

        var monsterData = GameData.getMonsterDataMap().get(TARGET_MONSTER_ID);
        if (monsterData == null) {
            reply(player, "No target monster data for " + TARGET_MONSTER_ID + "; check the resource files.");
            return;
        }

        var pos = player.getPosition();
        var rot = player.getRotation();

        List<DPSEntity> targets = new ArrayList<>(count);
        for (var i = 0; i < count; i++) {
            targets.add(new DPSEntity(scene, monsterData, pos.nearby2d(2f), rot, TARGET_LEVEL));
        }

        List<ChallengeTrigger> triggers = new ArrayList<>(2);
        triggers.add(new KillMonsterTrigger(DPSChallenge.CONFIG_ID)); // killing the target settles early
        triggers.add(new DPSTimeTrigger()); // settle when the timer runs out

        // Reset the party before starting so leftover low HP or spent bursts do not skew the reading.
        player.getTeamManager().getActiveTeam().forEach(DPSMeter::resetForTest);

        var challenge =
                new DPSChallenge(
                        scene, DPSChallenge.buildGroup(), triggers, player, targets, timeLimit);
        scene.setChallenge(challenge);
        challenge.start();
        targets.forEach(scene::addEntity);

        reply(
                player,
                count == 1
                        ? String.format("DPS test started: %d seconds. Send \"dpsstop\" to end early.", timeLimit)
                        : String.format(
                                "DPS test started: %d seconds / %d targets. Send \"dpsstop\" to end early.",
                                timeLimit, count));
    }

    /** Ends the running DPS test in this scene early. */
    public static void stop(Player player) {
        if (player == null) return;

        if (player.getScene().getChallenge() instanceof DPSChallenge challenge
                && challenge.inProgress()) {
            challenge.done();
        } else {
            reply(player, "No DPS test is currently running.");
        }
    }

    private static void resetForTest(EntityAvatar entity) {
        var player = entity.getPlayer();
        if (!entity.isAlive() && player != null) {
            player.getTeamManager().reviveAvatar(entity.getAvatar());
        }
        entity.heal(entity.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP), true);
        entity.addEnergy(100f, PropChangeReason.PropChangeReason_PROP_CHANGE_ENERGY_BALL);
        entity
                .getWorld()
                .broadcastPacket(
                        new PacketAvatarFightPropUpdateNotify(
                                entity.getAvatar(), FightProperty.FIGHT_PROP_CUR_HP));
    }
}
