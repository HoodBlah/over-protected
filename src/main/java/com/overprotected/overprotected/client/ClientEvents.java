package com.overprotected.overprotected.client;

import com.overprotected.overprotected.OverProtected;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client-only mod-bus event subscriber. Adds {@link StrapArmorLayer} to
 * both the default and slim player renderers so equipped straps display
 * the stored armor's model and texture.
 */
@EventBusSubscriber(modid = OverProtected.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        var models = event.getEntityModels();

        for (PlayerSkin.Model skin : event.getSkins()) {
            var renderer = event.getSkin(skin);
            if (!(renderer instanceof LivingEntityRenderer<?, ?> living)) continue;

            boolean slim = skin == PlayerSkin.Model.SLIM;
            HumanoidModel<AbstractClientPlayer> inner = new HumanoidModel<>(
                    models.bakeLayer(slim
                            ? ModelLayers.PLAYER_SLIM_INNER_ARMOR
                            : ModelLayers.PLAYER_INNER_ARMOR));
            HumanoidModel<AbstractClientPlayer> outer = new HumanoidModel<>(
                    models.bakeLayer(slim
                            ? ModelLayers.PLAYER_SLIM_OUTER_ARMOR
                            : ModelLayers.PLAYER_OUTER_ARMOR));

            @SuppressWarnings("unchecked")
            LivingEntityRenderer<AbstractClientPlayer, HumanoidModel<AbstractClientPlayer>> typed =
                    (LivingEntityRenderer<AbstractClientPlayer, HumanoidModel<AbstractClientPlayer>>) living;
            typed.addLayer(new StrapArmorLayer<>(typed, inner, outer));
        }
    }
}
