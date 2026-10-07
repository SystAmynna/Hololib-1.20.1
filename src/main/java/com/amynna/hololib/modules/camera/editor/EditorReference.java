package com.amynna.hololib.modules.camera.editor;

import com.amynna.hololib.holo.edit.HoloEditorModule;
import com.amynna.hololib.holo.edit.HoloEditorUi;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import net.minecraft.util.Mth;

/**
 * Repere de l'ecran au moment de l'ouverture de l'editor : un cercle autour du reticule et un cadre sur les bords de
 * l'ecran, a {@link #DISTANCE} bloc de l'oeil. Ancre {@code Local} follow face au regard : la session d'edition le fige
 * (camera normale) a l'ouverture, il reste alors immobile quand on tourne la tete. Interface de l'editeur sans outil
 * ({@link HoloEditorUi}) : dessine en session malgre son affichage {@code OFF}, tant que son editeur est actif.
 */
public final class EditorReference extends HoloObject implements HoloEditorUi
{
    public static final float DISTANCE = 0.5f;
    /** Marge pour que le cadre reste dans l'ecran. */
    private static final float INSET = 0.98f;
    /** Rayon du cercle, en fraction de la demi-hauteur de l'ecran. */
    private static final float CIRCLE = 0.08f;

    private float halfWidth = 0.6f;
    private float halfHeight = 0.4f;

    public EditorReference(String key)
    {
        super(key, new HoloAnchor.Local(true), HoloTransform.translation(0f, 0f, DISTANCE));
    }

    /**
     * Cote client, a l'ouverture : taille de l'ecran a la distance du repere.
     *
     * @param fov    champ de vision vertical, en degres
     * @param aspect largeur / hauteur de l'ecran
     */
    public void capture(float fov, float aspect)
    {
        halfHeight = transform().z() * (float) Math.tan(0.5f * fov * Mth.DEG_TO_RAD) * INSET;
        halfWidth = halfHeight * aspect;
    }

    @Override
    public boolean shownInSession() { return module() instanceof HoloEditorModule editor && editor.editorEnabled(); }

    /** Rayon du cercle autour du reticule (blocs). */
    public float circleRadius() { return halfHeight * CIRCLE; }

    @Override
    public float halfWidth() { return halfWidth; }

    @Override
    public float halfHeight() { return halfHeight; }

    @Override
    public boolean flat() { return true; }

    @Override
    public float boundingRadius() { return (float) Math.sqrt(halfWidth * halfWidth + halfHeight * halfHeight); }
}
