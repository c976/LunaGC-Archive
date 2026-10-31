package emu.grasscutter.command.commands;

import emu.grasscutter.command.Command;
import emu.grasscutter.command.CommandHandler;
import emu.grasscutter.command.CommandOutput;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.Inventory;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.player.Player;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
        label = "clear",
        permission = "player.clearinv",
        permissionTargeted = "player.clearinv.others")
public final class ClearCommand implements CommandHandler {
    private enum Scope {
        ALL,
        WEAPONS,
        ARTIFACTS,
        MATERIALS
    }

    @Override
    public CommandLine createCommandLine(Player sender, Player targetPlayer) {
        var commandLine = new CommandLine(new Args(sender, targetPlayer));
        commandLine.registerConverter(
                Scope.class,
                value ->
                        switch (value.toLowerCase(Locale.ROOT)) {
                            case "all" -> Scope.ALL;
                            case "wp", "weapon", "weapons" -> Scope.WEAPONS;
                            case "art", "artifact", "artifacts" -> Scope.ARTIFACTS;
                            case "mat", "material", "materials" -> Scope.MATERIALS;
                            default -> throw new CommandLine.TypeConversionException(
                                    "scope must be all|weapons|artifacts|materials");
                        });
        return commandLine;
    }

    @CommandLine.Command(name = "clear")
    private final class Args implements Runnable {
        private final Player sender;
        private final Player targetPlayer;

        @Parameters(index = "0", paramLabel = "<all|weapons|artifacts|materials>")
        private Scope scope;

        @Option(names = {"-l", "--level"}, defaultValue = "1", paramLabel = "<maxLevel>")
        private int level;

        @Option(names = {"-r", "--refinement"}, defaultValue = "1", paramLabel = "<maxRefinement>")
        private int refinement;

        @Option(names = {"--rarity"}, defaultValue = "4", paramLabel = "<maxRarity>")
        private int rarity;

        @Option(names = "--dry-run", description = "Preview matching items without removing them")
        private boolean dryRun;

        private Args(Player sender, Player targetPlayer) {
            this.sender = sender;
            this.targetPlayer = targetPlayer;
        }

        @Override
        public void run() {
            if (level < 0 || refinement < 0 || rarity < 0) {
                throw new CommandLine.ParameterException(
                        createCommandLine(sender, targetPlayer),
                        "level, refinement and rarity must be non-negative");
            }

            Inventory inventory = targetPlayer.getInventory();
            String playerName = targetPlayer.getNickname();
            if (dryRun) {
                var preview = summarize(selectedItems(inventory, scope, level, refinement, rarity));
                CommandOutput.sendMessage(
                        sender,
                        "Dry run for "
                                + playerName
                                + " ("
                                + scope.name().toLowerCase(Locale.ROOT)
                                + "): would remove "
                                + preview.stacks()
                                + " item stacks ("
                                + preview.quantity()
                                + " total items). No items changed.");
                return;
            }

            switch (scope) {
                case WEAPONS -> {
                    inventory.removeItems(getWeapons(inventory, level, refinement, rarity).toList());
                    CommandOutput.sendTranslatedMessage(sender, "commands.clear.weapons", playerName);
                }
                case ARTIFACTS -> {
                    inventory.removeItems(getRelics(inventory, level, rarity).toList());
                    CommandOutput.sendTranslatedMessage(sender, "commands.clear.artifacts", playerName);
                }
                case MATERIALS -> {
                    inventory.removeItems(getOther(ItemType.ITEM_MATERIAL, inventory, rarity).toList());
                    CommandOutput.sendTranslatedMessage(sender, "commands.clear.materials", playerName);
                }
                case ALL -> clearAll(sender, inventory, playerName, level, refinement, rarity);
            }
        }
    }

    record Preview(long stacks, long quantity) {}

    /** Count both inventory entries and stack quantities without mutating any item. */
    static Preview summarize(List<GameItem> items) {
        return new Preview(items.size(), items.stream().mapToLong(GameItem::getCount).sum());
    }

    /** The preview uses exactly the same category and threshold filters as real deletion. */
    private static List<GameItem> selectedItems(
            Inventory inventory, Scope scope, int level, int refinement, int rarity) {
        Stream<GameItem> selected =
                switch (scope) {
                    case WEAPONS -> getWeapons(inventory, level, refinement, rarity);
                    case ARTIFACTS -> getRelics(inventory, level, rarity);
                    case MATERIALS -> getOther(ItemType.ITEM_MATERIAL, inventory, rarity);
                    case ALL ->
                            Stream.of(
                                            getRelics(inventory, level, rarity),
                                            getWeapons(inventory, level, refinement, rarity),
                                            getOther(ItemType.ITEM_MATERIAL, inventory, rarity),
                                            getOther(ItemType.ITEM_FURNITURE, inventory, rarity),
                                            getOther(ItemType.ITEM_DISPLAY, inventory, rarity),
                                            getOther(ItemType.ITEM_VIRTUAL, inventory, rarity))
                                    .flatMap(stream -> stream);
                };
        // Inventory.removeItem rejects empty stacks; do not count them as deletions.
        return selected.filter(item -> item.getCount() > 0).toList();
    }

    private static Stream<GameItem> getOther(ItemType type, Inventory inventory, int rarity) {
        return inventory.getItems().values().stream()
                .filter(item -> item.getItemType() == type)
                .filter(item -> item.getItemData().getRankLevel() <= rarity)
                .filter(item -> !item.isLocked() && !item.isEquipped());
    }

    private static Stream<GameItem> getWeapons(
            Inventory inventory, int level, int refinement, int rarity) {
        return getOther(ItemType.ITEM_WEAPON, inventory, rarity)
                .filter(item -> item.getLevel() <= level)
                .filter(item -> item.getRefinement() < refinement);
    }

    private static Stream<GameItem> getRelics(Inventory inventory, int level, int rarity) {
        return getOther(ItemType.ITEM_RELIQUARY, inventory, rarity)
                .filter(item -> item.getLevel() <= level + 1);
    }

    private static void clearAll(
            Player sender,
            Inventory inventory,
            String playerName,
            int level,
            int refinement,
            int rarity) {
        inventory.removeItems(getRelics(inventory, level, rarity).toList());
        CommandOutput.sendTranslatedMessage(sender, "commands.clear.artifacts", playerName);
        inventory.removeItems(getWeapons(inventory, level, refinement, rarity).toList());
        CommandOutput.sendTranslatedMessage(sender, "commands.clear.weapons", playerName);
        inventory.removeItems(getOther(ItemType.ITEM_MATERIAL, inventory, rarity).toList());
        CommandOutput.sendTranslatedMessage(sender, "commands.clear.materials", playerName);
        inventory.removeItems(getOther(ItemType.ITEM_FURNITURE, inventory, rarity).toList());
        CommandOutput.sendTranslatedMessage(sender, "commands.clear.furniture", playerName);
        inventory.removeItems(getOther(ItemType.ITEM_DISPLAY, inventory, rarity).toList());
        CommandOutput.sendTranslatedMessage(sender, "commands.clear.displays", playerName);
        inventory.removeItems(getOther(ItemType.ITEM_VIRTUAL, inventory, rarity).toList());
        CommandOutput.sendTranslatedMessage(sender, "commands.clear.virtuals", playerName);
        CommandOutput.sendTranslatedMessage(sender, "commands.clear.everything", playerName);
    }
}
