package com.amynna.hololib.client.key;

import com.amynna.hololib.HoloLib;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Controles du mod dans les options ({@code KeyMapping}), et lecture de l'etat <b>physique</b> d'un controle. Les
 * modules qui s'en servent les lisent sur le fil client ({@code consumeClick}).
 */
@Mod.EventBusSubscriber(modid = HoloLib.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class HoloKeyMappings
{
    public static final String CATEGORY = "key.categories.hololib";

    /** Mode focus (module {@code hololib:focus}), H par defaut. */
    public static final KeyMapping FOCUS = new KeyMapping("key.hololib.focus", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);
    /** Mode edition (modules editeurs, {@code hololib:own_editor}...), G par defaut. */
    public static final KeyMapping EDITOR = new KeyMapping("key.hololib.editor", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);

    private HoloKeyMappings() {}

    @SubscribeEvent
    public static void onRegister(RegisterKeyMappingsEvent event)
    {
        event.register(FOCUS);
        event.register(EDITOR);
    }

    /**
     * Vrai si la touche (ou le bouton) liee au controle est enfoncee en ce moment, sans passer par l'etat du
     * {@code KeyMapping} : ignore les modes bascule (sneak, sprint) et ce qu'en fait le jeu.
     */
    public static boolean physicallyDown(Minecraft mc, KeyMapping mapping)
    {
        InputConstants.Key key = mapping.getKey();
        if (key.getValue() == InputConstants.UNKNOWN.getValue()) return false;
        long window = mc.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return InputConstants.isKeyDown(window, key.getValue());
    }
}
