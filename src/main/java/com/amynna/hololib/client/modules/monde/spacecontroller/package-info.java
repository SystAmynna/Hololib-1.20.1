/**
 * Logique client du module {@code hololib:space_controller} :
 * {@link com.amynna.hololib.client.modules.monde.spacecontroller.SpaceControllerClient} installe le backend du module
 * (joueur local operateur, module Space Pilot du joueur local, fraction de tick de la derniere frame pour la cible du
 * saut) au setup client. Le pilotage lui-meme (capture, joystick au regard, saut, envois) est dans le module (code
 * commun, {@code SpacePilotPad}) ; les cercles du joystick, la cible du saut et la roue de charge sont dessines par
 * {@code client.render.modules.camera.spacepilot} (T19, T22). Conception : {@code docs/space/}.
 */
package com.amynna.hololib.client.modules.monde.spacecontroller;
