package com.amynna.hololib.modules.camera.themeconfigurator;

import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.theme.HoloTheme;
import com.amynna.hololib.holo.theme.HoloThemeColor;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.holo.widget.control.HoloWidgetButton;
import net.minecraft.network.chat.Component;

/**
 * Ligne d'un parametre de theme dans {@link ThemeConfigWindow} : nom a gauche, pastille a droite (couleur du
 * brouillon, ou miniature du motif) ; un clic le choisit. Le parametre choisi est encadre de la couleur de selection.
 */
final class ThemeParamRow extends HoloWidgetButton
{
    private static final float SWATCH_WIDTH = 18f;
    private static final HoloColor FRAME = HoloColor.special(0xFF).darker(0.5f);
    private static final HoloColor SELECTED = HoloColor.selection(0x90).darker(0.3f);
    private static final HoloColor IDLE = HoloColor.secondary(0x40);
    private static final HoloColor SELECTED_BORDER = HoloColor.selection(0xFF);
    private static final HoloColor BORDER = HoloColor.primary(0xFF);
    private static final HoloColor TEXT = HoloColor.special(0xFF);

    private final int param;
    private final ThemeConfiguratorBase module;

    ThemeParamRow(float x, float y, float width, float height, int param, ThemeConfiguratorBase module)
    {
        super(x, y, width, height, label(param), button -> {});
        this.param = param;
        this.module = module;
        setOnPress(button -> module.selectParam(param));
    }

    private static Component label(int param)
    {
        HoloThemeColor role = HoloThemeColor.byOrdinal(param);
        return role != null ? role.label() : Component.literal("Motif");
    }

    void refresh()
    {
        boolean selected = module.param() == param;
        setColors(selected ? SELECTED : IDLE, selected ? SELECTED_BORDER : BORDER, TEXT);
    }

    @Override
    protected void draw(HoloCanvas canvas)
    {
        super.draw(canvas);
        HoloTheme draft = module.draft();
        float x = width() - SWATCH_WIDTH - 2f;
        float h = height() - 4f;
        HoloThemeColor role = HoloThemeColor.byOrdinal(param);
        if (role != null) {
            canvas.fill(x, 2f, SWATCH_WIDTH, h, draft.argb(role, 0xFF));
        } else {
            canvas.push(x, 2f);
            try {
                draft.pattern().draw(canvas, SWATCH_WIDTH, h, draft, 0xFF);
            } finally {
                canvas.pop();
            }
        }
        canvas.outline(x, 2f, SWATCH_WIDTH, h, 1f, canvas.color(FRAME));
    }
}
