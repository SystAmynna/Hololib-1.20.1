package com.amynna.hololib.client.edit;

import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.edit.HoloEditRotation;
import com.amynna.hololib.holo.edit.HoloEditScale;
import com.amynna.hololib.holo.edit.HoloEditTranslation;
import com.amynna.hololib.holo.edit.HoloRotationFrame;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.widget.HoloLabel;
import com.amynna.hololib.holo.widget.control.HoloWidgetMultiButton;
import com.amynna.hololib.holo.widget.control.HoloWidgetSwitch;
import com.amynna.hololib.holo.widget.control.HoloWidgetTextBox;
import com.amynna.hololib.holo.window.HoloWindow;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Panneau des outils d'un objet edite (overlay a cote de l'objet, {@code pixelScale = 1}). Une ligne par outil que
 * l'objet implemente : bouton a icone ({@link HoloEditToolButton} ; clic gauche : active l'outil, ou le desactive ;
 * clic droit : RESET du groupe) et "+" ; ouverte par ce "+" ({@code HoloEditTarget.expanded}), la ligne montre aussi
 * trois valeurs editables et "RESET" quand le groupe n'est pas a sa valeur par defaut. Rotation d'un
 * {@code HoloRelativeToOpen} : interrupteur du repere (joueur / regard). Echelle : interrupteur synchronise et,
 * desynchronise, choix de l'axe. Bascule fixe / dynamique si elle s'applique. Objet ancre dans le monde : ligne de la
 * portee d'edition (une valeur, molette avec l'outil actif). Taille fixee a la construction : le panneau est
 * reconstruit quand une ligne s'ouvre ou se ferme.
 */
final class HoloEditPanel extends HoloWindow
{
    private static final int PAD = 6;
    /** Hauteur d'une ligne, et des widgets qu'elle contient. */
    private static final int ROW = 24;
    private static final int ITEM = 20;
    private static final int GAP = 4;
    private static final int EXPAND_X = PAD + ITEM + 2;
    private static final int BOXES_X = EXPAND_X + ITEM + GAP;
    private static final int RANGE_WIDTH = 70;
    private static final int BOX_WIDTH = 56;
    private static final int BOX_STEP = BOX_WIDTH + 2;
    private static final int RESET_WIDTH = 40;
    private static final int SYNC_WIDTH = 84;
    private static final int AXIS_WIDTH = 24;
    private static final int AXIS_STEP = AXIS_WIDTH + 4;
    private static final int SWITCH_WIDTH = 130;

    private static final int IDLE_BACKGROUND = 0x6020A0FF;
    private static final int IDLE_BORDER = 0xFF40C0FF;
    private static final int ACTIVE_BACKGROUND = 0xA040A040;
    private static final int ACTIVE_BORDER = 0xFF80FF80;

    private final HoloEditTarget target;
    private final Row translation;
    private final Row rotation;
    private final HoloWidgetSwitch frame;
    private final Row scale;
    private final HoloWidgetSwitch sync;
    private final HoloWidgetMultiButton[] axes = new HoloWidgetMultiButton[3];
    private final HoloWidgetSwitch dynamic;
    private final Row range;

