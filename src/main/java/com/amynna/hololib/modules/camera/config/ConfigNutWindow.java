package com.amynna.hololib.modules.camera.config;

import com.amynna.hololib.holo.edit.HoloDisplay;
import com.amynna.hololib.holo.edit.HoloEditRotation;
import com.amynna.hololib.holo.edit.HoloEditScale;
import com.amynna.hololib.holo.edit.HoloEditTranslation;
import com.amynna.hololib.holo.edit.HoloRelativeToOpen;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.object.HoloVisibility;
import com.amynna.hololib.holo.widget.control.HoloWidgetButton;
import com.amynna.hololib.holo.window.HoloWindow;

import java.util.function.Consumer;

/**
 * Petite fenetre de Config, sans fond, qui ne contient que le bouton {@link ConfigNutButton}. Affichage {@code FOCUS}
 * non modifiable (pas d'outil de visibilite) ; placee en {@code Local} fixe a chaque ouverture du focus, d'apres sa
 * position relative au regard ({@link HoloRelativeToOpen}, en bas a gauche par defaut), editable dans l'editor.
 */
public final class ConfigNutWindow extends HoloWindow implements HoloRelativeToOpen, HoloEditTranslation,
        HoloEditRotation, HoloEditScale
{
    private static final int SIZE = 28;

    private final ConfigNutButton button;

    public ConfigNutWindow(String key, Consumer<? super HoloWidgetButton> onPress)
    {
        super(key, new HoloAnchor.Local(false), HoloTransform.translation(-28f, 16f, 0.8f), SIZE, SIZE, 1f / 400f);
        setDisplay(HoloDisplay.FOCUS);
        // Publique : un joueur qui inspecte le proprietaire la voit (sans pouvoir l'utiliser).
        setVisibility(HoloVisibility.PUBLIC);
        button = root().add(new ConfigNutButton(0, 0, SIZE, onPress));
    }

    public ConfigNutButton button() { return button; }
}
