package com.amynna.hololib.client.render.levelview;

import com.amynna.hololib.HoloLib;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mode degrade sans Embeddium : la vue ne touche pas au graphe de visibilite des chunks du joueur. Vanilla le calcule
 * pour une seule camera, en tache de fond ({@code LevelRenderer.setupRender}) : une seconde camera planifierait des
 * mises a jour completes depuis son point de vue, dont le resultat servirait aussi a la vue principale (trous,
 * scintillement). Pendant la vue (reflexion, tout restaure) :
 * <ul>
 *     <li>{@code lastFullRenderChunkUpdate} = tache jamais terminee et {@code nextFullUpdateMillis} = 0 : aucune mise
 *     a jour complete planifiee ; {@code needsFullRenderChunkUpdate} et {@code prevCamX/Y/Z} restaures apres ;</li>
 *     <li>{@code recentlyCompiledChunks} vide : pas de mise a jour partielle depuis la camera de la vue (les chunks
 *     compiles sont remis dans la file ensuite) ;</li>
 *     <li>{@code needsFrustumUpdate} vrai avant (chunks rendus = graphe du joueur filtre par le frustum de la vue) et
 *     apres (la passe principale re-applique le sien) ;</li>
 *     <li>{@code xTransparentOld/Y/Z} = camera de la vue, restaures apres : la vue ne retrie aucune section
 *     translucide ({@code renderChunkLayer} ne retrie qu'apres un deplacement de plus d'un bloc), la frame principale
 *     non plus a son retour (pas de ping-pong) ; les translucides de la vue restent tries pour le joueur.</li>
 * </ul>
 * Inerte avec Embeddium (qui remplace {@code setupRender}). En cas d'echec de la reflexion (journalise une fois), la
 * vue est rendue sans protection.
 */
final class LevelViewCulling
{
    private static final Future<?> NEVER_DONE = new CompletableFuture<Void>();

    private static Field prevCamX;
    private static Field prevCamY;
    private static Field prevCamZ;
    private static Field needsFullUpdate;
    private static Field lastFullUpdate;
    private static Field recentlyCompiled;
    private static Field needsFrustumUpdate;
    private static Field nextFullUpdateMillis;
    private static Field transparentX;
    private static Field transparentY;
    private static Field transparentZ;
    private static boolean resolved;
    private static boolean failed;

    /** Etat sauvegarde entre {@link #begin} et {@link #end} ; une vue a la fois. */
    private static boolean active;
    private static double savedX;
    private static double savedY;
    private static double savedZ;
    private static boolean savedNeedsFull;
    private static Object savedLastFull;
    private static long savedNextFull;
    private static double savedTransparentX;
    private static double savedTransparentY;
    private static double savedTransparentZ;
    private static final List<Object> COMPILED = new ArrayList<>();

    private LevelViewCulling() {}

    /** Avant le rendu de la vue ; {@code (x, y, z)} : position (approchee a moins d'un bloc) de sa camera. */
    static void begin(LevelRenderer renderer, double x, double y, double z)
    {
        if (HoloLevelViews.embeddium() || !resolve()) return;
        try
        {
            savedX = prevCamX.getDouble(renderer);
            savedY = prevCamY.getDouble(renderer);
            savedZ = prevCamZ.getDouble(renderer);
            savedNeedsFull = needsFullUpdate.getBoolean(renderer);
            savedTransparentX = transparentX.getDouble(renderer);
            savedTransparentY = transparentY.getDouble(renderer);
            savedTransparentZ = transparentZ.getDouble(renderer);
            savedLastFull = lastFullUpdate.get(renderer);
            AtomicLong next = (AtomicLong) nextFullUpdateMillis.get(renderer);
            Queue<?> queue = (Queue<?>) recentlyCompiled.get(renderer);
            AtomicBoolean frustum = (AtomicBoolean) needsFrustumUpdate.get(renderer);
            // Tout est lu : a partir d'ici, end() restaure meme si la suite echoue.
            active = true;
            savedNextFull = next.getAndSet(0L);
            lastFullUpdate.set(renderer, NEVER_DONE);
            Object chunk;
            while ((chunk = queue.poll()) != null) COMPILED.add(chunk);
            frustum.set(true);
            transparentX.setDouble(renderer, x);
            transparentY.setDouble(renderer, y);
            transparentZ.setDouble(renderer, z);
        }
        catch (ReflectiveOperationException | RuntimeException e)
        {
            fail(e);
        }
    }

    /** Apres le rendu de la vue (meme en cas d'exception). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void end(LevelRenderer renderer)
    {
        if (!active) return;
        active = false;
        try
        {
            prevCamX.setDouble(renderer, savedX);
            prevCamY.setDouble(renderer, savedY);
            prevCamZ.setDouble(renderer, savedZ);
            needsFullUpdate.setBoolean(renderer, savedNeedsFull);
            transparentX.setDouble(renderer, savedTransparentX);
            transparentY.setDouble(renderer, savedTransparentY);
            transparentZ.setDouble(renderer, savedTransparentZ);
            lastFullUpdate.set(renderer, savedLastFull);
            AtomicLong next = (AtomicLong) nextFullUpdateMillis.get(renderer);
            // Une echeance posee pendant la vue (aucune en pratique) est gardee.
            if (next.get() == 0L) next.set(savedNextFull);
            Queue queue = (Queue) recentlyCompiled.get(renderer);
            queue.addAll(COMPILED);
            ((AtomicBoolean) needsFrustumUpdate.get(renderer)).set(true);
        }
        catch (ReflectiveOperationException | RuntimeException e)
        {
            fail(e);
        }
        finally
        {
            COMPILED.clear();
            savedLastFull = null;
        }
    }

    private static boolean resolve()
    {
        if (resolved) return !failed;
        resolved = true;
        try
        {
            prevCamX = field("f_109425_");
            prevCamY = field("f_109426_");
            prevCamZ = field("f_109427_");
            needsFullUpdate = field("f_194300_");
            lastFullUpdate = field("f_194298_");
            recentlyCompiled = field("f_194306_");
            needsFrustumUpdate = field("f_194299_");
            nextFullUpdateMillis = field("f_194301_");
            transparentX = field("f_109445_");
            transparentY = field("f_109446_");
            transparentZ = field("f_109447_");
        }
        catch (RuntimeException e)
        {
            fail(e);
        }
        return !failed;
    }

    private static Field field(String srg)
    {
        return ObfuscationReflectionHelper.findField(LevelRenderer.class, srg);
    }

    private static void fail(Exception e)
    {
        if (!failed) HoloLib.LOGGER.error("Vues secondaires : graphe des chunks vanilla non protege", e);
        failed = true;
    }
}
