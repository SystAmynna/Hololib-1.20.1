/**
 * Module {@code hololib:quick_action} ({@link com.amynna.hololib.modules.camera.quickaction.QuickActionModule}) :
 * roue d'actions au centre de l'ecran, a l'ouverture du mode focus (camera normale).
 * <ul>
 *     <li>Roue ({@link com.amynna.hololib.modules.camera.quickaction.QuickActionWheelWindow}) : un secteur par action
 *     active autour d'une zone morte ; on vise en tournant la camera, un clic gauche ou droit execute l'action et
 *     masque la roue. Ignoree (regard trop loin sans rien choisir), elle se masque si le reglage le demande.</li>
 *     <li>Configuration (compatibilite avec Config) : roue affichee, masquage si ignoree, focus laisse actif apres une
 *     action, taille de la roue et de la zone morte, liste des actions activables et reordonnables au glisser.</li>
 * </ul>
 * <b>Declarer des actions</b> : implementer {@link com.amynna.hololib.modules.camera.quickaction.QuickActionProvider}
 * dans un module de la memoire de joueur et y creer ses
 * {@link com.amynna.hololib.modules.camera.quickaction.QuickAction} dans {@code onLoad} (nom + icone dessinee, voir
 * {@code demo.DemoQuickActionsModule}). Logique client : {@code client.modules.camera.quickaction}.
 */
package com.amynna.hololib.modules.camera.quickaction;
