package emu.grasscutter.command.commands;

import emu.grasscutter.command.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.tps.TpsWeaponSystem;
import emu.grasscutter.server.packet.send.PacketStoreItemChangeNotify;
import java.util.*;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

/** Third-person shooter weapons: hand them out, wear them on the current avatar, refill ammo. */
@Command(label = "tps", permission = "player.tps", permissionTargeted = "player.tps.others")
public final class TpsCommand implements CommandHandler {
    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Root(sender));
        commandLine.setExpandAtFiles(false);
        commandLine.addSubcommand("give", new Give(sender, targetPlayer));
        commandLine.addSubcommand("accessory", new Accessory(sender, targetPlayer));
        commandLine.addSubcommand("wear", new Wear(sender, targetPlayer));
        commandLine.addSubcommand("refill", new Refill(sender, targetPlayer));
        commandLine.addSubcommand("ammo", new Ammo(sender, targetPlayer));
        return commandLine;
    }

    @picocli.CommandLine.Command(name = "tps")
    private final class Root implements Runnable {
        private final Player sender;

        private Root(Player sender) {
            this.sender = sender;
        }

        @Override
        public void run() {
            TpsCommand.this.sendUsageMessage(sender);
        }
    }

    @picocli.CommandLine.Command(name = "give")
    private final class Give implements Runnable {
        private final Player sender;
        private final Player target;

        @Parameters(index = "0", arity = "0..1", paramLabel = "<weaponId>")
        private String weaponId;

        private Give(Player sender, Player target) {
            this.sender = sender;
            this.target = target;
        }

        @Override
        public void run() {
            TpsCommand.this.give(
                    sender, target, weaponId == null ? List.of() : List.of(weaponId));
        }
    }

    @picocli.CommandLine.Command(name = "accessory")
    private final class Accessory implements Runnable {
        private final Player sender;
        private final Player target;

        private Accessory(Player sender, Player target) {
            this.sender = sender;
            this.target = target;
        }

        @Override
        public void run() {
            TpsCommand.this.unlockAccessories(sender, target);
        }
    }

    @picocli.CommandLine.Command(name = "wear")
    private final class Wear implements Runnable {
        private final Player sender;
        private final Player target;

        @Parameters(arity = "0..*", paramLabel = "<weaponId>")
        private List<String> weaponIds = new ArrayList<>();

        private Wear(Player sender, Player target) {
            this.sender = sender;
            this.target = target;
        }

        @Override
        public void run() {
            TpsCommand.this.wear(sender, target, weaponIds);
        }
    }

    @picocli.CommandLine.Command(name = "refill")
    private static final class Refill implements Runnable {
        private final Player sender;
        private final Player target;

        private Refill(Player sender, Player target) {
            this.sender = sender;
            this.target = target;
        }

        @Override
        public void run() {
            TpsWeaponSystem.refillAmmunition(target);
            CommandOutput.sendMessage(sender, "TPS ammunition refilled.");
        }
    }

    @picocli.CommandLine.Command(name = "ammo")
    private final class Ammo implements Runnable {
        private final Player sender;
        private final Player target;

        @Parameters(arity = "0..*", paramLabel = "<setting>")
        private List<String> settings = new ArrayList<>();

        private Ammo(Player sender, Player target) {
            this.sender = sender;
            this.target = target;
        }

        @Override
        public void run() {
            TpsCommand.this.ammo(sender, target, settings);
        }
    }

    private void give(Player sender, Player target, List<String> args) {
        var ids = new ArrayList<Integer>();
        if (args.isEmpty()) {
            ids.addAll(GameData.getTpsWeaponDataMap().keySet());
        } else {
            var id = parseWeaponId(sender, args.get(0));
            if (id == null) return;
            ids.add(id);
        }
        ids.forEach(target.getInventory()::addItem);
        CommandOutput.sendMessage(sender, "Gave TPS weapons " + ids + ".");
    }

    private void unlockAccessories(Player sender, Player target) {
        var changed = new ArrayList<GameItem>();
        for (var accessory : GameData.getTpsWeaponAccessoryDataMap().values()) {
            var item = TpsWeaponSystem.findOwnedWeapon(target, accessory.getTpsWeaponId());
            if (item == null || item.getTpsAccessoryIds().contains(accessory.getId())) continue;
            item.getTpsAccessoryIds().add(accessory.getId());
            if (!changed.contains(item)) changed.add(item);
        }
        changed.forEach(GameItem::save);
        if (!changed.isEmpty()) target.sendPacket(new PacketStoreItemChangeNotify(changed));
        // Accessory affixes add abilities, so the wearers of changed weapons need them rebuilt.
        var changedIds = changed.stream().map(GameItem::getItemId).toList();
        for (var avatar : TpsWeaponSystem.getTpsWearers(target)) {
            if (avatar.getTpsWeaponIds().stream().noneMatch(changedIds::contains)) continue;
            avatar.recalcStats();
            TpsWeaponSystem.sendEquipChange(avatar);
        }
        CommandOutput.sendMessage(
                sender, "Unlocked accessories on " + changed.size() + " TPS weapons.");
    }

    private void wear(Player sender, Player target, List<String> args) {
        var entity = target.getTeamManager().getCurrentAvatarEntity();
        if (entity == null) {
            CommandOutput.sendMessage(sender, "No avatar on the field.");
            return;
        }

        var guids = new ArrayList<Long>();
        for (String arg : args) {
            var id = parseWeaponId(sender, arg);
            if (id == null) return;
            var item = TpsWeaponSystem.findOwnedWeapon(target, id);
            if (item == null) {
                CommandOutput.sendMessage(
                        sender, "TPS weapon " + id + " is not owned; /tps give first.");
                return;
            }
            guids.add(item.getGuid());
        }

        int retcode = TpsWeaponSystem.wear(target, entity.getAvatar().getGuid(), guids);
        CommandOutput.sendMessage(
                sender,
                retcode == 0
                        ? "TPS weapons now worn: " + args
                        : "Wear failed, retcode " + retcode + ".");
    }

    /**
     * Ammunition experiments: what the client reads from the weapons' ammunition list is not
     * settled, so the fill can be switched in game and the server-to-client pushes tried by hand.
     */
    private void ammo(Player sender, Player target, List<String> args) {
        boolean resend = false;
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i).toLowerCase();
            String next = i + 1 < args.size() ? args.get(i + 1).toLowerCase() : "";
            switch (arg) {
                case "type" -> {
                    TpsWeaponSystem.ammoTypeIsSlot = !next.equals("one");
                    resend = true;
                    i++;
                }
                case "current" -> {
                    switch (next) {
                        case "reserve" ->
                                TpsWeaponSystem.ammoCurrent =
                                        TpsWeaponSystem.AmmoCurrent.RESERVE;
                        case "limit" ->
                                TpsWeaponSystem.ammoCurrent = TpsWeaponSystem.AmmoCurrent.LIMIT;
                        default -> {
                            try {
                                TpsWeaponSystem.ammoFixed = Integer.parseInt(next);
                                TpsWeaponSystem.ammoCurrent = TpsWeaponSystem.AmmoCurrent.FIXED;
                            } catch (NumberFormatException e) {
                                sendUsageMessage(sender);
                                return;
                            }
                        }
                    }
                    resend = true;
                    i++;
                }
                case "notify" ->
                        CommandOutput.sendMessage(
                                sender,
                                "Sent 24371 with "
                                        + TpsWeaponSystem.sendAmmunitionNotify(target)
                                        + " entries.");
                case "supply" ->
                        CommandOutput.sendMessage(
                                sender,
                                "Sent SUPPLY to "
                                        + TpsWeaponSystem.sendAmmunitionSupply(target)
                                        + " avatars.");
                default -> {
                    sendUsageMessage(sender);
                    return;
                }
            }
        }
        if (resend) TpsWeaponSystem.getTpsWearers(target).forEach(TpsWeaponSystem::sendEquipChange);
        CommandOutput.sendMessage(
                sender,
                "TPS ammo list: type="
                        + (TpsWeaponSystem.ammoTypeIsSlot ? "slot" : "one")
                        + " current="
                        + TpsWeaponSystem.ammoCurrent
                        + (TpsWeaponSystem.ammoCurrent == TpsWeaponSystem.AmmoCurrent.FIXED
                                ? "(" + TpsWeaponSystem.ammoFixed + ")"
                                : ""));
    }

    private static Integer parseWeaponId(Player sender, String arg) {
        try {
            int id = Integer.parseInt(arg);
            if (GameData.getTpsWeaponDataMap().containsKey(id)) return id;
        } catch (NumberFormatException ignored) {
        }
        CommandOutput.sendMessage(
                sender,
                "Unknown TPS weapon " + arg + ". Known: " + GameData.getTpsWeaponDataMap().keySet());
        return null;
    }
}
