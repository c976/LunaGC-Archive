package emu.grasscutter.command;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
public @interface Command {
    String label() default "";

    String[] aliases() default {};

    String permission() default "";

    String permissionTargeted() default "";

    TargetRequirement targetRequirement() default TargetRequirement.ONLINE;

    /** Whether bare @player arguments on this command line are consumed as the command target. */
    boolean inlineTarget() default true;

    boolean threading() default false;

    enum TargetRequirement {
        NONE,
        OFFLINE,
        PLAYER,
        ONLINE
    }
}
