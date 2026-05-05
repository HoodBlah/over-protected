package com.overprotected.overprotected.init;

import com.overprotected.overprotected.OverProtected;
import com.overprotected.overprotected.menu.StrapMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, OverProtected.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<StrapMenu>> STRAP_MENU =
            MENU_TYPES.register("strap_menu", () ->
                    IMenuTypeExtension.create((windowId, inv, data) -> {
                        int slot = data.readInt();
                        net.minecraft.world.item.ItemStack stack;
                        if (slot == 40) {
                            stack = inv.player.getOffhandItem();
                        } else {
                            stack = inv.player.getInventory().getItem(slot);
                        }
                        return new StrapMenu(windowId, inv, stack, slot);
                    }));
}
