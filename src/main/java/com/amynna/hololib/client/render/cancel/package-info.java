/**
 * Annulation du rendu d'elements vanilla, partie client : etat des demandes
 * ({@link com.amynna.hololib.client.render.cancel.RenderCancels}, backend de {@code HoloRenderCanceler}), enveloppe des
 * effets de dimension (ciel, nuages, meteo) et remplissage du ciel avec un shaderpack
 * ({@link com.amynna.hololib.client.render.cancel.SkyCancelFill}). Les evenements Forge (main, reticule, debut de
 * frame) arrivent par {@code RenderDispatcher}.
 *
 * <h2>Points d'annulation (sans Mixin)</h2>
 * <ul>
 *     <li>{@code SKY}, {@code CLOUDS}, {@code WEATHER} : hooks Forge de {@code DimensionSpecialEffects}
 *     ({@code renderSky}, {@code renderClouds}, {@code renderSnowAndRain}), appeles en tete des methodes de
 *     {@code LevelRenderer}. Embeddium injecte en tete de {@code renderSky} (ciel saute dans le brouillard) et son
 *     renderer de nuages apres le hook : sans conflit. Les phases ciel d'Oculus entourent l'appel dans
 *     {@code renderLevel}.</li>
 *     <li>{@code SKY} : {@code getSunriseColor} de l'enveloppe ne renvoie aucune lueur, sinon
 *     {@code FogRenderer.setupColor} teinte la couleur du brouillard (donc le fond qui remplace le ciel) quand le
 *     regard se tourne vers le soleil.</li>
 *     <li>{@code SKY} avec un shaderpack : en plus, {@code SkyCancelFill} recouvre les pixels de profondeur 1 a
 *     {@code AFTER_LEVEL} (le pack y recalcule son ciel). Limites : le brouillard du terrain du pack tend vers le ciel
 *     du pack ; les nuages volumetriques calcules en composite ne sont pas annulables.</li>
 *     <li>{@code HAND} : {@code RenderHandEvent} (aussi poste par le rendu de la main d'Oculus).</li>
 *     <li>{@code CROSSHAIR}, {@code INVENTORY_HUD} : {@code RenderGuiOverlayEvent.Pre} des overlays vanilla
 *     correspondants ({@code CROSSHAIR} ; {@code HOTBAR}, {@code ITEM_NAME}, jauges de vie, armure, faim, air,
 *     experience, monture et barre de saut).</li>
 * </ul>
 * Pendant qu'un des trois premiers est annule, un mod qui teste la classe exacte de {@code level.effects()} voit
 * l'enveloppe : elle n'est installee que pendant ce temps.
 */
package com.amynna.hololib.client.render.cancel;
