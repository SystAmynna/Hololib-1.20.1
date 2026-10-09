/**
 * Module {@code hololib:space_environment} (memoire de dimension) et code commun partage par les trois modules Space
 * (Environment, Controller, Configurator). Dossier de conception : {@code docs/space/} (vision, architecture, modele,
 * mathematiques, synchronisation).
 * <p>
 * Trois parties :
 * <ul>
 *     <li>{@code model} : donnees pures de la galaxie (systemes, corps, orbites, bornes), sauvegarde NBT et format
 *     reseau ({@code docs/space/03-modele-donnees.md}) ;</li>
 *     <li>{@code math} : calculs deterministes communs (unites, orbites de Kepler, pose du monde, mouvement, entree du
 *     pilote, passage systeme -> ciel ; {@code docs/space/04-mathematiques.md}) ;</li>
 *     <li>ce package : le module Space Environment, seule autorite sur la galaxie et la pose du monde, simule des deux
 *     cotes ; le Controller ({@code modules.monde.spacecontroller}) et le Configurator
 *     ({@code modules.monde.spaceconfigurator}) le modifient par ses methodes serveur publiques.</li>
 * </ul>
 * Code commun : aucune classe client ; le client passe par un backend installe au setup client. Dessin du ciel :
 * {@code client.render.modules.dimension.space}.
 */
package com.amynna.hololib.modules.dimension.space;
