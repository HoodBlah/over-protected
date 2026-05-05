package com.overprotected.overprotected.menu;

import com.overprotected.overprotected.init.ModMenuTypes;
import com.overprotected.overprotected.item.StrapInventory;
import com.overprotected.overprotected.item.StrapItem;
import com.overprotected.overprotected.item.StrapTier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * Menu (container) for a Strap item.
 * Shows {@code strapSlots} armor slots at the top, followed by the player inventory.
 */
public class StrapMenu extends AbstractContainerMenu {

    private final StrapInventory strapInventory;
    private final int strapSlots;
    private final boolean isTwoRows;
    /** Index of the strap in the player inventory so we can find it. */
    private final int strapInvSlot;
    /** ATTRIBUTE_MODIFIERS when the menu was opened — used to cleanly remove live modifiers in removed(). */
    private final ItemAttributeModifiers modsAtOpen;

    // Slot layout constants
    private static final int STRAP_ROW_X = 8;
    private static final int STRAP_ROW_Y = 18;
    private static final int PLAYER_INV_X = 8;
    private static final int PLAYER_INV_Y_BASE = 84; // adjusted dynamically
    private static final int PLAYER_HOTBAR_Y_BASE = 142;

    public StrapMenu(int containerId, Inventory playerInv, ItemStack strapStack, int strapInvSlot) {
        super(ModMenuTypes.STRAP_MENU.get(), containerId);
        this.strapInvSlot = strapInvSlot;

        StrapItem strapItem = (StrapItem) strapStack.getItem();
        this.strapSlots = strapItem.getStrapTier().getSlots();
        net.minecraft.core.HolderLookup.Provider registries =
                playerInv.player.level().registryAccess();
        this.strapInventory = new StrapInventory(strapStack, strapSlots, registries);
        this.isTwoRows = strapSlots > 8;
        // Snapshot component state at open time — this is what the game applied to the live
        // AttributeMap at equip, so removed() can remove exactly those before adding new values.
        this.modsAtOpen = strapStack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);

        // Determine which armor type is accepted
        net.minecraft.world.item.ArmorItem.Type acceptedType = strapItem.getArmorType();

        // Netherite (16 slots) uses 2 rows of 8; all others fit in a single row.
        int slotsPerRow = isTwoRows ? 8 : strapSlots;

        // --- Strap slots (centered horizontally) ---
        int strapStartX = (176 - slotsPerRow * 18) / 2;
        for (int i = 0; i < strapSlots; i++) {
            int slotRow = i / slotsPerRow;
            int slotCol = i % slotsPerRow;
            int sx = strapStartX + slotCol * 18;
            int sy = STRAP_ROW_Y + slotRow * 18;
            final int slot = i;
            addSlot(new Slot(strapInventory, slot, sx, sy) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    if (!(stack.getItem() instanceof net.minecraft.world.item.ArmorItem armor)) return false;
                    return armor.getType() == acceptedType && !(stack.getItem() instanceof StrapItem);
                }
                @Override
                public int getMaxStackSize() { return 1; }
            });
        }

        // Player inventory sits below the last strap row with enough gap for the label.
        int strapRowCount = isTwoRows ? 2 : 1;
        int playerInvY = STRAP_ROW_Y + strapRowCount * 18 + 22;
        int hotbarY = playerInvY + 58;

        // --- Player inventory (3 rows) ---
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9,
                        PLAYER_INV_X + col * 18,
                        playerInvY + row * 18));
            }
        }

        // --- Hotbar ---
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, PLAYER_INV_X + col * 18, hotbarY));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /**
     * Shift-click support: moves items between strap and inventory.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            result = stackInSlot.copy();
            if (index < strapSlots) {
                // From strap -> player inventory
                if (!moveItemStackTo(stackInSlot, strapSlots, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // From player inventory -> strap
                if (!moveItemStackTo(stackInSlot, 0, strapSlots, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (stackInSlot.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return result;
    }

    public StrapInventory getStrapInventory() {
        return strapInventory;
    }

    public boolean isTwoRows() {
        return isTwoRows;
    }

    public StrapTier getStrapTier() {
        return ((StrapItem) strapInventory.getStrapStack().getItem()).getStrapTier();
    }

    /**
     * Called when the container is closed. Flushes inventory to the stack then
     * updates the player's live AttributeMap so new stats take effect immediately.
     */
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player.level().isClientSide()) return;

        strapInventory.writeToStack();

        ItemStack strapStack = strapInventory.getStrapStack();
        StrapItem strapItem  = (StrapItem) strapStack.getItem();
        EquipmentSlot equipSlot = strapItem.getArmorType().getSlot();

        if (player.getItemBySlot(equipSlot) != strapStack) {
            return;
        }

        ItemAttributeModifiers newMods = strapStack.getOrDefault(
                DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        for (ItemAttributeModifiers.Entry entry : modsAtOpen.modifiers()) {
            AttributeInstance inst = player.getAttribute(entry.attribute());
            if (inst != null) inst.removeModifier(entry.modifier().id());
        }
        for (ItemAttributeModifiers.Entry entry : newMods.modifiers()) {
            AttributeInstance inst = player.getAttribute(entry.attribute());
            if (inst == null) continue;
            inst.removeModifier(entry.modifier().id()); // safety: avoid duplicates
            if (entry.modifier().amount() != 0) inst.addTransientModifier(entry.modifier());
        }
    }
}
