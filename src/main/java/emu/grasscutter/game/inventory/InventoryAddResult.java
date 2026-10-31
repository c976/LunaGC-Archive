package emu.grasscutter.game.inventory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Per-item outcome of a batch grant. This is not a durable database transaction. */
public record InventoryAddResult(List<Entry> entries) {
    public enum Status {
        ADDED,
        EFFECT_APPLIED,
        ALREADY_OWNED,
        INVALID_ITEM,
        CAPACITY_FULL,
        STACK_LIMIT,
        UNSUPPORTED_TYPE,
        EFFECT_FAILED,
        AUTHORIZATION_FAILED
    }

    public record Entry(int itemId, int requested, int added, Status status) {
        public boolean accepted() {
            return added > 0
                    && added == requested
                    && (status == Status.ADDED || status == Status.EFFECT_APPLIED);
        }
    }

    public InventoryAddResult {
        entries = List.copyOf(entries);
    }

    public boolean allAccepted() {
        return !entries.isEmpty() && entries.stream().allMatch(Entry::accepted);
    }

    public boolean partiallyAccepted() {
        return entries.stream().anyMatch(Entry::accepted) && !allAccepted();
    }

    public static InventoryAddResult rejected(Collection<GameItem> items, Status reason) {
        List<Entry> failures = new ArrayList<>();
        if (items != null) {
            for (GameItem item : items) {
                failures.add(new Entry(
                        item == null ? 0 : item.getItemId(),
                        item == null ? 0 : item.getCount(),
                        0,
                        reason));
            }
        }
        return new InventoryAddResult(failures);
    }
}
