package com.amynna.hololib.client.render.view;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Dessin dans la vue du joueur, base sur la camera de rendu : apres le monde (tout ce qui est dessine passe devant les
 * blocs et les entites), avant la main et le GUI. Appele a {@code AFTER_LEVEL} par {@link ViewRenderPass}.
 * <p>
 * Le {@link PoseStack} recu est a l'identite, en <b>espace vue</b> : origine a l'oeil, {@code -Z} devant, {@code +Y}
 * en haut, {@code +X} a droite. Ce qui est dessine tel quel suit donc le regard ; pour placer un objet dans les axes du
 * monde (toujours visible, mais oriente comme le monde) appliquer d'abord
 * {@link ViewRenderContext#applyCameraRotation(PoseStack)}. La profondeur est videe avant le premier renderer : les
 * objets de la vue se cachent entre eux mais jamais derriere le monde.
 * <p>
 * Utiliser des {@code RenderType} qui ecrivent dans la cible principale (pas {@code ITEM_ENTITY_TARGET} : en Fabulous
 * cette cible a deja ete composee a ce stade, le dessin serait perdu). Le lightmap n'a pas de sens ici : passer
 * {@code LightTexture.FULL_BRIGHT}.
 */
public interface ViewRenderer
{
    /** Projection utilisee pour ce renderer. */
    enum Projection
    {
        /**
         * FOV configure dans les options (ni sprint, ni effets, ni eau, ni autre mod), sans bobbing, secousse de degats
         * ni nausee : l'objet ne tremble pas et ne change pas de taille.
         */
        STABLE,
        /** Projection exacte du monde (bobbing compris) : l'objet bouge avec le monde. */
        WORLD
    }

    /** Masque avec le GUI (F1). */
    default boolean hiddenWithGui() { return true; }

    default Projection projection() { return Projection.STABLE; }

    /** Appele chaque frame avant {@link #render} ; faux = rien a dessiner (aucun etat GL n'est touche). */
    default boolean shouldRender(ViewRenderContext ctx) { return true; }

    /**
     * Dessine dans {@code buffers} ; le dispatcher vide les buffers apres chaque renderer. La pile doit etre rendue
     * equilibree (autant de {@code popPose} que de {@code pushPose}).
     */
    void render(PoseStack pose, MultiBufferSource buffers, ViewRenderContext ctx);
}
