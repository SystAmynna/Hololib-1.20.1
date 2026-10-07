/**
 * Modules editeurs ({@link com.amynna.hololib.modules.camera.editor.EditorCapabilityModule}) : chacun donne une
 * capacite d'edition, tous s'ouvrent par la touche d'edition (G), chacun peut etre desactive (etat sauvegarde).
 * <ul>
 *     <li>{@code hololib:own_editor} : sa propre memoire ; porte le repere de l'ecran
 *     ({@link com.amynna.hololib.modules.camera.editor.EditorReference}, base des fenetres dynamiques) et la fenetre
 *     du cadre HUD ({@link com.amynna.hololib.modules.camera.editor.EditorWindow},
 *     {@link com.amynna.hololib.modules.camera.editor.EditorHudFrame}) ;</li>
 *     <li>{@code hololib:world_editor} : memoires de position ; {@code hololib:dimension_editor} : memoire de la
 *     dimension (objets dont le joueur a installe le module) ;</li>
 *     <li>{@code hololib:other_editor} (operateurs) : leve la propriete pour les deux precedents.</li>
 * </ul>
 * {@code hololib:editor_panel} : fenetre ({@link com.amynna.hololib.modules.camera.editor.EditorPanelWindow}) qui liste
 * les editeurs equipes et les active / desactive, visible en session a partir de deux editeurs.
 * <p>
 * Objets d'interface ({@code HoloEditorUi}) prives et masques ({@code OFF}) hors session. Logique client : {@code
 * client.modules.camera.editor.EditorController} et la session {@code client.edit.HoloEditSession} ; dessin :
 * {@code client.render.modules.camera.editor}.
 */
package com.amynna.hololib.modules.camera.editor;
