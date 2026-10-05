package com.amynna.hololib.holo.widget.control;

import com.amynna.hololib.holo.interact.HoloInteraction;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.ObjIntConsumer;

/**
 * Zone de survol qui prend aussi la molette quand on la regarde : chaque cran est transmis a un ecouteur (+ vers le
 * haut, - vers le bas). Le detail ({@link #setDetail}) sert typiquement a afficher la valeur pilotee.
 */
public class HoloWidgetScrollZone extends HoloWidgetHoverZone implements HoloScrollable
{
    private ObjIntConsumer<? super HoloWidgetScrollZone> onScroll;

    public HoloWidgetScrollZone(float x, float y, float width, float height, Component idleText, Component hoverText,
                                ObjIntConsumer<? super HoloWidgetScrollZone> onScroll)
    {
        super(x, y, width, height, idleText, hoverText);
        this.onScroll = Objects.requireNonNull(onScroll);
        setColors(0x40A040, 0xFF60E060, 0xFFA0FFA0);
    }

    public HoloWidgetScrollZone setOnScroll(ObjIntConsumer<? super HoloWidgetScrollZone> onScroll)
    {
        this.onScroll = Objects.requireNonNull(onScroll);
        return this;
    }

    @Override
    public void onScrolled(HoloInteraction ctx, int steps) { onScroll.accept(this, steps); }
}
