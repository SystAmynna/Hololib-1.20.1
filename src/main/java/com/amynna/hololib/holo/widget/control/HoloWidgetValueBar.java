package com.amynna.hololib.holo.widget.control;

import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.interact.HoloInteraction;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.holo.widget.HoloWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Barre de valeur (0..1, affichee en pourcentage) : la molette la change d'un pas ({@link #setStep}, 5 % par defaut)
 * quand on la regarde ; un clic gauche la place a l'abscisse visee et, maintenu, la fait suivre le regard. L'ecouteur
 * est appele a chaque cran et au relachement du clic (pas a chaque frame du glisser). Valeur arrondie au pourcent.
 */
public class HoloWidgetValueBar extends HoloWidget implements HoloScrollable, HoloClickable, HoloHoverable
{
    private Component label;
    private BiConsumer<? super HoloWidgetValueBar, Float> onChange;
    private float value;
    private float step = 0.05f;
    private boolean hovered;
    private boolean dragging;
    private static final HoloColor HOVER = HoloColor.special(0x20);
    private static final HoloColor TEXT = HoloColor.special(0xFF);

    private HoloColor fillColor = HoloColor.secondary(0xC0).lighter(0.12f);
    private HoloColor trackColor = HoloColor.secondary(0x60).darker(0.75f);
    private HoloColor border = HoloColor.primary(0xFF);
    private Component display = Component.empty();

    public HoloWidgetValueBar(float x, float y, float width, float height, Component label,
                              BiConsumer<? super HoloWidgetValueBar, Float> onChange)
    {
        super(x, y, width, height);
        this.label = Objects.requireNonNull(label);
        this.onChange = Objects.requireNonNull(onChange);
        refresh();
    }

    public float value() { return value; }

    /** Valeur venue du module (serveur) ; ignoree pendant un glisser. */
    public HoloWidgetValueBar setValue(float value)
    {
        if (dragging) return this;
        this.value = round(value);
        refresh();
        return this;
    }

    public HoloWidgetValueBar setStep(float step)
    {
        this.step = step;
        return this;
    }

    public HoloWidgetValueBar setLabel(Component label)
    {
        this.label = Objects.requireNonNull(label);
        refresh();
        return this;
    }

    public HoloWidgetValueBar setColors(int fillColor, int trackColor, int border)
    {
        return setColors(HoloColor.fixed(fillColor), HoloColor.fixed(trackColor), HoloColor.fixed(border));
    }

    /** Remplissage, piste, bordure (par defaut : secondaire, secondaire sombre, principale). */
    public HoloWidgetValueBar setColors(HoloColor fillColor, HoloColor trackColor, HoloColor border)
    {
        this.fillColor = fillColor;
        this.trackColor = trackColor;
        this.border = border;
        return this;
    }

    @Override
    public boolean hovered() { return hovered; }

    @Override
    public void setHovered(boolean hovered) { this.hovered = hovered; }

    @Override
    public void onScrolled(HoloInteraction ctx, int steps)
    {
        change(value + steps * step);
        onChange.accept(this, value);
    }

    @Override
    public boolean clickableBy(HoloControl button) { return button == HoloControl.LEFT_CLICK; }

    @Override
    public void onClick(HoloInteraction ctx, HoloControl button)
    {
        dragging = true;
        change(ctx.x() / width());
    }

    @Override
    public void onDrag(HoloInteraction ctx, HoloControl button) { change(ctx.x() / width()); }

    @Override
    public void onClickRelease(HoloInteraction ctx, HoloControl button)
    {
        dragging = false;
        onChange.accept(this, value);
    }

    private void change(float requested)
    {
        float rounded = round(requested);
        if (rounded == value) return;
        value = rounded;
        refresh();
    }

    private static float round(float value) { return Math.round(Mth.clamp(value, 0f, 1f) * 100f) / 100f; }

    /** Texte affiche, recalcule seulement quand la valeur ou le libelle change. */
    private void refresh()
    {
        display = Component.empty().append(label).append(" : " + Math.round(value * 100f) + " %");
    }

    @Override
    protected void draw(HoloCanvas canvas)
    {
        canvas.fill(0, 0, width(), height(), canvas.color(trackColor));
        canvas.fill(0, 0, width() * value, height(), canvas.color(fillColor));
        if (hovered || dragging) canvas.fill(0, 0, width(), height(), canvas.color(HOVER));
        canvas.outline(0, 0, width(), height(), 1f, canvas.color(border));
        canvas.text(display, (width() - canvas.textWidth(display)) / 2f, (height() - canvas.lineHeight()) / 2f + 1f,
                canvas.color(TEXT));
    }
}
