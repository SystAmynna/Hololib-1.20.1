/**
 * Widgets interactifs generiques, code commun.
 *
 * <h2>Traits</h2>
 * Interfaces de comportement a methodes par defaut, a implementer par le composant final (un {@code HoloWidget}
 * d'un module) : chacune branche les callbacks d'interaction ou de capture et ne demande que l'etat et les reactions
 * propres au composant.
 * <ul>
 *     <li>{@link com.amynna.hololib.holo.widget.control.HoloHoverable} : etat de survol, {@code onHoverChanged}.</li>
 *     <li>{@link com.amynna.hololib.holo.widget.control.HoloClickable} : clics gauche / droit / molette ramenes a
 *     {@code onClick(ctx, bouton)}.</li>
 *     <li>{@link com.amynna.hololib.holo.widget.control.HoloScrollable} : molette en crans entiers.</li>
 *     <li>{@link com.amynna.hololib.holo.widget.control.HoloToggleable} : interrupteur ON / OFF.</li>
 *     <li>{@link com.amynna.hololib.holo.widget.control.HoloDraggable} : glisser au regard (clic maintenu).</li>
 *     <li>{@link com.amynna.hololib.holo.widget.control.HoloTextInput} : saisie de texte (capture du clavier, sortie
 *     par Echap ou par un regard qui s'eloigne), sur un
 *     {@link com.amynna.hololib.holo.widget.control.HoloTextBuffer}.</li>
 *     <li>{@link com.amynna.hololib.holo.widget.control.HoloPointerInput} : capture des mouvements de la souris.</li>
 *     <li>{@link com.amynna.hololib.holo.widget.control.HoloDirectionalInput} : capture des controles de deplacement
 *     ({@link com.amynna.hololib.holo.widget.control.HoloDirections}).</li>
 * </ul>
 * Les traits se combinent : chaque famille de controles a sa propre methode d'interception
 * ({@code interceptsClick}, {@code interceptsScroll}, {@code interceptsKey}), il n'y a donc pas de conflit entre, par
 * exemple, un clic et la molette. Si deux traits fournissent le meme defaut (deux captures differentes), le composant
 * le redefinit et appelle {@code Trait.super.methode(...)}.
 *
 * <h2>Widgets prets a l'emploi</h2>
 * Classes non finales, heritables (couleurs, textes, {@code draw} et hooks proteges), prefixees {@code HoloWidget} :
 * {@code HoloWidgetButton}, {@code HoloWidgetMultiButton}, {@code HoloWidgetHoverZone}, {@code HoloWidgetScrollZone},
 * {@code HoloWidgetSwitch}, {@code HoloWidgetValueBar}, {@code HoloWidgetTextBox}, {@code HoloWidgetMouseBox},
 * {@code HoloWidgetDraggable}, {@code HoloWidgetKeyBox}, {@code HoloWidgetList} (liste encadree scrollable),
 * {@code HoloWidgetDragRow} (ligne d'une liste, reordonnee au glisser).
 * Leurs ecouteurs s'executent cote client : pour agir sur le
 * jeu, envoyer une action au module ({@code HoloModule.sendAction(source, ...)}), que le serveur valide, puis reposer
 * l'etat synchronise ({@code setValue}, {@code setOn}, {@code setText}...).
 */
package com.amynna.hololib.holo.widget.control;
