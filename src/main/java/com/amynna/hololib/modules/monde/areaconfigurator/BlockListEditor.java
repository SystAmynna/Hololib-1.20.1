package com.amynna.hololib.modules.monde.areaconfigurator;

import com.amynna.hololib.holo.capture.HoloCapture;
import com.amynna.hololib.holo.capture.HoloKeys;
import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.holo.widget.HoloPanel;
import com.amynna.hololib.holo.widget.HoloWidget;
import com.amynna.hololib.holo.widget.control.HoloWidgetButton;
import com.amynna.hololib.holo.widget.control.HoloWidgetList;
import com.amynna.hololib.holo.widget.control.HoloWidgetTextBox;
import com.amynna.hololib.modules.monde.areadisplayer.AreaDisplayerModule;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Editeur de la liste noire de blocs de l'Area Displayer Configurator : un champ d'id de bloc ({@code minecraft:stone},
 * ou {@code stone}) dont le texte devient <b>rouge</b> tant que l'id n'est pas un bloc connu, un bouton « Ajouter »,
 * puis la liste des blocs ignores, chacun avec une croix. Un id valide est ajoute par Entree (pas par Echap ni quand le
 * regard s'eloigne) ou par « Ajouter » ; le champ est alors vide. Callbacks cote client ; le module envoie la demande
 * au serveur (qui revalide) et repose la liste synchronisee par {@link #setBlocks} (lignes reconstruites seulement si
 * elle change). Modele : {@code KeyListEditor}.
 */
final class BlockListEditor extends HoloPanel
{
    private static final HoloColor EMPTY = HoloColor.special(0xFF).darker(0.47f);
    private static final HoloColor CHIP = HoloColor.secondary(0x30);
    private static final HoloColor TEXT = HoloColor.special(0xFF);
    private static final HoloColor INVALID = HoloColor.critical(0xFF).lighter(0.18f);

    private static final float FIELD_HEIGHT = 16f;
    private static final float BUTTON_WIDTH = 46f;
    private static final float GAP = 4f;
    private static final float ROW_HEIGHT = 12f;

    private final Consumer<String> onAdd;
    private final Consumer<String> onRemove;
    private final HoloWidgetTextBox field;
    private final HoloWidgetList list;
    private final List<String> shown = new ArrayList<>();
    private boolean enterPressed;

    /**
     * @param onAdd    id valide saisi (a envoyer au serveur)
     * @param onRemove id dont la croix a ete cliquee
     */
    BlockListEditor(float x, float y, float width, float height, Consumer<String> onAdd, Consumer<String> onRemove)
    {
        super(x, y, width, height);
        this.onAdd = Objects.requireNonNull(onAdd);
        this.onRemove = Objects.requireNonNull(onRemove);
        field = add(new HoloWidgetTextBox(0, 0, width - BUTTON_WIDTH - GAP, FIELD_HEIGHT,
                AreaDisplayerModule.MAX_BLOCK_ID, false)
        {
            @Override
            public void onEdited()
            {
                super.onEdited();
                colorize();
            }

            @Override
            public void onKeyPress(HoloCapture ctx, int key, int scanCode, int modifiers)
            {
                if (HoloKeys.isEnter(key)) enterPressed = true;
                super.onKeyPress(ctx, key, scanCode, modifiers);
            }
        });
        field.setPlaceholder(Component.literal("minecraft:stone"));
        field.setOnSubmit((box, text) -> {
            boolean enter = enterPressed;
            enterPressed = false;
            if (enter) submit();
        });
        add(new HoloWidgetButton(width - BUTTON_WIDTH, 0, BUTTON_WIDTH, FIELD_HEIGHT, Component.literal("Ajouter"),
                button -> submit()));
        list = add(new HoloWidgetList(0, FIELD_HEIGHT + GAP, width, height - FIELD_HEIGHT - GAP, ROW_HEIGHT));
    }

    /** Id valide du champ, {@code null} s'il est vide ou invalide. */
    private ResourceLocation valid()
    {
        ResourceLocation id = AreaDisplayerModule.parseBlock(field.text());
        return AreaDisplayerModule.isBlock(id) ? id : null;
    }

    /** Texte rouge si le champ n'est pas vide et n'est pas un bloc connu. */
    private void colorize()
    {
        String text = field.text();
        field.setTextColor(text.isBlank() || valid() != null ? TEXT : INVALID);
    }

    private void submit()
    {
        ResourceLocation id = valid();
        if (id == null) return;
        onAdd.accept(id.toString());
        field.buffer().setText("");
        colorize();
    }

    /** Blocs affiches ; lignes reconstruites seulement si la liste change. */
    void setBlocks(Collection<ResourceLocation> blocks)
    {
        List<String> next = new ArrayList<>(blocks.size());
        for (ResourceLocation id : blocks) next.add(id.toString());
        if (shown.equals(next)) return;
        shown.clear();
        shown.addAll(next);
        int first = list.first();
        list.clearItems();
        for (String id : shown) list.addItem(new Row(id));
        list.scrollTo(first);
    }

    @Override
    protected void draw(HoloCanvas canvas)
    {
        super.draw(canvas);
        if (shown.isEmpty()) {
            canvas.text(Component.literal("Aucun bloc ignore"), 6f, FIELD_HEIGHT + GAP + 3f, canvas.color(EMPTY));
        }
    }

    /** Ligne de la liste : id du bloc et croix de suppression. */
    private final class Row extends HoloPanel
    {
        private final Component text;
        private final HoloWidgetButton remove;

        Row(String id)
        {
            super(0, 0, 0, ROW_HEIGHT);
            text = Component.literal(id);
            remove = add(new HoloWidgetButton(0, 0, ROW_HEIGHT, ROW_HEIGHT, Component.literal("x"),
                    button -> onRemove.accept(id)));
            remove.setColors(HoloColor.critical(0x60).darker(0.39f), HoloColor.critical(0xFF).lighter(0.09f),
                    HoloColor.critical(0xFF).lighter(0.46f));
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
            canvas.fill(0, 0, width(), height(), canvas.color(CHIP));
            canvas.text(text, 3f, (height() - canvas.lineHeight()) / 2f + 1f, canvas.color(TEXT));
            super.draw(canvas);
        }
    }
}
