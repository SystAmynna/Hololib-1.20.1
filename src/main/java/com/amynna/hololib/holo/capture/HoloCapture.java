package com.amynna.hololib.holo.capture;

import net.minecraft.world.entity.player.Player;

/**
 * Contexte d'un callback de {@link HoloCaptor}. Interface commune, implementee cote client ; instance reutilisee (ne
 * pas la garder au-dela de l'appel). Angles en degres, conventions de Minecraft.
 */
public interface HoloCapture
{
    /** Joueur local. */
    Player player();

    /** Captor appele. */
    HoloCaptor captor();

    /** Duree de la frame precedente, en secondes (animations independantes du framerate). */
    float frameSeconds();

    /** Regard courant du joueur (verrouille pendant une capture de la souris). */
    float yaw();

    float pitch();

    /** Oriente le regard (pitch borne a +-90) ; sans effet si ce captor ne prend pas {@link HoloControl#MOUSE_MOVE}. */
    void setLook(float yaw, float pitch);

    /** Termine la capture explicite de ce captor ; sans effet pour un captor passif. */
    void release();
}
