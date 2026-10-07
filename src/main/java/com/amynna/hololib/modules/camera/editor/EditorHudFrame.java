package com.amynna.hololib.modules.camera.editor;

import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.edit.HoloEditTranslation;
import com.amynna.hololib.holo.interact.HoloInteraction;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.holo.widget.HoloWidget;
import com.amynna.hololib.holo.widget.control.HoloClickable;
import com.amynna.hololib.holo.widget.control.HoloScrollable;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * Cadre de la fenetre de l'editor : l'ecran en miniature, ou sont dessines les objets HUD pendant la session. Clic
 * gauche : selectionne l'objet HUD vise (ses boutons d'edition apparaissent) et, s'il est
 * {@link HoloEditTranslation}, le fait glisser ; molette : outil actif sur l'objet selectionne. Le dessin des objets
 * et la recherche de l'objet vise sont cote client ({@link Backend}, installe au setup client).
 */
public class EditorHudFrame extends HoloWidget implements HoloClickable, HoloScrollable
{
    /** Partie client du cadre. */
    @ApiStatus.Internal
    public interface Backend
    {
        /** Taille de l'ecran en pixels GUI (repere des objets HUD). */
        float guiWidth();

        float guiHeight();

        /** Objet HUD editable le plus en avant sous ce point (pixels GUI), ou {@code null}. */
        HoloObject objectAt(float guiX, float guiY);

        HoloObject selected();

        void select(HoloObject object);

        /** Debut ({@code object}) ou fin ({@code null}) d'un glisser. */
        void grab(HoloObject object);

        void scroll(HoloObject object, int steps);
    }

    private static Backend backend;

    private HoloObject grabbed;
    private float startX;
    private float startY;
    private float grabX;
    private float grabY;

    public EditorHudFrame(float x, float y, float width, float height)
    {
        super(x, y, width, height);
    }

    @ApiStatus.Internal
    public static void install(Backend backend) { EditorHudFrame.backend = Objects.requireNonNull(backend); }

    /** Pixels du cadre par pixel GUI. */
    public float scale() { return backend != null ? width() / Math.max(1f, backend.guiWidth()) : 1f; }

    @Override
    public boolean clickableBy(HoloControl button) { return button == HoloControl.LEFT_CLICK; }

    @Override
    public void onClick(HoloInteraction ctx, HoloControl button)
    {
        if (backend == null) return;
        float k = scale();
        HoloObject object = backend.objectAt(ctx.x() / k, ctx.y() / k);
        backend.select(object);
        if (!(object instanceof HoloEditTranslation)) return;
        grabbed = object;
        startX = object.transform().x();
        startY = object.transform().y();
        grabX = ctx.x();
        grabY = ctx.y();
        backend.grab(object);
    }

    @Override
    public void onDrag(HoloInteraction ctx, HoloControl button)
    {
        if (grabbed == null) return;
        float k = scale();
        HoloTransform t = grabbed.transform();
        float max = HoloAnchor.Hud.MAX_OFFSET;
        t.setTranslation(Mth.clamp(startX + (ctx.x() - grabX) / k, -max, max),
                Mth.clamp(startY + (ctx.y() - grabY) / k, -max, max), t.z());
    }

    @Override
    public void onClickRelease(HoloInteraction ctx, HoloControl button)
    {
        if (grabbed == null) return;
        grabbed = null;
        if (backend != null) backend.grab(null);
    }

    @Override
    public void onScrolled(HoloInteraction ctx, int steps)
    {
        if (backend != null && backend.selected() != null) backend.scroll(backend.selected(), steps);
    }

    @Override
    protected void draw(HoloCanvas canvas)
    {
        canvas.fill(0, 0, width(), height(), 0xA0000000);
        canvas.outline(0, 0, width(), height(), 1f, 0xFF8090A0);
    }
}