    HoloEditPanel(HoloEditTarget target, boolean fixOrDynamic)
    {
        super("edit_panel", new HoloAnchor.Local(false), new HoloTransform(), width(target, fixOrDynamic),
                height(target, fixOrDynamic), 1f);
        this.target = target;
        HoloObject object = target.object();
        root().setBackground(0xB0101828).setBorder(0xFF406080, 1f);
        int y = PAD;
        translation = object instanceof HoloEditTranslation ? new Row(y, HoloEditTool.TRANSLATE) : null;
        if (translation != null) y += ROW;
        rotation = object instanceof HoloEditRotation ? new Row(y, HoloEditTool.ROTATE) : null;
        if (rotation != null) y += ROW;
        frame = hasFrame(target) ? root().add(new HoloWidgetSwitch(PAD, y, SWITCH_WIDTH, ITEM,
                Component.literal("Rot. joueur"), (widget, on) -> HoloEditSession.setRotationFrame(object,
                on ? HoloRotationFrame.PLAYER : HoloRotationFrame.GAZE))) : null;
        if (frame != null) y += ROW;
        if (object instanceof HoloEditScale) {
            scale = new Row(y, HoloEditTool.SCALE);
            y += ROW;
            sync = root().add(new HoloWidgetSwitch(PAD, y, SYNC_WIDTH, ITEM, Component.literal("Sync"),
                    (widget, on) -> target.setScaleSync(on)));
            String[] names = {"X", "Y", "Z"};
            for (int i = 0; i < 3; i++) {
                int axis = i;
                axes[i] = root().add(new HoloWidgetMultiButton(PAD + SYNC_WIDTH + GAP + i * AXIS_STEP, y,
                        AXIS_WIDTH, ITEM,
                        Component.literal(names[i])));
                axes[i].on(HoloControl.LEFT_CLICK, widget -> target.setScaleAxis(axis));
            }
            y += ROW;
        } else {
            scale = null;
            sync = null;
        }
        dynamic = fixOrDynamic ? root().add(new HoloWidgetSwitch(PAD, y, SWITCH_WIDTH, ITEM,
                Component.literal("Dynamique"),
                (widget, on) -> HoloEditSession.setDynamic(object, on))) : null;
        if (dynamic != null) y += ROW;
        range = hasRange(target) ? new Row(y, HoloEditTool.RANGE) : null;
    }

    /** Ligne de la portee d'edition : objets ancres dans le monde (pas un objet HUD du cadre). */
    private static boolean hasRange(HoloEditTarget target)
    {
        return !target.hud() && target.object().anchor() instanceof HoloAnchor.World;
    }

    /** Interrupteur du repere de rotation : {@code HoloRelativeToOpen} avec l'outil rotation. */
    private static boolean hasFrame(HoloEditTarget target)
    {
        HoloObject object = target.object();
        return !target.hud() && object instanceof HoloEditRotation && object.openOffset() != null;
    }

    private static int width(HoloEditTarget target, boolean fixOrDynamic)
    {
        HoloObject object = target.object();
        int right = EXPAND_X + ITEM;
        boolean rows = false;
        if (object instanceof HoloEditTranslation) rows |= target.expanded(HoloEditTool.TRANSLATE);
        if (object instanceof HoloEditRotation) rows |= target.expanded(HoloEditTool.ROTATE);
        if (object instanceof HoloEditScale) {
            rows |= target.expanded(HoloEditTool.SCALE);
            right = Math.max(right, PAD + SYNC_WIDTH + GAP + 3 * AXIS_STEP);
        }
        if (rows) right = Math.max(right, BOXES_X + 3 * BOX_STEP + RESET_WIDTH);
        if (fixOrDynamic || hasFrame(target)) right = Math.max(right, PAD + SWITCH_WIDTH);
        if (hasRange(target)) right = Math.max(right, PAD + RANGE_WIDTH + GAP + BOX_STEP + RESET_WIDTH);
        return right + PAD;
    }

    private static int height(HoloEditTarget target, boolean fixOrDynamic)
    {
        HoloObject object = target.object();
        int rows = hasRange(target) ? 1 : 0;
        if (object instanceof HoloEditTranslation) rows++;
        if (object instanceof HoloEditRotation) rows++;
        if (hasFrame(target)) rows++;
        if (object instanceof HoloEditScale) rows += 2;
        if (fixOrDynamic) rows++;
        return Math.max(1, rows) * ROW + 2 * PAD - (ROW - ITEM);
    }

