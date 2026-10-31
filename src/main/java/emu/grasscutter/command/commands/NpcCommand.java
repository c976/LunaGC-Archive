package emu.grasscutter.command.commands;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.command.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.entity.EntityNPC;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.server.packet.send.PacketGroupSuiteNotify;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

/** Places or inspects NPCs for 7.1 scene/quest debugging. */
@Command(
        label = "npc",
        permission = "server.npc",
        permissionTargeted = "server.npc.others")
public final class NpcCommand implements CommandHandler {
    private static final double DISTANCE = 2.5;

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        return new CommandLine(new Args(sender, targetPlayer));
    }

    @CommandLine.Command(name = "npc")
    private static final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0..*", arity = "1..3", paramLabel = "<npcId|near|group|clear> [args]")
        private List<String> args = List.of();

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            var scene = targetPlayer.getScene();
            if (scene == null) {
                CommandOutput.sendMessage(sender, "Target player has no active scene.");
                return;
            }

            String op = args.get(0);
            if (op.equalsIgnoreCase("near")) {
                listNearby(sender, targetPlayer, args);
                return;
            }

            if (op.equalsIgnoreCase("group")) {
                if (args.size() < 3) {
                    usage(sender);
                    return;
                }
                try {
                    int groupId = Integer.parseInt(args.get(1));
                    int suiteId = Integer.parseInt(args.get(2));
                    targetPlayer.sendPacket(new PacketGroupSuiteNotify(groupId, suiteId));
                    CommandOutput.sendTranslatedMessage(
                            sender, "commands.npc.group_sent", groupId, suiteId);
                } catch (NumberFormatException e) {
                    usage(sender);
                }
                return;
            }

            if (op.equalsIgnoreCase("clear")) {
                var placed = new ArrayList<EntityNPC>();
                for (var entity : scene.getEntities().values()) {
                    if (entity instanceof EntityNPC npc && npc.isStandalone()) placed.add(npc);
                }
                placed.forEach(scene::removeEntity);
                CommandOutput.sendTranslatedMessage(sender, "commands.npc.cleared", placed.size());
                return;
            }

            int npcId;
            try {
                npcId = Integer.parseInt(op);
            } catch (NumberFormatException e) {
                usage(sender);
                return;
            }
            if (!GameData.getNpcDataMap().containsKey(npcId)) {
                CommandOutput.sendTranslatedMessage(sender, "commands.npc.not_found", npcId);
                return;
            }

            double yaw = Math.toRadians(targetPlayer.getRotation().getY());
            var pos =
                    targetPlayer
                            .getPosition()
                            .clone()
                            .addX((float) (Math.sin(yaw) * DISTANCE))
                            .addZ((float) (Math.cos(yaw) * DISTANCE));
            var rot = new Position(0, (targetPlayer.getRotation().getY() + 180) % 360, 0);

            var npc = new EntityNPC(scene, npcId, pos, rot);
            for (var block : scene.getLoadedBlocks()) {
                if (block.contains(pos)) {
                    npc.setBlockId(block.id);
                    break;
                }
            }
            scene.addEntity(npc);
            Grasscutter.getLogger()
                    .info(
                            "[npc] placed npc {} as entity {} in scene {} block {} at {}",
                            npcId,
                            npc.getId(),
                            scene.getId(),
                            npc.getBlockId(),
                            pos);
            CommandOutput.sendTranslatedMessage(sender, "commands.npc.spawned", npcId);
        }
    }

    private static void listNearby(Player sender, Player targetPlayer, List<String> args) {
        float radius = 15f;
        if (args.size() > 1) {
            try {
                radius = Float.parseFloat(args.get(1));
            } catch (NumberFormatException e) {
                usage(sender);
                return;
            }
        }

        var data = GameData.getSceneNpcBornData().get(targetPlayer.getSceneId());
        var here = targetPlayer.getPosition();
        var lines = new ArrayList<String>();
        if (data != null && data.getBornPosList() != null) {
            final float r = radius;
            data.getBornPosList().stream()
                    .filter(e -> e.getPos() != null && e.getPos().computeDistance(here) <= r)
                    .sorted(Comparator.comparingDouble(e -> e.getPos().computeDistance(here)))
                    .limit(20)
                    .forEach(
                            e ->
                                    lines.add(
                                            "%d  %.1fm  group %d"
                                                    .formatted(
                                                            e.getId(),
                                                            e.getPos().computeDistance(here),
                                                            e.getGroupId())));
        }

        if (lines.isEmpty()) {
            CommandOutput.sendTranslatedMessage(sender, "commands.npc.none_near", radius);
            return;
        }
        CommandOutput.sendMessage(sender, String.join("\n", lines));
    }

    private static void usage(Player sender) {
        CommandOutput.sendMessage(
                sender,
                "Usage: /npc <npcId> | /npc near [radius] | /npc group <groupId> <suiteId> | /npc clear");
    }
}
