package com.amynna.hololib.client.render.levelview;

import com.amynna.hololib.HoloLib;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Avec Embeddium : la vue ne reprogramme pas le tri des faces translucides. A chaque parcours du graphe
 * ({@code RenderSectionManager.createTerrainRenderList}), Embeddium reprogramme le tri de toute section translucide dont
 * la camera a change de section : la vue (ailleurs que le joueur) puis la frame principale feraient trier les memes
 * sections en ping-pong. Pendant la vue (reflexion, Embeddium jamais compile, tout restaure) :
 * <ul>
 *     <li>{@code translucencySorting} = faux : aucun tri programme, les translucides de la vue restent tries pour le
 *     joueur ;</li>
 *     <li>{@code buildResults} vide (resultats remis en tete de file ensuite) : un resultat traite pendant la vue
 *     n'aurait pas ses informations de tri mises a jour (meme indicateur), il est traite par la frame suivante.</li>
 * </ul>
 * Le parcours du graphe de la vue est garde : ses numeros de frame servent aussi au culling des entites, que la frame
 * principale refait a son propre parcours. Inerte sans Embeddium ; si un membre manque (autre version), journalise une
 * fois et inerte.
 */
final class LevelViewSodium
{
    private static Method instance;
    private static Field sectionManager;
    private static Field translucencySorting;
    private static Field buildResults;
    private static boolean resolved;
    private static boolean failed;

    /** Etat sauvegarde entre {@link #begin} et {@link #end} ; une vue a la fois. */
    private static Object manager;
    private static boolean savedSorting;
    private static final List<Object> RESULTS = new ArrayList<>();

    private LevelViewSodium() {}

    /** Avant le rendu de la vue. */
    static void begin()
    {
        if (!HoloLevelViews.embeddium() || !resolve()) return;
        try
        {
            Object renderer = instance.invoke(null);
            if (renderer == null) return;
            Object rsm = sectionManager.get(renderer);
            if (rsm == null) return;
            Deque<?> results = (Deque<?>) buildResults.get(rsm);
            savedSorting = translucencySorting.getBoolean(rsm);
            // Tout est lu : a partir d'ici, end() restaure meme si la suite echoue.
            manager = rsm;
            translucencySorting.setBoolean(rsm, false);
            Object result;
            while ((result = results.pollFirst()) != null) RESULTS.add(result);
        }
        catch (ReflectiveOperationException | RuntimeException e)
        {
            fail(e);
        }
    }

    /** Apres le rendu de la vue (meme en cas d'exception). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void end()
    {
        Object rsm = manager;
        if (rsm == null) return;
        manager = null;
        try
        {
            translucencySorting.setBoolean(rsm, savedSorting);
            Deque results = (Deque) buildResults.get(rsm);
            for (int i = RESULTS.size() - 1; i >= 0; i--) results.addFirst(RESULTS.get(i));
        }
        catch (ReflectiveOperationException | RuntimeException e)
        {
            fail(e);
        }
        finally
        {
            RESULTS.clear();
        }
    }

    private static boolean resolve()
    {
        if (resolved) return !failed;
        resolved = true;
        try
        {
            Class<?> worldRenderer = Class.forName("me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer");
            Class<?> rsm = Class.forName("me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager");
            instance = worldRenderer.getMethod("instanceNullable");
            sectionManager = accessible(worldRenderer.getDeclaredField("renderSectionManager"));
            translucencySorting = accessible(rsm.getDeclaredField("translucencySorting"));
            buildResults = accessible(rsm.getDeclaredField("buildResults"));
        }
        catch (ReflectiveOperationException | RuntimeException e)
        {
            fail(e);
        }
        return !failed;
    }

    private static Field accessible(Field field)
    {
        field.setAccessible(true);
        return field;
    }

    private static void fail(Exception e)
    {
        if (!failed) HoloLib.LOGGER.error("Vues secondaires : etat d'Embeddium non protege (tri des translucides)", e);
        failed = true;
    }
}
