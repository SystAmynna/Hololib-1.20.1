/**
 * Modules du mod (hors demonstrations), enregistres dans {@link com.amynna.hololib.modules.HoloLibModules}.
 * <p>
 * Organisation : un sous-package par compatibilite ({@code dimension}, puis {@code monde} pour les modules de
 * position et {@code camera} pour les modules de joueur), et dans chacun un package par module contenant tout son code
 * commun (module, objets). Les renderers des objets sont client et vivent en miroir dans
 * {@code com.amynna.hololib.client.render.modules} (meme arborescence).
 */
package com.amynna.hololib.modules;
