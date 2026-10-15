package com.amynna.hololib.modules.monde.trigger;

import com.amynna.hololib.holo.widget.HoloPanel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Groupe de {@link CommandListEditor} empiles verticalement dans un panneau, en <b>accordeon</b> : une seule liste est
 * ouverte a la fois (ouvrir une liste replie les autres, un clic sur l'entete de la liste ouverte la replie) ; les
 * sections suivantes descendent sous la liste ouverte. La taille d'une fenetre etant fixe, le panneau reserve la
 * hauteur d'<b>une</b> liste ouverte ({@link #totalHeight}) ; le reste est du vide quand tout est replie. Tout est
 * replie a la creation. Etat ouvert / ferme local, jamais synchronise.
 */
public final class CommandListGroup
{
    /** Espace entre deux sections, en pixels. */
    public static final float SPACING = 3f;

    private final HoloPanel panel;
    private final float x;
    private final float y;
    private final float width;
    private final List<CommandListEditor> sections = new ArrayList<>();

    /**
     * @param panel panneau qui recoit les sections (qui doit les contenir toutes, ouverte comprise)
     * @param x     abscisse des sections dans le panneau
     * @param y     ordonnee de la premiere section
     * @param width largeur des sections
     */
    public CommandListGroup(HoloPanel panel, float x, float y, float width)
    {
        this.panel = Objects.requireNonNull(panel);
        this.x = x;
        this.y = y;
        this.width = width;
    }

    /** Hauteur a reserver pour {@code count} sections dont une est ouverte. */
    public static float totalHeight(int count)
    {
        return count * CommandListEditor.HEADER_HEIGHT + (count - 1) * SPACING + CommandListEditor.BODY_HEIGHT;
    }

    /** Ajoute une section (a la suite) et renvoie son editeur. */
    public CommandListEditor add(String title, CommandListEditor.Listener listener)
    {
        int index = sections.size();
        CommandListEditor editor = panel.add(new CommandListEditor(width, title, listener, () -> toggle(index)));
        sections.add(editor);
        layout();
        return editor;
    }

    public CommandListEditor section(int index) { return sections.get(index); }

    /** Ouvre la section {@code index} (replie les autres), ou la replie si elle est deja ouverte. */
    private void toggle(int index)
    {
        boolean open = !sections.get(index).expanded();
        for (int i = 0; i < sections.size(); i++) sections.get(i).setExpanded(open && i == index);
        layout();
    }

    /** Replie tout (changement d'onglet). */
    public void collapseAll()
    {
        for (CommandListEditor section : sections) section.setExpanded(false);
        layout();
    }

    /** Empile les sections selon leur hauteur courante. */
    private void layout()
    {
        float top = y;
        for (CommandListEditor section : sections) {
            section.setBounds(x, top, width, section.sectionHeight());
            top += section.sectionHeight() + SPACING;
        }
    }
}
