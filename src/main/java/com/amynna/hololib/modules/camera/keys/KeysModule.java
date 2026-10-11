package com.amynna.hololib.modules.camera.keys;

import com.amynna.hololib.holo.memory.HoloContainers;
import com.amynna.hololib.holo.memory.player.PlayerHoloContainer;
import com.amynna.hololib.holo.module.HoloModule;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Set;
import java.util.UUID;

/**
 * Module {@code hololib:keys} (memoire de joueur) : <b>cles</b> du joueur, des chaines ({@link KeySet} : au plus
 * {@value KeySet#MAX_KEYS} cles de {@value KeySet#MAX_LENGTH} caracteres). D'autres modules, y compris ceux d'autres
 * joueurs, demandent si un joueur possede une cle : cote serveur, {@link #haveKey(UUID, String)} (memoire active du
 * joueur) ou {@link #of(UUID)} puis {@link #haveKey(String)}.
 * <p>
 * Aucun objet ni action : les cles changent par l'API serveur ({@link #addKey}, {@link #removeKey}), appelee par
 * d'autres modules (Keys Editor) qui verifient leurs droits. Sauvegarde ; synchronise <b>au proprietaire seulement</b>
 * (son client lit ses propres cles ; jamais envoye aux observateurs).
 */
public final class KeysModule extends HoloModule
{
    private final KeySet keys = new KeySet();

    // ---------------------------------------------------------------- lecture (deux cotes)

    /** Vrai si le joueur possede la cle (comparaison exacte). */
    public boolean haveKey(String key) { return keys.contains(key); }

    /** Vrai si le joueur possede l'une des cles de {@code wanted}. */
    public boolean haveAnyKey(KeySet wanted) { return keys.containsAny(wanted); }

    /** Cles, dans l'ordre d'ajout (vue non modifiable). */
    public Set<String> keys() { return keys.keys(); }

    /** Compteur de modifications des cles. */
    public int revision() { return keys.revision(); }

    // ---------------------------------------------------------------- serveur

    /** Cote serveur : module de la memoire active du joueur, ou {@code null} (hors ligne, module absent). */
    public static KeysModule of(UUID player)
    {
        PlayerHoloContainer container = HoloContainers.player(player);
        if (container == null) return null;
        for (HoloModule module : container.modules()) {
            if (module instanceof KeysModule found) return found;
        }
        return null;
    }

    /** Cote serveur : vrai si la memoire active du joueur contient ce module et la cle. */
    public static boolean haveKey(UUID player, String key)
    {
        KeysModule module = of(player);
        return module != null && module.haveKey(key);
    }

    /** Cote serveur : ajoute une cle (normalisee, {@link KeySet#normalize}) ; faux si refusee ou deja presente. */
    public boolean addKey(String key)
    {
        if (host().clientSide() || !keys.add(key)) return false;
        markDirty();
        return true;
    }

    /** Cote serveur : retire une cle ; faux si absente. */
    public boolean removeKey(String key)
    {
        if (host().clientSide() || !keys.remove(key)) return false;
        markDirty();
        return true;
    }

    @Override
    public void save(CompoundTag tag) { keys.save(tag, "keys"); }

    @Override
    public void load(CompoundTag tag) { keys.load(tag, "keys"); }

    @Override
    public void writeSync(FriendlyByteBuf buf, boolean forOwner)
    {
        buf.writeBoolean(forOwner);
        if (forOwner) keys.write(buf);
    }

    @Override
    public void readSync(FriendlyByteBuf buf)
    {
        if (buf.readBoolean()) keys.read(buf);
    }
}
