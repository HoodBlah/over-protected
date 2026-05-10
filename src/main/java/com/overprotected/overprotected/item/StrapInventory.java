package com.overprotected.overprotected.item;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * NBT-backed inventory stored inside the {@code minecraft:custom_data} component
 * on the strap ItemStack. Computes and writes bonus armor stats and enchantments
 * to the strap whenever its contents change.
 */
public class StrapInventory implements Container {

    private static final Logger LOGGER = LogManager.getLogger("overprotected");

    /** Components never copied from stored pieces onto the strap. */
    @SuppressWarnings("rawtypes")
    private static final Set<DataComponentType> COPY_BLOCKLIST = Set.of(
            DataComponents.CUSTOM_DATA,          // managed by us
            DataComponents.ATTRIBUTE_MODIFIERS,   // injected via ItemAttributeModifierEvent
            DataComponents.ENCHANTMENTS,          // handled separately (stacking)
            DataComponents.MAX_STACK_SIZE,
            DataComponents.MAX_DAMAGE,
            DataComponents.DAMAGE,               // don't copy durability state
            DataComponents.CUSTOM_NAME,          // don't rename the strap
            DataComponents.ITEM_NAME,            // don't rename the strap (translation key)
            DataComponents.LORE,
            DataComponents.RARITY,
            DataComponents.REPAIR_COST,
            DataComponents.FOOD,
            DataComponents.TOOL,
            DataComponents.HIDE_TOOLTIP,
            DataComponents.HIDE_ADDITIONAL_TOOLTIP
    );

    public static final String NBT_KEY          = "StrapItems";
    public static final String BONUS_ARMOR_KEY     = "BonusArmor";
    public static final String BONUS_TOUGHNESS_KEY  = "BonusToughness";
    public static final String BONUS_KNOCKBACK_KEY  = "BonusKnockback";
    /** NBT list of {Attr, Op, Amount} for every attribute modifier accumulated from stored items.
     *  Supersedes BonusArmor/BonusToughness/BonusKnockback — injected via ItemAttributeModifierEvent. */
    public static final String BONUS_MODIFIERS_KEY  = "BonusModifiers";

    private final ItemStack strapStack;
    private final NonNullList<ItemStack> items;
    private final int size;
    private final HolderLookup.Provider registries;

    public StrapInventory(ItemStack strapStack, int size, HolderLookup.Provider registries) {
        this.strapStack = strapStack;
        this.size = size;
        this.registries = registries;
        this.items = NonNullList.withSize(size, ItemStack.EMPTY);
        readFromStack();
    }

    // ---------- NBT round-trip ----------

    public void readFromStack() {
        CustomData customData = strapStack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return;
        CompoundTag rootTag = customData.copyTag();
        if (rootTag.contains(NBT_KEY, Tag.TAG_LIST)) {
            ListTag list = rootTag.getList(NBT_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                int slot = entry.getByte("Slot") & 0xFF;
                if (slot < size) {
                    ItemStack.parse(registries, entry).ifPresent(parsed -> items.set(slot, parsed));
                }
            }
        }
    }

