package com.amynna.hololib.client.render.sky;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Vector3f;

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
 * <p>
 * Un renderer du ciel peut aussi fixer la couleur du brouillard et du fond de l'ecran ({@link #fogColor}), calculee
 * avant la passe : c'est elle qui teinte l'horizon et le terrain lointain, meme si le ciel vanilla est recouvert.
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

    /**
     * Couleur du brouillard et du fond de l'ecran de la frame, appelee avant la passe du ciel (evenement
     * {@code ViewportEvent.ComputeFogColor}, poste par {@code FogRenderer.setupColor}), dans l'ordre d'enregistrement :
     * chaque renderer recoit la couleur laissee par le precedent et peut la modifier ({@code rgb} entre 0 et 1). Ne
     * rien faire par defaut. Appelee meme quand {@link #shouldRender} ou {@link #hiddenWithSky} masqueraient le
     * renderer : c'est a lui de decider. Respecter le type de brouillard de la camera
     * ({@code camera.getFluidInCamera()} : eau, lave, neige poudreuse : laisser vanilla). Une exception est loguee
     * (une fois par renderer) et la couleur recue est conservee.
     *
     * @param rgb couleur courante (x = rouge, y = vert, z = bleu), a modifier en place ; ne pas la conserver
     */
    default void fogColor(Camera camera, float partialTick, Vector3f rgb) {}
}
