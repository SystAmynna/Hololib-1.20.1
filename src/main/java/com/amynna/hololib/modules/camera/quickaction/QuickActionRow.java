package com.amynna.hololib.modules.camera.quickaction;

import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.holo.widget.HoloWidget;
import com.amynna.hololib.holo.widget.control.HoloWidgetDragRow;
import com.amynna.hololib.holo.widget.control.HoloWidgetSwitch;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Ligne de la liste des actions dans la configuration de Quick Action : poignee, icone et nom de l'action, puis un
 * interrupteur (action active dans la roue). La ligne se reordonne au glisser ({@link HoloWidgetDragRow}).
 */
final class QuickActionRow extends HoloWidgetDragRow
{
    private static final float SWITCH_WIDTH = 28f;
    private static final HoloColor GRIP = HoloColor.primary(0xFF).lighter(0.3f);
    private static final HoloColor TEXT = HoloColor.special(0xFF);

    private final QuickAction action;
    private final HoloWidgetSwitch toggle;

    QuickActionRow(QuickAction action, Consumer<Boolean> onToggle)
    {
        super(0, 0, SWITCH_WIDTH, 16);
        this.action = action;
        toggle = add(new HoloWidgetSwitch(0, 0, SWITCH_WIDTH, 14, Component.empty(),
                (widget, on) -> onToggle.accept(on)));
        placeToggle();
    }

    void setOn(boolean on) { toggle.setOn(on); }

    @Override
    public HoloWidget setBounds(float x, float y, float width, float height)
    {
        super.setBounds(x, y, width, height);
        // Appele aussi par le constructeur parent, avant la creation de l'interrupteur.
        if (toggle != null) placeToggle();
        return this;
    }

    private void placeToggle() { toggle.setBounds(width() - SWITCH_WIDTH - 1f, 1f, SWITCH_WIDTH, height() - 2f); }

    @Override
    protected void draw(HoloCanvas canvas)
    {
        super.draw(canvas);
        float mid = height() / 2f;
        for (int i = -1; i <= 1; i++) canvas.fill(3f, mid + 3f * i - 0.5f, 6f, 1f, canvas.color(GRIP));
        float icon = height() - 4f;
        canvas.push(12f, 2f);
        try {
            action.drawIcon(canvas, icon);
        } finally {
            canvas.pop();
        }
        canvas.text(action.name(), 16f + icon, (height() - canvas.lineHeight()) / 2f + 1f, canvas.color(TEXT));
    }
}
