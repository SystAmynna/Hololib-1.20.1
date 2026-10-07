package com.amynna.hololib.holo.edit;

import com.amynna.hololib.holo.object.HoloTransform;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

/**
 * Calculs d'edition sur les {@link HoloTransform} (conventions de {@code HoloAnchor} : yaw 0 = sud, positif vers la
 * droite, pitch positif vers le bas ; rotation {@code yaw, pitch, roll} appliquee dans l'ordre Y, X, Z). Instance
 * avec ses objets temporaires (aucune allocation par appel) : une instance par fil, jamais partagee.
 */
public final class HoloEditMath
{
    private final Quaternionf world = new Quaternionf();
    private final Quaternionf direction = new Quaternionf();
    private final Quaternionf tmp = new Quaternionf();
    private final Quaternionf tmp2 = new Quaternionf();
    private final Vector3f forward = new Vector3f();
    private final Vector3f euler = new Vector3f();

    /** Orientation dont la face {@code +Z} pointe dans la direction {@code (yaw, pitch)}. */
    public static Quaternionf yawPitch(Quaternionf dest, float yaw, float pitch)
    {
        return dest.rotationYXZ(-yaw * Mth.DEG_TO_RAD, pitch * Mth.DEG_TO_RAD, 0f);
    }

    /** Rotation du transform. */
    public static Quaternionf rotation(Quaternionf dest, HoloTransform transform)
    {
        return dest.rotationYXZ(-transform.yaw() * Mth.DEG_TO_RAD, transform.pitch() * Mth.DEG_TO_RAD,
                transform.roll() * Mth.DEG_TO_RAD);
    }

    /** Yaw (degres) de la direction {@code v}. */
    public static float yawOf(Vector3f v) { return (float) Math.atan2(-v.x, v.z) * Mth.RAD_TO_DEG; }

    /** Pitch (degres) de la direction unitaire {@code v}. */
    public static float pitchOf(Vector3f v) { return (float) Math.asin(Mth.clamp(-v.y, -1f, 1f)) * Mth.RAD_TO_DEG; }

    /** Pose la rotation du transform depuis un quaternion (decomposition Y, X, Z). */
    public HoloTransform setRotation(HoloTransform transform, Quaternionfc rotation)
    {
        rotation.getEulerAnglesYXZ(euler);
        return transform.setRotation(-euler.y * Mth.RAD_TO_DEG, euler.x * Mth.RAD_TO_DEG, euler.z * Mth.RAD_TO_DEG);
    }

    /** Vrai si les deux transforms ont la meme rotation, a {@code epsilon} pres (quelle que soit la decomposition). */
    public boolean sameRotation(HoloTransform a, HoloTransform b, float epsilon)
    {
        rotation(tmp, a);
        rotation(tmp2, b);
        return Math.abs(tmp.dot(tmp2)) >= 1f - epsilon;
    }

    /**
     * Reexprime un transform {@code Local} d'une base de sphere a une autre, sans changer le placement de l'objet :
     * {@code from} et {@code to} sont l'orientation de la sphere (identite pour un {@code Local} relatif, regard pour
     * un {@code Local} follow). Direction, distance, rotation et echelle sont recalculees dans {@code out} (qui peut
     * etre {@code source}).
     */
    public HoloTransform rebaseLocal(HoloTransform source, Quaternionfc from, Quaternionfc to, HoloTransform out)
    {
        // Orientation monde de l'objet : base * direction * demi-tour (face vers l'oeil) * rotation propre.
        yawPitch(direction, source.x(), source.y());
        world.set(from).mul(direction).rotateY(Mth.PI).mul(rotation(tmp, source));
        // Direction dans la nouvelle base.
        tmp.set(to).conjugate().mul(from).mul(direction);
        forward.set(0f, 0f, 1f).rotate(tmp);
        float yaw = yawOf(forward);
        float pitch = pitchOf(forward);
        // Rotation propre restante : (to * direction' * demi-tour)^-1 * monde.
        yawPitch(direction, yaw, pitch);
        tmp.set(to).mul(direction).rotateY(Mth.PI).conjugate().mul(world);
        float distance = source.z();
        float sx = source.scaleX();
        float sy = source.scaleY();
        float sz = source.scaleZ();
        out.setTranslation(yaw, pitch, distance);
        setRotation(out, tmp);
        return out.setScale(sx, sy, sz);
    }
}
