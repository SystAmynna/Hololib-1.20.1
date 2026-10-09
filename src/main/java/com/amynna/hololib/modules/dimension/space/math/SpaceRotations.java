package com.amynna.hololib.modules.dimension.space.math;

import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Arithmetique des rotations (interne au package) ecrite a la main pour le determinisme : trigonometrie par
 * {@link StrictMath}, produits sans {@code Math.fma} (les methodes JOML equivalentes dependent de
 * {@code joml.useMathFma} et {@code joml.fastmath}). JOML ne sert que de conteneur ({@code x, y, z, w} publics).
 * <p>
 * Conventions de JOML : rotations directes (main droite) ; un quaternion {@code q} tourne un vecteur par
 * {@code q v q^-1} ; {@code a * b} applique {@code b} puis {@code a}. Aucune allocation.
 */
final class SpaceRotations
{
    private SpaceRotations() {}

    /** {@code q <- q * b}. */
    static void multiply(Quaterniond q, double bx, double by, double bz, double bw)
    {
        double ax = q.x;
        double ay = q.y;
        double az = q.z;
        double aw = q.w;
        q.x = aw * bx + ax * bw + ay * bz - az * by;
        q.y = aw * by - ax * bz + ay * bw + az * bx;
        q.z = aw * bz + ax * by - ay * bx + az * bw;
        q.w = aw * bw - ax * bx - ay * by - az * bz;
    }

    /** {@code q <- q * Rot(axe, angle)}, axe unitaire, angle en radians (sens direct autour de l'axe). */
    static void multiplyAxisAngle(Quaterniond q, double axisX, double axisY, double axisZ, double angle)
    {
        double half = angle * 0.5;
        double s = StrictMath.sin(half);
        double c = StrictMath.cos(half);
        multiply(q, axisX * s, axisY * s, axisZ * s, c);
    }

    /** Normalise {@code q} ; un quaternion nul ou non fini devient l'identite. */
    static void normalize(Quaterniond q)
    {
        double norm = Math.sqrt(q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w);
        if (!(norm > 0.0) || !Double.isFinite(norm)) {
            q.x = 0.0;
            q.y = 0.0;
            q.z = 0.0;
            q.w = 1.0;
            return;
        }
        q.x /= norm;
        q.y /= norm;
        q.z /= norm;
        q.w /= norm;
    }

    /** {@code dest <- q v q^-1} pour {@code q} unitaire ({@code dest} peut etre n'importe quel vecteur). */
    static Vector3d rotate(Quaterniond q, double x, double y, double z, Vector3d dest)
    {
        return rotate(q.x, q.y, q.z, q.w, x, y, z, dest);
    }

    /** {@code dest <- q^-1 v q} pour {@code q} unitaire (rotation inverse). */
    static Vector3d rotateInverse(Quaterniond q, double x, double y, double z, Vector3d dest)
    {
        return rotate(-q.x, -q.y, -q.z, q.w, x, y, z, dest);
    }

    /** Rotation d'un vecteur par le quaternion unitaire {@code (qx, qy, qz, qw)} : {@code v + w t + u x t}, {@code t = 2 u x v}. */
    private static Vector3d rotate(double qx, double qy, double qz, double qw, double x, double y, double z,
                                   Vector3d dest)
    {
        double tx = 2.0 * (qy * z - qz * y);
        double ty = 2.0 * (qz * x - qx * z);
        double tz = 2.0 * (qx * y - qy * x);
        dest.x = x + qw * tx + (qy * tz - qz * ty);
        dest.y = y + qw * ty + (qz * tx - qx * tz);
        dest.z = z + qw * tz + (qx * ty - qy * tx);
        return dest;
    }

    /**
     * {@code dest <- Ry(angleY) Rx(angleX) Rz(angleZ) v} (radians) : meme rotation que
     * {@code Quaterniond.rotationYXZ(angleY, angleX, angleZ)}, appliquee directement au vecteur.
     */
    static Vector3d rotateYXZ(double angleY, double angleX, double angleZ, double x, double y, double z, Vector3d dest)
    {
        // Rz
        double s = StrictMath.sin(angleZ);
        double c = StrictMath.cos(angleZ);
        double x1 = x * c - y * s;
        double y1 = x * s + y * c;
        // Rx
        s = StrictMath.sin(angleX);
        c = StrictMath.cos(angleX);
        double y2 = y1 * c - z * s;
        double z2 = y1 * s + z * c;
        // Ry
        s = StrictMath.sin(angleY);
        c = StrictMath.cos(angleY);
        dest.x = x1 * c + z2 * s;
        dest.y = y2;
        dest.z = -x1 * s + z2 * c;
        return dest;
    }

    /**
     * Quaternion de la matrice de rotation {@code m} ({@code m_lc} = ligne {@code l}, colonne {@code c}, convention
     * {@code v' = m v}), methode de Shepperd ; resultat normalise.
     */
    static Quaterniond fromMatrix(double m00, double m01, double m02, double m10, double m11, double m12,
                                  double m20, double m21, double m22, Quaterniond dest)
    {
        double trace = m00 + m11 + m22;
        if (trace > 0.0) {
            double s = Math.sqrt(trace + 1.0) * 2.0;
            dest.w = 0.25 * s;
            dest.x = (m21 - m12) / s;
            dest.y = (m02 - m20) / s;
            dest.z = (m10 - m01) / s;
        } else if (m00 > m11 && m00 > m22) {
            double s = Math.sqrt(1.0 + m00 - m11 - m22) * 2.0;
            dest.w = (m21 - m12) / s;
            dest.x = 0.25 * s;
            dest.y = (m01 + m10) / s;
            dest.z = (m02 + m20) / s;
        } else if (m11 > m22) {
            double s = Math.sqrt(1.0 + m11 - m00 - m22) * 2.0;
            dest.w = (m02 - m20) / s;
            dest.x = (m01 + m10) / s;
            dest.y = 0.25 * s;
            dest.z = (m12 + m21) / s;
        } else {
            double s = Math.sqrt(1.0 + m22 - m00 - m11) * 2.0;
            dest.w = (m10 - m01) / s;
            dest.x = (m02 + m20) / s;
            dest.y = (m12 + m21) / s;
            dest.z = 0.25 * s;
        }
        normalize(dest);
        return dest;
    }
}
