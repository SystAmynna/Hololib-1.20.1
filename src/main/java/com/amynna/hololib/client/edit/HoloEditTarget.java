package com.amynna.hololib.client.edit;

import com.amynna.hololib.client.render.holo.HoloAttachment;
import com.amynna.hololib.client.render.holo.HoloRouting;
import com.amynna.hololib.holo.edit.HoloEditRules;
import com.amynna.hololib.holo.edit.HoloEditVisibility;
import com.amynna.hololib.holo.object.HoloAccess;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.window.HoloWindow;

/**
 * Objet en cours d'edition et ses boutons : panneau des outils a cote ({@link HoloEditPanel}) et oeil de l'affichage
 * au-dessus, deux overlays clients ({@link HoloAttachment}) hors de la silhouette de l'objet, orientes face au joueur
 * a leur creation (ouverture de la session, objet devenu editable) puis fixes, re-places apres chaque transformation
 * ({@link #replace()}).
 * Pour un objet HUD, les boutons sont poses a gauche de l'hote du cadre HUD. Garde aussi les reglages de l'outil echelle (synchronise, axe choisi).
 */
final class HoloEditTarget
{
    private final HoloObject object;
    private final boolean hud;
    private final HoloEditPanel panel;
    private final HoloAttachment panelAttachment;
    private final HoloWindow eye;
    private final HoloAttachment eyeAttachment;
    private boolean scaleSync = true;
    private int scaleAxis;

    private HoloEditTarget(HoloObject object, HoloObject host, boolean hud)
    {
        this.object = object;
        this.hud = hud;
        boolean fixOrDynamic = !hud && object.openOffset() == null
                && HoloEditRules.fixOrDynamic(object, object.anchor(), HoloEditSession.playerScope(object));
        this.panel = new HoloEditPanel(this, fixOrDynamic);
        overlay(panel);
        this.panelAttachment = new HoloAttachment(panel, host,
                hud ? HoloAttachment.Side.LEFT : HoloAttachment.Side.RIGHT);
        if (object instanceof HoloEditVisibility) {
            eye = new HoloWindow("edit_eye", new HoloAnchor.Local(false), new HoloTransform(), HoloEditEyeButton.WIDTH,
                    HoloEditEyeButton.HEIGHT, 1f);
            eye.root().add(new HoloEditEyeButton(object));
            overlay(eye);
            eyeAttachment = new HoloAttachment(eye, host,
                    hud ? HoloAttachment.Side.TOP_LEFT : HoloAttachment.Side.TOP_RIGHT);
        } else {
            eye = null;
            eyeAttachment = null;
        }
    }

    static HoloEditTarget create(HoloObject object, HoloObject host, boolean hud)
    {
        return new HoloEditTarget(object, host, hud);
    }

    private static void overlay(HoloWindow window)
    {
        window.setAccess(HoloAccess.EVERYONE);
    }

    HoloObject object() { return object; }

    /** Objet HUD edite depuis le cadre de l'editor. */
    boolean hud() { return hud; }

    boolean scaleSync() { return scaleSync; }

    void setScaleSync(boolean sync) { this.scaleSync = sync; }

    /** Axe de l'echelle desynchronisee : 0 = X, 1 = Y, 2 = Z. */
    int scaleAxis() { return scaleAxis; }

    void setScaleAxis(int axis) { this.scaleAxis = axis; }

    void attach()
    {
        HoloRouting.attach(panelAttachment);
        if (eyeAttachment != null) HoloRouting.attach(eyeAttachment);
    }

    void detach()
    {
        HoloRouting.detach(panelAttachment);
        if (eyeAttachment != null) HoloRouting.detach(eyeAttachment);
    }

    /** Re-place les boutons (apres une transformation de l'objet) : face au joueur, a cote de sa silhouette. */
    void replace()
    {
        panelAttachment.replace();
        if (eyeAttachment != null) eyeAttachment.replace();
    }

    /** Chaque tick client : valeurs affichees et animations des widgets. */
    void tick()
    {
        panel.refresh();
        panel.tick();
        if (eye != null) eye.tick();
    }
}
