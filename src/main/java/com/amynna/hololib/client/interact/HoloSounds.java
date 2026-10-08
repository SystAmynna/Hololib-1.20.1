package com.amynna.hololib.client.interact;

import com.amynna.hololib.holo.capture.HoloControl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * Sons d'interface de l'interaction au regard (fil client), tous regroupes ici :
 * <ul>
 *     <li>{@link #click} : son generique d'un bouton, different selon le clic (gauche, droit, molette) ;</li>
 *     <li>{@link #scroll} : cran de molette sur un element qui la prend (espaces d'au moins
 *     {@value #SCROLL_INTERVAL_NS} ns) ;</li>
 *     <li>{@link #edit} : debut de manipulation d'un objet en mode edition.</li>
 * </ul>
 * Joues par {@link HoloInteractions} pour les traits {@code HoloClickable} / {@code HoloScrollable} qui les
 * acceptent ({@code clickSound()}, {@code scrollSound()}).
 */
public final class HoloSounds
{
    private static final float VOLUME = 0.35f;
    static final long SCROLL_INTERVAL_NS = 40_000_000L;

    private static long lastScroll;

    private HoloSounds() {}

    /** Clic sur un bouton ({@code LEFT_CLICK}, {@code RIGHT_CLICK}, {@code MIDDLE_CLICK}). */
    public static void click(HoloControl button)
    {
        switch (button.mouseButton()) {
            case 0 -> play(SoundEvents.UI_BUTTON_CLICK.value(), 1f);
            case 1 -> play(SoundEvents.UI_BUTTON_CLICK.value(), 0.6f);
            case 2 -> play(SoundEvents.NOTE_BLOCK_BIT.value(), 1.5f);
            default -> {}
        }
    }

    /** Cran de molette. */
    public static void scroll()
    {
        long now = System.nanoTime();
        if (now - lastScroll < SCROLL_INTERVAL_NS) return;
        lastScroll = now;
        play(SoundEvents.NOTE_BLOCK_HAT.value(), 1.8f);
    }

    /** Un objet est saisi pour etre edite. */
    public static void edit() { play(SoundEvents.AMETHYST_BLOCK_HIT, 1.4f); }

    private static void play(SoundEvent sound, float pitch)
    {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, VOLUME));
    }
}
