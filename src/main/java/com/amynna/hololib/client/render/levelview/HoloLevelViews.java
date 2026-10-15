package com.amynna.hololib.client.render.levelview;

import com.amynna.hololib.HoloLib;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Registre et ordonnanceur des {@link HoloLevelView}. Fil de rendu uniquement.
 * <p>
 * Au debut de chaque frame ({@code RenderDispatcher}, {@code RenderTickEvent} START en dernier), au plus <b>une</b>
 * vue est rendue, et jamais deux frames de suite (les frames lourdes ne s'enchainent pas). Candidates : vues dessinees
 * dans le rendu principal depuis moins de {@value #SEEN_TIMEOUT_MILLIS} ms sur au moins {@value #MIN_PIXELS} px de
 * haut ; cadence effective = cadence maximale x clamp(hauteur a l'ecran / hauteur de l'image, {@value #MIN_RATE}, 1) ;
 * la plus en retard (relativement a son intervalle) est rendue. <b>Budget</b> : seau a jetons, les vues occupent au plus
 * {@value #BUDGET_PERCENT} % du temps reel en moyenne ; au-dela, elles attendent (leur cadence baisse, pas les FPS).
 * Rien sans niveau ni joueur, ni quand le rendu est coupe.
 * <p>
 * Mesures sur {@value #STATS_SECONDS} s (rendus par seconde, temps d'une vue, temps d'une frame, part des vues),
 * affichees dans l'ecran F3 ({@link #debugLines}) ; {@link #setPaused} fige toutes les vues (diagnostic).
 */
public final class HoloLevelViews
{
    /** Une vue non dessinee depuis plus longtemps n'est plus rendue. */
    private static final long SEEN_TIMEOUT_MILLIS = 250L;
    private static final long SEEN_TIMEOUT = SEEN_TIMEOUT_MILLIS * 1_000_000L;
    /** Hauteur a l'ecran sous laquelle une vue n'est plus rendue, en pixels. */
    private static final float MIN_PIXELS = 12f;
    /** Fraction minimale de la cadence pour une vue petite a l'ecran. */
    private static final float MIN_RATE = 0.15f;
    /** Part maximale du temps reel passee dans les vues, en pour cent. */
    private static final long BUDGET_PERCENT = 20L;
    /** Reserve maximale du seau a jetons : un pic isole tolere, pas une rafale. */
    private static final long BUDGET_CAPACITY = 100_000_000L;
    private static final long STATS_SECONDS = 5L;
    private static final long STATS_WINDOW = STATS_SECONDS * 1_000_000_000L;

    private static final List<HoloLevelView> VIEWS = new ArrayList<>();
    private static final boolean EMBEDDIUM = ModList.get().isLoaded("embeddium");
    private static HoloLevelView current;
    private static int nextId;
    private static int frame;
    private static boolean paused;
    private static boolean renderedLastFrame;
    /** Seau a jetons du budget, en nanosecondes de rendu disponibles (negatif : dette a rembourser). */
    private static long budget = BUDGET_CAPACITY;
    private static long lastFrameStart;

    // Mesures de la fenetre en cours et texte de la derniere fenetre complete.
    private static long windowStart;
    private static int windowViews;
    private static long windowViewNanos;
    private static long windowViewMax;
    private static int windowFrames;
    private static long windowFrameNanos;
    private static long windowFrameMax;
    private static String statsLine = "HoloLib vues : mesure en cours";
    private static String costLine = "";

    private HoloLevelViews() {}

    /** Nouvelle vue, sans image ; a fermer par {@link HoloLevelView#close()}. */
    public static HoloLevelView create()
    {
        RenderSystem.assertOnRenderThread();
        HoloLevelView view = new HoloLevelView(
                ResourceLocation.fromNamespaceAndPath(HoloLib.MODID, "level_view/" + nextId++));
        VIEWS.add(view);
        return view;
    }

    /**
     * Vue en cours de rendu, {@code null} sinon. Pendant une vue, les passes du mod dessinent le ciel et le monde mais
     * pas la vue (objets devant l'oeil du joueur) ; un renderer d'effet personnel au joueur local (radar, ciblage...)
     * doit s'abstenir.
     */
    public static HoloLevelView current() { return current; }

    /** Embeddium present : chunks visibles recalcules par camera ; sinon mode degrade (voir le package). */
    public static boolean embeddium() { return EMBEDDIUM; }

    /** Fige toutes les vues (derniere image gardee) ; diagnostic. */
    public static void setPaused(boolean paused) { HoloLevelViews.paused = paused; }

    public static boolean paused() { return paused; }

    /** Numero de la frame courante, pour regrouper les {@code markSeen} d'une meme frame. */
    static int frame() { return frame; }

    /** FOV vertical a imposer pendant la vue en cours (elargi pour le recadrage), en degres. */
    public static float cameraFov() { return LevelViewRenderer.renderFov; }

    /**
     * Pendant la vue en cours, a appeler par {@code RenderDispatcher} a {@code ComputeFov} du monde (apres l'ecriture
     * de la distance de rendu par {@code GameRenderer.renderLevel}) : distance de vue de la vue (plan lointain).
     */
    public static void applyViewDistance() { LevelViewRenderer.applyViewDistance(); }

    /** Pendant la vue en cours, a appeler par {@code RenderDispatcher} pour chaque {@code RenderFog} : brouillard. */
    public static void applyFog(ViewportEvent.RenderFog event) { LevelViewRenderer.applyFog(event); }

    /**
     * Pendant la vue en cours, a appeler par {@code RenderDispatcher} apres les post-effets des entites
     * ({@code AFTER_BLOCK_ENTITIES}) : ils remettent le viewport a la taille de la fenetre.
     */
    public static void fixViewport() { LevelViewRenderer.fixViewport(); }

    /** Lignes de l'ecran F3 (texte recalcule a chaque fenetre de mesure, aucune allocation sinon). */
    public static void debugLines(List<String> lines)
    {
        if (VIEWS.isEmpty()) return;
        lines.add(statsLine);
        if (!costLine.isEmpty()) lines.add(costLine);
    }

    static void remove(HoloLevelView view) { VIEWS.remove(view); }

    static void setCurrent(HoloLevelView view) { current = view; }

    /** Debut de frame : rend au plus une vue. Appele par {@code RenderDispatcher}. */
    public static void renderFrame(float partialTick)
    {
        if (current != null) return;
        frame++;
        long now = Util.getNanos();
        long elapsed = lastFrameStart == 0L ? 0L : now - lastFrameStart;
        lastFrameStart = now;
        if (VIEWS.isEmpty()) return;
        recordFrame(now, elapsed);
        budget = Math.min(BUDGET_CAPACITY, budget + elapsed * BUDGET_PERCENT / 100L);
        Minecraft mc = Minecraft.getInstance();
        if (paused || mc.level == null || mc.player == null || mc.noRender) return;
        if (renderedLastFrame) {
            renderedLastFrame = false;
            return;
        }
        if (budget <= 0L) return;
        HoloLevelView chosen = null;
        float late = 0f;
        for (HoloLevelView view : VIEWS) {
            if (now - view.lastSeen > SEEN_TIMEOUT || view.seenPixels < MIN_PIXELS) continue;
            float rate = Mth.clamp(view.seenPixels / view.height(), MIN_RATE, 1f);
            float interval = 1_000_000_000f / (view.maxFps() * rate);
            float ratio = (now - view.lastRender) / interval;
            if (ratio >= 1f && ratio > late) {
                late = ratio;
                chosen = view;
            }
        }
        if (chosen == null) return;
        chosen.lastRender = now;
        long start = Util.getNanos();
        LevelViewRenderer.render(chosen, partialTick);
        long cost = Util.getNanos() - start;
        budget -= cost;
        renderedLastFrame = true;
        windowViews++;
        windowViewNanos += cost;
        if (cost > windowViewMax) windowViewMax = cost;
    }

    /** Mesure de la frame precedente ; texte F3 recalcule a la fin de chaque fenetre. */
    private static void recordFrame(long now, long elapsed)
    {
        if (windowStart == 0L) windowStart = now;
        if (elapsed > 0L) {
            windowFrames++;
            windowFrameNanos += elapsed;
            if (elapsed > windowFrameMax) windowFrameMax = elapsed;
        }
        long span = now - windowStart;
        if (span < STATS_WINDOW) return;
        float seconds = span / 1e9f;
        statsLine = String.format(Locale.ROOT, "HoloLib vues : %d, %.1f rendus/s%s, vue moy %.2f ms max %.2f ms",
                VIEWS.size(), windowViews / seconds, paused ? " (pause)" : "",
                windowViews > 0 ? windowViewNanos / 1e6f / windowViews : 0f, windowViewMax / 1e6f);
        costLine = String.format(Locale.ROOT, "HoloLib frames : moy %.2f ms max %.2f ms, part des vues %.1f %%",
                windowFrames > 0 ? windowFrameNanos / 1e6f / windowFrames : 0f, windowFrameMax / 1e6f,
                windowViewNanos * 100f / span);
        windowStart = now;
        windowViews = 0;
        windowViewNanos = 0L;
        windowViewMax = 0L;
        windowFrames = 0;
        windowFrameNanos = 0L;
        windowFrameMax = 0L;
    }
}
