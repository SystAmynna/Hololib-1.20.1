package com.amynna.hololib.client.capture;

import com.amynna.hololib.HoloLib;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;

/**
 * Ecran invisible ouvert par {@link HoloControlCapture} tant qu'un captor prend tout le clavier
 * ({@code HoloControl.KEYBOARD}). Sans Mixin, un {@code Screen} est la seule source des caracteres tapes en jeu
 * ({@code KeyboardHandler.charTyped} ne les donne qu'a l'ecran ouvert) ; sous un ecran, vanilla ne lit plus aucun
 * {@code KeyMapping} ({@code handleKeybinds} exige {@code screen == null}) : tout le clavier est retire au jeu.
 * <p>
 * Ne dessine rien, ne met pas en pause, ne se ferme pas sur Echap (la touche va au captor). {@code setScreen} libere
 * la souris : elle est reprise a l'{@link #init} pour que la camera tourne toujours ({@code turnPlayer} ne depend que
 * de la souris capturee). {@code MouseHandler.grabMouse} fermerait l'ecran : le drapeau est donc pose par reflexion
 * (noms SRG, {@code ObfuscationReflectionHelper}) ; si elle echoue, le curseur reste libre, comme sous le chat.
 */
final class HoloKeyboardScreen extends Screen
{
    private static final Field MOUSE_GRABBED = field("f_91520_");
    private static final Field IGNORE_FIRST_MOVE = field("f_91511_");

    HoloKeyboardScreen()
    {
        super(Component.empty());
    }

    @Override
    protected void init() { grabMouse(minecraft); }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers)
    {
        HoloControlCapture.screenKeyPressed(key, scanCode, modifiers);
        return true;
    }

    @Override
    public boolean keyReleased(int key, int scanCode, int modifiers)
    {
        HoloControlCapture.screenKeyReleased(key, scanCode, modifiers);
        return true;
    }

    @Override
    public boolean charTyped(char character, int modifiers)
    {
        HoloControlCapture.screenCharTyped(character, modifiers);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta)
    {
        HoloControlCapture.screenScrolled(delta);
        return true;
    }

    /** Capture la souris sans fermer l'ecran ; sans effet si la fenetre n'a pas le focus ou deja capturee. */
    static void grabMouse(Minecraft mc)
    {
        MouseHandler mouse = mc.mouseHandler;
        if (MOUSE_GRABBED == null || IGNORE_FIRST_MOVE == null || !mc.isWindowActive() || mouse.isMouseGrabbed()) {
            return;
        }
        try {
            MOUSE_GRABBED.setBoolean(mouse, true);
            IGNORE_FIRST_MOVE.setBoolean(mouse, true);
        } catch (IllegalAccessException e) {
            HoloLib.LOGGER.warn("Impossible de capturer la souris sous l'ecran clavier", e);
            return;
        }
        InputConstants.grabOrReleaseMouse(mc.getWindow().getWindow(), GLFW.GLFW_CURSOR_DISABLED,
                mc.getWindow().getScreenWidth() / 2.0, mc.getWindow().getScreenHeight() / 2.0);
    }

    private static Field field(String srgName)
    {
        try {
            return ObfuscationReflectionHelper.findField(MouseHandler.class, srgName);
        } catch (RuntimeException e) {
            HoloLib.LOGGER.warn("Champ {} de MouseHandler introuvable : curseur libre sous l'ecran clavier", srgName,
                    e);
            return null;
        }
    }
}
