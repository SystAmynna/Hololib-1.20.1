package com.amynna.hololib.modules.camera.editor;

import com.amynna.hololib.holo.edit.HoloEditScale;
import com.amynna.hololib.holo.edit.HoloEditTranslation;
import com.amynna.hololib.holo.edit.HoloEditorModule;
import com.amynna.hololib.holo.edit.HoloEditorUi;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.widget.HoloLabel;
import com.amynna.hololib.holo.window.HoloWindow;
import net.minecraft.network.chat.Component;

/**
 * Fenetre de l'editor : un titre et le cadre HUD ({@link EditorHudFrame}). Ancree {@code Local} follow sous le regard ;
 * pendant la session elle est figee comme les autres fenetres dynamiques et se deplace / se redimensionne avec les
 * outils d'edition (elle est {@link HoloEditTranslation} et {@link HoloEditScale}). Interface de l'editeur
 * ({@link HoloEditorUi}) : visible en session tant que son editeur est actif.
 */
public final class EditorWindow extends HoloWindow implements HoloEditorUi, HoloEditTranslation, HoloEditScale
{
    public static final int WIDTH = 240;
    public static final int HEIGHT = 160;
    private static final int MARGIN = 8;
    private static final int TOP = 20;

    private final EditorHudFrame frame;

    public EditorWindow(String key)
    {
        super(key, new HoloAnchor.Local(true), HoloTransform.translation(0f, 22f, 0.6f), WIDTH, HEIGHT, 1f / 400f);
        root().setBackground(0xC0101828).setBorder(0xFF40C0FF, 1f);
        root().add(new HoloLabel(MARGIN, 4, WIDTH - 2 * MARGIN, 12, Component.literal("Editeur : HUD"))
                .setAlign(HoloLabel.Align.CENTER).setColor(0xFF40C0FF));
        frame = root().add(new EditorHudFrame(MARGIN, TOP, WIDTH - 2 * MARGIN, HEIGHT - TOP - MARGIN));
    }

    public EditorHudFrame frame() { return frame; }

    @Override
    public boolean shownInSession() { return module() instanceof HoloEditorModule editor && editor.editorEnabled(); }

    /** Cote client, a l'ouverture : cadre aux proportions de l'ecran ({@code aspect} = largeur / hauteur). */
    public void fitFrame(float aspect)
    {
        float areaWidth = WIDTH - 2 * MARGIN;
        float areaHeight = HEIGHT - TOP - MARGIN;
        float width = areaWidth;
        float height = areaWidth / aspect;
        if (height > areaHeight) {
            height = areaHeight;
            width = areaHeight * aspect;
        }
        frame.setBounds((WIDTH - width) / 2f, TOP + (areaHeight - height) / 2f, width, height);
    }
}
