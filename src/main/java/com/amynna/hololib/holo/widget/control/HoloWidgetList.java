package com.amynna.hololib.holo.widget.control;

import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.interact.HoloInteraction;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.holo.widget.HoloPanel;
import com.amynna.hololib.holo.widget.HoloWidget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Liste encadree d'elements empiles verticalement, de hauteur fixe, qui defile a la molette (un element par cran)
 * quand ils ne tiennent pas tous ({@link HoloScrollable}). Les elements sont des widgets quelconques (ligne avec
 * interrupteur, bouton...) : la liste gere leurs bornes (pleine largeur, barre de defilement deduite) et leur
 * visibilite ; le dernier element peut etre affiche en partie, rogne au cadre ({@link #setPartialItems}). Une molette sur un element qui
 * ne la prend pas remonte a la liste.
 */
public class HoloWidgetList extends HoloPanel implements HoloScrollable
{
    private static final float PAD = 3f;
    private static final float SPACING = 2f;
    private static final float BAR_WIDTH = 3f;

    private final List<HoloWidget> items = new ArrayList<>();
    private final List<HoloWidget> itemsView = Collections.unmodifiableList(items);
    private final float itemHeight;
    private int first;
    private boolean partialItems = true;
    private HoloColor barColor = HoloColor.primary(0xFF);

    /** @param itemHeight hauteur de chaque element, en pixels */
    public HoloWidgetList(float x, float y, float width, float height, float itemHeight)
    {
        super(x, y, width, height);
        this.itemHeight = itemHeight;
        setFlatChildren(true);
        setBackground(HoloColor.secondary(0x60).darker(0.84f));
        setBorder(HoloColor.secondary(0xFF).darker(0.46f), 1f);
    }

    /** Ajoute un element a la fin ; renvoie l'element. */
    public <W extends HoloWidget> W addItem(W item)
    {
        items.add(add(item));
        layout();
        return item;
    }

    public void removeItem(HoloWidget item)
    {
        if (!items.remove(item)) return;
        remove(item);
        layout();
    }

    public void clearItems()
    {
        for (HoloWidget item : items) remove(item);
        items.clear();
        first = 0;
        layout();
    }

    /** Elements, dans l'ordre (vue non modifiable). */
    public List<HoloWidget> items() { return itemsView; }

    public HoloWidgetList setBarColor(int argb) { return setBarColor(HoloColor.fixed(argb)); }

    public HoloWidgetList setBarColor(HoloColor color)
    {
        this.barColor = Objects.requireNonNull(color);
        return this;
    }

    /** Affiche aussi le dernier element en partie, rogne au cadre (vrai par defaut) ; sinon seuls les entiers. */
    public HoloWidgetList setPartialItems(boolean partial)
    {
        this.partialItems = partial;
        layout();
        return this;
    }

    /** Nombre de lignes affichees, partielle comprise ({@link #visibleCount()} : entieres seulement). */
    private int shownCount()
    {
        if (!partialItems) return visibleCount();
        float room = height() - 2f * PAD + SPACING;
        return Math.max(1, (int) Math.ceil(room / (itemHeight + SPACING) - 1e-4f));
    }

    /** Nombre d'elements affiches entierement a la fois. */
    public int visibleCount()
    {
        return Math.max(1, (int) ((height() - 2f * PAD + SPACING) / (itemHeight + SPACING)));
    }

    /** Indice du premier element affiche. */
    public int first() { return first; }

    /** Fait defiler pour que l'element d'indice {@code index} soit le premier affiche (borne). */
    public void scrollTo(int index)
    {
        first = Math.max(0, Math.min(index, items.size() - visibleCount()));
        layout();
    }

    /**
     * Indice de l'element affiche a l'ordonnee {@code y} (repere de la liste), borne aux elements affiches ; -1 si la
     * liste est vide.
     */
    public int indexAt(float y)
    {
        if (items.isEmpty()) return -1;
        int row = (int) Math.floor((y - PAD + SPACING / 2f) / (itemHeight + SPACING));
        int last = Math.min(first + shownCount(), items.size()) - 1;
        return Math.max(first, Math.min(first + row, last));
    }

    /**
     * Deplace l'element d'indice {@code from} a l'indice {@code to}, puis replace les elements (meme si egaux : un
     * element deplace a la main reprend sa place). Sans effet hors des bornes.
     */
    public void moveItem(int from, int to)
    {
        if (from < 0 || to < 0 || from >= items.size() || to >= items.size()) return;
        if (from != to) items.add(to, items.remove(from));
        layout();
    }

    @Override
    public HoloWidget setBounds(float x, float y, float width, float height)
    {
        super.setBounds(x, y, width, height);
        // Appele aussi par le constructeur parent, avant la creation de la liste des elements.
        if (items != null) scrollTo(first);
        return this;
    }

    @Override
    public void onScrolled(HoloInteraction ctx, int steps) { scrollTo(first - steps); }

    private boolean overflows() { return items.size() > visibleCount(); }

    /** Bornes et visibilite des elements selon le defilement. */
    private void layout()
    {
        int visible = shownCount();
        float width = width() - 2f * PAD - (overflows() ? BAR_WIDTH + SPACING : 0f);
        for (int i = 0; i < items.size(); i++) {
            HoloWidget item = items.get(i);
            int row = i - first;
            boolean shown = row >= 0 && row < visible;
            item.setVisible(shown);
            if (shown) item.setBounds(PAD, PAD + row * (itemHeight + SPACING), width, itemHeight);
        }
    }

    @Override
    protected void drawChildren(HoloCanvas canvas)
    {
        // Rognage au cadre interieur : un element partiel est coupe au lieu d'apparaitre / disparaitre.
        canvas.pushClip(PAD, PAD, width() - 2f * PAD, height() - 2f * PAD);
        try {
            super.drawChildren(canvas);
        } finally {
            canvas.popClip();
        }
    }

    @Override
    protected void draw(HoloCanvas canvas)
    {
        super.draw(canvas);
        if (!overflows()) return;
        float track = height() - 2f * PAD;
        float bar = Math.max(4f, track * visibleCount() / items.size());
        float y = PAD + (track - bar) * first / (items.size() - visibleCount());
        canvas.fill(width() - PAD - BAR_WIDTH, y, BAR_WIDTH, bar, canvas.color(barColor));
    }
}
