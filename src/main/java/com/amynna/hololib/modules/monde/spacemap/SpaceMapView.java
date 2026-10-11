package com.amynna.hololib.modules.monde.spacemap;

import com.amynna.hololib.modules.dimension.space.model.SpaceSystem;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem.SystemHeader;

import java.util.List;

/**
 * Vue d'un {@link SpaceMapCube} : ce que le renderer du cube lit pour le dessiner (copie synchronisee de la galaxie,
 * cadrage, selection, temps affiche). Implementee par le module qui porte le cube : {@link SpaceMapModule} (T37 ; le
 * Space Configurator, qui l'implementait aussi pour son cube provisoire, n'a plus de cube depuis T42 : C110 levee).
 * Lecture seule, sans allocation ; deux cotes.
 * <p>
 * Repere : le cube est {@code [-1, 1]^3} ({@link SpaceMapCube#HALF_SIZE}) ; galaxie {@code q = (g - c) / H}
 * ({@link #frameX()}..{@link #frameHalf()}, al) ; systeme {@code q = (p - p_centre) / H} ({@link #centerIndex()},
 * {@link #viewHalf()} en UA, {@link SpaceSystemScale#positions}).
 */
public interface SpaceMapView
{
    /** Vrai si le cube montre un systeme ({@link #shownSystem()}), faux pour la carte galactique. */
    boolean systemView();

    /** Vrai si la dimension du marker a un Space Environment (copie). */
    boolean environmentPresent();

    /** En-tetes de tous les systemes (copie), dans l'ordre de la galaxie ; liste non modifiable. */
    List<SystemHeader> headers();

    /** Id du systeme courant de l'Environment (copie), {@code 0} sans Environment. */
    int currentSystemId();

    /** Systeme origine effectif des coordonnees galactiques ({@link SpaceCoordinates#origin}), {@code null} sans systeme. */
    SystemHeader originHeader();

    /** Centre du cadrage galactique, al. */
    double frameX();

    double frameY();

    double frameZ();

    /** Demi-etendue du cadrage galactique, al ; {@code <= 0} : pas encore cadre (rien n'est dessine). */
    double frameHalf();

    /** Id du systeme selectionne (carte galactique), {@code 0} = aucun. */
    int selectedSystem();

    /** Systeme montre par la vue systeme (copie complete), {@code null} hors de la vue systeme. */
    SpaceSystem shownSystem();

    /** Id du corps selectionne dans {@link #shownSystem()}, {@code 0} = aucun. */
    int selectedBody();

    /** Indice du corps au centre de la vue systeme, {@code -1} = origine du systeme. */
    int centerIndex();

    /** Demi-etendue de la vue systeme, UA. */
    double viewHalf();

    /** Temps simule affiche par la vue systeme, en jours (meme valeur pour tous les appels d'une frame). */
    double displayDays(float partialTick);

    /**
     * Id d'un corps dont l'orbite est dessinee par un autre objet (poignee d'orbite du Configurator du meme marker,
     * T42), {@code 0} sinon.
     */
    default int hiddenOrbitBody() { return 0; }

    /** Client : vrai si le cube est dessine pour le joueur local ({@code shown()}). */
    default boolean cubeShown() { return true; }

    /** Vrai si la molette sur le cube zoome la vue ({@link #requestZoom}). */
    default boolean zoomable() { return false; }

    /** Client : zoom de la vue de {@code steps} crans (positif = rapproche) demande depuis le cube {@code source}. */
    default void requestZoom(SpaceMapCube source, int steps) {}
}
