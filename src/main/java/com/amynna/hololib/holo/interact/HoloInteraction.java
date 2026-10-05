package com.amynna.hololib.holo.interact;

import com.amynna.hololib.holo.capture.HoloCaptor;
import com.amynna.hololib.holo.object.HoloObject;
import net.minecraft.world.entity.player.Player;

/**
 * Contexte d'un callback de {@link HoloInteractible}. Interface commune, implementee cote client ; instance reutilisee
 * (ne pas la garder au-dela de l'appel). Angles en degres, conventions de Minecraft.
 */
public interface HoloInteraction
{
    /** Joueur local, qui interagit. */
    Player player();

    /** Objet holographique vise (contenant l'element appele). */
    HoloObject object();

    /** Vrai si le regard est actuellement sur l'objet (faux pour un relachement apres avoir detourne le regard). */
    boolean hovering();

    /**
     * Point vise dans le repere de l'element appele : pixels depuis son coin haut gauche pour un widget ou une
     * fenetre, plan local {@code z = 0} de l'objet sinon. Dernier point connu si {@link #hovering()} est faux.
     */
    float x();

    float y();

    /** Duree de la frame precedente, en secondes (animations independantes du framerate). */
    float frameSeconds();

    /** Regard courant du joueur. */
    float yaw();

    float pitch();

    /** Direction de l'oeil vers le centre de l'objet (regard courant pour un objet du HUD). */
    float objectYaw();

    float objectPitch();

    /**
     * Commence une capture explicite ({@code HoloCaptures.begin}) <b>liee a cet objet</b> : comme une capture
     * ordinaire elle dure jusqu'a sa liberation, mais elle est aussi terminee si l'objet disparait, n'est plus
     * dessine ou n'est plus utilisable par le joueur. Faux si le captor capture deja.
     */
    boolean capture(HoloCaptor captor);
}
