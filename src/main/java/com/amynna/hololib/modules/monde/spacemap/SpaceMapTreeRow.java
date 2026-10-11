package com.amynna.hololib.modules.monde.spacemap;

import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.interact.HoloInteraction;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.holo.widget.control.HoloWidgetMultiButton;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/**
 * Ligne de l'arborescence de la {@link SpaceMapWindow} : un {@link HoloWidgetMultiButton} dont le texte est aligne a
 * gauche et retire selon sa profondeur, precede d'un repere {@code +} / {@code -} si la ligne se deplie. Clic gauche
 * sur le repere (ou clic droit n'importe ou) : deplier / replier ; clic gauche sur le nom : selectionner (recentrer).
 * Les bornes sont posees par la liste ({@code HoloWidgetList}).
 * <p>
 * <b>Bouton de cible</b> (T43, D33 ; ligne selectionnee seulement, {@link #setTargetButton}) : zone de
 * {@value #TARGET_WIDTH} px a l'extremite droite de la ligne (« CIBLER » / « DECIBLER »), decidee comme le repere par
 * l'abscisse du clic : un clic gauche dedans declenche l'action de cible, ailleurs rien ne change (selection,
 * depliage). Zone de la ligne plutot qu'un {@code HoloWidgetButton} enfant : les lignes de {@code HoloWidgetList} sont
 * des widgets simples, sans enfants (meme principe que le repere de depliage). Le texte du nom est tronque d'autant
 * ({@link #textWidth(float, int, boolean)}).
 */
public final class SpaceMapTreeRow extends HoloWidgetMultiButton
{
    /** Retrait d'un niveau, en pixels. */
    public static final float INDENT = 7f;
    /** Largeur de la zone du repere de depliage, en pixels. */
    public static final float TOGGLE_WIDTH = 9f;
    private static final float PAD = 2f;
    /** Largeur de la zone du bouton de cible, en pixels (« DECIBLER » : 46 px dans la police vanilla, + marges). */
    public static final float TARGET_WIDTH = 50f;
    private static final Component EXPANDED = Component.literal("-");
    private static final Component COLLAPSED = Component.literal("+");

    private final int depth;
    private final boolean expandable;
    private final boolean expanded;
    private int background = 0x6020A0FF;
    private int border = 0xFF40C0FF;
    private int textColor = 0xFFFFFFFF;
    private int toggleColor = 0xFFA0C8E0;
    /** Abscisse du dernier clic dans la ligne (pixels). */
    private float clickX;
    /** Bouton de cible (T43) : libelle, {@code null} = pas de bouton ; couleurs ; action. */
    private Component targetLabel;
    private int targetText = 0xFFFFFFFF;
    private int targetBackground = 0x90183048;
    private Runnable onTarget;

    /**
     * @param label      texte deja tronque a la largeur disponible ({@link #textWidth})
     * @param depth      profondeur dans l'arborescence (0 = racine)
     * @param expandable vrai si la ligne a des enfants
     * @param expanded   vrai si ses enfants sont affiches
     */
    public SpaceMapTreeRow(Component label, float height, int depth, boolean expandable, boolean expanded,
                           Runnable onSelect, Runnable onToggle)
    {
        super(0, 0, 1, height, label);
        this.depth = depth;
        this.expandable = expandable;
        this.expanded = expanded;
        Objects.requireNonNull(onSelect);
        Objects.requireNonNull(onToggle);
        on(HoloControl.LEFT_CLICK, row -> {
            if (onTarget != null && clickX >= width() - TARGET_WIDTH) onTarget.run();
            else if (expandable && clickX < toggleRight()) onToggle.run();
            else onSelect.run();
        });
        if (expandable) on(HoloControl.RIGHT_CLICK, row -> onToggle.run());
    }

    /** Largeur disponible pour le texte d'une ligne de profondeur {@code depth} dans une ligne de {@code width} px. */
    public static float textWidth(float width, int depth) { return width - PAD - depth * INDENT - TOGGLE_WIDTH - PAD; }

    /** Comme {@link #textWidth(float, int)}, moins la zone du bouton de cible si la ligne en porte un (T43). */
    public static float textWidth(float width, int depth, boolean targetButton)
    {
        return textWidth(width, depth) - (targetButton ? TARGET_WIDTH : 0f);
    }

    /**
     * Ajoute le bouton de cible a l'extremite droite (T43) : libelle {@code label} (« CIBLER » / « DECIBLER ») de
     * couleur {@code textColor} sur le fond {@code background} ; clic gauche dans la zone = {@code onTarget}.
     */
    public SpaceMapTreeRow setTargetButton(Component label, int textColor, int background, Runnable onTarget)
    {
        this.targetLabel = Objects.requireNonNull(label);
        this.targetText = textColor;
        this.targetBackground = background;
        this.onTarget = Objects.requireNonNull(onTarget);
        return this;
    }

    /** Libelle du bouton de cible, {@code null} si la ligne n'en porte pas. */
    public Component targetLabel() { return targetLabel; }

    private float toggleRight() { return PAD + depth * INDENT + TOGGLE_WIDTH; }

    @Override
    public SpaceMapTreeRow setColors(int background, int border, int textColor)
    {
        super.setColors(background, border, textColor);
        this.background = background;
        this.border = border;
        this.textColor = textColor;
        return this;
    }

    /** Couleur du repere de depliage. */
    public SpaceMapTreeRow setToggleColor(int argb)
    {
        this.toggleColor = argb;
        return this;
    }

    @Override
    public void onClick(HoloInteraction ctx, HoloControl button)
    {
        clickX = ctx.x();
        super.onClick(ctx, button);
    }

    @Override
    protected void draw(HoloCanvas canvas)
    {
        canvas.fill(0, 0, width(), height(), background);
        if (pressed() || hovered()) canvas.fill(0, 0, width(), height(), pressed() ? PRESSED_OVERLAY : HOVER_OVERLAY);
        canvas.outline(0, 0, width(), height(), 1f, border);
        float y = (height() - canvas.lineHeight()) / 2f + 1f;
        float x = PAD + depth * INDENT;
        if (expandable) {
            Component mark = expanded ? EXPANDED : COLLAPSED;
            canvas.text(mark, x + (TOGGLE_WIDTH - canvas.textWidth(mark)) / 2f, y, toggleColor);
        }
        canvas.text(text(), x + TOGGLE_WIDTH, y, textColor);
        if (targetLabel != null) {
            float left = width() - TARGET_WIDTH;
            canvas.fill(left, 0, TARGET_WIDTH, height(), targetBackground);
            canvas.outline(left, 0, TARGET_WIDTH, height(), 1f, border);
            canvas.text(targetLabel, left + (TARGET_WIDTH - canvas.textWidth(targetLabel)) / 2f, y, targetText);
        }
    }
}
