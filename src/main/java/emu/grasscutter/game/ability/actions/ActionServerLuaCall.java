package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction.LuaCallType;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.scripts.ScriptLoader;
import java.util.concurrent.TimeUnit;
import javax.script.Bindings;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaValue;

@AbilityAction(AbilityModifierAction.Type.ServerLuaCall)
public final class ActionServerLuaCall extends AbilityActionHandler {
    private static final MissingFunctionWarningLimiter missingFunctionWarnings =
            new MissingFunctionWarningLimiter(TimeUnit.MINUTES.toNanos(1));

    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        var scene = target.getScene();
        var scriptManager = scene.getScriptManager();

        var functionName = action.funcName;

        // Set the script library's manager.
        var scriptLib = ScriptLoader.getScriptLib();
        scriptLib.setCurrentEntity(target);
        scriptLib.setSceneScriptManager(scriptManager);

        // Configs without a call type (the Ice Creature SLC_IceCreature_ReportInteract calls) mean
        // the gadget's own controller, the same as Gadget.
        var callType = action.luaCallType == null ? LuaCallType.Gadget : action.luaCallType;

        // Attempt to call the function.
        return switch (callType) {
            default -> false;
            case FromGroup -> {
                var groupId = target.getGroupId();
                var group = scriptManager.getGroupById(groupId);
                if (group == null) yield false;
                var script = group.getBindings();

                // Set the script library's group.
                scriptLib.setCurrentGroup(group);

                yield ActionServerLuaCall.callFunction(script, functionName, ability, action);
            }
            case SpecificGroup -> {
                if (action.callParamList == null || action.callParamList.length == 0) yield false;
                var groupId = action.callParamList[0];
                var group = scriptManager.getGroupById(groupId);
                if (group == null) yield false;
                var script = group.getBindings();

                // Set the script library's group.
                scriptLib.setCurrentGroup(group);

                yield ActionServerLuaCall.callFunction(script, functionName, ability, action);
            }
            case Gadget -> {
                var controller = target.getEntityController();
                if (controller == null || functionName == null || functionName.isBlank()) yield false;

                // Hand off the function handling to the controller.
            //    controller.callControllerScriptFunc(target, functionName, ability, action);

                yield true;
            }
        };
    }

    /**
     * Handles fetching and calling a function.
     *
     * @param bindings The bindings to fetch the function from.
     * @param functionName The name of the function to call.
     * @param ability The ability data.
     * @param action The ability action data.
     * @return Whether the function was called successfully.
     */
    private static boolean callFunction(
        Bindings bindings, String functionName,
        Ability ability, AbilityModifierAction action
    ) {
        try {
            // Resolve the function from the script.
            var function = bindings.get(functionName);
            if (!(function instanceof LuaFunction luaFunction)) {
                long suppressed = missingFunctionWarnings.acquireWarning(System.nanoTime());
                if (suppressed >= 0) {
                    Grasscutter.getLogger().warn(
                            "Unable to invoke {}: binding is not a LuaFunction ({});"
                                    + " suppressed {} repeated missing-function warnings.",
                            functionName,
                            function == null ? "missing" : function.getClass().getSimpleName(),
                            suppressed);
                }
                return false;
            }

            // Convert parameters to Lua values.
            var lParam1 = LuaValue.valueOf(action.param1.getInt(ability));
            var lParam2 = LuaValue.valueOf(action.param2.getInt(ability));
            var lParam3 = LuaValue.valueOf(action.param3.getInt(ability));

            // Invoke the function with the parameters.
            switch (action.paramNum) {
                case 1 -> luaFunction.invoke(new LuaValue[] { lParam1 });
                case 2 -> luaFunction.invoke(new LuaValue[] { lParam1, lParam2 });
                case 3 -> luaFunction.invoke(new LuaValue[] { lParam1, lParam2, lParam3 });
                default -> luaFunction.invoke(new LuaValue[] { ScriptLoader.getScriptLibLua() });
            }

            return true;
        } catch (Exception exception) {
            Grasscutter.getLogger().warn("Unable to invoke {}.", functionName, exception);
            return false;
        }
    }

    // One global warning sample per interval; never retain players, bindings or missing names.
    static final class MissingFunctionWarningLimiter {
        private final long intervalNanos;
        private long lastWarningNanos = Long.MIN_VALUE;
        private long suppressedWarnings;

        MissingFunctionWarningLimiter(long intervalNanos) {
            this.intervalNanos = intervalNanos;
        }

        // Protect both the window transition and the suppressed count. A successful
        // timestamp CAS followed by a counter reset could otherwise discard concurrent
        // misses while the winning thread was paused between the two operations.
        synchronized long acquireWarning(long nowNanos) {
            if (this.lastWarningNanos != Long.MIN_VALUE
                    && nowNanos - this.lastWarningNanos < this.intervalNanos) {
                this.suppressedWarnings++;
                return -1;
            }
            long suppressed = this.suppressedWarnings;
            this.suppressedWarnings = 0;
            this.lastWarningNanos = nowNanos;
            return suppressed;
        }
    }
}
