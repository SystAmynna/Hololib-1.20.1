/**
 * Inspection entre joueurs : par defaut, un joueur ne voit aucun affichage des autres joueurs. Un acces (A voit B) est
 * enregistre dans la memoire active de A ({@link com.amynna.hololib.holo.inspect.HoloInspection}) et applique par le
 * serveur ({@link com.amynna.hololib.holo.inspect.HoloInspections}) : B n'est envoye a A que si A suit son entite et
 * l'inspecte, et seulement pour ses objets {@code PUBLIC}. Les demandes non forcees passent par
 * {@link com.amynna.hololib.holo.inspect.HoloInspectEvent}, annulable (politiques d'acces futures).
 */
package com.amynna.hololib.holo.inspect;
