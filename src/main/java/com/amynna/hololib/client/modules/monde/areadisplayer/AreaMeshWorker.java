package com.amynna.hololib.client.modules.monde.areadisplayer;

import com.amynna.hololib.HoloLib;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fil de maillage des tuiles de l'Area Displayer : calcule {@link AreaMesher#mesh} sur la copie d'une tuile pendant que
 * le jeu continue. Un seul fil (daemon), les resultats sont repris au tick client par {@link #drain} (fil client) et
 * remis a la vue ({@link AreaView#apply} jette ceux qui sont perimes). Ne touche jamais aux voxels de la vue.
 */
final class AreaMeshWorker
{
    private record Done(AreaView view, int epoch, long tileKey, int version, AreaMesher.Result result) {}

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "hololib-area-mesher");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private static final ConcurrentLinkedQueue<Done> DONE = new ConcurrentLinkedQueue<>();

    private AreaMeshWorker() {}

    /** Demande le maillage de {@code padded} (copie d'une tuile) ; le resultat est repris par {@link #drain}. */
    static void submit(AreaView view, int epoch, long tileKey, int version, byte[] padded)
    {
        EXECUTOR.execute(() -> {
            try {
                DONE.add(new Done(view, epoch, tileKey, version, AreaMesher.mesh(padded)));
            } catch (RuntimeException e) {
                HoloLib.LOGGER.error("Maillage d'une tuile de l'Area Displayer", e);
            }
        });
    }

    /** Fil client : remet a leurs vues les maillages termines, {@code max} au plus. */
    static void drain(int max)
    {
        Done done;
        while (max-- > 0 && (done = DONE.poll()) != null) {
            done.view.apply(done.epoch, done.tileKey, done.version, done.result);
        }
    }
}