    /** Valeurs, RESET et etat des boutons, depuis l'objet et l'outil actif. */
    void refresh()
    {
        HoloObject object = target.object();
        HoloTransform t = object.transform();
        if (translation != null) translation.refresh(t.x(), t.y(), t.z());
        if (rotation != null) rotation.refresh(t.yaw(), t.pitch(), t.roll());
        if (frame != null) frame.setOn(object.rotationFrame() == HoloRotationFrame.PLAYER);
        if (scale != null) {
            scale.refresh(t.scaleX(), t.scaleY(), t.scaleZ());
            sync.setOn(target.scaleSync());
            for (int i = 0; i < 3; i++) {
                axes[i].setVisible(!target.scaleSync());
                highlight(axes[i], i == target.scaleAxis());
            }
        }
        if (dynamic != null) dynamic.setOn(HoloEditSession.dynamic(object));
        if (range != null) range.refresh(object.editRange(), 0f, 0f);
    }

    private static void highlight(HoloWidgetMultiButton button, boolean on)
    {
        button.setColors(on ? ACTIVE_BACKGROUND : IDLE_BACKGROUND, on ? ACTIVE_BORDER : IDLE_BORDER, 0xFFFFFFFF);
    }

    private static String format(float value) { return String.format(Locale.ROOT, "%.2f", value); }

    /**
     * Ligne d'un outil : transformation (icone, "+", et ses trois valeurs si elle est ouverte) ou portee (bouton texte
     * et sa valeur, toujours visibles).
     */
    private final class Row
    {
        private final HoloEditTool tool;
        private final HoloWidgetMultiButton button;
        /** Vide si la ligne est fermee. */
        private final HoloWidgetTextBox[] boxes;
        private final HoloLabel reset;

        private Row(int y, HoloEditTool tool)
        {
            this.tool = tool;
            HoloObject object = target.object();
            boolean isRange = tool == HoloEditTool.RANGE;
            int x;
            int count;
            if (isRange) {
                button = root().add(new HoloWidgetMultiButton(PAD, y, RANGE_WIDTH, ITEM, Component.literal("Portee")));
                x = PAD + RANGE_WIDTH + GAP;
                count = 1;
            } else {
                button = root().add(new HoloEditToolButton(PAD, y, ITEM, tool));
                boolean expanded = target.expanded(tool);
                HoloWidgetMultiButton expand = root().add(new HoloWidgetMultiButton(EXPAND_X, y, ITEM, ITEM,
                        Component.literal(expanded ? "-" : "+")));
                expand.on(HoloControl.LEFT_CLICK, widget -> target.toggleExpanded(tool));
                x = BOXES_X;
                count = expanded ? 3 : 0;
            }
            button.on(HoloControl.LEFT_CLICK, widget -> HoloEditTools.toggle(target, tool));
            button.on(HoloControl.RIGHT_CLICK, widget -> HoloEditSession.reset(object, tool));
            boxes = new HoloWidgetTextBox[count];
            for (int i = 0; i < count; i++) {
                int index = i;
                boxes[i] = root().add(new HoloWidgetTextBox(x + i * BOX_STEP, y, BOX_WIDTH, ITEM, 12, false));
                boxes[i].setPlaceholder(Component.empty()).setHoverMargin(12f)
                        .setOnSubmit((box, text) -> HoloEditSession.submit(object, tool, index, text));
            }
            reset = count > 0 ? root().add(new HoloLabel(x + count * BOX_STEP, y, RESET_WIDTH, ITEM,
                    Component.literal("RESET")).setAlign(HoloLabel.Align.CENTER).setColor(0xFFFFA040)) : null;
        }

        /** Valeurs des cases (les suivantes ignorees s'il y en a moins de trois). */
        private void refresh(float a, float b, float c)
        {
            if (boxes.length > 0) boxes[0].setText(format(a));
            if (boxes.length > 1) boxes[1].setText(format(b));
            if (boxes.length > 2) boxes[2].setText(format(c));
            if (reset != null) reset.setVisible(!HoloEditSession.isDefault(target.object(), tool));
            highlight(button, HoloEditTools.target() == target && HoloEditTools.tool() == tool);
        }
    }
}
