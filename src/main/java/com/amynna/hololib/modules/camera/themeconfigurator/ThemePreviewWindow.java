package com.amynna.hololib.modules.camera.themeconfigurator;

import com.amynna.hololib.holo.edit.HoloDisplay;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.object.HoloVisibility;
import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.theme.HoloTheme;
import com.amynna.hololib.holo.theme.HoloThemeColor;
import com.amynna.hololib.holo.theme.HoloThemes;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.holo.widget.HoloLabel;
import com.amynna.hololib.holo.widget.HoloPanel;
import com.amynna.hololib.holo.widget.HoloWidget;
import com.amynna.hololib.holo.widget.control.HoloWidgetButton;
import com.amynna.hololib.holo.widget.control.HoloWidgetSwitch;
import com.amynna.hololib.holo.widget.control.HoloWidgetTextBox;
import com.amynna.hololib.holo.widget.control.HoloWidgetValueBar;
import com.amynna.hololib.holo.window.HoloWindow;
import com.amynna.hololib.modules.camera.config.ConfigWindow;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Fenetre d'apercu des themes : widgets d'exemple dessines avec le brouillon du configurateur
 * ({@link #themeOverride()}), qui suit les reglages en direct. Ouverte avec la fenetre de configuration
 * ({@link #shown()} : son etat d'ouverture, synchronise) et placee a sa droite par le client du proprietaire
 * ({@link #follow()}, chaque tick ; le placement est envoye quand il change, pour les observateurs).
 * <p>
 * Pas {@code HoloRelativeToOpen} : le focus ne la place pas lui-meme, elle suit la configuration.
 */
final class ThemePreviewWindow extends HoloWindow
{
    private static final int WIDTH = 150;
    private static final int HEIGHT = 150;
    private static final int BAR = ConfigWindow.BAR_HEIGHT;
    private static final int MARGIN = 6;
    private static final int ROW = 14;
    /** Ecart avec la fenetre de configuration, en blocs (a distance et echelle 1). */
    private static final float GAP = 0.04f;

    private final ThemeConfiguratorBase module;
    private final ConfigWindow config;

    ThemePreviewWindow(String key, ThemeConfiguratorBase module, ConfigWindow config)
    {
        super(key, new HoloAnchor.Local(false), HoloTransform.translation(0f, 0f, 0.75f), WIDTH, HEIGHT, 1f / 400f);
        this.module = module;
        this.config = config;
        setThemeSource(config.themeSource()).setStaffTheme(config.staffTheme());
        setDisplay(HoloDisplay.FOCUS);
        setVisibility(HoloVisibility.PUBLIC);
        HoloPanel root = root();
        root.setPattern(0xC0).setBorder(HoloColor.primary(0xFF), 1f);
        root.add(new HoloPanel(0, 0, WIDTH, BAR).setBackground(HoloColor.primary(0x40)));
        root.add(new HoloLabel(4, 2, WIDTH - 24, BAR - 4, Component.literal("Apercu"))
                .setColor(HoloColor.primary(0xFF)));
        root.add(new Cross(WIDTH - 14, 2, 12, 12));

        float width = WIDTH - 2 * MARGIN;
        float y = BAR + MARGIN;
        root.add(new HoloWidgetButton(MARGIN, y, width, ROW, Component.literal("Bouton"), button -> {}));
        y += ROW + 4;
        float chip = (width - 4) / 2f;
        HoloThemeColor[] roles = HoloThemeColor.values();
        for (int i = 0; i < roles.length; i++) {
            float cx = MARGIN + (i % 2) * (chip + 4);
            float cy = y + (i / 2) * (ROW + 3);
            HoloThemeColor role = roles[i];
            root.add(new HoloWidgetButton(cx, cy, chip, ROW, role.label(), button -> {})
                    .setColors(HoloColor.of(role, 0x60), HoloColor.of(role, 0xFF), HoloColor.special(0xFF)));
        }
        y += 3 * (ROW + 3) + 1;
        HoloWidgetSwitch toggle = root.add(new HoloWidgetSwitch(MARGIN, y, width, ROW,
                Component.literal("Interrupteur"), (widget, on) -> {}));
        toggle.setOn(true);
        y += ROW + 4;
        root.add(new HoloWidgetValueBar(MARGIN, y, width, ROW - 2, Component.literal("Valeur"), (bar, v) -> {}))
                .setValue(0.6f);
        y += ROW + 2;
        root.add(new HoloWidgetTextBox(MARGIN, y, width, ROW, 24, false)).setText("Texte");
    }

    @Override
    public HoloTheme themeOverride() { return module.draft(); }

    @Override
    public boolean shown() { return config.opened() && (!module.admin() || HoloThemes.localOp()); }

    /** Client du proprietaire : se place a droite de la fenetre de configuration, a la meme distance. */
    void follow()
    {
        if (!config.opened()) return;
        HoloTransform source = config.transform();
        float scale = source.scaleX();
        float distance = Math.max(0.05f, source.z());
        // Fenetres planes face a l'oeil : on additionne les demi-largeurs angulaires (et non les largeurs, sinon les
        // bords se chevauchent), plus l'ecart.
        double angle = Math.atan2(config.halfWidth() * scale, distance) + Math.atan2(GAP * scale, distance)
                + Math.atan2(halfWidth() * scale, distance);
        float yaw = Mth.wrapDegrees(source.x() + (float) Math.toDegrees(angle));
        HoloTransform t = transform();
        if (Math.abs(Mth.wrapDegrees(t.x() - yaw)) < 1e-3f && Math.abs(t.y() - source.y()) < 1e-3f
                && Math.abs(t.z() - distance) < 1e-4f && t.yaw() == source.yaw() && t.pitch() == source.pitch()
                && t.roll() == source.roll() && t.scaleX() == scale) {
            return;
        }
        t.setTranslation(yaw, source.y(), distance);
        t.setRotation(source.yaw(), source.pitch(), source.roll());
        t.setScale(source.scaleX(), source.scaleY(), source.scaleZ());
        requestTransform();
    }

    /** Croix de fermeture factice (couleur critique). */
    private static final class Cross extends HoloWidget
    {
        private static final HoloColor COLOR = HoloColor.critical(0xFF).lighter(0.3f);

        Cross(float x, float y, float width, float height) { super(x, y, width, height); }

        @Override
        protected void draw(HoloCanvas canvas)
        {
            int color = canvas.color(COLOR);
            canvas.line(3f, 3f, width() - 3f, height() - 3f, 1.2f, color);
            canvas.line(width() - 3f, 3f, 3f, height() - 3f, 1.2f, color);
        }
    }
}
