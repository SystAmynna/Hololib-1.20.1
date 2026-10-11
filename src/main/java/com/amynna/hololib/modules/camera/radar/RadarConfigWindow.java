package com.amynna.hololib.modules.camera.radar;

import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.interact.HoloInteraction;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.holo.widget.control.HoloWidgetSwitch;
import com.amynna.hololib.holo.widget.control.HoloWidgetValueBar;
import com.amynna.hololib.modules.camera.config.ConfigWindow;
import net.minecraft.network.chat.Component;

import java.util.function.IntFunction;

/**
 * Fenetre de configuration « Radar » de {@link RadarModule} (objet du module, cree dans {@code onLoad} des deux
 * cotes) : barres a crans de l'epaisseur de mur (0 a 16 blocs puis infini) et du rayon (8 a 128 blocs), interrupteurs
 * des joueurs et des autres entites. {@code PUBLIC} comme les autres fenetres de Config.
 */
final class RadarConfigWindow extends ConfigWindow
{
    private static final int WIDTH = 170;
    private static final int MARGIN = 6;
    private static final int ROW = 16;
    private static final int BAR = 14;
    private static final int GAP = 3;

    private final RadarModule module;
    private final StepBar thickness;
    private final StepBar radius;
    private final HoloWidgetSwitch players;
    private final HoloWidgetSwitch entities;

    RadarConfigWindow(String key, Component title, RadarModule module)
    {
        super(key, title, HoloTransform.translation(28f, 0f, 0.7f), WIDTH, 2 * (BAR + GAP) + 2 * (ROW + GAP) + 4,
                1f / 400f);
        this.module = module;
        float width = WIDTH - 2 * MARGIN;
        float y = 4f;
        thickness = content().add(new StepBar(MARGIN, y, width, BAR, Component.literal("Epaisseur de mur"), 0, 1,
                RadarModule.INFINITE_THICKNESS,
                v -> v >= RadarModule.INFINITE_THICKNESS ? "∞" : v + (v > 1 ? " blocs" : " bloc"),
                v -> module.requestSetting(RadarModule.THICKNESS, v)));
        y += BAR + GAP;
        radius = content().add(new StepBar(MARGIN, y, width, BAR, Component.literal("Rayon"), RadarModule.MIN_RADIUS,
                RadarModule.RADIUS_STEP, (RadarModule.MAX_RADIUS - RadarModule.MIN_RADIUS) / RadarModule.RADIUS_STEP,
                v -> v + " blocs", v -> module.requestSetting(RadarModule.RADIUS, v)));
        y += BAR + GAP;
        players = content().add(new HoloWidgetSwitch(MARGIN, y, width, ROW, Component.literal("Joueurs"),
                (widget, on) -> module.requestSetting(RadarModule.PLAYERS, on ? 1 : 0)));
        y += ROW + GAP;
        entities = content().add(new HoloWidgetSwitch(MARGIN, y, width, ROW, Component.literal("Autres entites"),
                (widget, on) -> module.requestSetting(RadarModule.ENTITIES, on ? 1 : 0)));
    }

    /** Widgets d'apres l'etat du module (deux cotes). */
    void refresh()
    {
        thickness.setSteppedValue(module.thickness());
        radius.setSteppedValue(module.radius());
        players.setOn(module.players());
        entities.setOn(module.entities());
    }

    /** Recepteur d'une valeur entiere. */
    @FunctionalInterface
    private interface IntSink
    {
        void accept(int value);
    }

    /**
     * Barre de valeur a crans entiers : la valeur 0..1 de {@link HoloWidgetValueBar} couvre {@code steps} crans,
     * valeur = {@code min + cran * step} ; un cran de molette vaut un cran. Seul le texte change (« Rayon : 48 blocs »
     * au lieu d'un pourcentage). Meme dessin que la barre d'origine, couleurs par defaut.
     */
    private static final class StepBar extends HoloWidgetValueBar
    {
        private static final int FILL = 0xC040A0FF;
        private static final int TRACK = 0x60202040;
        private static final int BORDER = 0xFF40C0FF;

        private final Component label;
        private final int min;
        private final int step;
        private final int steps;
        private final IntFunction<String> format;
        private boolean dragging;
        private int shown = Integer.MIN_VALUE;
        private Component display = Component.empty();

        StepBar(float x, float y, float width, float height, Component label, int min, int step, int steps,
                IntFunction<String> format, IntSink onChange)
        {
            super(x, y, width, height, label, (bar, value) -> ((StepBar) bar).changed(onChange));
            this.label = label;
            this.min = min;
            this.step = step;
            this.steps = steps;
            this.format = format;
            setStep(1f / steps);
        }

        /** Valeur venue du module ; ignoree pendant un glisser (comme {@link #setValue}). */
        void setSteppedValue(int value) { setValue((float) (value - min) / (step * steps)); }

        /** Cran de molette ou relachement : recale la barre sur un cran entier et l'envoie au module. */
        private void changed(IntSink onChange)
        {
            int v = stepped();
            setSteppedValue(v);
            onChange.accept(v);
        }

        private int stepped() { return min + Math.round(value() * steps) * step; }

        @Override
        public void onClick(HoloInteraction ctx, HoloControl button)
        {
            dragging = true;
            super.onClick(ctx, button);
        }

        @Override
        public void onClickRelease(HoloInteraction ctx, HoloControl button)
        {
            dragging = false;
            super.onClickRelease(ctx, button);
        }

        @Override
        protected void draw(HoloCanvas canvas)
        {
            int v = stepped();
            if (v != shown) {
                // Recalcule seulement quand la valeur affichee change.
                shown = v;
                display = Component.empty().append(label).append(" : " + format.apply(v));
            }
            canvas.fill(0, 0, width(), height(), TRACK);
            canvas.fill(0, 0, width() * value(), height(), FILL);
            if (hovered() || dragging) canvas.fill(0, 0, width(), height(), 0x20FFFFFF);
            canvas.outline(0, 0, width(), height(), 1f, BORDER);
            canvas.text(display, (width() - canvas.textWidth(display)) / 2f, (height() - canvas.lineHeight()) / 2f + 1f,
                    0xFFFFFFFF);
        }
    }
}
