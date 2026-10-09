package com.amynna.hololib.modules.dimension.space.math;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Pose du monde dans le systeme courant (voir {@code docs/space/04-mathematiques.md} par. 2) : position {@code s} du
 * monde en km (repere systeme) et orientation {@code Q} (quaternion unitaire) qui envoie une direction des axes du
 * monde vers les axes du systeme ({@code v_S = Q v_W}). Le ciel vu depuis le monde est l'inverse :
 * {@code p_W = Q^-1 (p_S - s)} ({@link SpaceSkyFrame}).
 * <p>
 * Objet <b>mutable</b>, reutilisable (aucune allocation pour le copier, l'interpoler ou le lire) ; {@link #position()}
 * et {@link #orientation()} rendent les instances internes, modifiables. Non thread-safe.
 */
public final class SpacePose
{
    /** Ecart toleree a la norme 1 d'une orientation recue du reseau (au-dela : paquet invalide). */
    private static final double NORM_TOLERANCE = 1.0e-6;

    private final Vector3d position = new Vector3d();
    private final Quaterniond orientation = new Quaterniond();

    /** Pose a l'origine du systeme, axes du monde alignes sur ceux du systeme. */
    public SpacePose() {}

    /** Position du monde, en km (repere systeme) ; instance interne, modifiable. */
    public Vector3d position() { return position; }

    /** Orientation monde -> systeme, unitaire ; instance interne, modifiable (a garder unitaire). */
    public Quaterniond orientation() { return orientation; }

    // ---------------------------------------------------------------- affectation

    /** Copie {@code other} dans cette pose. */
    public SpacePose set(SpacePose other)
    {
        position.set(other.position);
        orientation.set(other.orientation);
        return this;
    }

    /** Pose les composantes (l'orientation doit etre unitaire). */
    public SpacePose set(double x, double y, double z, double qx, double qy, double qz, double qw)
    {
        position.set(x, y, z);
        orientation.set(qx, qy, qz, qw);
        return this;
    }

    /** Origine, identite. */
    public SpacePose reset()
    {
        position.zero();
        orientation.identity();
        return this;
    }

    /** Nouvelle pose egale a celle-ci (alloue : pas dans un chemin par frame). */
    public SpacePose copy() { return new SpacePose().set(this); }

    /** Vrai si toutes les composantes sont finies. */
    public boolean isFinite()
    {
        return Double.isFinite(position.x) && Double.isFinite(position.y) && Double.isFinite(position.z)
                && Double.isFinite(orientation.x) && Double.isFinite(orientation.y)
                && Double.isFinite(orientation.z) && Double.isFinite(orientation.w);
    }

    // ---------------------------------------------------------------- comparaison, interpolation

    /** Distance entre les positions des deux poses, en km. */
    public double distanceTo(SpacePose other) { return position.distance(other.position); }

    /** Angle de la rotation qui mene de cette orientation a celle de {@code other}, en radians {@code [0, pi]}. */
    public double angleTo(SpacePose other)
    {
        double dot = Math.abs(orientation.x * other.orientation.x + orientation.y * other.orientation.y
                + orientation.z * other.orientation.z + orientation.w * other.orientation.w);
        return 2.0 * Math.acos(Math.min(1.0, dot));
    }

    /**
     * Interpolation d'affichage : {@code lerp} des positions et {@code slerp} (chemin le plus court) des orientations,
     * {@code t} dans {@code [0, 1]}. {@code dest} peut etre {@code a} ou {@code b}. Sans allocation. Non deterministe
     * bit a bit (JOML) : reserve au rendu, jamais a la simulation.
     */
    public static SpacePose lerp(SpacePose a, SpacePose b, double t, SpacePose dest)
    {
        dest.position.set(a.position.x + (b.position.x - a.position.x) * t,
                a.position.y + (b.position.y - a.position.y) * t,
                a.position.z + (b.position.z - a.position.z) * t);
        a.orientation.slerp(b.orientation, t, dest.orientation);
        dest.orientation.normalize();
        return dest;
    }

    // ---------------------------------------------------------------- reseau (ordre : x, y, z, qx, qy, qz, qw)

    public void write(FriendlyByteBuf buf)
    {
        buf.writeDouble(position.x);
        buf.writeDouble(position.y);
        buf.writeDouble(position.z);
        buf.writeDouble(orientation.x);
        buf.writeDouble(orientation.y);
        buf.writeDouble(orientation.z);
        buf.writeDouble(orientation.w);
    }

    /**
     * Lecture stricte <b>dans cette pose</b> : leve (sans rien modifier) sur une valeur non finie ou une orientation
     * non unitaire. L'orientation n'est <b>pas</b> renormalisee : le client garde les bits exacts du serveur, pour que
     * le rejeu de {@link SpaceMotion#step} donne le meme resultat des deux cotes.
     */
    public SpacePose read(FriendlyByteBuf buf)
    {
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        double qx = buf.readDouble();
        double qy = buf.readDouble();
        double qz = buf.readDouble();
        double qw = buf.readDouble();
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Position de pose non finie");
        }
        double norm = qx * qx + qy * qy + qz * qz + qw * qw;
        if (!Double.isFinite(norm) || Math.abs(norm - 1.0) > NORM_TOLERANCE) {
            throw new IllegalArgumentException("Orientation de pose invalide (norme^2 = " + norm + ")");
        }
        return set(x, y, z, qx, qy, qz, qw);
    }

    // ---------------------------------------------------------------- NBT

    /** Format : {@code { x, y, z, qx, qy, qz, qw }} (cles courtes et stables). */
    public CompoundTag save()
    {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("x", position.x);
        tag.putDouble("y", position.y);
        tag.putDouble("z", position.z);
        tag.putDouble("qx", orientation.x);
        tag.putDouble("qy", orientation.y);
        tag.putDouble("qz", orientation.z);
        tag.putDouble("qw", orientation.w);
        return tag;
    }

    /**
     * Lecture tolerante <b>dans cette pose</b> : position manquante ou non finie = origine, orientation manquante, non
     * finie ou nulle = identite ; orientation renormalisee. Ne leve jamais.
     */
    public SpacePose load(CompoundTag tag)
    {
        double x = number(tag, "x");
        double y = number(tag, "y");
        double z = number(tag, "z");
        if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)) position.set(x, y, z);
        else position.zero();
        if (tag.contains("qw", Tag.TAG_ANY_NUMERIC)) {
            orientation.set(number(tag, "qx"), number(tag, "qy"), number(tag, "qz"), number(tag, "qw"));
        } else {
            orientation.identity();
        }
        SpaceRotations.normalize(orientation);
        return this;
    }

    private static double number(CompoundTag tag, String key)
    {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getDouble(key) : 0.0;
    }

    @Override
    public String toString()
    {
        return "SpacePose[" + position.x + ", " + position.y + ", " + position.z + " | " + orientation.x + ", "
                + orientation.y + ", " + orientation.z + ", " + orientation.w + "]";
    }
}
