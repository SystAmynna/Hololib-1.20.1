package com.amynna.hololib.client.render.modules.monde.triggerarea;

import com.amynna.hololib.client.render.holo.HoloObjectRenderers;
import com.amynna.hololib.modules.monde.triggerarea.TriggerAreaZone;

/**
 * Enregistrement des renderers de la Trigger Area, appele par {@code ModuleRenderers} sur le fil de rendu : la zone. La
 * fenetre du Trigger Area Configurator utilise le renderer generique des fenetres (heritage).
 */
public final class TriggerAreaRenderers
{
    private TriggerAreaRenderers() {}

    public static void register()
    {
        HoloObjectRenderers.register(TriggerAreaZone.class, new TriggerAreaZoneRenderer());
    }
}
