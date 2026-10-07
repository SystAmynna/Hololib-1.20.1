package com.amynna.hololib.client.holo;

import com.amynna.hololib.holo.mode.HoloMode;
import com.amynna.hololib.holo.mode.HoloModeModule;
import com.amynna.hololib.holo.module.HoloModule;

/** Lecture cote client des modes ({@link HoloModeModule}) portes par les memoires de joueur. Fil client. */
public final class HoloModes
{
    private HoloModes() {}

    /** Vrai si un module de la portee porte ce mode et l'a actif ; faux pour {@code container} nul. */
    public static boolean active(ClientHoloContainer container, HoloMode mode)
    {
        if (container == null) return false;
        for (HoloModule module : container.modules()) {
            if (module instanceof HoloModeModule holder && holder.mode() == mode && holder.modeActive()) return true;
        }
        return false;
    }

    /** Premier module de la portee de ce type, ou {@code null}. */
    public static <M extends HoloModule> M find(ClientHoloContainer container, Class<M> type)
    {
        if (container == null) return null;
        for (HoloModule module : container.modules()) {
            if (type.isInstance(module)) return type.cast(module);
        }
        return null;
    }
}
