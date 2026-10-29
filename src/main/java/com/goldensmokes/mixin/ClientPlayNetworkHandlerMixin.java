package com.goldensmokes.mixin;

import com.goldensmokes.TotemAnimation;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Das Totem-Aufploppen (Entity-Status 35) ruft im Original showFloatingItem() auf.
 * Wir ersetzen genau diesen Aufruf durch unsere Schachtel-Animation.
 * Partikel und Sound des Originals bleiben erhalten.
 */
@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    @Redirect(
        method = "onEntityStatus",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/GameRenderer;showFloatingItem(Lnet/minecraft/item/ItemStack;)V"
        )
    )
    private void goldensmokes$playBoxAnimation(GameRenderer renderer, ItemStack stack) {
        TotemAnimation.start();
    }
}
