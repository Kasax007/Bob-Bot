package net.kasax.bobbot.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * The client side, when the mod is there too (Bob works with it on the server only as well): a key
 * for his control panel (B by default, in the controls under "Bob").
 */
public final class BobBotClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("bobbot", "bob"));
    private static final KeyMapping PANEL = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.bobbot.panel", InputConstants.Type.KEYBOARD, InputConstants.KEY_B, CATEGORY));

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (PANEL.consumeClick()) {
                if (mc.player != null && mc.getConnection() != null && mc.gui.screen() == null) mc.getConnection().sendCommand("bob panel");
            }
        });
    }
}
