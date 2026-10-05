/**
 * Objets holographiques et leur placement. Un objet ne choisit pas un renderer : il choisit un
 * {@link com.amynna.hololib.holo.object.HoloAnchor} ({@code Sky}, {@code World}, {@code Local} relatif ou follow,
 * {@code Hud}), que le client resout par observateur en famille de renderers. Changer l'ancrage en runtime change donc
 * la famille. Sa position dans le repere de l'ancrage est son
 * {@link com.amynna.hololib.holo.object.HoloTransform} (translation, rotation, echelle), mutable et interprete selon
 * l'ancrage : tel quel au ciel, depuis l'hote dans le monde, angles + distance en camera, pixels GUI + ordre en HUD.
 */
package com.amynna.hololib.holo.object;
