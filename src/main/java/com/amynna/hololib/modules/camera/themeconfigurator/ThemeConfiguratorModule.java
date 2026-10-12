package com.amynna.hololib.modules.camera.themeconfigurator;

import com.amynna.hololib.holo.theme.HoloThemeLibrary;
import com.amynna.hololib.holo.theme.HoloThemeSelector;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Module {@code hololib:theme_configurator} (memoire de joueur) : themes du joueur, un par jeu (standard, staff pour un
 * op), lus par le client dans sa memoire active ({@link HoloThemeSelector}). Par defaut le joueur suit le serveur ;
 * il choisit un theme de la bibliotheque, ou en cree (copie) et edite les siens. Selection sauvegardee et
 * synchronisee au proprietaire seulement ; un theme disparu ramene a "Suivre le serveur" (serveur, chaque tick). Ses
 * fenetres prennent le theme du client, standard.
 */
public final class ThemeConfiguratorModule extends ThemeConfiguratorBase implements HoloThemeSelector
{
    private static final Component NAME = Component.literal("Themes");

    private int standard = FOLLOW_SERVER;
    private int staff = FOLLOW_SERVER;

    @Override
    boolean admin() { return false; }

    @Override
    int selection(boolean staff) { return staff ? this.staff : standard; }

    @Override
    public int selectedTheme(boolean staff) { return selection(staff); }

    @Override
    public Component configName() { return NAME; }

    @Override
    void localSelect(boolean staff, int id)
    {
        if (staff) this.staff = id;
        else standard = id;
    }

    @Override
    boolean applySelection(ServerPlayer player, HoloThemeLibrary library, boolean staff, int id)
    {
        if (id != FOLLOW_SERVER && library.get(id) == null || selection(staff) == id) return false;
        localSelect(staff, id);
        return true;
    }

    @Override
    public void serverTick(ServerLevel level)
    {
        HoloThemeLibrary library = HoloThemeLibrary.of(level.getServer());
        if (standard != FOLLOW_SERVER && library.get(standard) == null) {
            standard = FOLLOW_SERVER;
            markDirty();
        }
        if (staff != FOLLOW_SERVER && library.get(staff) == null) {
            staff = FOLLOW_SERVER;
            markDirty();
        }
    }

    @Override
    public void save(CompoundTag tag)
    {
        tag.putInt("standard", standard);
        tag.putInt("staff", staff);
    }

    @Override
    public void load(CompoundTag tag)
    {
        standard = tag.contains("standard") ? tag.getInt("standard") : FOLLOW_SERVER;
        staff = tag.contains("staff") ? tag.getInt("staff") : FOLLOW_SERVER;
    }

    @Override
    public void writeSync(FriendlyByteBuf buf, boolean forOwner)
    {
        buf.writeBoolean(forOwner);
        if (!forOwner) return;
        buf.writeVarInt(standard);
        buf.writeVarInt(staff);
    }

    @Override
    public void readSync(FriendlyByteBuf buf)
    {
        if (!buf.readBoolean()) return;
        standard = buf.readVarInt();
        staff = buf.readVarInt();
    }
}
