package com.amynna.hololib.holo.widget.control;

import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.holo.widget.HoloWidget;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Zone de texte ({@link HoloTextInput} sur un {@link HoloTextBuffer}) : clic pour saisir, Echap (ou Entree sur une
 * seule ligne) pour valider, et sortie automatique quand le regard s'eloigne au-dela de la marge
 * ({@link #setHoverMargin}). Le texte valide part a l'ecouteur ({@link #setOnSubmit}) ; le module l'envoie au serveur
 * et repose le texte synchronise par {@link #setText} (ignore pendant la saisie). Retour a la ligne automatique,
 * defilement, curseur clignotant, texte d'attente quand elle est vide.
 */
public class HoloWidgetTextBox extends HoloWidget implements HoloTextInput
{
    protected static final float PADDING = 4f;
    protected static final float LINE_HEIGHT = 10f;
    private static final int BLINK_TICKS = 10;
    /** Lignes tapees au plus en mode multiligne. */
    private static final int MAX_HARD_LINES = 32;

    private final HoloTextBuffer buffer;
    private BiConsumer<? super HoloWidgetTextBox, String> onSubmit = (box, text) -> {};
    private Component placeholder = Component.literal("Clic : ecrire ; Echap : valider");
    private float hoverMargin = 24f;
    private boolean hovered;
    private int age;
    private int background = 0x60101828;
    private int editingBackground = 0xA0101828;
    private int border = 0xFF808060;
    private int editingBorder = 0xFFFFE060;

    /**
     * @param maxLength caracteres au plus
     * @param multiline Entree = nouvelle ligne ; sinon Entree valide
     */
    public HoloWidgetTextBox(float x, float y, float width, float height, int maxLength, boolean multiline)
    {
        super(x, y, width, height);
        int visible = Math.max(1, (int) ((height - 2f * PADDING) / LINE_HEIGHT));
        buffer = new HoloTextBuffer(maxLength, multiline ? MAX_HARD_LINES : 1, visible, wrapWidth(width));
    }

    private static float wrapWidth(float width) { return width - 2f * PADDING - 2f; }

    @Override
    public HoloWidget setBounds(float x, float y, float width, float height)
    {
        super.setBounds(x, y, width, height);
        // Appele aussi par le constructeur parent, avant la creation du buffer.
        if (buffer != null) buffer.setWrapWidth(wrapWidth(width));
        return this;
    }

    @Override
    public HoloTextBuffer buffer() { return buffer; }

    public String text() { return buffer.text(); }

    /** Texte venu du module (serveur) ; ignore pendant la saisie. */
    public HoloWidgetTextBox setText(String text)
    {
        if (!editing()) buffer.setText(text);
        return this;
    }

    public HoloWidgetTextBox setOnSubmit(BiConsumer<? super HoloWidgetTextBox, String> onSubmit)
    {
        this.onSubmit = Objects.requireNonNull(onSubmit);
        return this;
    }

    public HoloWidgetTextBox setPlaceholder(Component placeholder)
    {
        this.placeholder = Objects.requireNonNull(placeholder);
        return this;
    }

    /** Distance (pixels) au-dela de laquelle un regard qui s'eloigne termine la saisie. */
    public HoloWidgetTextBox setHoverMargin(float margin)
    {
        this.hoverMargin = margin;
        return this;
    }

    public HoloWidgetTextBox setColors(int background, int editingBackground, int border, int editingBorder)
    {
        this.background = background;
        this.editingBackground = editingBackground;
        this.border = border;
        this.editingBorder = editingBorder;
        return this;
    }

    @Override
    public float hoverMargin() { return hoverMargin; }

    @Override
    public boolean hovered() { return hovered; }

    @Override
    public void setHovered(boolean hovered) { this.hovered = hovered; }

    @Override
    public void onSubmit(String text) { onSubmit.accept(this, text); }

    @Override
    public void onEdited() { age = 0; }

    @Override
    public void tick() { age++; }

    @Override
    protected void draw(HoloCanvas canvas)
    {
        boolean editing = editing();
        canvas.fill(0, 0, width(), height(), editing ? editingBackground : background);
        if (hovered && !editing) canvas.fill(0, 0, width(), height(), 0x10FFFFFF);
        canvas.outline(0, 0, width(), height(), 1f, editing ? editingBorder : border);
        if (!editing && buffer.isEmpty()) {
            canvas.text(placeholder, PADDING, PADDING + 1f, 0xFF909090);
            return;
        }
        int first = buffer.firstLine();
        int last = Math.min(buffer.lineCount(), first + buffer.visibleLines());
        for (int i = first; i < last; i++) {
            canvas.text(buffer.lineText(i), PADDING, PADDING + 1f + (i - first) * LINE_HEIGHT, 0xFFFFFFFF);
        }
        if (buffer.lineCount() > buffer.visibleLines()) {
            // Barre de defilement.
            float track = height() - 4f;
            float barHeight = Math.max(4f, track * buffer.visibleLines() / buffer.lineCount());
            float barY = 2f + (track - barHeight) * first / (buffer.lineCount() - buffer.visibleLines());
            canvas.fill(width() - 3f, barY, 1f, barHeight, editingBorder);
        }
        if (editing && (age / BLINK_TICKS) % 2 == 0) {
            canvas.fill(PADDING + buffer.cursorX(), PADDING + (buffer.cursorLine() - first) * LINE_HEIGHT, 1f,
                    LINE_HEIGHT - 1f, editingBorder);
        }
    }
}
