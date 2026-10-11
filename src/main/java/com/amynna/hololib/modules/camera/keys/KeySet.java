package com.amynna.hololib.modules.camera.keys;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Ensemble de cles (chaines), dans l'ordre d'ajout : cles d'un joueur ({@link KeysModule}), whitelist d'un module qui
 * les interroge. Bornes : {@value #MAX_KEYS} cles de {@value #MAX_LENGTH} caracteres au plus ({@link #normalize}).
 * {@link #revision()} change a chaque modification (detection des changements sans copie). Sauvegarde et
 * synchronisation : {@link #save} / {@link #load}, {@link #write} / {@link #read}.
 */
public final class KeySet
{
    public static final int MAX_KEYS = 64;
    public static final int MAX_LENGTH = 32;

    private final Set<String> keys = new LinkedHashSet<>();
    private final Set<String> view = Collections.unmodifiableSet(keys);
    private int revision;

    /**
     * Cle valide tiree de {@code raw} (espaces du bord retires), ou {@code null} : vide, trop longue, ou caractere de
     * controle.
     */
    public static String normalize(String raw)
    {
        if (raw == null) return null;
        String key = raw.strip();
        if (key.isEmpty() || key.length() > MAX_LENGTH) return null;
        for (int i = 0; i < key.length(); i++) {
            if (Character.isISOControl(key.charAt(i))) return null;
        }
        return key;
    }

    /** Cles, dans l'ordre d'ajout (vue non modifiable). */
    public Set<String> keys() { return view; }

    public int size() { return keys.size(); }

    /** Compteur de modifications. */
    public int revision() { return revision; }

    public boolean contains(String key) { return key != null && keys.contains(key); }

    /** Vrai si l'une des cles de {@code other} est dans cet ensemble. */
    public boolean containsAny(KeySet other)
    {
        for (String key : other.keys) {
            if (keys.contains(key)) return true;
        }
        return false;
    }

    /** Ajoute une cle normalisee ; faux si invalide, deja presente ou ensemble plein. */
    public boolean add(String raw)
    {
        String key = normalize(raw);
        if (key == null || keys.size() >= MAX_KEYS || !keys.add(key)) return false;
        revision++;
        return true;
    }

    /** Retire une cle ; faux si absente. */
    public boolean remove(String key)
    {
        if (key == null || !keys.remove(key)) return false;
        revision++;
        return true;
    }

    /** Remplace le contenu par {@code values} (normalisees, bornees) ; faux si rien n'a change. */
    public boolean setAll(Collection<String> values)
    {
        Set<String> next = new LinkedHashSet<>();
        for (String value : values) {
            String key = normalize(value);
            if (key != null && next.size() < MAX_KEYS) next.add(key);
        }
        if (sameOrder(next, keys)) return false;
        keys.clear();
        keys.addAll(next);
        revision++;
        return true;
    }

    private static boolean sameOrder(Set<String> a, Set<String> b)
    {
        if (a.size() != b.size()) return false;
        Iterator<String> other = b.iterator();
        for (String key : a) {
            if (!key.equals(other.next())) return false;
        }
        return true;
    }

    public void save(CompoundTag tag, String name)
    {
        ListTag list = new ListTag();
        for (String key : keys) list.add(StringTag.valueOf(key));
        tag.put(name, list);
    }

    public void load(CompoundTag tag, String name)
    {
        keys.clear();
        ListTag list = tag.getList(name, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            String key = normalize(list.getString(i));
            if (key != null && keys.size() < MAX_KEYS) keys.add(key);
        }
        revision++;
    }

    public void write(FriendlyByteBuf buf)
    {
        buf.writeVarInt(keys.size());
        for (String key : keys) buf.writeUtf(key, MAX_LENGTH);
    }

    public void read(FriendlyByteBuf buf)
    {
        keys.clear();
        int count = buf.readVarInt();
        for (int i = 0; i < count; i++) {
            String key = buf.readUtf(MAX_LENGTH);
            if (keys.size() < MAX_KEYS) keys.add(key);
        }
        revision++;
    }
}
