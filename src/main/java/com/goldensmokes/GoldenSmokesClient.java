package com.goldensmokes;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class GoldenSmokesClient implements ClientModInitializer {
    public static final String MOD_ID = "goldensmokes";

    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register(TotemAnimation::render);
    }
}