    public void writeToStack() {
        // Guard: if the stack was cleared (e.g. player disconnected mid-session) bail out.
        if (strapStack.isEmpty() || !(strapStack.getItem() instanceof StrapItem)) return;
        // Clear any stale custom name that may have been copied from a previous stored item.
        strapStack.remove(DataComponents.CUSTOM_NAME);

        CustomData existing = strapStack.get(DataComponents.CUSTOM_DATA);
        CompoundTag rootTag = existing != null ? existing.copyTag() : new CompoundTag();

        ListTag list = new ListTag();
        double bonusArmor = 0, bonusToughness = 0, bonusKnockback = 0;
        // General accumulator for ALL attribute types (captures non-standard gem stats).
        // Key: attribute registry name -> (operation ordinal -> summed amount)
        Map<String, Map<Integer, Double>> attrAccum = new java.util.LinkedHashMap<>();
        Map<Holder<Enchantment>, Integer> enchMap = new HashMap<>();

        // Track first armor item's registry key for client-side render proxy.
        String firstArmorKey = "";

        for (int i = 0; i < size; i++) {
            ItemStack stored = items.get(i);
            if (stored.isEmpty()) continue;

            CompoundTag entry = new CompoundTag();
            entry.putByte("Slot", (byte) i);
            list.add(stored.save(registries, entry));

            if (stored.getItem() instanceof ArmorItem armorItem) {
                if (firstArmorKey.isEmpty()) {
                    firstArmorKey = net.minecraft.core.registries.BuiltInRegistries.ITEM
                            .getResourceKey(stored.getItem())
                            .map(k -> k.location().toString())
                            .orElse("");
                }

                EquipmentSlot itemSlot = armorItem.getType().getSlot();
                var allMods = stored.getAttributeModifiers();
                LOGGER.info("[OP-DEBUG] writeToStack slot={} item={} totalModifiers={}",
                        itemSlot.getName(),
                        net.minecraft.core.registries.BuiltInRegistries.ITEM
                                .getKey(stored.getItem()),
                        allMods.modifiers().size());
                for (var ae : allMods.modifiers()) {
                    String attrName = ae.attribute().unwrapKey()
                            .map(k -> k.location().toString()).orElse("?");
                    LOGGER.info("[OP-DEBUG]   mod: attr={} amount={} slot={} passesFilter={}",
                            attrName, ae.modifier().amount(), ae.slot(), ae.slot().test(itemSlot));
                    if (!ae.slot().test(itemSlot)) continue;
                    var av = ae.attribute().value();
                    if (av == Attributes.ARMOR.value())                bonusArmor     += ae.modifier().amount();
                    else if (av == Attributes.ARMOR_TOUGHNESS.value())      bonusToughness += ae.modifier().amount();
                    else if (av == Attributes.KNOCKBACK_RESISTANCE.value())  bonusKnockback += ae.modifier().amount();
                    // Accumulate into general map for ALL attr types (including standard ones)
                    ae.attribute().unwrapKey().ifPresent(k -> {
                        attrAccum.computeIfAbsent(k.location().toString(),
                                        x -> new java.util.LinkedHashMap<>())
                                .merge(ae.modifier().operation().ordinal(),
                                        ae.modifier().amount(), Double::sum);
                    });
                }
                LOGGER.info("[OP-DEBUG]   running totals after {}: armor={} tough={} kb={} extraAttrTypes={}",
                        itemSlot.getName(), bonusArmor, bonusToughness, bonusKnockback, attrAccum.size());
            }

            // Collect enchantments.
            ItemEnchantments enchants = stored.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            for (var enc : enchants.entrySet()) {
                enchMap.merge(enc.getKey(), enc.getValue(), Integer::sum);
            }

            // Copy only vanilla (minecraft namespace) components onto the strap so modded
            // item-identity data (e.g. Apotheosis affixes) is never transferred.
            for (TypedDataComponent<?> comp : stored.getComponents()) {
                if (COPY_BLOCKLIST.contains(comp.type())) continue;
                boolean isVanilla = net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE
                        .getResourceKey(comp.type())
                        .map(k -> k.location().getNamespace().equals("minecraft"))
                        .orElse(false);
                if (isVanilla) copyComponent(strapStack, comp);
            }
        }

        LOGGER.info("[OP-DEBUG] writeToStack FINAL: armor={} tough={} kb={} allAttrTypes={}",
                bonusArmor, bonusToughness, bonusKnockback, attrAccum.keySet());
        rootTag.put(NBT_KEY, list);
        rootTag.putDouble(BONUS_ARMOR_KEY,     bonusArmor);
        rootTag.putDouble(BONUS_TOUGHNESS_KEY, bonusToughness);
        rootTag.putDouble(BONUS_KNOCKBACK_KEY, bonusKnockback);
        // Store full modifier list (includes all gem attribute types)
        net.minecraft.nbt.ListTag bonusModsTag = new net.minecraft.nbt.ListTag();
        for (var attrEntry : attrAccum.entrySet()) {
            for (var opEntry : attrEntry.getValue().entrySet()) {
                if (opEntry.getValue() == 0) continue;
                CompoundTag mod = new CompoundTag();
                mod.putString("Attr", attrEntry.getKey());
                mod.putInt("Op", opEntry.getKey());
                mod.putDouble("Amount", opEntry.getValue());
                bonusModsTag.add(mod);
            }
        }
        rootTag.put(BONUS_MODIFIERS_KEY, bonusModsTag);
        if (!firstArmorKey.isEmpty()) rootTag.putString("FirstArmorKey", firstArmorKey);
        else rootTag.remove("FirstArmorKey");
        strapStack.set(DataComponents.CUSTOM_DATA, CustomData.of(rootTag));

        // Enchantments.
        if (enchMap.isEmpty()) {
            strapStack.remove(DataComponents.ENCHANTMENTS);
        } else {
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            enchMap.forEach(mutable::set);
            strapStack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
        }

        // NOTE: ATTRIBUTE_MODIFIERS are no longer written to the stack here.
        // They are injected dynamically via ItemAttributeModifierEvent in CommonEvents,
        // reading BonusArmor/BonusToughness/BonusKnockback from CUSTOM_DATA above.
        // Explicitly clear the component so that any stale values persisted by older
        // versions of this mod do not accumulate with the event-injected values.
        strapStack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
    }

