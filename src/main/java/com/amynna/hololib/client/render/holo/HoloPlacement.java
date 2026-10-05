package com.amynna.hololib.client.render.holo;

import com.amynna.hololib.holo.object.HoloTransform;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Calculs de placement partages par les ponts (sans allocation : objets temporaires fournis par l'appelant). Chaque
 * famille place d'abord le repere d'origine de l'objet selon son ancrage, puis {@link #applyLocal} applique la rotation
 * et l'echelle du {@link HoloTransform}.
 */
final class HoloPlacement
{
    private HoloPlacement() {}

    /** Orientation dont la face {@code +Z} pointe dans la direction {@code (yaw, pitch)} de Minecraft. */
    static Quaternionf yawPitch(Quaternionf dest, float yaw, float pitch)
    {
        return dest.rotationYXZ(-yaw * Mth.DEG_TO_RAD, pitch * Mth.DEG_TO_RAD, 0f);
    }

    /** Rotation du transform : yaw et pitch comme {@link #yawPitch}, puis roll autour de l'axe {@code +Z} de l'objet. */
    static Quaternionf rotation(Quaternionf dest, HoloTransform transform)
    {
        return dest.rotationYXZ(-transform.yaw() * Mth.DEG_TO_RAD, transform.pitch() * Mth.DEG_TO_RAD,
                transform.roll() * Mth.DEG_TO_RAD);
    }

    /** Rotation puis echelle du transform, dans le repere d'origine deja place. */
    static void applyLocal(PoseStack pose, Quaternionf tmp, HoloTransform transform)
    {
        pose.mulPose(rotation(tmp, transform));
        pose.scale(transform.scaleX(), transform.scaleY(), transform.scaleZ());
    }

    /**
     * Place la pile sur une sphere de rayon {@code radius} centree sur son origine, dans la direction
     * {@code (yaw, pitch)} exprimee dans les axes de la pile (axes de Minecraft : {@code +Z} devant, {@code +X} a
     * gauche), face {@code +Z} tournee vers le centre. Sert a {@code Local} relatif (pile orientee comme le monde) et a
     * {@code Local} follow (pile orientee comme la camera).
     */
    static void onSphere(PoseStack pose, Quaternionf tmp, float yaw, float pitch, float radius)
    {
        pose.mulPose(yawPitch(tmp, yaw, pitch));
        pose.translate(0f, 0f, radius);
        pose.mulPose(tmp.rotationY(Mth.PI));
    }

    /**
     * Equivalent de {@link #onSphere} suivi de la rotation du transform, en calcul pur (ancrage {@code Local} dessine
     * dans le monde). {@code orientation} doit contenir la base de la sphere (identite en mode relatif, regard en mode
     * follow) ; elle recoit l'orientation finale de l'objet et {@code offset} sa position relative au centre.
     */
    static void onSphere(Quaternionf orientation, Vector3f offset, Quaternionf tmp, HoloTransform transform)
    {
        orientation.mul(yawPitch(tmp, transform.x(), transform.y()));
        offset.set(0f, 0f, transform.z()).rotate(orientation);
        orientation.rotateY(Mth.PI).mul(rotation(tmp, transform));
    }
}
