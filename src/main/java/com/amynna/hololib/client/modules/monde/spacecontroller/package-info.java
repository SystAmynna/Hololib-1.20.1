/**
 * Logique client du module {@code hololib:space_controller} :
 * {@link com.amynna.hololib.client.modules.monde.spacecontroller.SpaceControllerClient} installe le backend du module
 * (joueur local, joueur local operateur) au setup client. Le pilotage lui-meme (capture, joystick au regard, envois)
 * est dans le module (code commun, {@code SpacePilotPad}) ; la mini-interface du joystick est dessinee par
 * {@code client.render.modules.monde.spacecontroller}. Conception : {@code docs/space/}.
 */
package com.amynna.hololib.client.modules.monde.spacecontroller;
