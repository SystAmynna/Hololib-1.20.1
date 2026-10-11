package com.amynna.hololib.modules.camera.attackguard;

import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.widget.HoloLabel;
import com.amynna.hololib.modules.camera.config.ConfigWindow;
import com.amynna.hololib.modules.camera.keys.KeyListEditor;
import net.minecraft.network.chat.Component;

/**
 * Fenetre de configuration « Attack Guard » de {@link AttackGuardModule} (objet du module, cree dans {@code onLoad}
 * des deux cotes) : explication d'une ligne puis la whitelist ({@link KeyListEditor} : champ, Ajouter, cles avec
 * croix). Un joueur qui possede l'une de ces cles ne peut pas etre attaque.
 */
final class AttackGuardConfigWindow extends ConfigWindow
{
    private static final int WIDTH = 180;
    private static final int MARGIN = 6;
    private static final int GAP = 4;
    private static final int LABEL_HEIGHT = 12;
    private static final int EDITOR_HEIGHT = 120;

    private final AttackGuardModule module;
    private final KeyListEditor editor;

    AttackGuardConfigWindow(String key, Component title, AttackGuardModule module)
    {
        super(key, title, HoloTransform.translation(28f, 0f, 0.7f), WIDTH,
                GAP + LABEL_HEIGHT + GAP + EDITOR_HEIGHT + MARGIN, 1f / 400f);
        this.module = module;
        float width = WIDTH - 2 * MARGIN;
        content().add(new HoloLabel(MARGIN, GAP, width, LABEL_HEIGHT, Component.literal("Cles protegees :"))
                .setColor(0xFFC0D0E0));
        editor = content().add(new KeyListEditor(MARGIN, GAP + LABEL_HEIGHT + GAP, width, EDITOR_HEIGHT,
                module::requestAdd, module::requestRemove));
    }

    /** Liste d'apres l'etat du module (deux cotes). */
    void refresh() { editor.setKeys(module.whitelist().keys()); }
}
