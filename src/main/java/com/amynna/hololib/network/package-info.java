/**
 * Canal reseau ({@link com.amynna.hololib.network.HoloNetwork}) et paquets. Serveur vers client : snapshot d'une
 * portee, ajout/mise a jour/retrait de module, placement d'objet (ancrage + transform), origine d'une portee (marker
 * deplace), oubli. Client vers serveur : deplacement d'objet par son proprietaire (valide par
 * {@code HoloAnchor.acceptable}), action de module, et edition en jeu d'un objet de toute portee
 * ({@link com.amynna.hololib.network.HoloEditObjectPacket}, validee par {@code HoloEditRules}). Les handlers clients
 * sont dans {@code client.holo}, atteints via {@code DistExecutor}. Protocole {@code "6"}.
 */
package com.amynna.hololib.network;
