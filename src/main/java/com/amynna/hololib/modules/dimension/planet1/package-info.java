/**
 * Module {@code hololib:planet_1} : une enorme geante gazeuse cubique et son anneau carre dans le ciel de la
 * dimension (ancrage {@code Sky} non celeste : fixes par rapport au monde).
 * <p>
 * Geometrie : le repere du ciel est centre sur l'oeil. La planete ({@link
 * com.amynna.hololib.modules.dimension.planet1.Planet1Planet}) est a {@code 40} blocs ; l'anneau ({@link
 * com.amynna.hololib.modules.dimension.planet1.Planet1Ring}) partage son centre mais pas son orientation, et son plan
 * passe a quelques blocs de l'oeil : le monde est pris dans l'anneau, qui apparait comme une nappe striee traversant le
 * ciel. Un anneau plat vu exactement dans son plan serait invisible (vu par la tranche), d'ou ce petit decalage. Tout
 * tient sous le plan lointain minimal (128 blocs).
 * <p>
 * Dessin : {@code client.render.modules.dimension.planet1}.
 */
package com.amynna.hololib.modules.dimension.planet1;
