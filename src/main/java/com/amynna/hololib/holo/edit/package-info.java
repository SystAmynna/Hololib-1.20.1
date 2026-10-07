/**
 * Edition en jeu des objets holographiques, partie <b>commune</b> (aucune classe client).
 * <ul>
 *     <li>Interfaces implementees par un {@code HoloObject} : {@link com.amynna.hololib.holo.edit.HoloEditable}
 *     (base : {@code ownRequired}, {@code opRequired} ; {@link com.amynna.hololib.holo.edit.HoloEditorUi} pour
 *     l'interface de l'editeur) et un outil chacune :
 *     {@link com.amynna.hololib.holo.edit.HoloEditTranslation}, {@link com.amynna.hololib.holo.edit.HoloEditRotation},
 *     {@link com.amynna.hololib.holo.edit.HoloEditScale}, {@link com.amynna.hololib.holo.edit.HoloEditVisibility},
 *     {@link com.amynna.hololib.holo.edit.HoloEditFixOrDynamic}.</li>
 *     <li>{@link com.amynna.hololib.holo.edit.HoloDisplay} : quand un objet est dessine ({@code OFF}, {@code FOCUS},
 *     {@code ON}), synchronise par le framework ; un objet ancre {@code World} a aussi une portee d'edition
 *     ({@code HoloObject.editRange()}, editable).</li>
 *     <li>{@link com.amynna.hololib.holo.edit.HoloRelativeToOpen} : objet replace devant le regard a chaque ouverture
 *     du mode focus, depuis sa position relative ({@code HoloObject.openOffset()}).</li>
 *     <li>{@link com.amynna.hololib.holo.edit.HoloEditRules} : qui peut editer, et ce qu'une modification a le droit
 *     de changer (memes regles cote client et serveur) ; {@link com.amynna.hololib.holo.edit.HoloEditMath} :
 *     conversions de rotation et de repere ({@code Local} relatif <-> follow).</li>
 * </ul>
 * L'edition ne depend pas du droit d'usage ({@code HoloAccess}) : l'objet de l'outil actif est visable et manipulable
 * meme sans acces. Capacites : un module de memoire de joueur {@link com.amynna.hololib.holo.edit.HoloEditorModule}
 * ouvre une portee ({@link com.amynna.hololib.holo.edit.HoloEditorCapability} : sa memoire, positions, dimension) ou
 * leve la propriete (Other Editor, operateurs). La session d'edition (outils, boutons, gizmos) est cote client : {@code client.edit}.
 * Une modification part au serveur par {@code HoloEditObjectPacket} ; le serveur la valide ou renvoie son etat.
 */
package com.amynna.hololib.holo.edit;
