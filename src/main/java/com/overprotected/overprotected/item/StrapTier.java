package com.overprotected.overprotected.item;

/**
 * Defines the tier of a Strap — determines how many armor pieces can be stored.
 */
public enum StrapTier {
    LEATHER(2),
    IRON(4),
    GOLDEN(6),
    DIAMOND(8),
    NETHERITE(16);

    private final int slots;

    StrapTier(int slots) {
        this.slots = slots;
    }

    public int getSlots() {
        return slots;
    }
}
