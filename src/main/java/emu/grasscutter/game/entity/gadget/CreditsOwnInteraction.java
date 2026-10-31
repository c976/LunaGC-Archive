package emu.grasscutter.game.entity.gadget;

/**
 * Gadget content that sends the INTERACT_GADGET quest event itself, and only when its own checks
 * pass. EntityGadget then skips its generic credit, which would count rejected interactions and
 * accepted ones twice.
 */
public interface CreditsOwnInteraction {}
