package com.amynna.hololib.modules.camera.quickaction;

import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.edit.HoloDisplay;
import com.amynna.hololib.holo.edit.HoloRelativeToOpen;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.object.HoloVisibility;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.holo.widget.HoloWidget;
import com.amynna.hololib.holo.window.HoloWindow;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Roue d'actions de Quick Action : un {@link QuickActionWheelSector} par action active autour d'une zone morte, et le
 * nom de l'action visee au centre. La roue entiere (zone morte et secteurs) est dessinee d'un coup par un seul widget,
 * dans une seule couche du canvas : elle reste plate et ronde. Placee au centre du regard a chaque ouverture du focus
 * ({@link HoloRelativeToOpen}, {@code Local} fixe : on la parcourt en tournant la camera) ; sans outil d'edition, elle reste au centre. Affichage
 * {@code FOCUS} ; la fenetre prend les clics (zone morte, fond) pour ne rien casser derriere.
 * <p>
 * Ouverte ({@link #shown()}) ou non : {@code HoloObject.opened()}, decide par {@code QuickActionController} chez le
 * proprietaire et transmis aux observateurs (roue publique : un joueur qui inspecte le proprietaire la voit).
 */
public final class QuickActionWheelWindow extends HoloWindow implements HoloRelativeToOpen
{
    public static final int MIN_RADIUS = 40;
    public static final int MAX_RADIUS = 110;
    private static final int SIZE = 2 * MAX_RADIUS + 4;
    private static final float PIXEL_SCALE = 1f / 400f;
    private static final float DISTANCE = 0.8f;
    /** Au-dela de ce multiple du rayon angulaire de la roue, le regard l'a ignoree. */
    private static final float IGNORE_FACTOR = 1.25f;

    private final Consumer<QuickAction> onSelect;
    private final List<QuickActionWheelSector> sectors = new ArrayList<>();
    private final Wheel wheelShape;
    private float radius = (MIN_RADIUS + MAX_RADIUS) / 2f;
    private float deadRadius = radius / 3f;

    public QuickActionWheelWindow(String key, Consumer<QuickAction> onSelect)
    {
        super(key, new HoloAnchor.Local(false), HoloTransform.translation(0f, 0f, DISTANCE), SIZE, SIZE,
                PIXEL_SCALE);
        this.onSelect = onSelect;
        setDisplay(HoloDisplay.FOCUS);
        setVisibility(HoloVisibility.PUBLIC);
        setOpened(false);
        setInterceptedInputs(HoloControl.LEFT_CLICK, HoloControl.RIGHT_CLICK);
        wheelShape = root().add(new Wheel());
    }

    /** Remplace les secteurs : un par action, dans l'ordre, le premier en haut. */
    public void setActions(List<QuickAction> actions)
    {
        for (QuickActionWheelSector sector : sectors) root().remove(sector);
        sectors.clear();
        for (QuickAction action : actions) sectors.add(root().add(new QuickActionWheelSector(SIZE, action, onSelect)));
        // Le dessin de la roue passe apres les secteurs (qui ne dessinent rien).
        root().add(wheelShape);
        layout();
    }

    public boolean hasSectors() { return !sectors.isEmpty(); }

    /** Rayon de la roue et de la zone morte, en pixels (bornes). */
    public void setGeometry(float radius, float deadRadius)
    {
        radius = Mth.clamp(radius, MIN_RADIUS, MAX_RADIUS);
        deadRadius = Mth.clamp(deadRadius, 0f, radius - 4f);
        if (radius == this.radius && deadRadius == this.deadRadius) return;
        this.radius = radius;
        this.deadRadius = deadRadius;
        layout();
    }

    private void layout()
    {
        int count = sectors.size();
        float span = count > 0 ? Mth.TWO_PI / count : Mth.TWO_PI;
        for (int i = 0; i < count; i++) sectors.get(i).setGeometry(deadRadius, radius, i * span - span / 2f, span);
    }

    /** Action du secteur vise, ou {@code null}. */
    public QuickAction hoveredAction()
    {
        for (int i = 0; i < sectors.size(); i++) {
            if (sectors.get(i).hovered()) return sectors.get(i).action();
        }
        return null;
    }

    /** Vrai si la roue est ouverte. */
    public boolean isOpen() { return opened(); }

    /** Cote client (proprietaire) : ouvre ou ferme la roue, sans la deplacer. */
    public void setOpen(boolean open) { requestOpened(open); }

    @Override
    public boolean shown() { return opened(); }

    /**
     * Vrai si le regard {@code (yRot, xRot)} (degres) s'est eloigne du centre de la roue de plus de
     * {@value #IGNORE_FACTOR} fois son rayon angulaire. La roue doit etre en {@code Local} fixe (focus ouvert).
     */
    public boolean gazeBeyond(float yRot, float xRot)
    {
        HoloTransform transform = transform();
        if (!(transform.z() > 0f)) return false;
        double angle = angleBetween(yRot, xRot, transform.x(), transform.y());
        double reach = Math.atan2(radius * pixelScale() * transform.maxScale(), transform.z());
        return angle > reach * IGNORE_FACTOR;
    }

    /** Angle en radians entre deux directions (yaw, pitch en degres, conventions de Minecraft). */
    private static double angleBetween(float yaw0, float pitch0, float yaw1, float pitch1)
    {
        double y0 = Math.toRadians(yaw0);
        double p0 = Math.toRadians(pitch0);
        double y1 = Math.toRadians(yaw1);
        double p1 = Math.toRadians(pitch1);
        double dot = Math.sin(y0) * Math.cos(p0) * Math.sin(y1) * Math.cos(p1) + Math.sin(p0) * Math.sin(p1)
                + Math.cos(y0) * Math.cos(p0) * Math.cos(y1) * Math.cos(p1);
        return Math.acos(Mth.clamp(dot, -1.0, 1.0));
    }

    /**
     * Dessin de la roue : zone morte et couronnes de tous les secteurs dans une couche, puis leurs icones, puis le nom
     * de l'action visee. Jamais vise (les secteurs le sont).
     */
    private final class Wheel extends HoloWidget
    {
        private static final int SEGMENTS = 48;
        private static final HoloColor DISK = HoloColor.secondary(0x60).darker(0.84f);
        private static final HoloColor RING = HoloColor.primary(0x80);
        private static final int LABEL = 0xC0000000;
        private static final HoloColor TEXT = HoloColor.special(0xFF);

        Wheel() { super(0, 0, SIZE, SIZE); }

        @Override
        public boolean contains(float px, float py) { return false; }

        @Override
        protected void draw(HoloCanvas canvas)
        {
            float c = SIZE / 2f;
            float r = deadRadius;
            canvas.beginLayer();
            if (r > 1f) {
                float rim = r - 1f;
                int disk = canvas.color(DISK);
                int ring = canvas.color(RING);
                for (int i = 0; i < SEGMENTS; i++) {
                    float a0 = i * Mth.TWO_PI / SEGMENTS;
                    float a1 = (i + 1) * Mth.TWO_PI / SEGMENTS;
                    float s0 = Mth.sin(a0);
                    float c0 = Mth.cos(a0);
                    float s1 = Mth.sin(a1);
                    float c1 = Mth.cos(a1);
                    canvas.quad(c, c, c + rim * s0, c - rim * c0, c + rim * s1, c - rim * c1, c + rim * s1,
                            c - rim * c1, disk);
                    canvas.quad(c + rim * s0, c - rim * c0, c + r * s0, c - r * c0, c + r * s1, c - r * c1,
                            c + rim * s1, c - rim * c1, ring);
                }
            }
            for (int i = 0; i < sectors.size(); i++) sectors.get(i).drawShape(canvas);
            canvas.endLayer();
            for (int i = 0; i < sectors.size(); i++) sectors.get(i).drawIcon(canvas);
            QuickAction hovered = hoveredAction();
            if (hovered == null) return;
            Component name = hovered.name();
            float width = canvas.textWidth(name) + 6f;
            float height = canvas.lineHeight() + 4f;
            canvas.fill(c - width / 2f, c - height / 2f, width, height, LABEL);
            canvas.text(name, c - width / 2f + 3f, c - height / 2f + 2f + 1f, canvas.color(TEXT));
        }
    }
}
