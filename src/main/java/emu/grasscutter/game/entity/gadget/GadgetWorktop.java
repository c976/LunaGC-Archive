package emu.grasscutter.game.entity.gadget;

import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.gadget.worktop.WorktopWorktopOptionHandler;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq;
import emu.grasscutter.net.proto.SceneGadgetInfoOuterClass.SceneGadgetInfo;
import emu.grasscutter.net.proto.SelectWorktopOptionReqOuterClass.SelectWorktopOptionReq;
import emu.grasscutter.net.proto.WorktopInfoOuterClass.WorktopInfo;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public final class GadgetWorktop extends GadgetContent {
    // Lua, packet handlers and delayed tasks share these few options. Iterators must retain a
    // stable snapshot while callers add, remove or clear the live set.
    private final Set<Integer> worktopOptions = new CopyOnWriteArraySet<>();
    private WorktopWorktopOptionHandler handler;

    public GadgetWorktop(EntityGadget gadget) {
        super(gadget);
    }

    public Set<Integer> getWorktopOptions() {
        return worktopOptions;
    }

    public void addWorktopOptions(int[] options) {
        this.worktopOptions.addAll(Arrays.stream(options).boxed().toList());
    }

    public void removeWorktopOption(int option) {
        this.worktopOptions.remove(option);
    }

    public boolean onInteract(Player player, GadgetInteractReq req) {
        return false;
    }

    public void onBuildProto(SceneGadgetInfo.Builder gadgetInfo) {
        var worktop = WorktopInfo.newBuilder().addAllOptionList(this.getWorktopOptions()).build();
        gadgetInfo.setWorktop(worktop);
    }

    public void setOnSelectWorktopOptionEvent(WorktopWorktopOptionHandler handler) {
        this.handler = handler;
    }

    public boolean onSelectWorktopOption(SelectWorktopOptionReq req) {
        return this.handler != null && this.handler.onSelectWorktopOption(this, req.getOptionId());
    }
}
