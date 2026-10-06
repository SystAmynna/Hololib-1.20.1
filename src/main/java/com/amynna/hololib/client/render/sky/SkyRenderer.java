package com.amynna.hololib.client.render.sky;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Dessin dans le ciel (skybox) : juste apres le ciel vanilla (degrade, soleil, lune, etoiles), avant le terrain. Tout
 * le reste du monde (terrain, nuages, entites) passe donc devant. Appele a {@code AFTER_SKY} par {@link SkyRenderPass}.
 * <p>
 * Le {@link PoseStack} recu est centre sur l'oeil et oriente comme le monde, sans translation : un objet suit la
 * camera comme le soleil, il parait infiniment loin. Le placer a {@link SkyRenderContext#SKY_RADIUS} blocs (distance
 * du soleil) ; au-dela de {@link SkyRenderContext#farPlane()} il serait coupe. Pour suivre le cycle jour/nuit,
 * appliquer {@link SkyRenderContext#applyCelestialRotation(PoseStack)}.
 * <p>
 * La profondeur est utilisable entre objets du ciel (un modele 3D se cache lui-meme) : elle est videe apres la passe.
 * La projection est celle du monde sans la translation du bobbing : un objet se comporte comme s'il etait a l'infini
 * (comme le soleil), il suit les oscillations du terrain sans parallaxe, quelle que soit sa distance.
 * Utiliser des {@code RenderType} de la cible principale et {@code LightTexture.FULL_BRIGHT}.
 */
public interface SkyRenderer
{
    /** Masque quand le ciel vanilla n'est pas dessine (voir {@link SkyRenderContext#skyVisible()}). */
    default boolean hiddenWithSky() { return true; }

    /** Appele chaque frame avant {@link #render} ; faux = rien a dessiner. */
    default boolean shouldRender(SkyRenderContext ctx) { return true; }

    /**
     * Dessine dans {@code buffers} ; le dispatcher vide les buffers apres chaque renderer. La pile doit etre rendue
     * equilibree.
     */
    void render(PoseStack pose, MultiBufferSource buffers, SkyRenderContext ctx);
}
