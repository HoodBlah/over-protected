package com.overprotected.overprotected.init;

import com.overprotected.overprotected.OverProtected;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, OverProtected.MODID);

    public static final Supplier<CreativeModeTab> STRAPS_TAB =
            CREATIVE_TABS.register("straps", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.overprotected.straps"))
                    .icon(() -> ModItems.DIAMOND_CHESTPLATE_STRAP.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        // Ordered: helmet, chestplate, leggings, boots — per tier
                        output.accept(ModItems.LEATHER_HELMET_STRAP.get());
                        output.accept(ModItems.LEATHER_CHESTPLATE_STRAP.get());
                        output.accept(ModItems.LEATHER_LEGGINGS_STRAP.get());
                        output.accept(ModItems.LEATHER_BOOTS_STRAP.get());

                        output.accept(ModItems.IRON_HELMET_STRAP.get());
                        output.accept(ModItems.IRON_CHESTPLATE_STRAP.get());
                        output.accept(ModItems.IRON_LEGGINGS_STRAP.get());
                        output.accept(ModItems.IRON_BOOTS_STRAP.get());

                        output.accept(ModItems.GOLDEN_HELMET_STRAP.get());
                        output.accept(ModItems.GOLDEN_CHESTPLATE_STRAP.get());
                        output.accept(ModItems.GOLDEN_LEGGINGS_STRAP.get());
                        output.accept(ModItems.GOLDEN_BOOTS_STRAP.get());

                        output.accept(ModItems.DIAMOND_HELMET_STRAP.get());
                        output.accept(ModItems.DIAMOND_CHESTPLATE_STRAP.get());
                        output.accept(ModItems.DIAMOND_LEGGINGS_STRAP.get());
                        output.accept(ModItems.DIAMOND_BOOTS_STRAP.get());

                        output.accept(ModItems.NETHERITE_HELMET_STRAP.get());
                        output.accept(ModItems.NETHERITE_CHESTPLATE_STRAP.get());
                        output.accept(ModItems.NETHERITE_LEGGINGS_STRAP.get());
                        output.accept(ModItems.NETHERITE_BOOTS_STRAP.get());
                    })
                    .build());
}
