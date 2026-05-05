package com.overprotected.overprotected.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.overprotected.overprotected.item.StrapItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Render layer that makes an equipped StrapItem visually display the model and
 * texture of the first armor piece stored inside it. Added to player renderers
 * via {@link ClientEvents#onAddLayers}.
 *
 * Mirrors the logic of vanilla {@code HumanoidArmorLayer}, but reads the armor
 * item to render from the strap's {@code custom_data} (key {@code FirstArmorKey})
 * instead of from the item in the slot directly.
 */
@OnlyIn(Dist.CLIENT)
public class StrapArmorLayer<T extends LivingEntity, M extends HumanoidModel<T>>
        extends RenderLayer<T, M> {

    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.HEAD
    };

    private final HumanoidModel<T> innerModel; // leggings (layer_2)
    private final HumanoidModel<T> outerModel; // everything else (layer_1)

    @SuppressWarnings("unchecked")
    public StrapArmorLayer(RenderLayerParent<T, M> parent,
                           HumanoidModel<?> innerModel,
                           HumanoidModel<?> outerModel) {
        super(parent);
        this.innerModel = (HumanoidModel<T>) innerModel;
        this.outerModel = (HumanoidModel<T>) outerModel;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                       T entity, float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!(stack.getItem() instanceof StrapItem)) continue;

            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData == null) continue;
            CompoundTag tag = customData.copyTag();
            String keyStr = tag.getString("FirstArmorKey");
            if (keyStr.isEmpty()) continue;

            ResourceLocation rl = ResourceLocation.tryParse(keyStr);
            if (rl == null) continue;

            Item item = BuiltInRegistries.ITEM.get(rl);
            if (!(item instanceof ArmorItem armorItem)) continue;
            // Ensure the stored armor type actually belongs in this equipment slot.
            if (armorItem.getEquipmentSlot() != slot) continue;

            boolean inner = (slot == EquipmentSlot.LEGS);
            HumanoidModel<T> model = inner ? innerModel : outerModel;
            this.getParentModel().copyPropertiesTo(model);
            setPartVisibility(model, slot);

            int dyeArgb = FastColor.ARGB32.opaque(DyedItemColor.getOrDefault(stack, -6265536));

            for (ArmorMaterial.Layer layer : armorItem.getMaterial().value().layers()) {
                int color = layer.dyeable() ? dyeArgb : -1;
                VertexConsumer consumer = bufferSource.getBuffer(
                        RenderType.armorCutoutNoCull(layer.texture(inner)));
                model.renderToBuffer(poseStack, consumer, packedLight,
                        OverlayTexture.NO_OVERLAY, color);
            }
        }
    }

    protected void setPartVisibility(HumanoidModel<?> model, EquipmentSlot slot) {
        model.setAllVisible(false);
        switch (slot) {
            case HEAD  -> { model.head.visible = true; model.hat.visible = true; }
            case CHEST -> { model.body.visible = true; model.rightArm.visible = true; model.leftArm.visible = true; }
            case LEGS  -> { model.body.visible = true; model.rightLeg.visible = true; model.leftLeg.visible = true; }
            case FEET  -> { model.rightLeg.visible = true; model.leftLeg.visible = true; }
            default    -> {}
        }
    }
}
