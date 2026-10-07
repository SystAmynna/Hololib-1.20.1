package com.amynna.hololib.client.render.holo;

import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloTransform;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
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
     * Repere d'un overlay place par {@link HoloAttachment#place}, la pile etant centree sur le point de vue et orientee
     * comme le monde.
     */
    static void overlay(PoseStack pose, Vector3f position, Quaternionf orientation, float scale)
    {
        pose.translate(position.x, position.y, position.z);
        pose.mulPose(orientation);
        pose.scale(scale, scale, scale);
    }

    /** Equivalent de {@link #overlay(PoseStack, Vector3f, Quaternionf, float)} sur une matrice. */
    static void overlay(Matrix4f matrix, Vector3f position, Quaternionf orientation, float scale)
    {
        matrix.translate(position).rotate(orientation).scale(scale);
    }

    /**
     * Centre et orientation d'un objet {@code Local} dans les axes du monde, relatifs a l'oeil (calcul pur de
     * {@link #onSphere(Quaternionf, Vector3f, Quaternionf, HoloTransform)}).
     *
     * @param yaw   regard de l'oeil (base de la sphere en follow)
     * @param pitch regard de l'oeil
     */
    static void local(Quaternionf orientation, Vector3f center, Quaternionf tmp, HoloAnchor.Local anchor,
                      HoloTransform transform, float yaw, float pitch)
    {
        if (anchor.follow()) {
            yawPitch(orientation, yaw, pitch);
        } else {
            orientation.identity();
        }
        onSphere(orientation, center, tmp, transform);
    }

    /**
     * Centre et orientation d'un objet {@code Sky} dans les axes du monde, relatifs a l'oeil : rotation celeste vanilla
     * ({@code SkyRenderContext.applyCelestialRotation}) si l'ancrage est celeste, puis le transform.
     *
     * @param timeOfDay {@code ClientLevel.getTimeOfDay(partialTick)}
     */
    static void sky(Quaternionf orientation, Vector3f center, Quaternionf tmp, HoloAnchor.Sky anchor,
                    HoloTransform transform, float timeOfDay)
    {
        if (anchor.celestial()) {
            orientation.rotationY(-90f * Mth.DEG_TO_RAD).rotateX(timeOfDay * 360f * Mth.DEG_TO_RAD);
        } else {
            orientation.identity();
        }
        center.set(transform.x(), transform.y(), transform.z()).rotate(orientation);
        orientation.mul(rotation(tmp, transform));
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
