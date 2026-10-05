package com.amynna.hololib.demo;

import com.amynna.hololib.holo.capture.HoloCapture;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.holo.widget.control.HoloWidgetKeyBox;
import net.minecraft.util.Mth;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * {@link HoloWidgetKeyBox} multijoueur : un point par joueur. Les points sont l'etat du module, cote serveur
 * ({@link #syncPoints}) : un autre joueur qui a acces a la fenetre et la clique fait apparaitre le sien. Le point du
 * joueur local est celui de la boite, predit localement pendant le controle ; sa position est envoyee au serveur
 * ({@code onSend}) au debut et a la fin du controle, et toutes les {@link #SEND_INTERVAL} ticks s'il a bouge. Les
 * points des autres sont lisses vers la derniere position recue.
 */
final class DemoPad extends HoloWidgetKeyBox
{
    private static final int SEND_INTERVAL = 2;
    /** Part de l'ecart rattrapee a chaque tick par un point distant. */
    private static final float SMOOTHING = 0.5f;

    /** Point recu du serveur : cible et position affichee (lissee). */
    private static final class Point
    {
        private float targetX;
        private float targetY;
        private float x;
        private float y;
        private float previousX;
        private float previousY;
        private final int color;

        private Point(UUID player, float x, float y)
        {
            this.targetX = this.x = this.previousX = x;
            this.targetY = this.y = this.previousY = y;
            this.color = 0xFF000000 | Mth.hsvToRgb((player.hashCode() & 0xFFFF) / 65536f, 0.6f, 1f);
        }
    }

    private final BiConsumer<Float, Float> onSend;
    private final Map<UUID, Point> points = new LinkedHashMap<>();
    private UUID self;
    private boolean moved;
    private int sinceSend;

    DemoPad(float x, float y, float width, float height, BiConsumer<Float, Float> onSend)
    {
        super(x, y, width, height);
        this.onSend = onSend;
    }

    /** Points connus du serveur (joueur -> {@code x, y}) ; pendant le controle, le point local reste predit. */
    void syncPoints(Map<UUID, float[]> received)
    {
        points.keySet().retainAll(received.keySet());
        for (Map.Entry<UUID, float[]> entry : received.entrySet()) {
            float[] position = entry.getValue();
            Point point = points.get(entry.getKey());
            if (point == null) {
                points.put(entry.getKey(), new Point(entry.getKey(), position[0], position[1]));
            } else {
                point.targetX = position[0];
                point.targetY = position[1];
            }
        }
    }

    @Override
    public void onCaptureStart(HoloCapture ctx)
    {
        self = ctx.player().getUUID();
        Point mine = points.get(self);
        if (mine != null) setPoint(mine.targetX, mine.targetY);
        else setPoint(width() / 2f, height() / 2f);
        // Fait apparaitre le point (ou le reprend) cote serveur.
        send();
    }

    @Override
    public void onCaptureEnd(HoloCapture ctx)
    {
        super.onCaptureEnd(ctx);
        send();
        Point mine = points.get(self);
        if (mine != null) {
            // Pas de saut en attendant l'echo du serveur.
            mine.targetX = mine.x = mine.previousX = pointX();
            mine.targetY = mine.y = mine.previousY = pointY();
        }
    }

    @Override
    protected void onPointMoved() { moved = true; }

    private void send()
    {
        onSend.accept(pointX(), pointY());
        moved = false;
        sinceSend = 0;
    }

    @Override
    public void tick()
    {
        super.tick();
        for (Point point : points.values()) {
            point.previousX = point.x;
            point.previousY = point.y;
            point.x += (point.targetX - point.x) * SMOOTHING;
            point.y += (point.targetY - point.y) * SMOOTHING;
        }
        if (controlling() && ++sinceSend >= SEND_INTERVAL && moved) send();
    }

    @Override
    protected void drawContent(HoloCanvas canvas, float partialTick, boolean controlling)
    {
        for (Map.Entry<UUID, Point> entry : points.entrySet()) {
            boolean mine = entry.getKey().equals(self);
            if (mine && controlling) continue;
            Point point = entry.getValue();
            drawDot(canvas, point.previousX + (point.x - point.previousX) * partialTick,
                    point.previousY + (point.y - point.previousY) * partialTick, point.color, mine);
        }
        if (controlling) {
            Point mine = points.get(self);
            super.drawContent(canvas, partialTick, true);
            // Couleur du joueur par-dessus le point par defaut, entoure de blanc.
            if (mine != null) {
                drawDot(canvas, interpolatedX(partialTick), interpolatedY(partialTick), mine.color, true);
            }
        }
    }
}
