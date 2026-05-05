package com.overprotected.overprotected.item;

import com.overprotected.overprotected.menu.StrapMenu;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/**
 * A Strap item worn in an armor slot. Extends {@link Item} (not ArmorItem) so we
 * have full control over ATTRIBUTE_MODIFIERS.
 */
public class StrapItem extends Item {

    private final StrapTier strapTier;
    private final ArmorItem.Type armorType;

    public StrapItem(ArmorItem.Type armorType, StrapTier tier, Properties properties) {
        // Start with EMPTY attribute modifiers; values are set by StrapInventory.writeToStack()
        // whenever items are placed inside the strap.
        super(properties.component(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY));
        this.strapTier = tier;
        this.armorType = armorType;
    }

    public StrapTier getStrapTier() {
        return strapTier;
    }

    public ArmorItem.Type getArmorType() {
        return armorType;
    }

    /** Tells NeoForge/vanilla which armor slot this item goes into. */
    @Override
    public EquipmentSlot getEquipmentSlot(ItemStack stack) {
        return armorType.getSlot();
    }

    /**
     * Right-click to open the strap inventory GUI.
     * Opening from the armor slot is handled via the equip mechanic separately.
     */
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(com.overprotected.overprotected.client.StrapItemClientExtensions.INSTANCE);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            int slotIndex = hand == InteractionHand.MAIN_HAND
                    ? player.getInventory().selected
                    : 40; // off-hand slot index

            final int finalSlot = slotIndex;
            ((ServerPlayer) player).openMenu(
                    new SimpleMenuProvider(
                            (id, inv, p) -> new StrapMenu(id, inv, stack, finalSlot),
                            Component.translatable("container.overprotected." + armorType.getName() + "_strap")
                    ),
                    (RegistryFriendlyByteBuf buf) -> buf.writeInt(finalSlot)
            );
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

}
