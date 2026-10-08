package com.amynna.hololib.modules.camera.editor;

import com.amynna.hololib.holo.edit.HoloEditorCapability;
import com.amynna.hololib.holo.edit.HoloEditorModule;
import com.amynna.hololib.holo.module.HoloModule;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Base des modules editeurs : une capacite d'edition, active par defaut. Le joueur peut la desactiver depuis la
 * fenetre "Editeurs" de Config ({@link EditorPanelModule}) ; l'etat est sauvegarde (desactive jusqu'a reactivation) et
 * synchronise. Le serveur ne compte que les editeurs actifs.
 */
public abstract class EditorCapabilityModule extends HoloModule implements HoloEditorModule
{
    private final HoloEditorCapability capability;
    private final Component name;
    private boolean enabled = true;

    protected EditorCapabilityModule(HoloEditorCapability capability, String name)
    {
        this.capability = capability;
        this.name = Component.literal(name);
    }

    @Override
    public final HoloEditorCapability editorCapability() { return capability; }

    @Override
    public final boolean editorEnabled() { return enabled; }

    @Override
    public final Component editorName() { return name; }

    /** Cote client (proprietaire) : active ou desactive l'editeur, localement puis sur le serveur. */
    public final void requestEnabled(boolean enabled)
    {
        if (!host().canAct() || enabled == this.enabled) return;
        this.enabled = enabled;
        sendAction(buf -> buf.writeBoolean(enabled));
    }

    @Override
    public void save(CompoundTag tag) { tag.putBoolean("enabled", enabled); }

    @Override
    public void load(CompoundTag tag) { enabled = !tag.contains("enabled") || tag.getBoolean("enabled"); }

    @Override
    public void writeSync(FriendlyByteBuf buf, boolean forOwner) { buf.writeBoolean(enabled); }

    @Override
    public void readSync(FriendlyByteBuf buf) { enabled = buf.readBoolean(); }

    @Override
    public void handleAction(ServerPlayer player, FriendlyByteBuf buf)
    {
        boolean requested = buf.readBoolean();
        if (requested == enabled) return;
        enabled = requested;
        markDirty();
    }
}
