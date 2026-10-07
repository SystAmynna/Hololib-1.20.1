package com.amynna.hololib.client.edit;

import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.edit.HoloEditRotation;
import com.amynna.hololib.holo.edit.HoloEditScale;
import com.amynna.hololib.holo.edit.HoloEditTranslation;
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
 * l'objet implemente : bouton (clic gauche : active l'outil, ou le desactive ; clic droit : RESET du groupe), trois
 * valeurs editables, et "RESET" quand le groupe n'est pas a sa valeur par defaut. Echelle : interrupteur synchronise
 * et, desynchronise, choix de l'axe. Bascule fixe / dynamique si elle s'applique. Objet ancre dans le monde : ligne
 * de la portee d'edition (une valeur, molette avec l'outil actif).
 */
final class HoloEditPanel extends HoloWindow
{
    private static final int PAD = 6;
    /** Hauteur d'une ligne, et des widgets qu'elle contient. */
    private static final int ROW = 24;
    private static final int ITEM = 20;
    private static final int GAP = 4;
    private static final int BUTTON_WIDTH = 70;
    private static final int BOX_WIDTH = 56;
    private static final int BOX_STEP = BOX_WIDTH + 2;
    private static final int RESET_WIDTH = 40;
    private static final int SYNC_WIDTH = 84;
    private static final int AXIS_WIDTH = 24;
    private static final int AXIS_STEP = AXIS_WIDTH + 4;
    private static final int DYNAMIC_WIDTH = 130;
    static final int WIDTH = PAD + BUTTON_WIDTH + GAP + 3 * BOX_STEP + RESET_WIDTH + PAD;

    private static final int IDLE_BACKGROUND = 0x6020A0FF;
    private static final int IDLE_BORDER = 0xFF40C0FF;
    private static final int ACTIVE_BACKGROUND = 0xA040A040;
    private static final int ACTIVE_BORDER = 0xFF80FF80;

    private final HoloEditTarget target;
    private final Row translation;
    private final Row rotation;
    private final Row scale;
    private final HoloWidgetSwitch sync;
    private final HoloWidgetMultiButton[] axes = new HoloWidgetMultiButton[3];
    private final HoloWidgetSwitch dynamic;
    private final Row range;

    HoloEditPanel(HoloEditTarget target, boolean fixOrDynamic)
    {
        super("edit_panel", new HoloAnchor.Local(false), new HoloTransform(), WIDTH,
                height(target, fixOrDynamic), 1f);
        this.target = target;
        HoloObject object = target.object();
        root().setBackground(0xB0101828).setBorder(0xFF406080, 1f);
        int y = PAD;
        translation = object instanceof HoloEditTranslation ? new Row(y, "Position", HoloEditTool.TRANSLATE, 3) : null;
        if (translation != null) y += ROW;
        rotation = object instanceof HoloEditRotation ? new Row(y, "Rotation", HoloEditTool.ROTATE, 3) : null;
        if (rotation != null) y += ROW;
        if (object instanceof HoloEditScale) {
            scale = new Row(y, "Echelle", HoloEditTool.SCALE, 3);
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
        dynamic = fixOrDynamic ? root().add(new HoloWidgetSwitch(PAD, y, DYNAMIC_WIDTH, ITEM,
                Component.literal("Dynamique"),
                (widget, on) -> HoloEditSession.setDynamic(object, on))) : null;
        if (dynamic != null) y += ROW;
        range = hasRange(target) ? new Row(y, "Portee", HoloEditTool.RANGE, 1) : null;
    }

    /** Ligne de la portee d'edition : objets ancres dans le monde (pas un objet HUD du cadre). */
    private static boolean hasRange(HoloEditTarget target)
    {
        return !target.hud() && target.object().anchor() instanceof HoloAnchor.World;
    }

    private static int height(HoloEditTarget target, boolean fixOrDynamic)
    {
        HoloObject object = target.object();
        int rows = hasRange(target) ? 1 : 0;
        if (object instanceof HoloEditTranslation) rows++;
        if (object instanceof HoloEditRotation) rows++;
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

    /** Ligne d'un outil. */
    private final class Row
    {
        private final HoloEditTool tool;
        private final HoloWidgetMultiButton button;
        private final HoloWidgetTextBox[] boxes;
        private final HoloLabel reset;

        /** @param count nombre de valeurs editables (1 a 3) */
        private Row(int y, String name, HoloEditTool tool, int count)
        {
            boxes = new HoloWidgetTextBox[count];
            this.tool = tool;
            HoloObject object = target.object();
            button = root().add(new HoloWidgetMultiButton(PAD, y, BUTTON_WIDTH, ITEM, Component.literal(name)));
            button.on(HoloControl.LEFT_CLICK, widget -> HoloEditTools.toggle(target, tool));
            button.on(HoloControl.RIGHT_CLICK, widget -> HoloEditSession.reset(object, tool));
            for (int i = 0; i < count; i++) {
                int index = i;
                boxes[i] = root().add(new HoloWidgetTextBox(PAD + BUTTON_WIDTH + GAP + i * BOX_STEP, y, BOX_WIDTH,
                        ITEM, 12, false));
                boxes[i].setPlaceholder(Component.empty()).setHoverMargin(12f)
                        .setOnSubmit((box, text) -> HoloEditSession.submit(object, tool, index, text));
            }
            reset = root().add(new HoloLabel(PAD + BUTTON_WIDTH + GAP + 3 * BOX_STEP, y, RESET_WIDTH, ITEM,
                    Component.literal("RESET")).setAlign(HoloLabel.Align.CENTER).setColor(0xFFFFA040));
        }

        /** Valeurs des cases (les suivantes ignorees s'il y en a moins de trois). */
        private void refresh(float a, float b, float c)
        {
            boxes[0].setText(format(a));
            if (boxes.length > 1) boxes[1].setText(format(b));
            if (boxes.length > 2) boxes[2].setText(format(c));
            reset.setVisible(!HoloEditSession.isDefault(target.object(), tool));
            highlight(button, HoloEditTools.target() == target && HoloEditTools.tool() == tool);
        }
    }
}
