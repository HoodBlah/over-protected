package com.overprotected.overprotected.client;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

/**
 * Returns null from {@link #getHumanoidArmorModel} to prevent NeoForge's
 * {@code HumanoidArmorLayer} from rendering the strap. Visual rendering is
 * handled entirely by {@link StrapArmorLayer}.
 */
@OnlyIn(Dist.CLIENT)
public class StrapItemClientExtensions implements IClientItemExtensions {

    public static final StrapItemClientExtensions INSTANCE = new StrapItemClientExtensions();

    @Override
    public @Nullable net.minecraft.client.model.HumanoidModel<?> getHumanoidArmorModel(
            LivingEntity entity, ItemStack itemStack,
            EquipmentSlot armorSlot,
            net.minecraft.client.model.HumanoidModel<?> _default) {
        return null;
    }
}
