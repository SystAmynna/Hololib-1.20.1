package com.amynna.hololib.client.render.modules.dimension.space.warp;

import com.amynna.hololib.modules.dimension.space.SpaceEnvironmentModule;
import com.amynna.hololib.modules.dimension.space.math.SpaceJump;
import com.amynna.hololib.modules.dimension.space.math.SpacePose;
import com.amynna.hololib.modules.dimension.space.math.SpaceSkyFrame;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Chronologie de la distorsion du saut (T35, D23, C73) : phase visuelle courante et grandeurs qui en decoulent
 * (convergence des etoiles, blanc des flashs, apparition de la scene du voyage, direction de la cible), <b>en lecture
 * seule</b> pour le ciel, les surcouches qui se coupent et les sons (T36). Fil client (rendu et tick, le meme fil).
 * <p>
 * Temps : {@code e} = temps ecoule depuis le debut du voyage affiche, en ticks d'affichage ({@code ticks + fraction}
 * de {@code SpaceEnvironmentModule.jumpAt / jumpFraction}, borne a la duree {@code N} du voyage) : compte du saut, donc
 * le meme chez tous les joueurs de la dimension (un joueur qui arrive pendant le voyage voit tout de suite la bonne
 * phase). La charge (60 ticks) n'appartient pas a la distorsion.
 * <ol>
 *     <li>{@link Phase#CONVERGE} ({@code 0 <= e < C}, {@code C} = {@value #CONVERGE_TICKS} ticks, 1 s ; au plus
 *     {@code N / 2} pour un voyage sauvegarde avant C73) : les etoiles du ciel (fond procedural et autres systemes)
 *     glissent vers la cible sur leur grand cercle, fraction {@link #convergence()} = {@code (e / C)^}{@value
 *     #CONVERGE_POWER} (accelere) ; le blanc monte sur les {@value #ENTRY_FLASH_RISE} derniers ticks ;</li>
 *     <li>{@link Phase#WARP} ({@code C <= e}) : <b>flash d'entree</b> a {@code e = C} (ciel entierement blanc), qui
 *     redescend en {@value #ENTRY_FLASH_FADE} ticks sur la scene du voyage ; le blanc remonte sur les
 *     {@value #ARRIVAL_RISE} derniers ticks du voyage (blanc entier a {@code e = N}) ;</li>
 *     <li>{@link Phase#ARRIVAL} (apres le voyage) : <b>flash d'arrivee</b>, ciel blanc tenu {@value #ARRIVAL_HOLD}
 *     ticks puis estompe en {@value #ARRIVAL_FADE} ticks sur le ciel du nouveau systeme. L'affichage passe directement
 *     du voyage au nouveau systeme (T21) : cette phase est chronometree ici, en ticks d'affichage depuis la derniere
 *     lecture faite en voyage (comme C64 ; meme chose si le voyage est annule ou abandonne).</li>
 * </ol>
 * Instants cles pour les sons : debut de {@code CONVERGE} (moteur), {@code e = C} (debut de {@code WARP} : flash
 * d'entree, souffle), debut d'{@code ARRIVAL} (flash d'arrivee). {@link #phaseTicks()} et {@link #phaseProgress()}
 * situent l'instant dans la phase.
 * <p>
 * Usage : {@link #at(SpaceEnvironmentModule, float)} met a jour puis rend l'instance partagee, a lire aussitot (le
 * prochain appel l'ecrase). Le dernier instant vu en voyage est partage par tous les appelants : memes arguments dans
 * une frame, meme resultat. Aucune allocation. Un lecteur qui ne suit pas la frame (sons de T36, lus dans le tick
 * client) garde sa propre instance ({@link #separate()}, {@link #read}) pour ne pas deplacer ce dernier instant.
 */
public final class SpaceWarpTimeline
{
    // ---------------------------------------------------------------- durees (ticks d'affichage)

    /** Convergence des etoiles : premiers ticks du voyage (C73, purement visuelle). */
    public static final int CONVERGE_TICKS = 20;
    /** Montee du flash d'entree, a la fin de la convergence. */
    public static final int ENTRY_FLASH_RISE = 3;
    /** Descente du flash d'entree sur la scene du voyage. */
    public static final int ENTRY_FLASH_FADE = 10;
    /** Montee du blanc a la fin du voyage (blanc entier a l'arrivee). */
    public static final int ARRIVAL_RISE = 4;
    /** Flash d'arrivee : blanc tenu, puis fondu sur le nouveau systeme. */
    public static final int ARRIVAL_HOLD = 4;
    public static final int ARRIVAL_FADE = 12;

    // ---------------------------------------------------------------- convergence

    /** Exposant de la convergence : {@code c = u^p}, {@code u = e / C} (depart lent, arrivee rapide). */
    public static final double CONVERGE_POWER = 3.0;
    /** Retard de la queue d'une etoile, en fraction de la convergence : trainee qui s'allonge avec la vitesse. */
    public static final double TAIL_LAG = 0.12;

    /** Phase visuelle de la distorsion. */
    public enum Phase
    {
        /** Pas de distorsion (charge comprise). */
        NONE,
        /** Convergence des etoiles vers la cible, debut du voyage. */
        CONVERGE,
        /** Voyage : flash d'entree, scene de la distorsion, montee du blanc a la fin. */
        WARP,
        /** Flash d'arrivee puis fondu sur le nouveau systeme. */
        ARRIVAL
    }

    // ---------------------------------------------------------------- etat partage

    private static final SpaceWarpTimeline INSTANCE = new SpaceWarpTimeline();

    /** Environment suivi : un autre (changement de dimension, reinstallation) remet le suivi a zero. */
    private SpaceEnvironmentModule tracked;
    /** Dernier temps d'affichage vu en voyage (depart du flash d'arrivee). */
    private double lastTravelTime = Double.NEGATIVE_INFINITY;

    private Phase phase = Phase.NONE;
    private double time;
    private double elapsed;
    private int duration;
    private double phaseTicks;
    private double phaseProgress;
    private float convergence;
    private float convergenceTail;
    private float whiteness;
    private float sceneRamp;
    /** Direction de la cible : axes du monde (vue depuis la pose affichee) et axes du systeme. */
    private final Vector3f vanishing = new Vector3f(0f, 0f, -1f);
    private final Vector3f target = new Vector3f(0f, 0f, -1f);

    private final SpacePose pose = new SpacePose();
    private final SpaceSkyFrame frame = new SpaceSkyFrame();

    private SpaceWarpTimeline() {}

    // ---------------------------------------------------------------- acces

    /**
     * Chronologie de {@code env} au temps d'affichage de {@code partialTick} : instance partagee mise a jour, a lire
     * aussitot ({@code env} nul : {@link Phase#NONE}). Fil client.
     */
    public static SpaceWarpTimeline at(SpaceEnvironmentModule env, float partialTick)
    {
        INSTANCE.update(env, partialTick);
        return INSTANCE;
    }

    /** Vrai si la distorsion est affichee (convergence, voyage ou flash d'arrivee) : surcouches et reperage coupes. */
    public static boolean active(SpaceEnvironmentModule env, float partialTick)
    {
        return at(env, partialTick).active();
    }

    /**
     * Chronologie <b>separee</b> de l'instance partagee (T36) : son propre suivi (Environment, dernier instant vu en
     * voyage, donc depart du flash d'arrivee), mis a jour seulement par {@link #read}. Pour un lecteur d'un autre
     * rythme que la frame (sons, lus dans le tick client), qui ne doit pas deplacer le flash d'arrivee vu par le
     * rendu. Une allocation, a garder.
     */
    public static SpaceWarpTimeline separate() { return new SpaceWarpTimeline(); }

    /**
     * Met a jour cette instance au temps d'affichage de {@code partialTick} et la rend (meme calcul que
     * {@link #at}) ; a n'appeler que sur une instance de {@link #separate()}. Fil client.
     */
    public SpaceWarpTimeline read(SpaceEnvironmentModule env, float partialTick)
    {
        if (this == INSTANCE) throw new IllegalStateException("SpaceWarpTimeline : instance partagee, utiliser at()");
        update(env, partialTick);
        return this;
    }

    // ---------------------------------------------------------------- lecture

    /** Phase visuelle courante. */
    public Phase phase() { return phase; }

    /** Vrai hors de {@link Phase#NONE}. */
    public boolean active() { return phase != Phase.NONE; }

    /** Temps d'affichage de la lecture, en ticks ({@code SpaceEnvironmentModule.displayTicks}) : animations. */
    public double time() { return time; }

    /** Temps ecoule depuis le debut du voyage, en ticks ({@code [0, N]} ; {@code N} pendant l'arrivee, 0 sans saut). */
    public double elapsed() { return elapsed; }

    /** Duree {@code N} du voyage en cours ou du dernier vu, en ticks. */
    public int duration() { return duration; }

    /** Temps depuis le debut de la phase courante, en ticks d'affichage ({@code 0} sans distorsion). */
    public double phaseTicks() { return phaseTicks; }

    /** Progression dans la phase courante, {@code [0, 1]} ({@code WARP} : jusqu'a l'arrivee). */
    public double phaseProgress() { return phaseProgress; }

    /** Fraction de la convergence des tetes des etoiles, {@code [0, 1]} ({@code 0} hors {@code CONVERGE}). */
    public float convergence() { return convergence; }

    /** Fraction de la convergence des queues des etoiles ({@code <=} {@link #convergence()}). */
    public float convergenceTail() { return convergenceTail; }

    /** Blanc des flashs, {@code [0, 1]} (1 : ciel entierement blanc). */
    public float whiteness() { return whiteness; }

    /** Apparition de la scene du voyage sous le flash d'entree, {@code [0, 1]} (trainees, source, flares). */
    public float sceneRamp() { return sceneRamp; }

    /** Vrai si la scene du voyage remplace le ciel (phase {@code WARP}). */
    public boolean sceneVisible() { return phase == Phase.WARP; }

    /** Vrai si rien du ciel normal n'est visible : scene du voyage, ou flash entierement blanc. */
    public boolean skyHidden() { return phase == Phase.WARP || whiteness >= 1f; }

    /** Direction de la cible, axes du monde, unitaire (derniere vue en voyage) ; lecture seule. */
    public Vector3fc vanishing() { return vanishing; }

    /** Direction de la cible, axes du systeme (origine -> cible, galactique), unitaire ; lecture seule. */
    public Vector3fc target() { return target; }

    // ---------------------------------------------------------------- calcul

    private void update(SpaceEnvironmentModule env, float partialTick)
    {
        if (env == null) {
            clear();
            return;
        }
        if (env != tracked) {
            tracked = env;
            lastTravelTime = Double.NEGATIVE_INFINITY;
        }
        double now = env.displayTicks(partialTick);
        time = now;
        SpaceJump jump = env.jumpAt(partialTick);
        if (jump.phase() == SpaceJump.Phase.TRAVEL) {
            travel(env, jump, partialTick);
            lastTravelTime = now;
            return;
        }
        double since = now - lastTravelTime;
        double total = ARRIVAL_HOLD + ARRIVAL_FADE;
        if (!(since < total)) {
            clear();
            return;
        }
        if (since < -total) {
            // Horloge revenue loin en arriere (resynchronisation) : flash abandonne.
            lastTravelTime = Double.NEGATIVE_INFINITY;
            clear();
            return;
        }
        double s = Math.max(0.0, since);
        phase = Phase.ARRIVAL;
        elapsed = duration;
        phaseTicks = s;
        phaseProgress = s / total;
        convergence = 0f;
        convergenceTail = 0f;
        sceneRamp = 0f;
        whiteness = s < ARRIVAL_HOLD ? 1f : (float) (1.0 - smoothstep((s - ARRIVAL_HOLD) / ARRIVAL_FADE));
    }

    /** Pendant le voyage affiche : temps ecoule, phase, convergence, blanc, direction de la cible. */
    private void travel(SpaceEnvironmentModule env, SpaceJump jump, float partialTick)
    {
        int n = Math.max(1, jump.duration());
        duration = n;
        // L'intervalle qui mene a l'etat de compte 0 est le premier ; borne a N (client au bout du compte).
        double e = Math.min(jump.ticks() + env.jumpFraction(partialTick), n);
        elapsed = e;
        double end = Math.min(CONVERGE_TICKS, 0.5 * n);
        float entry;
        if (e < end) {
            phase = Phase.CONVERGE;
            double u = e / end;
            phaseTicks = e;
            phaseProgress = u;
            convergence = (float) Math.pow(u, CONVERGE_POWER);
            convergenceTail = (float) Math.pow(Math.max(0.0, u - TAIL_LAG), CONVERGE_POWER);
            sceneRamp = 0f;
            entry = (float) smoothstep((e - (end - ENTRY_FLASH_RISE)) / ENTRY_FLASH_RISE);
        } else {
            phase = Phase.WARP;
            phaseTicks = e - end;
            phaseProgress = n > end ? (e - end) / (n - end) : 1.0;
            convergence = 0f;
            convergenceTail = 0f;
            double fade = smoothstep((e - end) / ENTRY_FLASH_FADE);
            sceneRamp = (float) fade;
            entry = (float) (1.0 - fade);
        }
        float arrival = (float) smoothstep((e - (n - ARRIVAL_RISE)) / ARRIVAL_RISE);
        whiteness = Math.max(entry, arrival);
        direction(env, jump, partialTick);
    }

    /**
     * Direction de la cible : axes du systeme (position galactique de la cible moins celle de l'origine, figees dans le
     * saut, C58) et axes du monde vue depuis la pose affichee ; gardee si elle est indefinie.
     */
    private void direction(SpaceEnvironmentModule env, SpaceJump jump, float partialTick)
    {
        Vector3dc from = jump.origin();
        Vector3dc to = jump.destination();
        double dx = to.x() - from.x(), dy = to.y() - from.y(), dz = to.z() - from.z();
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!(length > 0.0) || !Double.isFinite(length)) return;
        target.set((float) (dx / length), (float) (dy / length), (float) (dz / length));
        frame.set(env.pose(partialTick, pose));
        float x = vanishing.x, y = vanishing.y, z = vanishing.z;
        if (!(frame.galacticDirection(from.x(), from.y(), from.z(), to.x(), to.y(), to.z(), vanishing) > 0.0)) {
            vanishing.set(x, y, z);
        }
    }

    private void clear()
    {
        phase = Phase.NONE;
        elapsed = 0.0;
        phaseTicks = 0.0;
        phaseProgress = 0.0;
        convergence = 0f;
        convergenceTail = 0f;
        whiteness = 0f;
        sceneRamp = 0f;
    }

    /** {@code u^2 (3 - 2u)} sur {@code u} borne a {@code [0, 1]}. */
    static double smoothstep(double u)
    {
        double x = u < 0.0 ? 0.0 : Math.min(1.0, u);
        return x * x * (3.0 - 2.0 * x);
    }
}
