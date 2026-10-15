package com.amynna.hololib.modules.monde.areadisplayer;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.Arrays;

/**
 * Voxels d'une {@link AreaGrid} en stockage <b>creux</b> : seules les sections non vides sont gardees (cle de
 * {@link AreaGrid#sectionKey}, absente = vide). Une section uniforme pleine ou fluide est un tableau partage et
 * immuable ; les autres sont des tableaux de {@link AreaGrid#SECTION_VOXELS} octets proprietes de la table. Aucun
 * plafond de taille : la memoire suit le nombre de sections non vides.
 * <p>
 * Un tableau rendu par {@link #section} ne doit jamais etre modifie. Ecriture seulement par {@link #put}. Une seule
 * thread a la fois (fil serveur ou fil client).
 */
public final class AreaVoxels
{
    /** Sections uniformes partagees, par valeur ({@code AIR} : jamais stockee). */
    private static final byte[][] UNIFORM = new byte[3][];

    static
    {
        for (int value = 1; value < UNIFORM.length; value++) {
            UNIFORM[value] = new byte[AreaGrid.SECTION_VOXELS];
            Arrays.fill(UNIFORM[value], (byte) value);
        }
    }

    private final Long2ObjectOpenHashMap<byte[]> sections = new Long2ObjectOpenHashMap<>();

    /** Section {@code key} (tableau en lecture seule), {@code null} si elle est vide. */
    public byte[] section(long key) { return sections.get(key); }

    /** Vrai si la section {@code key} contient au moins un voxel non vide. */
    public boolean contains(long key) { return sections.containsKey(key); }

    /** Nombre de sections non vides. */
    public int size() { return sections.size(); }

    /** Cles des sections non vides (vue vivante : ne pas ecrire pendant le parcours). */
    public LongSet keys() { return sections.keySet(); }

    /** Voxel du bloc de grille {@code (x, y, z)} ({@code 0} hors de la grille ou dans une section vide). */
    public int get(int x, int y, int z)
    {
        byte[] section = sections.get(AreaGrid.sectionKey(x >> 4, y >> 4, z >> 4));
        return section == null ? AreaGrid.AIR : section[AreaGrid.localIndex(x & 15, y & 15, z & 15)];
    }

    /**
     * Remplace la section {@code key} par {@code data} (copie si elle est gardee : l'appelant peut reutiliser son
     * tableau ; {@code null} = vide).
     *
     * @return vrai si le contenu de la section a change
     */
    public boolean put(long key, byte[] data)
    {
        int uniform = data == null ? AreaGrid.AIR : uniformValue(data);
        byte[] old = sections.get(key);
        if (uniform == AreaGrid.AIR) return sections.remove(key) != null;
        if (uniform > 0) {
            if (old == UNIFORM[uniform]) return false;
            sections.put(key, UNIFORM[uniform]);
            return true;
        }
        if (old != null && Arrays.equals(old, data)) return false;
        sections.put(key, Arrays.copyOf(data, AreaGrid.SECTION_VOXELS));
        return true;
    }

    /** Vide tout. */
    public void clear() { sections.clear(); }

    /** Valeur commune des voxels de {@code data}, {@code -1} s'ils different. */
    private static int uniformValue(byte[] data)
    {
        byte first = data[0];
        for (int i = 1; i < AreaGrid.SECTION_VOXELS; i++) if (data[i] != first) return -1;
        return first;
    }
}
