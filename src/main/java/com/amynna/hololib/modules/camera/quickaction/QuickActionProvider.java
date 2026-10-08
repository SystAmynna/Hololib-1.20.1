package com.amynna.hololib.modules.camera.quickaction;

import java.util.List;

/**
 * Couche de compatibilite avec le module {@code hololib:quick_action} ({@link QuickActionModule}) : un
 * {@code HoloModule} de la memoire de joueur qui implemente cette interface declare des actions pour la roue.
 * L'implementer <b>est</b> la declaration : Quick Action decouvre lui-meme les modules {@code QuickActionProvider} de
 * la memoire active (rien a enregistrer ni a retirer).
 * <p>
 * Une action est reconnue par {@code <id du type du module>/<id de l'action>} : ordre et activation choisis par le
 * joueur sont sauvegardes sous cette cle.
 * <p>
 * La roue est publique : pour qu'un joueur qui inspecte le proprietaire y voie les actions, le module doit lui etre
 * envoye ({@code HoloModule.sharedWithObservers()} vrai, ou un objet {@code PUBLIC}).
 */
public interface QuickActionProvider
{
    /** Actions du module, dans l'ordre par defaut ; liste stable creee dans {@code onLoad}. */
    List<QuickAction> quickActions();
}
