package com.amynna.hololib.modules.monde.trigger;

import com.amynna.hololib.holo.capture.HoloCapture;
import com.amynna.hololib.holo.capture.HoloKeys;
import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.holo.widget.HoloPanel;
import com.amynna.hololib.holo.widget.HoloWidget;
import com.amynna.hololib.holo.widget.control.HoloWidgetButton;
import com.amynna.hololib.holo.widget.control.HoloWidgetDragRow;
import com.amynna.hololib.holo.widget.control.HoloWidgetList;
import com.amynna.hololib.holo.widget.control.HoloWidgetTextBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Editeur d'une liste de commandes des Configurators Trigger : une <b>entete</b> (bouton « &gt; Nom (n) » replie,
 * « v Nom (n) » ouvert, n = nombre de commandes) et, ouvert, un <b>corps</b> : un champ de commande avec un petit
 * « + », puis la liste des commandes dans l'ordre d'execution, chacune avec un petit « x » (suppression) et glissable
 * ({@link HoloWidgetDragRow}) pour la reordonner. Une commande est ajoutee par Entree (pas par Echap ni quand le regard
 * s'eloigne) ou par « + » ; le champ est alors vide ; une commande vide est ignoree.
 * <p>
 * <b>Modification</b> : le crayon (orange, avant le « x ») d'une ligne la <b>selectionne</b> (ligne encadree) et renvoie
 * sa commande dans le champ ; « + » (devenu orange) et Entree <b>remplacent alors la commande selectionnee</b> au lieu
 * d'en ajouter une (inchangee ou vide : rien n'est envoye pour une inchangee, une vide est ignoree). Un nouveau clic sur
 * le crayon de la ligne selectionnee, ou le repli de la section, annule. La selection suit la ligne quand on la glisse
 * et tombe si la liste change sous elle (autre operateur, suppression).
 * <p>
 * Callbacks cote client ({@link Listener}) : le module envoie la demande au serveur (qui revalide) et l'etat
 * synchronise revient par {@link #setCommands} (lignes reconstruites seulement si la liste change ; apres un glisser
 * ou le defilement, rien n'est reconstruit). Retracte a la creation ; l'etat ouvert / ferme est local, l'accordeon est
 * tenu par {@link CommandListGroup}. Modeles : {@code BlockListEditor} (champ et liste), {@code QuickActionRow} (ligne
 * glissable).
 */
public final class CommandListEditor extends HoloPanel
{
    /** Demandes de l'utilisatrice, a envoyer au serveur. */
    public interface Listener
    {
        /** Commande saisie (non vide). */
        void add(String command);

        /** Croix d'une ligne : position actuelle et texte (le serveur verifie les deux). */
        void remove(int index, String command);

        /** Commande selectionnee modifiee : position et ancien texte (verifies par le serveur), nouveau texte. */
        void edit(int index, String old, String command);

        /** Ligne glissee de {@code from} vers {@code to}. */
        void move(int from, int to);
    }

    public static final float HEADER_HEIGHT = 14f;
    public static final float FIELD_HEIGHT = 16f;
    public static final float GAP = 4f;
    public static final float ROW_HEIGHT = 14f;
    /** Hauteur de la liste : trois lignes entieres et une quatrieme rognee (la molette fait defiler). */
    public static final float LIST_HEIGHT = 64f;
    /** Hauteur du corps (champ et liste), ouvert. */
    public static final float BODY_HEIGHT = GAP + FIELD_HEIGHT + GAP + LIST_HEIGHT;
    private static final float PLUS_WIDTH = 16f;

    private static final HoloColor EMPTY = HoloColor.special(0xFF).darker(0.47f);
    private static final HoloColor TEXT = HoloColor.special(0xFF);
    private static final HoloColor GRIP = HoloColor.primary(0xFF).lighter(0.3f);
    private static final HoloColor TAB_ACTIVE_BACKGROUND = HoloColor.secondary(0xC0).darker(0.23f);
    private static final HoloColor HEADER_BACKGROUND = HoloColor.secondary(0x60);
    /** Modification : role « action » du theme (jaune / orange selon le theme). */
    private static final HoloColor EDIT_BACKGROUND = HoloColor.action(0x60).darker(0.39f);
    private static final HoloColor EDIT_BORDER = HoloColor.action(0xFF).lighter(0.09f);
    private static final HoloColor EDIT_TEXT = HoloColor.action(0xFF).lighter(0.46f);
    private static final HoloColor PENCIL_BODY = HoloColor.special(0xFF);
    private static final HoloColor PENCIL_TIP = HoloColor.special(0xFF).darker(0.45f);
    private static final HoloColor PENCIL_ERASER = HoloColor.critical(0xFF).lighter(0.2f);
    private static final HoloColor PLUS_BACKGROUND = HoloColor.selection(0x60).darker(0.39f);
    private static final HoloColor PLUS_BORDER = HoloColor.selection(0xFF).lighter(0.09f);
    private static final HoloColor PLUS_TEXT = HoloColor.selection(0xFF).lighter(0.46f);

    private final String title;
    private final Listener listener;
    private final HoloWidgetButton header;
    private final HoloWidgetTextBox field;
    private final HoloWidgetButton plus;
    private final HoloWidgetList list;
    /** Commandes affichees, dans l'ordre. */
    private final List<String> shown = new ArrayList<>();
    private boolean expanded;
    private boolean enterPressed;
    /** Ligne selectionnee pour modification ({@code -1} : aucune) et son texte au moment de la selection. */
    private int editingIndex = -1;
    private String editingText = "";

    /**
     * @param title    nom de la liste, dans l'entete
     * @param onHeader clic sur l'entete (le groupe ouvre ou replie, accordeon)
     */
    public CommandListEditor(float width, String title, Listener listener, Runnable onHeader)
    {
        super(0, 0, width, HEADER_HEIGHT);
        this.title = Objects.requireNonNull(title);
        this.listener = Objects.requireNonNull(listener);
        Objects.requireNonNull(onHeader);
        header = add(new HoloWidgetButton(0, 0, width, HEADER_HEIGHT, Component.empty(), b -> onHeader.run()));
        float fieldY = HEADER_HEIGHT + GAP;
        field = add(new HoloWidgetTextBox(0, fieldY, width - PLUS_WIDTH - GAP, FIELD_HEIGHT,
                TriggerCommands.MAX_LENGTH, false)
        {
            @Override
            public void onKeyPress(HoloCapture ctx, int key, int scanCode, int modifiers)
            {
                if (HoloKeys.isEnter(key)) enterPressed = true;
                super.onKeyPress(ctx, key, scanCode, modifiers);
            }
        });
        field.setPlaceholder(Component.literal("commande"));
        field.setOnSubmit((box, text) -> {
            boolean enter = enterPressed;
            enterPressed = false;
            if (enter) submit();
        });
        plus = add(new HoloWidgetButton(width - PLUS_WIDTH, fieldY, PLUS_WIDTH, FIELD_HEIGHT, Component.literal("+"),
                b -> submit()));
        list = add(new HoloWidgetList(0, fieldY + FIELD_HEIGHT + GAP, width, LIST_HEIGHT, ROW_HEIGHT));
        showEditing();
        setExpanded(false);
    }

    /** Hauteur de la section : l'entete seule, ou l'entete et le corps. */
    public float sectionHeight() { return expanded ? HEADER_HEIGHT + BODY_HEIGHT : HEADER_HEIGHT; }

    public boolean expanded() { return expanded; }

    /** Ouvre ou replie la section ; la hauteur est a reappliquer par le groupe ({@link #sectionHeight}). */
    public void setExpanded(boolean expanded)
    {
        this.expanded = expanded;
        if (!expanded && editingIndex >= 0) {
            stopEditing();
            field.buffer().setText("");
        }
        field.setVisible(expanded);
        plus.setVisible(expanded);
        list.setVisible(expanded);
        refreshHeader();
    }

    private void refreshHeader()
    {
        header.setText(Component.literal((expanded ? "v " : "> ") + title + " (" + shown.size() + ")"));
        header.setColors(expanded ? TAB_ACTIVE_BACKGROUND : HEADER_BACKGROUND, HoloColor.primary(0xFF), TEXT);
    }

    /** « + » ou Entree : modifie la commande selectionnee, sinon ajoute une commande. */
    private void submit()
    {
        String command = field.text().strip();
        if (command.isEmpty()) return;
        if (editingIndex >= 0) {
            int index = editingIndex;
            String old = editingText;
            stopEditing();
            field.buffer().setText("");
            if (!command.equals(old)) listener.edit(index, old, command);
            return;
        }
        listener.add(command);
        field.buffer().setText("");
    }

    /** Crayon d'une ligne : selectionne sa commande et la renvoie dans le champ ; deuxieme clic : annule. */
    private void startEdit(CommandRow row)
    {
        int index = list.items().indexOf(row);
        if (index < 0) return;
        if (index == editingIndex) {
            stopEditing();
            field.buffer().setText("");
            return;
        }
        editingIndex = index;
        editingText = row.command;
        field.buffer().setText(row.command);
        field.buffer().moveToEnd();
        showEditing();
    }

    private void stopEditing()
    {
        editingIndex = -1;
        editingText = "";
        showEditing();
    }

    /** Ligne encadree et « + » orange selon la selection. */
    private void showEditing()
    {
        if (editingIndex >= 0) plus.setColors(EDIT_BACKGROUND, EDIT_BORDER, EDIT_TEXT);
        else plus.setColors(PLUS_BACKGROUND, PLUS_BORDER, PLUS_TEXT);
        List<HoloWidget> items = list.items();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) instanceof CommandRow row) row.setEditing(i == editingIndex);
        }
    }

    /** Commandes affichees ; lignes reconstruites seulement si la liste change (defilement conserve). */
    public void setCommands(List<String> commands)
    {
        if (shown.equals(commands)) return;
        shown.clear();
        shown.addAll(commands);
        int first = list.first();
        list.clearItems();
        for (String command : shown) {
            CommandRow row = new CommandRow(command);
            row.setOnMove((moved, from, to) -> {
                // La liste affichee suit le glisser : la synchro qui revient est deja identique, sans reconstruction.
                shown.add(to, shown.remove(from));
                editingIndex = movedIndex(editingIndex, from, to);
                showEditing();
                listener.move(from, to);
            });
            list.addItem(row);
        }
        list.scrollTo(first);
        refreshHeader();
        // La ligne selectionnee a bouge ou disparu (autre operateur) : la selection tombe, le champ reste.
        if (editingIndex >= 0 && (editingIndex >= shown.size() || !shown.get(editingIndex).equals(editingText))) {
            stopEditing();
        } else {
            showEditing();
        }
    }

    /** Position de la ligne {@code index} apres le deplacement de {@code from} vers {@code to} ({@code -1} : aucune). */
    private static int movedIndex(int index, int from, int to)
    {
        if (index < 0) return -1;
        if (index == from) return to;
        int moved = from < index ? index - 1 : index;
        return to <= moved ? moved + 1 : moved;
    }

    @Override
    protected void draw(HoloCanvas canvas)
    {
        super.draw(canvas);
        if (expanded && shown.isEmpty()) {
            canvas.text(Component.literal("Aucune commande"), 6f, HEADER_HEIGHT + GAP + FIELD_HEIGHT + GAP + 4f,
                    canvas.color(EMPTY));
        }
    }

    /** Ligne de la liste : poignee, commande (rognee a la largeur) et croix de suppression ; glissable. */
    private final class CommandRow extends HoloWidgetDragRow
    {
        private static final float GRIP_WIDTH = 12f;
        private static final String ELLIPSIS = "...";

        private final String command;
        private final HoloWidgetButton edit;
        private final HoloWidgetButton remove;
        private boolean editing;

        CommandRow(String command)
        {
            super(0, 0, 0, ROW_HEIGHT);
            this.command = command;
            edit = add(new HoloWidgetButton(0, 0, ROW_HEIGHT, ROW_HEIGHT, Component.empty(),
                    b -> startEdit(CommandRow.this))
            {
                @Override
                protected void draw(HoloCanvas canvas)
                {
                    super.draw(canvas);
                    drawPencil(canvas, width(), height());
                }
            });
            edit.setColors(EDIT_BACKGROUND, EDIT_BORDER, EDIT_TEXT);
            remove = add(new HoloWidgetButton(0, 0, ROW_HEIGHT, ROW_HEIGHT, Component.literal("x"),
                    b -> listener.remove(list.items().indexOf(this), command)));
            remove.setColors(HoloColor.critical(0x60).darker(0.39f), HoloColor.critical(0xFF).lighter(0.09f),
                    HoloColor.critical(0xFF).lighter(0.46f));
            placeRemove();
        }

        @Override
        public HoloWidget setBounds(float x, float y, float width, float height)
        {
            super.setBounds(x, y, width, height);
            // Appele aussi par le constructeur parent, avant la creation de la croix.
            if (remove != null) placeRemove();
            return this;
        }

        private void placeRemove()
        {
            remove.setBounds(width() - height(), 0, height(), height());
            edit.setBounds(width() - 2f * height(), 0, height(), height());
        }

        void setEditing(boolean editing) { this.editing = editing; }

        /** Crayon incline en icone (quadrilateres, une couche) : mine, corps et gomme. */
        private static void drawPencil(HoloCanvas canvas, float w, float h)
        {
            float s = Math.min(w, h) / 14f;
            float ox = (w - 14f * s) / 2f;
            float oy = (h - 14f * s) / 2f;
            // Axe de la pointe (3, 11) vers la gomme (11, 3), demi-largeur 1,7 : decalage perpendiculaire 1,2.
            float hp = 1.2f;
            float bx = 4.7f;
            float by = 9.3f;
            float ex = 9.7f;
            float ey = 4.3f;
            canvas.beginLayer();
            canvas.quad(ox + (bx + hp) * s, oy + (by + hp) * s, ox + (ex + hp) * s, oy + (ey + hp) * s,
                    ox + (ex - hp) * s, oy + (ey - hp) * s, ox + (bx - hp) * s, oy + (by - hp) * s,
                    canvas.color(PENCIL_BODY));
            canvas.quad(ox + (bx + hp) * s, oy + (by + hp) * s, ox + 3f * s, oy + 11f * s, ox + (bx - hp) * s,
                    oy + (by - hp) * s, ox + (bx - hp) * s, oy + (by - hp) * s, canvas.color(PENCIL_TIP));
            canvas.quad(ox + (ex + hp) * s, oy + (ey + hp) * s, ox + (11.4f + hp) * s, oy + (2.6f + hp) * s,
                    ox + (11.4f - hp) * s, oy + (2.6f - hp) * s, ox + (ex - hp) * s, oy + (ey - hp) * s,
                    canvas.color(PENCIL_ERASER));
            canvas.endLayer();
        }

        @Override
        protected void draw(HoloCanvas canvas)
        {
            super.draw(canvas);
            if (editing) canvas.outline(0, 0, width(), height(), 1f, canvas.color(EDIT_BORDER));
            float mid = height() / 2f;
            for (int i = -1; i <= 1; i++) canvas.fill(3f, mid + 2.5f * i - 0.5f, 6f, 1f, canvas.color(GRIP));
            float room = width() - 2f * height() - GRIP_WIDTH - 2f;
            canvas.text(fit(canvas, command, room), GRIP_WIDTH, (height() - canvas.lineHeight()) / 2f + 1f,
                    canvas.color(TEXT));
        }

        /** Texte raccourci par « ... » pour tenir dans {@code room} pixels (le texte n'est pas rogne par le clip). */
        private Component fit(HoloCanvas canvas, String text, float room)
        {
            Component full = Component.literal(text);
            if (canvas.textWidth(full) <= room) return full;
            int end = text.length();
            while (end > 0 && canvas.textWidth(Component.literal(text.substring(0, end) + ELLIPSIS)) > room) end--;
            return Component.literal(text.substring(0, end) + ELLIPSIS);
        }
    }
}
