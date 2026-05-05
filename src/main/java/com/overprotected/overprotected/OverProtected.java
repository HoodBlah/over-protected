package com.overprotected.overprotected;

import com.overprotected.overprotected.init.ModCreativeTabs;
import com.overprotected.overprotected.init.ModItems;
import com.overprotected.overprotected.init.ModMenuTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(OverProtected.MODID)
public class OverProtected {
    public static final String MODID = "overprotected";

    public OverProtected(IEventBus modEventBus) {
        ModItems.ITEMS.register(modEventBus);
        ModMenuTypes.MENU_TYPES.register(modEventBus);
        ModCreativeTabs.CREATIVE_TABS.register(modEventBus);
    }
}
