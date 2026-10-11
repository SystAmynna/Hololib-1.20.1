package com.amynna.hololib.modules.camera.keys;

import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.holo.widget.HoloPanel;
import com.amynna.hololib.holo.widget.HoloWidget;
import com.amynna.hololib.holo.widget.control.HoloWidgetButton;
import com.amynna.hololib.holo.widget.control.HoloWidgetList;
import com.amynna.hololib.holo.widget.control.HoloWidgetTextBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Editeur d'une liste de cles, pour les fenetres des modules (Keys Editor, Attack Guard) : un champ de texte d'une
 * ligne ({@value KeySet#MAX_LENGTH} caracteres) et un bouton « Ajouter » (la cle n'est ajoutee qu'au clic, pas a la
 * validation du champ, qui a lieu aussi quand le regard s'eloigne), puis la liste des cles, chacune avec une croix de
 * suppression. Les callbacks s'executent cote client ; le module envoie la demande au serveur et repose la liste
 * synchronisee par {@link #setKeys} (lignes reconstruites seulement si elle change). {@link #setEditable} masque le
 * champ, le bouton et les croix (sujet indisponible).
 */
public class KeyListEditor extends HoloPanel
{
    private static final float FIELD_HEIGHT = 18f;
    private static final float BUTTON_WIDTH = 46f;
    private static final float GAP = 4f;
    private static final float ROW_HEIGHT = 14f;

    private final Consumer<String> onRemove;
    private final HoloWidgetTextBox field;
    private final HoloWidgetButton add;
    private final HoloWidgetList list;
    private final List<String> shown = new ArrayList<>();
    private boolean editable = true;

    /**
     * @param onAdd    texte du champ, au clic sur « Ajouter » (non vide)
     * @param onRemove cle dont la croix a ete cliquee
     */
    public KeyListEditor(float x, float y, float width, float height, Consumer<String> onAdd, Consumer<String> onRemove)
    {
        super(x, y, width, height);
        Objects.requireNonNull(onAdd);
        this.onRemove = Objects.requireNonNull(onRemove);
        field = add(new HoloWidgetTextBox(0, 0, width - BUTTON_WIDTH - GAP, FIELD_HEIGHT, KeySet.MAX_LENGTH, false));
        field.setPlaceholder(Component.literal("Nouvelle cle"));
        add = add(new HoloWidgetButton(width - BUTTON_WIDTH, 0, BUTTON_WIDTH, FIELD_HEIGHT,
                Component.literal("Ajouter"), button -> {
            String key = KeySet.normalize(field.text());
            if (key == null) return;
            onAdd.accept(key);
            field.buffer().setText("");
        }));
        list = add(new HoloWidgetList(0, FIELD_HEIGHT + GAP, width, height - FIELD_HEIGHT - GAP, ROW_HEIGHT));
    }

    /** Cles affichees ; lignes reconstruites seulement si la liste change. */
    public void setKeys(Collection<String> keys)
    {
        if (shown.size() == keys.size() && shown.equals(new ArrayList<>(keys))) return;
        shown.clear();
        shown.addAll(keys);
        int first = list.first();
        list.clearItems();
        for (String key : shown) list.addItem(new Row(key)).remove.setVisible(editable);
        list.scrollTo(first);
    }

    /** Champ, bouton et croix visibles ou non. */
    public void setEditable(boolean editable)
    {
        if (this.editable == editable) return;
        this.editable = editable;
        field.setVisible(editable);
        add.setVisible(editable);
        for (HoloWidget item : list.items()) ((Row) item).remove.setVisible(editable);
    }

    @Override
    protected void draw(HoloCanvas canvas)
    {
        super.draw(canvas);
        if (shown.isEmpty()) {
            canvas.text(Component.literal("Aucune cle"), 6f, FIELD_HEIGHT + GAP + 6f, 0xFF808890);
        }
    }

    /** Ligne de la liste : texte de la cle et croix de suppression. */
    private final class Row extends HoloPanel
    {
        private final Component text;
        private final HoloWidgetButton remove;

        Row(String key)
        {
            super(0, 0, 0, ROW_HEIGHT);
            text = Component.literal(key);
            remove = add(new HoloWidgetButton(0, 0, ROW_HEIGHT, ROW_HEIGHT, Component.literal("x"),
                    button -> onRemove.accept(key)));
            remove.setColors(0x60A02020, 0xFFFF6060, 0xFFFFA0A0);
            placeRemove();
        }

        @Override
        public HoloWidget setBounds(float x, float y, float width, float height)
        {
            super.setBounds(x, y, width, height);
            // Appele aussi par le constructeur parent, avant la creation du bouton.
            if (remove != null) placeRemove();
            return this;
        }

        private void placeRemove() { remove.setBounds(width() - height(), 0, height(), height()); }

        @Override
        protected void draw(HoloCanvas canvas)
        {
            canvas.fill(0, 0, width(), height(), 0x3020A0FF);
            canvas.text(text, 3f, (height() - canvas.lineHeight()) / 2f + 1f, 0xFFFFFFFF);
            super.draw(canvas);
        }
    }
}
