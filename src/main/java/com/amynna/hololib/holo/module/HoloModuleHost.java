package com.amynna.hololib.holo.module;

import com.amynna.hololib.holo.memory.HoloScope;
import net.minecraftforge.fml.LogicalSide;

/**
 * Contexte d'une instance de {@link HoloModule}.
 *
 * @param scope     portee (memoire) a laquelle appartient le module
 * @param side      {@code SERVER} : instance faisant autorite (logique, sauvegarde) ; {@code CLIENT} : miroir
 * @param ownerView vrai si l'instance voit l'etat complet (serveur, ou client du joueur proprietaire) ; faux pour un
 *                  observateur, qui ne recoit que l'etat public
 */
public record HoloModuleHost(HoloScope scope, LogicalSide side, boolean ownerView)
{
    public boolean clientSide() { return side == LogicalSide.CLIENT; }

    /**
     * Vrai si ce miroir client peut envoyer des actions au serveur : client du proprietaire, ou n'importe quel
     * destinataire d'une portee sans proprietaire (dimension, position).
     */
    public boolean canAct() { return clientSide() && (ownerView || scope.owner() == null); }
}
