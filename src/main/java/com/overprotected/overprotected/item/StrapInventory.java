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
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;

/**
 * NBT-backed inventory stored inside the {@code minecraft:custom_data} component
 * on the strap ItemStack. Computes and writes bonus armor stats and enchantments
 * to the strap whenever its contents change.
 */
public class StrapInventory implements Container {

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
    public static final String BONUS_TOUGHNESS_KEY = "BonusToughness";
    public static final String BONUS_KNOCKBACK_KEY = "BonusKnockback";

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
        // Clear any stale custom name that may have been copied from a previous stored item.
        strapStack.remove(DataComponents.CUSTOM_NAME);

        CustomData existing = strapStack.get(DataComponents.CUSTOM_DATA);
        CompoundTag rootTag = existing != null ? existing.copyTag() : new CompoundTag();

        ListTag list = new ListTag();
        double bonusArmor = 0, bonusToughness = 0, bonusKnockback = 0;
        Map<Holder<Enchantment>, Integer> enchMap = new HashMap<>();

        // Track extra attrs by Attribute object identity + operation (captures inline holders
        // and avoids string-key issues). Separate map keeps the Holder reference for building.
        Map<Attribute, Map<AttributeModifier.Operation, Double>> extraAmounts = new IdentityHashMap<>();
        Map<Attribute, Holder<Attribute>> extraHolders = new IdentityHashMap<>();

        // Singleton references for fast identity-based exclusion of standard armor attrs.
        Attribute armorAttr    = Attributes.ARMOR.value();
        Attribute toughnessAttr = Attributes.ARMOR_TOUGHNESS.value();
        Attribute knockbackAttr = Attributes.KNOCKBACK_RESISTANCE.value();

        // Track first armor item's registry key for client-side render proxy.
        String firstArmorKey = "";

        for (int i = 0; i < size; i++) {
            ItemStack stored = items.get(i);
            if (stored.isEmpty()) continue;

            CompoundTag entry = new CompoundTag();
            entry.putByte("Slot", (byte) i);
            list.add(stored.save(registries, entry));

            if (stored.getItem() instanceof ArmorItem armorItem) {
                ArmorMaterial mat = armorItem.getMaterial().value();
                double defense   = mat.defense().getOrDefault(armorItem.getType(), 0);
                double toughness = mat.toughness();
                double knockback = mat.knockbackResistance();
                bonusArmor     += defense;
                bonusToughness += toughness;
                bonusKnockback += knockback;

                if (firstArmorKey.isEmpty()) {
                    // Safe to call registryAccess().lookup() on server; on client BuiltInRegistries works too.
                    firstArmorKey = net.minecraft.core.registries.BuiltInRegistries.ITEM
                            .getResourceKey(stored.getItem())
                            .map(k -> k.location().toString())
                            .orElse("");
                }

                // Fire ItemAttributeModifierEvent for the stored item so that mods like
                // Apotheosis (which inject stats via the event, not the component) contribute.
                // Constructor takes (ItemStack, existing ItemAttributeModifiers from the stack).
                ItemAttributeModifiers existingMods = stored.getOrDefault(
                        DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
                ItemAttributeModifierEvent innerEvent = new ItemAttributeModifierEvent(stored, existingMods);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(innerEvent);

                for (ItemAttributeModifiers.Entry ae : innerEvent.getModifiers()) {
                    Attribute av = ae.attribute().value();
                    // Skip standard armor attrs — handled via ArmorMaterial above.
                    if (av == armorAttr || av == toughnessAttr || av == knockbackAttr) continue;
                    extraAmounts
                            .computeIfAbsent(av, k -> new HashMap<>())
                            .merge(ae.modifier().operation(), ae.modifier().amount(), Double::sum);
                    extraHolders.putIfAbsent(av, ae.attribute());
                }
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

        rootTag.put(NBT_KEY, list);
        rootTag.putDouble(BONUS_ARMOR_KEY,     bonusArmor);
        rootTag.putDouble(BONUS_TOUGHNESS_KEY, bonusToughness);
        rootTag.putDouble(BONUS_KNOCKBACK_KEY, bonusKnockback);
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

        // Build ATTRIBUTE_MODIFIERS component — everything except Apotheosis event-injected
        // stats, which are re-added dynamically in CommonEvents.onItemAttributes().
        EquipmentSlotGroup slotGroup = EquipmentSlotGroup.bySlot(
                ((StrapItem) strapStack.getItem()).getArmorType().getSlot());
        ItemAttributeModifiers.Builder modBuilder = ItemAttributeModifiers.builder();
        if (bonusArmor != 0)
            modBuilder.add(Attributes.ARMOR, new AttributeModifier(
                    ResourceLocation.fromNamespaceAndPath("overprotected", "strap_armor"),
                    bonusArmor, AttributeModifier.Operation.ADD_VALUE), slotGroup);
        if (bonusToughness != 0)
            modBuilder.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(
                    ResourceLocation.fromNamespaceAndPath("overprotected", "strap_toughness"),
                    bonusToughness, AttributeModifier.Operation.ADD_VALUE), slotGroup);
        if (bonusKnockback != 0)
            modBuilder.add(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(
                    ResourceLocation.fromNamespaceAndPath("overprotected", "strap_knockback"),
                    bonusKnockback, AttributeModifier.Operation.ADD_VALUE), slotGroup);

        int extraCount = 0;
        for (var attrEntry : extraAmounts.entrySet()) {
            Attribute av = attrEntry.getKey();
            Holder<Attribute> holder = extraHolders.get(av);
            for (var opEntry : attrEntry.getValue().entrySet()) {
                if (opEntry.getValue() == 0) continue;
                String attrPath = holder.unwrapKey()
                        .map(k -> k.location().toString()
                                .replace(":", "_").replace("/", "_").replace(".", "_"))
                        .orElseGet(() -> "inline_" + Integer.toHexString(System.identityHashCode(av)));
                ResourceLocation modId = ResourceLocation.fromNamespaceAndPath("overprotected",
                        "strap_" + attrPath + "_" + opEntry.getKey().ordinal());
                modBuilder.add(holder, new AttributeModifier(modId, opEntry.getValue(),
                        opEntry.getKey()), slotGroup);
                extraCount++;
            }
        }

        strapStack.set(DataComponents.ATTRIBUTE_MODIFIERS, modBuilder.build());
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
            stored.hurtAndBreak(amount, entity, strapSlot);
            if (stored.isEmpty()) {
                items.set(i, ItemStack.EMPTY);
                anyBroke = true;
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
