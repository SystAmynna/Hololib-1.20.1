package com.amynna.hololib.modules.dimension.space;

import com.amynna.hololib.HoloLib;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Archive de l'etat du {@link SpaceEnvironmentModule} d'une dimension (T14, D12) : {@code SavedData} du niveau, cle
 * {@value #NAME} (fichier {@code data/hololib_space.dat} de la dimension), independante de la memoire de dimension du
 * framework. Elle survit au retrait du module et sert a deux choses :
 * <ul>
 *     <li><b>reinstallation</b> : un Environment installe sans etat (commande) s'y recharge au lieu de creer la galaxie
 *     par defaut (galaxie, ids, systeme courant, pose, referentiel, reglages) ;</li>
 *     <li><b>sauvegarde fiable</b> : la memoire de dimension n'est ecrite que si un module a appele {@code markDirty}
 *     depuis la sauvegarde precedente, ce que l'Environment ne fait pas a chaque tick (envois limites, C19). Tant
 *     qu'un Environment y est branche ({@link #attach}), l'archive est toujours a ecrire et recopie son etat
 *     <b>vivant</b> a chaque sauvegarde du niveau (auto-sauvegarde, pause en solo, arret) : au chargement,
 *     l'Environment prend l'archive si elle est plus recente que l'etat de sa memoire.</li>
 * </ul>
 * Format : {@code { env: <tag de SpaceEnvironmentModule.save, copie exacte>, saved: long }} ; {@code saved} = tick de
 * jeu de la pose copiee (cle {@code time} de l'etat, {@code -1} si inconnu). Fil serveur uniquement.
 */
public final class SpaceArchive extends SavedData
{
    /** Cle dans le {@code DimensionDataStorage} du niveau. */
    public static final String NAME = HoloLib.MODID + "_space";

    /** Copie de l'etat, ou {@code null} (aucun Environment encore archive). */
    private CompoundTag env;
    private long saved = -1L;
    /** Environment installe dont l'etat vivant est recopie a chaque sauvegarde, ou {@code null}. */
    private SpaceEnvironmentModule live;

    private SpaceArchive() {}

    /** Archive du niveau, creee vide au premier acces. */
    public static SpaceArchive of(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(SpaceArchive::load, SpaceArchive::new, NAME);
    }

    /** Vrai si un etat est archive. */
    public boolean present() { return env != null; }

    /** Etat archive (tag de {@link SpaceEnvironmentModule#save}), {@code null} sans etat ; a lire sans le modifier. */
    public CompoundTag state() { return env; }

    /** Tick de jeu de la pose archivee, {@code -1} si inconnu ou sans etat. */
    public long saved() { return saved; }

    /** Branche l'Environment installe : son etat vivant sera recopie a chaque sauvegarde du niveau. */
    void attach(SpaceEnvironmentModule module)
    {
        live = module;
        setDirty();
    }

    /** Debranche {@code module} s'il est l'Environment branche. */
    void detach(SpaceEnvironmentModule module)
    {
        if (live == module) live = null;
    }

    /** Remplace la copie (retrait du module, dechargement) ; ecrite a la prochaine sauvegarde du niveau. */
    void store(CompoundTag state, long tick)
    {
        env = state;
        saved = tick;
        setDirty();
    }

    /** Oublie l'etat archive et l'Environment branche (remise a zero, {@code onReset}) ; fichier reecrit vide. */
    void clear()
    {
        env = null;
        saved = -1L;
        live = null;
        setDirty();
    }

    /** Toujours a ecrire tant qu'un Environment est branche : son etat change sans {@code markDirty}. */
    @Override
    public boolean isDirty() { return live != null || super.isDirty(); }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        if (live != null) {
            CompoundTag state = new CompoundTag();
            live.save(state);
            env = state;
            saved = live.stateTime();
        }
        if (env != null) {
            tag.put("env", env);
            tag.putLong("saved", saved);
        }
        return tag;
    }

    /** Lecture tolerante : sans cle {@code env} (ou illisible), archive vide. */
    private static SpaceArchive load(CompoundTag tag)
    {
        SpaceArchive archive = new SpaceArchive();
        if (tag.contains("env", Tag.TAG_COMPOUND)) {
            archive.env = tag.getCompound("env");
            archive.saved = tag.contains("saved", Tag.TAG_LONG) ? tag.getLong("saved") : -1L;
        }
        return archive;
    }
}
