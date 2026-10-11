/**
 * Logique client du module {@code hololib:space_pilot} :
 * {@link com.amynna.hololib.client.modules.camera.spacepilot.SpacePilotClient} retrouve le module de la memoire du
 * joueur local, une fois par tick, pour les renderers de Space Pilot ({@code client.render.modules.camera.spacepilot} :
 * joystick, saut, lien et anneau du referentiel, T19 a T33) et le Space Controller (D11), sans allocation par frame ;
 * {@link com.amynna.hololib.client.modules.camera.spacepilot.SpacePilotBackend} (T34) est le backend du module
 * ({@code SpacePilotModule.Backend}, installe au setup client) : regard de la camera pour la cible de la vitesse de
 * croisiere.
 * Conception : {@code docs/space/}.
 */
package com.amynna.hololib.client.modules.camera.spacepilot;
