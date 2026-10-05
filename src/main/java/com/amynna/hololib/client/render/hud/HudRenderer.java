package com.amynna.hololib.client.render.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Dessin a l'ecran, comme un HUD : projection orthographique du GUI, avant tous les elements du HUD vanilla (qui
 * restent toujours devant). Appele par {@link HudRenderPass} depuis un overlay Forge enregistre sous tous les autres.
 * Masque avec le GUI (F1) ; aussi dessine sous un ecran ouvert.
 * <p>
 * Le {@link PoseStack} recu est celui du GUI : pixels GUI, origine en haut a gauche, {@code y} vers le bas,
 * {@code +Z} vers l'observateur. La profondeur est utilisable entre objets du HUD ; elle est videe apres la passe.
 * Pas de lightmap : passer {@code LightTexture.FULL_BRIGHT}.
 */
public interface HudRenderer
{
    /** Appele chaque frame avant {@link #render} ; faux = rien a dessiner. */
    default boolean shouldRender(HudRenderContext ctx) { return true; }

    /**
     * Dessine dans {@code buffers} ; le dispatcher vide les buffers apres chaque renderer. La pile doit etre rendue
     * equilibree.
     */
    void render(PoseStack pose, MultiBufferSource buffers, HudRenderContext ctx);
}
