package com.overprotected.overprotected;

import com.overprotected.overprotected.item.StrapInventory;
import com.overprotected.overprotected.item.StrapItem;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@EventBusSubscriber(modid = OverProtected.MODID)
public class CommonEvents {

    private static final Logger LOGGER = LogManager.getLogger("overprotected");

    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    /**
     * Injects attribute modifiers from CUSTOM_DATA into the strap's attribute set.
     * Uses the BonusModifiers list (new format) which captures ALL attribute types
     * including non-armor Apotheosis gem stats (max health, dodge, etc.).
     * Falls back to BonusArmor/Toughness/Knockback keys for old saves.
     */
    @SubscribeEvent
    public static void onItemAttributeModifier(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof StrapItem strapItem)) return;

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return;
        var tag = customData.copyTag();

        String slotName = strapItem.getArmorType().getName();
        EquipmentSlotGroup slotGroup = EquipmentSlotGroup.bySlot(strapItem.getArmorType().getSlot());

        // New format: BonusModifiers list — covers ALL gem attribute types.
        if (tag.contains(StrapInventory.BONUS_MODIFIERS_KEY, Tag.TAG_LIST)) {
            var modsList = tag.getList(StrapInventory.BONUS_MODIFIERS_KEY, Tag.TAG_COMPOUND);
            if (!modsList.isEmpty()) {
                Set<Holder<Attribute>> cleared = new HashSet<>();
                for (int i = 0; i < modsList.size(); i++) {
                    var modTag = modsList.getCompound(i);
                    String attrKey = modTag.getString("Attr");
                    int opOrdinal  = modTag.getInt("Op");
                    double amount  = modTag.getDouble("Amount");
                    if (amount == 0) continue;
                    var attrOpt = net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE
                            .getHolder(ResourceLocation.parse(attrKey));
                    if (attrOpt.isEmpty()) {
                        LOGGER.warn("[OP-DEBUG] onItemAttributeModifier: unknown attribute {}", attrKey);
                        continue;
                    }
                    Holder<Attribute> attrHolder = attrOpt.get();
                    if (cleared.add(attrHolder)) event.removeAllModifiersFor(attrHolder);
                    AttributeModifier.Operation op = AttributeModifier.Operation.values()[opOrdinal];
                    String suffix = attrKey.replace(":", "_").replace("/", "_").replace(".", "_");
                    event.addModifier(attrHolder,
                            new AttributeModifier(
                                    ResourceLocation.fromNamespaceAndPath("overprotected",
                                            "strap_" + slotName + "_" + suffix + "_" + opOrdinal),
                                    amount, op),
                            slotGroup);
                    LOGGER.info("[OP-DEBUG] inject: attr={} amount={} op={}", attrKey, amount, op);
                }
                return;
            }
        }

        // Backward compat: old saves with only BonusArmor/Toughness/Knockback keys.
        double bonusArmor     = tag.getDouble(StrapInventory.BONUS_ARMOR_KEY);
        double bonusToughness = tag.getDouble(StrapInventory.BONUS_TOUGHNESS_KEY);
        double bonusKnockback = tag.getDouble(StrapInventory.BONUS_KNOCKBACK_KEY);
        LOGGER.info("[OP-DEBUG] onItemAttributeModifier (legacy): slot={} item={} armor={} tough={} kb={}",
                stack.getItem().builtInRegistryHolder().key().location(),
                strapItem.getArmorType().getName(),
                bonusArmor, bonusToughness, bonusKnockback);

        if (bonusArmor == 0 && bonusToughness == 0 && bonusKnockback == 0) return;

        event.removeAllModifiersFor(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
        event.removeAllModifiersFor(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS);
        event.removeAllModifiersFor(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);

        if (bonusArmor != 0)
            event.addModifier(
                net.minecraft.world.entity.ai.attributes.Attributes.ARMOR,
                new AttributeModifier(
                    ResourceLocation.fromNamespaceAndPath("overprotected", "strap_armor_" + slotName),
                    bonusArmor, AttributeModifier.Operation.ADD_VALUE),
                slotGroup);
        if (bonusToughness != 0)
            event.addModifier(
                net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS,
                new AttributeModifier(
                    ResourceLocation.fromNamespaceAndPath("overprotected", "strap_toughness_" + slotName),
                    bonusToughness, AttributeModifier.Operation.ADD_VALUE),
                slotGroup);
        if (bonusKnockback != 0)
            event.addModifier(
                net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE,
                new AttributeModifier(
                    ResourceLocation.fromNamespaceAndPath("overprotected", "strap_knockback_" + slotName),
                    bonusKnockback, AttributeModifier.Operation.ADD_VALUE),
                slotGroup);
    }

    /**
     * Whenever a living entity takes physical damage, apply the same per-hit
     * durability damage to every armor piece stored inside any equipped strap.
     */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)) return;
        float rawDamage = event.getOriginalDamage();
        if (rawDamage <= 0) return;
        int armorDamage = Math.max(1, (int) (rawDamage / 4.0f));

        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack equipped = entity.getItemBySlot(slot);
            if (!(equipped.getItem() instanceof StrapItem strapItem)) continue;
            StrapInventory inv = new StrapInventory(
                    equipped, strapItem.getStrapTier().getSlots(),
                    entity.level().registryAccess());
            inv.applyArmorDamage(armorDamage, entity, slot);
        }
    }

    /**
     * When a player picks up an XP orb, repair the leftmost damaged stored item
     * that has Mending, consuming XP from the orb.
     */
    @SubscribeEvent
    public static void onXpPickup(PlayerXpEvent.PickupXp event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        var orb = event.getOrb();
        if (orb.value <= 0) return;

        Registry<Enchantment> enchReg = player.level().registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT);
        Optional<Holder.Reference<Enchantment>> mendingOpt = enchReg.getHolder(Enchantments.MENDING);
        if (mendingOpt.isEmpty()) return;
        Holder<Enchantment> mendingHolder = mendingOpt.get();

        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack equipped = player.getItemBySlot(slot);
            if (!(equipped.getItem() instanceof StrapItem strapItem)) continue;
            StrapInventory inv = new StrapInventory(
                    equipped, strapItem.getStrapTier().getSlots(),
                    player.level().registryAccess());

            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stored = inv.getItem(i);
                if (stored.isEmpty() || !stored.isDamageableItem() || stored.getDamageValue() == 0) continue;
                if (EnchantmentHelper.getItemEnchantmentLevel(mendingHolder, stored) == 0) continue;

                int repairAmount = Math.min(orb.value * 2, stored.getDamageValue());
                int xpUsed = (repairAmount + 1) / 2;
                stored.setDamageValue(stored.getDamageValue() - repairAmount);
                orb.value -= xpUsed;
                inv.saveItemsOnly();
                return;
            }
        }
    }
}
