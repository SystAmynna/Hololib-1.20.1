/**
 * Canal reseau ({@link com.amynna.hololib.network.HoloNetwork}) et paquets. Serveur vers client : snapshot d'une
 * portee, ajout/mise a jour/retrait de module, placement d'objet (ancrage + transform), origine d'une portee (marker
 * deplace), oubli. Client vers serveur : deplacement d'objet (valide par {@code HoloAnchor.acceptable}) et action de
 * module. Les handlers clients sont dans
 * {@code client.holo}, atteints via {@code DistExecutor}.
 */
package com.amynna.hololib.network;
