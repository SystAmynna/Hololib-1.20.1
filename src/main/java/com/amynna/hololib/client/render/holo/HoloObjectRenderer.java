package com.amynna.hololib.client.render.holo;

import com.amynna.hololib.holo.object.HoloObject;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Dessin d'une classe de {@link HoloObject}, enregistre dans {@link HoloObjectRenderers}. Le meme renderer sert pour
 * toutes les familles (ciel, monde, vue) : la pile est deja placee dans le repere local de l'objet (1 unite = 1 bloc,
 * centre a l'origine, face visible vers {@code +Z}).
 * <p>
 * Utiliser des {@code RenderType} de la cible principale (pas {@code ITEM_ENTITY_TARGET}) et ne pas vider les buffers.
 * Lire l'etat de l'objet, ne pas le modifier.
 */
@FunctionalInterface
public interface HoloObjectRenderer<T extends HoloObject>
{
    /** @param packedLight lumiere a utiliser (pleine lumiere : un hologramme emet sa propre lumiere) */
    void render(T object, PoseStack pose, MultiBufferSource buffers, int packedLight, HoloDrawContext ctx);
}
