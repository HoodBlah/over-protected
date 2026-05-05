package com.overprotected.overprotected.init;

import com.overprotected.overprotected.OverProtected;
import com.overprotected.overprotected.item.StrapItem;
import com.overprotected.overprotected.item.StrapTier;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(OverProtected.MODID);

    // ---- Leather Straps (2 slots) ----
    public static final DeferredItem<StrapItem> LEATHER_HELMET_STRAP = register(
            "leather_helmet_strap", ArmorItem.Type.HELMET, StrapTier.LEATHER);
    public static final DeferredItem<StrapItem> LEATHER_CHESTPLATE_STRAP = register(
            "leather_chestplate_strap", ArmorItem.Type.CHESTPLATE, StrapTier.LEATHER);
    public static final DeferredItem<StrapItem> LEATHER_LEGGINGS_STRAP = register(
            "leather_leggings_strap", ArmorItem.Type.LEGGINGS, StrapTier.LEATHER);
    public static final DeferredItem<StrapItem> LEATHER_BOOTS_STRAP = register(
            "leather_boots_strap", ArmorItem.Type.BOOTS, StrapTier.LEATHER);

    // ---- Iron Straps (4 slots) ----
    public static final DeferredItem<StrapItem> IRON_HELMET_STRAP = register(
            "iron_helmet_strap", ArmorItem.Type.HELMET, StrapTier.IRON);
    public static final DeferredItem<StrapItem> IRON_CHESTPLATE_STRAP = register(
            "iron_chestplate_strap", ArmorItem.Type.CHESTPLATE, StrapTier.IRON);
    public static final DeferredItem<StrapItem> IRON_LEGGINGS_STRAP = register(
            "iron_leggings_strap", ArmorItem.Type.LEGGINGS, StrapTier.IRON);
    public static final DeferredItem<StrapItem> IRON_BOOTS_STRAP = register(
            "iron_boots_strap", ArmorItem.Type.BOOTS, StrapTier.IRON);

    // ---- Gold Straps (6 slots) ----
    public static final DeferredItem<StrapItem> GOLDEN_HELMET_STRAP = register(
            "golden_helmet_strap", ArmorItem.Type.HELMET, StrapTier.GOLDEN);
    public static final DeferredItem<StrapItem> GOLDEN_CHESTPLATE_STRAP = register(
            "golden_chestplate_strap", ArmorItem.Type.CHESTPLATE, StrapTier.GOLDEN);
    public static final DeferredItem<StrapItem> GOLDEN_LEGGINGS_STRAP = register(
            "golden_leggings_strap", ArmorItem.Type.LEGGINGS, StrapTier.GOLDEN);
    public static final DeferredItem<StrapItem> GOLDEN_BOOTS_STRAP = register(
            "golden_boots_strap", ArmorItem.Type.BOOTS, StrapTier.GOLDEN);

    // ---- Diamond Straps (8 slots) ----
    public static final DeferredItem<StrapItem> DIAMOND_HELMET_STRAP = register(
            "diamond_helmet_strap", ArmorItem.Type.HELMET, StrapTier.DIAMOND);
    public static final DeferredItem<StrapItem> DIAMOND_CHESTPLATE_STRAP = register(
            "diamond_chestplate_strap", ArmorItem.Type.CHESTPLATE, StrapTier.DIAMOND);
    public static final DeferredItem<StrapItem> DIAMOND_LEGGINGS_STRAP = register(
            "diamond_leggings_strap", ArmorItem.Type.LEGGINGS, StrapTier.DIAMOND);
    public static final DeferredItem<StrapItem> DIAMOND_BOOTS_STRAP = register(
            "diamond_boots_strap", ArmorItem.Type.BOOTS, StrapTier.DIAMOND);

    // ---- Netherite Straps (16 slots) ----
    public static final DeferredItem<StrapItem> NETHERITE_HELMET_STRAP = register(
            "netherite_helmet_strap", ArmorItem.Type.HELMET, StrapTier.NETHERITE);
    public static final DeferredItem<StrapItem> NETHERITE_CHESTPLATE_STRAP = register(
            "netherite_chestplate_strap", ArmorItem.Type.CHESTPLATE, StrapTier.NETHERITE);
    public static final DeferredItem<StrapItem> NETHERITE_LEGGINGS_STRAP = register(
            "netherite_leggings_strap", ArmorItem.Type.LEGGINGS, StrapTier.NETHERITE);
    public static final DeferredItem<StrapItem> NETHERITE_BOOTS_STRAP = register(
            "netherite_boots_strap", ArmorItem.Type.BOOTS, StrapTier.NETHERITE);

    private static DeferredItem<StrapItem> register(String name, ArmorItem.Type type, StrapTier tier) {
        return ITEMS.register(name, () -> new StrapItem(type, tier, new Item.Properties().stacksTo(1)));
    }
}
