package com.amynna.hololib.modules.camera.focus;

import com.amynna.hololib.holo.mode.HoloMode;
import com.amynna.hololib.holo.mode.HoloModeModule;
import com.amynna.hololib.holo.module.HoloModule;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * Mode focus du joueur : tant qu'il est actif, les objets {@code HoloDisplay.FOCUS} de sa memoire (et, pour lui, des
 * memoires sans proprietaire) sont dessines, et ses objets {@code HoloRelativeToOpen} ont ete places devant son regard
 * a l'ouverture. L'etat n'est pas sauvegarde (le focus redemarre ferme) ; il est envoye aux observateurs.
 * <p>
 * Le client du proprietaire ouvre et ferme le mode ({@link #requestActive}, applique aussitot puis confirme par le
 * serveur) ; la touche et les conditions de sortie sont lues cote client.
 */
public final class FocusModule extends HoloModule implements HoloModeModule
{
    private boolean active;

    @Override
    public HoloMode mode() { return HoloMode.FOCUS; }

    @Override
    public boolean modeActive() { return active; }

    /** Cote client (proprietaire) : ouvre ou ferme le mode, localement puis sur le serveur. */
    public void requestActive(boolean active)
    {
        if (!host().canAct()) return;
        this.active = active;
        sendAction(buf -> buf.writeBoolean(active));
    }

    @Override
    public boolean sharedWithObservers() { return true; }

    @Override
    public void writeSync(FriendlyByteBuf buf, boolean forOwner) { buf.writeBoolean(active); }

    @Override
    public void readSync(FriendlyByteBuf buf) { active = buf.readBoolean(); }

    @Override
    public void handleAction(ServerPlayer player, FriendlyByteBuf buf)
    {
        boolean requested = buf.readBoolean();
        if (requested == active) return;
        active = requested;
        markDirty();
    }
}