    /**
     * Damages every stored armor piece by {@code amount}, mirroring vanilla's
     * per-slot armor-durability formula. If any item breaks the strap stats are
     * recomputed via {@link #writeToStack()}; otherwise only the item list is
     * persisted so the heavier stat-recompute is avoided.
     */
    public void applyArmorDamage(int amount, net.minecraft.world.entity.LivingEntity entity, EquipmentSlot strapSlot) {
        if (amount <= 0) return;
        boolean anyBroke = false;
        for (int i = 0; i < size; i++) {
            ItemStack stored = items.get(i);
            if (stored.isEmpty() || !stored.isDamageableItem()) continue;
            // Apply durability damage directly — do NOT call hurtAndBreak(amount, entity, strapSlot).
            // That method calls entity.onEquippedItemBroken(item, strapSlot) when a stored piece
            // breaks, which then calls entity.setItemSlot(strapSlot, EMPTY) and physically removes
            // the STRAP from the armor slot, causing all armor values to reset to zero.
            int newDamage = stored.getDamageValue() + amount;
            if (newDamage >= stored.getMaxDamage()) {
                items.set(i, ItemStack.EMPTY);
                anyBroke = true;
            } else {
                stored.setDamageValue(newDamage);
            }
        }
        if (anyBroke) {
            writeToStack();
        } else {
            saveItemsOnly();
        }
    }

    /**
     * Persists only the item list (slot positions and damage values) back into
     * the strap's {@code custom_data} without recomputing attribute modifiers,
     * enchantments, or the {@code FirstArmorKey}. Use when content didn't change
     * but individual items were damaged.
     */
    public void saveItemsOnly() {
        CustomData existing = strapStack.get(DataComponents.CUSTOM_DATA);
        CompoundTag rootTag = existing != null ? existing.copyTag() : new CompoundTag();
        ListTag list = new ListTag();
        for (int i = 0; i < size; i++) {
            ItemStack stored = items.get(i);
            if (stored.isEmpty()) continue;
            CompoundTag entry = new CompoundTag();
            entry.putByte("Slot", (byte) i);
            list.add(stored.save(registries, entry));
        }
        rootTag.put(NBT_KEY, list);
        strapStack.set(DataComponents.CUSTOM_DATA, CustomData.of(rootTag));
    }

    /** Type-safe helper: copies a single component onto a target stack. */
    private static <T> void copyComponent(ItemStack target, TypedDataComponent<T> comp) {
        target.set(comp.type(), comp.value());
    }

    /** Returns the ItemStack this inventory is backed by. */
    public ItemStack getStrapStack() { return strapStack; }

    // ---------- Container ----------

    @Override public int getContainerSize() { return size; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) writeToStack();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack result = ContainerHelper.takeItem(items, slot);
        if (!result.isEmpty()) writeToStack();
        return result;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        writeToStack();
    }

    @Override public void setChanged() { writeToStack(); }
    @Override public boolean stillValid(Player player) { return true; }
    @Override public void clearContent() { items.clear(); writeToStack(); }
}
