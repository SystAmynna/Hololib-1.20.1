package com.amynna.hololib.modules.camera.quickaction;

import com.amynna.hololib.holo.widget.HoloCanvas;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Icone d'une {@link QuickAction} : dessin dans un carre {@code size} x {@code size} pixels, origine en haut a gauche
 * ({@link HoloCanvas}, code commun). Appelee a chaque frame : ne rien allouer.
 */
@FunctionalInterface
public interface QuickActionIcon
{
    void draw(HoloCanvas canvas, float size);

    /** Icone tiree d'une image (texture entiere, sans teinte). */
    static QuickActionIcon texture(ResourceLocation texture)
    {
        Objects.requireNonNull(texture);
        return (canvas, size) -> canvas.texture(texture, 0f, 0f, size, size, 0f, 0f, 1f, 1f, 0xFFFFFFFF);
    }
}
