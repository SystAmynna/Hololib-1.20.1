package com.amynna.hololib.holo.widget.control;

import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.interact.HoloInteraction;
import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.theme.HoloColors;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.holo.widget.HoloPanel;
import com.amynna.hololib.holo.widget.HoloWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Selecteur de couleur {@code 0xRRGGBB} (sans alpha) : carre saturation (horizontale) / valeur (verticale) de la
 * teinte courante, barre de teinte verticale, pastille et champ {@code #rrggbb}. Carre et barre se reglent au clic
 * gauche maintenu (le regard projete suit, {@code onDrag}) ; le champ se valide par Entree ou Echap.
 * <p>
 * Prise genereuse : les controles sont entoures d'une marge de {@value #PAD} pixels, et un clic sur le panneau hors
 * des controles (marge, ecart entre le carre et la barre) prend le controle le plus proche, a la position bornee.
 * <p>
 * Deux ecouteurs : {@link #setOnChange} a chaque mouvement (apercu local), {@link #setOnCommit} au relachement ou a la
 * validation du champ (envoi au serveur). La couleur venue du module ({@link #setRgb}) est ignoree pendant un
 * glisser. Etat en teinte / saturation / valeur : la teinte survit a une couleur grise.
 */
public class HoloWidgetColorPicker extends HoloPanel implements HoloClickable
{
    /** Marge autour des controles, prise par le panneau (clic redirige vers le controle le plus proche). */
    private static final float PAD = 4f;
    private static final float GAP = 4f;
    private static final float HUE_WIDTH = 12f;
    private static final float ROW = 14f;
    private static final int[] HUES = {0xFF0000, 0xFFFF00, 0x00FF00, 0x00FFFF, 0x0000FF, 0xFF00FF, 0xFF0000};
    private static final HoloColor FRAME = HoloColor.special(0xFF).darker(0.5f);
    private static final HoloColor CURSOR = HoloColor.special(0xFF);
    private static final HoloColor CURSOR_SHADOW = HoloColor.fixed(0xFF000000);

    private final SatValSquare square;
    private final HueBar hueBar;
    private final Swatch swatch;
    private final HoloWidgetTextBox hex;
    private final float[] hsv = new float[3];
    private BiConsumer<? super HoloWidgetColorPicker, Integer> onChange = (picker, rgb) -> {};
    private BiConsumer<? super HoloWidgetColorPicker, Integer> onCommit = (picker, rgb) -> {};
    private int rgb;
    /** Controle pris par un clic sur le panneau (marge), ou {@code null}. */
    private Picker delegate;

    public HoloWidgetColorPicker(float x, float y, float width, float height)
    {
        super(x, y, width, height);
        float side = Math.max(8f, Math.min(width - 2f * PAD - HUE_WIDTH - GAP, height - 2f * PAD - ROW - GAP));
        square = add(new SatValSquare(PAD, PAD, side, side));
        hueBar = add(new HueBar(PAD + side + GAP, PAD, HUE_WIDTH, side));
        float rowY = PAD + side + GAP;
        swatch = add(new Swatch(PAD, rowY, ROW, ROW));
        hex = add(new HoloWidgetTextBox(PAD + ROW + GAP, rowY, Math.max(20f, side + GAP + HUE_WIDTH - ROW - GAP), ROW,
                7, false));
        hex.setPlaceholder(Component.literal("#rrggbb"));
        hex.setOnSubmit((box, text) -> submitHex(text));
        setRgb(0xFFFFFF);
    }

    /** Couleur courante {@code 0xRRGGBB}. */
    public int rgb() { return rgb; }

    /** Couleur venue du module ; ignoree pendant un glisser. */
    public HoloWidgetColorPicker setRgb(int rgb)
    {
        if (square.dragging || hueBar.dragging) return this;
        rgb &= 0xFFFFFF;
        if (rgb == this.rgb && hex.text().length() == 7) return this;
        float hue = hsv[0];
        HoloColors.rgbToHsv(rgb, hsv);
        // Gris : la teinte n'est pas definie, on garde la precedente.
        if (hsv[1] <= 0f || hsv[2] <= 0f) hsv[0] = hue;
        this.rgb = rgb;
        hex.setText(HoloColors.toHex(rgb));
        return this;
    }

    /** Ecouteur appele a chaque mouvement (apercu). */
    public HoloWidgetColorPicker setOnChange(BiConsumer<? super HoloWidgetColorPicker, Integer> onChange)
    {
        this.onChange = Objects.requireNonNull(onChange);
        return this;
    }

    /** Ecouteur appele au relachement ou a la validation du champ (couleur definitive). */
    public HoloWidgetColorPicker setOnCommit(BiConsumer<? super HoloWidgetColorPicker, Integer> onCommit)
    {
        this.onCommit = Objects.requireNonNull(onCommit);
        return this;
    }

    // ---------------------------------------------------------------- clic sur la marge du panneau

    @Override
    public boolean clickableBy(HoloControl button) { return button == HoloControl.LEFT_CLICK; }

    /** Clic hors des controles : le carre a gauche de l'ecart, la barre de teinte a droite (sous eux : rien). */
    @Override
    public void onClick(HoloInteraction ctx, HoloControl button)
    {
        float x = ctx.x();
        float y = ctx.y();
        if (y > square.y() + square.height() + GAP / 2f) return;
        delegate = x < hueBar.x() - GAP / 2f ? square : hueBar;
        delegate.begin(x - delegate.x(), y - delegate.y());
    }

    @Override
    public void onDrag(HoloInteraction ctx, HoloControl button)
    {
        if (delegate != null) delegate.move(ctx.x() - delegate.x(), ctx.y() - delegate.y());
    }

    @Override
    public void onClickRelease(HoloInteraction ctx, HoloControl button)
    {
        if (delegate == null) return;
        delegate.end();
        delegate = null;
    }

    private void updateFromHsv()
    {
        int next = HoloColors.hsvToRgb(hsv[0], hsv[1], hsv[2]);
        if (next == rgb) return;
        rgb = next;
        hex.setText(HoloColors.toHex(rgb));
        onChange.accept(this, rgb);
    }

    private void submitHex(String text)
    {
        int parsed = HoloColors.parseHex(text);
        if (parsed < 0) {
            hex.setText(HoloColors.toHex(rgb));
            return;
        }
        setRgb(parsed);
        onChange.accept(this, rgb);
        onCommit.accept(this, rgb);
    }

    /**
     * Controle reglable au glisser (carre ou barre) : pris directement (clic sur lui) ou par le panneau (clic sur la
     * marge). Coordonnees dans son repere, bornees par {@link #pick}.
     */
    private abstract class Picker extends HoloWidget implements HoloClickable
    {
        boolean dragging;

        Picker(float x, float y, float width, float height) { super(x, y, width, height); }

        abstract void pick(float x, float y);

        void begin(float x, float y)
        {
            dragging = true;
            pick(x, y);
        }

        void move(float x, float y)
        {
            if (dragging) pick(x, y);
        }

        void end()
        {
            if (!dragging) return;
            dragging = false;
            onCommit.accept(HoloWidgetColorPicker.this, rgb);
        }

        @Override
        public boolean clickableBy(HoloControl button) { return button == HoloControl.LEFT_CLICK; }

        @Override
        public void onClick(HoloInteraction ctx, HoloControl button) { begin(ctx.x(), ctx.y()); }

        @Override
        public void onDrag(HoloInteraction ctx, HoloControl button) { move(ctx.x(), ctx.y()); }

        @Override
        public void onClickRelease(HoloInteraction ctx, HoloControl button) { end(); }
    }

    /** Carre saturation / valeur de la teinte courante. */
    private final class SatValSquare extends Picker
    {
        SatValSquare(float x, float y, float width, float height) { super(x, y, width, height); }

        @Override
        void pick(float x, float y)
        {
            hsv[1] = Mth.clamp(x / width(), 0f, 1f);
            hsv[2] = 1f - Mth.clamp(y / height(), 0f, 1f);
            updateFromHsv();
        }

        @Override
        protected void draw(HoloCanvas canvas)
        {
            int hue = 0xFF000000 | HoloColors.hsvToRgb(hsv[0], 1f, 1f);
            canvas.gradient(0, 0, width(), height(), 0xFFFFFFFF, hue, hue, 0xFFFFFFFF);
            canvas.gradient(0, 0, width(), height(), 0, 0, 0xFF000000, 0xFF000000);
            canvas.outline(0, 0, width(), height(), 1f, canvas.color(FRAME));
            float cx = hsv[1] * width();
            float cy = (1f - hsv[2]) * height();
            canvas.outline(cx - 3f, cy - 3f, 6f, 6f, 1f, canvas.color(CURSOR_SHADOW));
            canvas.outline(cx - 2f, cy - 2f, 4f, 4f, 1f, canvas.color(CURSOR));
        }
    }

    /** Barre de teinte verticale (rouge en haut et en bas). */
    private final class HueBar extends Picker
    {
        HueBar(float x, float y, float width, float height) { super(x, y, width, height); }

        @Override
        void pick(float x, float y)
        {
            // Teinte 1 = teinte 0 : on reste juste sous 1 pour garder le curseur en bas.
            hsv[0] = Mth.clamp(y / height(), 0f, 0.9999f);
            updateFromHsv();
        }

        @Override
        protected void draw(HoloCanvas canvas)
        {
            float segment = height() / (HUES.length - 1);
            canvas.beginLayer();
            for (int i = 0; i + 1 < HUES.length; i++) {
                int top = 0xFF000000 | HUES[i];
                int bottom = 0xFF000000 | HUES[i + 1];
                canvas.gradient(0, i * segment, width(), segment, top, top, bottom, bottom);
            }
            canvas.endLayer();
            canvas.outline(0, 0, width(), height(), 1f, canvas.color(FRAME));
            float cy = hsv[0] * height();
            canvas.fill(-1f, cy - 1.5f, width() + 2f, 3f, canvas.color(CURSOR_SHADOW));
            canvas.fill(-1f, cy - 0.5f, width() + 2f, 1f, canvas.color(CURSOR));
        }
    }

    /** Pastille de la couleur courante. */
    private final class Swatch extends HoloWidget
    {
        Swatch(float x, float y, float width, float height) { super(x, y, width, height); }

        @Override
        protected void draw(HoloCanvas canvas)
        {
            canvas.fill(0, 0, width(), height(), 0xFF000000 | rgb);
            canvas.outline(0, 0, width(), height(), 1f, canvas.color(FRAME));
        }
    }
}
