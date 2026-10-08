package com.amynna.hololib.modules.camera.config;

import net.minecraft.network.chat.Component;

/**
 * Couche de compatibilite avec le module {@code hololib:config} ({@link ConfigModule}) : un {@code HoloModule} de la
 * memoire de joueur qui implemente cette interface est configurable en jeu. L'implementer <b>est</b> la declaration :
 * Config liste lui-meme les modules {@code Configurable} de la memoire active (rien a enregistrer ni a retirer), et un
 * clic sur la ligne du module ouvre sa fenetre de configuration devant le regard.
 * <p>
 * Le module detient sa fenetre ({@link ConfigWindow}, objet du module cree dans {@code onLoad}, des deux cotes) et
 * place ses widgets dans {@link ConfigWindow#content()} ; Config se charge de l'ouvrir, de la fermer et de l'epingler.
 * Les reglages passent par l'etat du module ({@code sendAction}, valide par le serveur), comme tout widget.
 */
public interface Configurable
{
    /** Nom du module dans la liste de Config. */
    Component configName();

    /** Fenetre de configuration du module (un de ses objets). */
    ConfigWindow configWindow();
}
