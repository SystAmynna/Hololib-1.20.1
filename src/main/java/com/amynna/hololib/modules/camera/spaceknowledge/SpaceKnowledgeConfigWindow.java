package com.amynna.hololib.modules.camera.spaceknowledge;

import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.interact.HoloInteraction;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.object.HoloVisibility;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.holo.widget.control.HoloWidgetSwitch;
import com.amynna.hololib.holo.widget.control.HoloWidgetValueBar;
import com.amynna.hololib.modules.camera.config.ConfigWindow;
import net.minecraft.network.chat.Component;

/**
 * Fenetre de configuration « Space Knowledge » de {@link SpaceKnowledgeModule} (objet du module, cree dans
 * {@code onLoad} des deux cotes ; reprise de la fenetre de Space Pilot en T33) : un interrupteur par reglage booleen
 * (sauf {@code enabled}, bascule par la roue Quick Action) et la barre de l'angle du nom au regard, affichee en degres.
 * {@code PRIVATE} (C45) : contrairement aux autres fenetres de Config, un joueur qui inspecte le proprietaire ne la
 * recoit pas.
 */
final class SpaceKnowledgeConfigWindow extends ConfigWindow
{
    private static final int WIDTH = 170;
    private static final int MARGIN = 6;
    private static final int ROW = 16;
    private static final int BAR = 14;
    private static final int GAP = 2;

    private final SpaceKnowledgeModule module;
    private final HoloWidgetSwitch orbits;
    private final HoloWidgetSwitch bodies;
    private final HoloWidgetSwitch stars;
    private final HoloWidgetSwitch names;
    private final AngleBar nameAngle;
    private final HoloWidgetSwitch distance;

    SpaceKnowledgeConfigWindow(String key, Component title, SpaceKnowledgeModule module)
    {
        super(key, title, HoloTransform.translation(28f, 0f, 0.7f), WIDTH, 5 * (ROW + GAP) + BAR + 2 * GAP + 4 + MARGIN,
                1f / 400f);
        this.module = module;
        setVisibility(HoloVisibility.PRIVATE);
        float width = WIDTH - 2 * MARGIN;
        float y = 4f;
        orbits = flagSwitch(y, "Orbites", SpaceKnowledgeModule.ORBITS);
        y += ROW + GAP;
        bodies = flagSwitch(y, "Reperage des corps", SpaceKnowledgeModule.BODIES);
        y += ROW + GAP;
        stars = flagSwitch(y, "Etoiles exterieures", SpaceKnowledgeModule.STARS);
        y += ROW + GAP;
        names = flagSwitch(y, "Nom au regard", SpaceKnowledgeModule.NAMES);
        y += ROW + 2 * GAP;
        nameAngle = content().add(new AngleBar(MARGIN, y, width, BAR, Component.literal("Angle du nom"), module));
        y += BAR + 2 * GAP;
        distance = flagSwitch(y, "Distance", SpaceKnowledgeModule.DISTANCE);
    }

    private HoloWidgetSwitch flagSwitch(float y, String label, int index)
    {
        return content().add(new HoloWidgetSwitch(MARGIN, y, WIDTH - 2 * MARGIN, ROW, Component.literal(label),
                (widget, on) -> module.requestFlag(index, on)));
    }

    /** Widgets d'apres l'etat du module (deux cotes). */
    void refresh()
    {
        orbits.setOn(module.orbits());
        bodies.setOn(module.bodies());
        stars.setOn(module.stars());
        names.setOn(module.names());
        nameAngle.setDegrees(module.nameAngle());
        distance.setOn(module.distance());
    }

    /**
     * Barre de valeur de l'angle du nom : la valeur 0..1 de {@link HoloWidgetValueBar} couvre
     * {@value SpaceKnowledgeModule#MIN_NAME_ANGLE} a {@value SpaceKnowledgeModule#MAX_NAME_ANGLE} degres, un cran de
     * molette vaut 1 degre ; seul le texte change (« Angle du nom : 5° » au lieu d'un pourcentage). Meme dessin que la
     * barre d'origine, couleurs par defaut.
     */
    private static final class AngleBar extends HoloWidgetValueBar
    {
        private static final int SPAN = SpaceKnowledgeModule.MAX_NAME_ANGLE - SpaceKnowledgeModule.MIN_NAME_ANGLE;
        private static final int FILL = 0xC040A0FF;
        private static final int TRACK = 0x60202040;
        private static final int BORDER = 0xFF40C0FF;

        private final Component label;
        private boolean dragging;
        private int shown = -1;
        private Component display = Component.empty();

        AngleBar(float x, float y, float width, float height, Component label, SpaceKnowledgeModule module)
        {
            super(x, y, width, height, label, (bar, value) -> ((AngleBar) bar).changed(module));
            this.label = label;
            setStep(1f / SPAN);
        }

        /** Valeur venue du module ; ignoree pendant un glisser (comme {@link #setValue}). */
        void setDegrees(int degrees) { setValue(toValue(degrees)); }

        /** Cran de molette ou relachement : recale la barre sur un degre entier et l'envoie au module. */
        private void changed(SpaceKnowledgeModule module)
        {
            int degrees = degrees();
            setValue(toValue(degrees));
            module.requestNameAngle(degrees);
        }

        private int degrees() { return SpaceKnowledgeModule.MIN_NAME_ANGLE + Math.round(value() * SPAN); }

        private static float toValue(int degrees)
        {
            return (float) (degrees - SpaceKnowledgeModule.MIN_NAME_ANGLE) / SPAN;
        }

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
            int degrees = degrees();
            if (degrees != shown) {
                // Recalcule seulement quand l'angle affiche change.
                shown = degrees;
                display = Component.empty().append(label).append(" : " + degrees + "°");
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
