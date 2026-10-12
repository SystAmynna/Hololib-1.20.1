package com.amynna.hololib.client.edit;

import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.edit.HoloDisplay;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.holo.widget.control.HoloWidgetMultiButton;
import net.minecraft.network.chat.Component;

/**
 * Bouton de l'affichage d'un objet edite, en forme d'oeil : barre ({@code OFF}), ambre ({@code FOCUS}) ou blanc
 * ({@code ON}). Clic gauche : affichage suivant ; clic droit : {@code ON} (valeur par defaut).
 */
final class HoloEditEyeButton extends HoloWidgetMultiButton
{
    private static final HoloColor BACKGROUND = HoloColor.secondary(0xA0).darker(0.84f);
    private static final HoloColor EYE_OFF = HoloColor.special(0xFF).darker(0.43f);
    private static final HoloColor EYE_FOCUS = HoloColor.action(0xFF);
    private static final HoloColor EYE_ON = HoloColor.special(0xFF);
    private static final HoloColor STRIKE = HoloColor.critical(0xFF);

    static final int WIDTH = 28;
    static final int HEIGHT = 20;
    /** Le dessin est decrit dans un repere 20 x 14, mis a l'echelle du bouton. */
    private static final float SX = WIDTH / 20f;
    private static final float SY = HEIGHT / 14f;

    private final HoloObject object;

    HoloEditEyeButton(HoloObject object)
    {
        super(0, 0, WIDTH, HEIGHT, Component.empty());
        this.object = object;
        on(HoloControl.LEFT_CLICK, widget -> HoloEditSession.cycleDisplay(object));
        on(HoloControl.RIGHT_CLICK, widget -> HoloEditSession.setDisplay(object, HoloDisplay.ON));
    }

    @Override
    protected void draw(HoloCanvas canvas)
    {
        HoloDisplay display = object.display();
        canvas.fill(0, 0, width(), height(), canvas.color(BACKGROUND));
        if (pressed() || hovered()) {
            canvas.fill(0, 0, width(), height(), canvas.color(pressed() ? PRESSED_OVERLAY : HOVER_OVERLAY));
        }
        int color = canvas.color(switch (display) {
            case OFF -> EYE_OFF;
            case FOCUS -> EYE_FOCUS;
            case ON -> EYE_ON;
        });
        canvas.outline(0, 0, width(), height(), 1f, color);
        // Paupieres puis pupille.
        fill(canvas, 7, 3, 6, 1, color);
        fill(canvas, 5, 4, 2, 1, color);
        fill(canvas, 13, 4, 2, 1, color);
        fill(canvas, 3, 5, 2, 4, color);
        fill(canvas, 15, 5, 2, 4, color);
        fill(canvas, 5, 9, 2, 1, color);
        fill(canvas, 13, 9, 2, 1, color);
        fill(canvas, 7, 10, 6, 1, color);
        fill(canvas, 8, 5, 4, 4, color);
        if (display == HoloDisplay.OFF) {
            for (int i = 0; i < 10; i++) fill(canvas, 4f + i * 1.2f, 2f + i, 1.5f, 1f, canvas.color(STRIKE));
        }
    }

    /** Rectangle du repere 20 x 14, a l'echelle du bouton. */
    private static void fill(HoloCanvas canvas, float x, float y, float width, float height, int argb)
    {
        canvas.fill(x * SX, y * SY, width * SX, height * SY, argb);
    }
}
