/**
 * Module {@code hololib:space_environment} (memoire de dimension) et code commun partage par les quatre modules Space
 * (Environment, Controller, Configurator, Pilot). Dossier de conception : {@code docs/space/} (vision, architecture,
 * modele, mathematiques, synchronisation).
 * <p>
 * Quatre parties :
 * <ul>
 *     <li>{@code model} : donnees pures de la galaxie (systemes, corps, orbites, bornes), sauvegarde NBT et format
 *     reseau ({@code docs/space/03-modele-donnees.md}) ;</li>
 *     <li>{@code math} : calculs deterministes communs (unites, orbites de Kepler, pose du monde, mouvement, entree du
 *     pilote, saut interstellaire, passage systeme -> ciel ; {@code docs/space/04-mathematiques.md}) ;</li>
 *     <li>ce package : le module Space Environment, seule autorite sur la galaxie, la pose du monde, le saut et la
 *     cible du vaisseau (T43, posee par la Space Map),
 *     simule des deux cotes ; le Controller ({@code modules.monde.spacecontroller}) et le Configurator
 *     ({@code modules.monde.spaceconfigurator}) le modifient par ses methodes serveur publiques, Space Pilot
 *     ({@code modules.camera.spacepilot}) ne fait que lire son miroir client ; et
 *     {@code SpaceArchive}, {@code SavedData} du niveau qui garde une copie de son etat (sauvegarde fiable, retrait /
 *     reinstallation, T14) ; {@code SpaceFormat}, formats d'affichage partages (vitesse, distance, nombre a trois
 *     chiffres significatifs : fenetre du Controller, HUD de Space Pilot) ; {@code SpaceBodyTextures}, textures
 *     integrees des astres (ids, table nom -> id de Sol, regle "auto", resolution d'une reference de texture, T29 ;
 *     fichiers sous {@code assets/hololib/textures/space/}).</li>
 *     <li>{@code texture} (T45, D27) : canal reseau dedie aux textures importees ({@code hololib:space_textures}),
 *     stockage serveur par dimension, validation, envoi au seul client qui demande.</li>
 * </ul>
 * Code commun : aucune classe client ; le client passe par un backend installe au setup client. Dessin du ciel :
 * {@code client.render.modules.dimension.space}.
 */
package com.amynna.hololib.modules.dimension.space;
