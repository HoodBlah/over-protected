package com.overprotected.overprotected;

import com.overprotected.overprotected.item.StrapInventory;
import com.overprotected.overprotected.item.StrapItem;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

@EventBusSubscriber(modid = OverProtected.MODID)
public class CommonEvents {

    private static final Logger LOGGER = LogManager.getLogger("overprotected");

    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    /**
     * Whenever a living entity takes physical damage, apply the same
     * per-hit durability damage to every armor piece stored inside any
     * equipped strap — mirroring vanilla's hurtArmor formula (max(1, dmg/4)).
     */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        // Damage sources that bypass armor don't damage armor items either.
        if (event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)) return;
        float rawDamage = event.getOriginalDamage();
        if (rawDamage <= 0) return;
        int armorDamage = Math.max(1, (int) (rawDamage / 4.0f));

        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack equipped = entity.getItemBySlot(slot);
            if (!(equipped.getItem() instanceof StrapItem strapItem)) continue;

            // Log armor attribute BEFORE damage is applied
            net.minecraft.world.entity.ai.attributes.AttributeInstance armorBefore =
                    entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
            LOGGER.info("[OP-DEBUG] HIT slot={} entity={} rawDmg={} armorDmg={} | armorValue BEFORE={}",
                    slot.getName(), entity.getName().getString(), rawDamage, armorDamage,
                    armorBefore != null ? armorBefore.getValue() : "null");

            // Log ATTRIBUTE_MODIFIERS on strapStack before
            ItemAttributeModifiers modsBefore = equipped.getOrDefault(
                    net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
                    ItemAttributeModifiers.EMPTY);
            LOGGER.info("[OP-DEBUG]   modifiers on stack BEFORE ({} entries):", modsBefore.modifiers().size());
            for (ItemAttributeModifiers.Entry e : modsBefore.modifiers()) {
                LOGGER.info("[OP-DEBUG]     attr={} amount={} op={}",
                        e.attribute().unwrapKey().map(k -> k.location().toString()).orElse("?"),
                        e.modifier().amount(), e.modifier().operation());
            }

            StrapInventory inv = new StrapInventory(
                    equipped, strapItem.getStrapTier().getSlots(),
                    entity.level().registryAccess());
            inv.applyArmorDamage(armorDamage, entity, slot);

            // Log ATTRIBUTE_MODIFIERS on strapStack after save
            ItemAttributeModifiers modsAfter = equipped.getOrDefault(
                    net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
                    ItemAttributeModifiers.EMPTY);
            LOGGER.info("[OP-DEBUG]   modifiers on stack AFTER save ({} entries):", modsAfter.modifiers().size());
            for (ItemAttributeModifiers.Entry e : modsAfter.modifiers()) {
                LOGGER.info("[OP-DEBUG]     attr={} amount={} op={}",
                        e.attribute().unwrapKey().map(k -> k.location().toString()).orElse("?"),
                        e.modifier().amount(), e.modifier().operation());
            }

            reapplyModifiers(equipped, slot, entity);

            // Log armor attribute AFTER reapply
            net.minecraft.world.entity.ai.attributes.AttributeInstance armorAfter =
                    entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
            LOGGER.info("[OP-DEBUG]   armorValue AFTER reapply={}",
                    armorAfter != null ? armorAfter.getValue() : "null");
        }
    }

    /**
     * Force-reapplies the ATTRIBUTE_MODIFIERS from the given strapStack to the
     * entity's live AttributeMap. Called after any write to CUSTOM_DATA so that
     * NeoForge's equipment-change detection cannot accidentally remove them.
     */
    private static void reapplyModifiers(ItemStack strapStack, EquipmentSlot slot, LivingEntity entity) {
        ItemAttributeModifiers mods = strapStack.getOrDefault(
                net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
                ItemAttributeModifiers.EMPTY);
        mods.forEach(slot, (attrHolder, modifier) -> {
            AttributeInstance inst = entity.getAttribute(attrHolder);
            String attrName = attrHolder.unwrapKey().map(k -> k.location().toString()).orElse("?");
            if (inst == null) {
                LOGGER.warn("[OP-DEBUG]   reapply: AttributeInstance is NULL for attr={}", attrName);
                return;
            }
            inst.removeModifier(modifier.id());
            if (modifier.amount() != 0) inst.addTransientModifier(modifier);
            LOGGER.info("[OP-DEBUG]   reapply: attr={} amount={} -> instValue={}",
                    attrName, modifier.amount(), inst.getValue());
        });
    }

    /**
     * When a player picks up an XP orb, repair the leftmost damaged stored
     * item that has Mending, consuming XP from the orb. Only one item per orb
     * is repaired (vanilla mending handles the rest with remaining orb value).
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

                // Vanilla mending formula: 2 durability repaired per 1 XP consumed.
                int repairAmount = Math.min(orb.value * 2, stored.getDamageValue());
                int xpUsed = (repairAmount + 1) / 2;
                stored.setDamageValue(stored.getDamageValue() - repairAmount);
                orb.value -= xpUsed;
                inv.saveItemsOnly();
                reapplyModifiers(equipped, slot, player);
                return; // only one stored item per orb pickup
            }
        }
    }
}
