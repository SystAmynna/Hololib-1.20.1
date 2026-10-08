/**
 * Modules editeurs ({@link com.amynna.hololib.modules.camera.editor.EditorCapabilityModule}) : chacun donne une
 * capacite d'edition, tous s'ouvrent par la touche d'edition (G), chacun peut etre desactive (etat sauvegarde).
 * <ul>
 *     <li>{@code hololib:own_fix_editor} : objets fixes de sa propre memoire ({@code Local} relatif, monde, ciel) ;</li>
 *     <li>{@code hololib:own_hud_editor} : objets HUD de sa propre memoire ; porte la fenetre du cadre HUD
 *     ({@link com.amynna.hololib.modules.camera.editor.EditorWindow},
 *     {@link com.amynna.hololib.modules.camera.editor.EditorHudFrame}) ;</li>
 *     <li>{@code hololib:own_dyn_editor} : objets dynamiques de sa propre memoire ({@code Local} follow,
 *     {@code HoloRelativeToOpen}), tenus dans le repere du regard a l'ouverture ; porte le repere de l'ecran
 *     ({@link com.amynna.hololib.modules.camera.editor.EditorReference}, cercle et cadre). La bascule fixe / dynamique
 *     n'est proposee qu'avec les deux own editors actifs ;</li>
 *     <li>{@code hololib:world_editor} : memoires de position ; {@code hololib:dimension_editor} : memoire de la
 *     dimension (objets dont le joueur a installe le module) ;</li>
 *     <li>{@code hololib:other_editor} (operateurs) : leve la propriete pour les deux precedents.</li>
 * </ul>
 * {@code hololib:editor_panel} ({@link com.amynna.hololib.modules.camera.editor.EditorPanelModule}) : activation des
 * editeurs dans la couche de configuration ({@code Configurable} : fenetre "Editeurs" de la liste Config, un
 * interrupteur par editeur equipe).
 * <p>
 * Objets d'interface ({@code HoloEditorUi}) prives et masques ({@code OFF}) hors session. Logique client : {@code
 * client.modules.camera.editor.EditorController} et la session {@code client.edit.HoloEditSession} ; dessin :
 * {@code client.render.modules.camera.editor}.
 */
package com.amynna.hololib.modules.camera.editor;
