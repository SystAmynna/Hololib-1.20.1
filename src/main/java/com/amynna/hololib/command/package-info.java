/**
 * Commandes. Operateurs : {@code /hololib_marker} (creer / supprimer des markers), {@code /hololib_module} (ajouter
 * / retirer des modules d'une memoire de dimension, de position ou de joueur), {@code /holo_memory} (lister et remettre
 * a zero les sauvegardes des modules d'une memoire ; ajouter, copier, retirer, lister les memoires d'un joueur) et
 * {@code /hololib_force_inspect}. Tous :
 * {@code /hololib_inspect} (acces d'inspection entre joueurs, soumis a {@code HoloInspectEvent}). {@code ModuleSlot} masque la difference
 * entre memoire chargee et memoire au repos ; {@code ModuleListArgument} gere la liste de modules (ajout, retrait,
 * remise a zero) et son autocompletion filtree.
 */
package com.amynna.hololib.command;
