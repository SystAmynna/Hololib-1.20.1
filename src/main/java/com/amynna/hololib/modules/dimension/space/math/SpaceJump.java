package com.amynna.hololib.modules.dimension.space.math;

import com.amynna.hololib.modules.dimension.space.model.SpaceGalaxy.BackgroundStar;
import com.amynna.hololib.modules.dimension.space.model.SpaceSystem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;

import java.util.List;

/**
 * Etat du saut interstellaire du monde (voir {@code docs/space/04-mathematiques.md} par. 8, decisions D10, D14,
 * C39 a C43) et ses fonctions pures : orientation de la charge, duree du voyage, pose d'arrivee.
 * <p>
 * <b>Phases</b> ({@link Phase}) et compteur {@link #ticks()} (pas faits dans la phase courante) :
 * <ul>
 *     <li>{@code NONE} : pas de saut, mouvement normal ;</li>
 *     <li>{@code CHARGE} ({@value #CHARGE_TICKS} pas tant que l'entree garde le drapeau du saut) : au pas {@code n}
 *     ({@code 1..}{@value #CHARGE_TICKS}),
 *     {@code Q = slerp(Q0, Q_cible, lisse(min(n, }{@value #ALIGN_TICKS}{@code ) / }{@value #ALIGN_TICKS}{@code ))}
 *     ({@link #chargeOrientation}) ; frein, gaz / roulis / joystick ignores (C40) ; drapeau perdu -> {@code NONE} (orientation atteinte gardee) ; au pas {@value #CHARGE_TICKS} -> {@code TRAVEL},
 *     compteur a 0, {@code V = 0} ;</li>
 *     <li>{@code TRAVEL} ({@link #duration()} pas) : pose figee, compteur borne a la duree ; quand il l'atteint
 *     ({@link #arrived()}), le <b>serveur seul</b> fait l'arrivee (systeme courant <- cible, {@link #arrivalPose}) ;
 *     un client reste en {@code TRAVEL} au bout du compte jusqu'a l'etat qui porte le nouveau systeme.</li>
 * </ul>
 * Figes au debut de la charge ({@link #begin}) : id de la cible, orientation {@code Q0}, positions galactiques
 * d'origine (systeme courant, {@code double}) et de cible (position de son etoile de fond : coordonnees du systeme
 * arrondies en {@code float}, comme {@code BackgroundStar}, pour que serveur et clients calculent la meme direction a
 * partir de ce qu'ils ont), duree du voyage ({@link #travelTicks}, vitesse de distorsion du moment). La cible est
 * choisie par le client du pilote a l'appui de la touche du saut ({@link #pickTarget} : etoile de fond la plus proche
 * du centre de l'ecran a moins de {@value #TARGET_CONE} degres, C41, T22).
 * <p>
 * Objet <b>mutable</b>, reutilisable, comme {@link SpaceFrameState} : chaque suite de pas a le sien (serveur, rejeu
 * client, prediction du pilote, historiques), mis a jour en place par {@link SpaceMotion}, sans allocation. Une
 * instance rendue par l'Environment ({@code jump()}) est en <b>lecture seule</b>. Etat simule : synchronise
 * ({@link #write} / {@link #read}) et sauvegarde ({@link #save} / {@link #load}). Les calculs sont deterministes bit a
 * bit ({@link StrictMath}, arithmetique ecrite a la main, aucune methode JOML de calcul).
 */
public final class SpaceJump
{
    /** Duree de la charge, en pas (3 s). */
    public static final int CHARGE_TICKS = 60;
    /** Duree de l'alignement sur la cible, en pas depuis le debut de la charge (2 s, C39). */
    public static final int ALIGN_TICKS = 40;
    /** Duree minimale du voyage, en pas (1 s). */
    public static final int MIN_TRAVEL_TICKS = 20;
    /**
     * Arrivee : distance a l'etoile principale, en rayons de l'etoile (C42, C59) : proche de l'etoile cible, disque
     * d'environ 11 degres (Soleil : 0,047 UA, en deca de Mercure).
     */
    public static final double ARRIVAL_STAR_RADII = 10.0;
    /**
     * Cible au reticule (C41, T22) : demi-angle du cone, en degres, autour du centre de l'ecran dans lequel une etoile
     * de fond peut etre choisie ({@link #pickTarget}).
     */
    public static final double TARGET_CONE = 3.0;
    private static final double COS_TARGET_CONE = StrictMath.cos(StrictMath.toRadians(TARGET_CONE));
    /** Sinus de l'angle sous lequel deux directions sont tenues pour paralleles ou opposees. */
    private static final double PARALLEL = 1.0e-12;
    /** Ecart toleree a la norme 1 d'une orientation recue du reseau ou chargee. */
    private static final double NORM_TOLERANCE = 1.0e-6;
    /** Ecart a la norme 1 au-dela duquel une orientation chargee est renormalisee. */
    private static final double RENORMALIZE = 1.0e-12;

    /** Phase du saut ; l'ordinal est le code reseau et NBT. */
    public enum Phase
    {
        /** Pas de saut. */
        NONE,
        /** Charge : alignement sur la cible puis remplissage de la roue. */
        CHARGE,
        /** Voyage en distorsion. */
        TRAVEL;

        private static final Phase[] VALUES = values();

        /** Phase du code {@code code}, {@code null} si inconnu. */
        public static Phase of(int code) { return code >= 0 && code < VALUES.length ? VALUES[code] : null; }
    }

    /**
     * Recherche d'une cible de saut, fournie par l'appelant du pas (serveur : la galaxie ; client : les etoiles de
     * fond) : les deux cotes doivent rendre exactement la meme position pour le meme id.
     */
    public interface Targets
    {
        /**
         * Position galactique (al) de l'etoile de fond du systeme {@code systemId}, coordonnees arrondies en
         * {@code float} (comme {@code BackgroundStar}), ecrite dans {@code dest} ; faux si ce n'est pas une cible
         * valide (systeme inconnu, ou systeme courant). Sans allocation.
         */
        boolean position(int systemId, Vector3d dest);
    }

    private Phase phase = Phase.NONE;
    private int target;
    private int ticks;
    private int duration;
    private final Quaterniond start = new Quaterniond();
    private final Vector3d origin = new Vector3d();
    private final Vector3d destination = new Vector3d();

    /** Pas de saut. */
    public SpaceJump() {}

    // ---------------------------------------------------------------- lecture

    /** Phase courante. */
    public Phase phase() { return phase; }

    /** Vrai pendant une charge ou un voyage. */
    public boolean active() { return phase != Phase.NONE; }

    /** Id du systeme cible ({@code 0} sans saut). */
    public int target() { return target; }

    /**
     * Pas faits dans la phase courante : charge {@code 1..}{@value #CHARGE_TICKS} (un etat lu entre deux pas n'atteint
     * jamais {@value #CHARGE_TICKS} : il passe alors en voyage) ; voyage {@code 0..}{@link #duration()}.
     */
    public int ticks() { return ticks; }

    /** Duree du voyage, en pas ({@code 0} sans saut). */
    public int duration() { return duration; }

    /** Orientation du monde au debut de la charge ({@code Q0}) ; instance interne, lecture seule. */
    public Quaterniondc startOrientation() { return start; }

    /** Position galactique du systeme d'origine (al) ; instance interne, lecture seule. */
    public Vector3dc origin() { return origin; }

    /** Position galactique de la cible (al, coordonnees de son etoile de fond) ; instance interne, lecture seule. */
    public Vector3dc destination() { return destination; }

    /** Distance galactique du voyage, en al (calcul ecrit a la main : JOML peut utiliser {@code fma}). */
    public double distance()
    {
        double dx = destination.x - origin.x;
        double dy = destination.y - origin.y;
        double dz = destination.z - origin.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Vrai si le voyage est au bout de son compte : l'arrivee (serveur) est due. */
    public boolean arrived() { return phase == Phase.TRAVEL && ticks >= duration; }

    // ---------------------------------------------------------------- progression (affichage)

    /**
     * Progression de la charge {@code [0, 1]} pour un etat lu apres un pas et la fraction {@code partial}
     * ({@code [0, 1]}) de l'intervalle qui mene a lui : {@code (ticks - 1 + partial) / 60} en charge, {@code 1} en
     * voyage, {@code 0} sans saut. {@code partial = 1} : progression au tick de l'etat.
     */
    public double chargeProgress(double partial)
    {
        return switch (phase) {
            case CHARGE -> clamp01((ticks - 1 + partial) / CHARGE_TICKS);
            case TRAVEL -> 1.0;
            default -> 0.0;
        };
    }

    /**
     * Progression du voyage {@code [0, 1]} (meme convention que {@link #chargeProgress}) :
     * {@code (ticks - 1 + partial) / duree} en voyage, {@code 0} sinon.
     */
    public double travelProgress(double partial)
    {
        return phase == Phase.TRAVEL ? clamp01((ticks - 1 + partial) / duration) : 0.0;
    }

    /**
     * Temps de voyage restant, en secondes (20 pas par seconde) : en voyage, {@code (duree - (ticks - 1 + partial))
     * / 20}, borne a {@code [0, duree / 20]} ; en charge, la duree entiere du voyage a venir ; {@code 0} sans saut.
     */
    public double remainingSeconds(double partial)
    {
        return switch (phase) {
            case TRAVEL -> Math.max(0.0, Math.min(duration, duration - (ticks - 1 + partial)))
                    / SpaceUnits.TICKS_PER_SECOND;
            case CHARGE -> duration / (double) SpaceUnits.TICKS_PER_SECOND;
            default -> 0.0;
        };
    }

    // ---------------------------------------------------------------- transitions (SpaceMotion)

    /**
     * Debut d'une charge vers {@code targetId} : orientation {@code q0}, positions d'origine et de cible figees, duree
     * du voyage calculee a la vitesse de distorsion {@code warpSpeed} (al/s). Compteur a 0.
     */
    void begin(int targetId, Quaterniondc q0, double originX, double originY, double originZ, Vector3dc targetPosition,
               double warpSpeed)
    {
        phase = Phase.CHARGE;
        target = targetId;
        ticks = 0;
        start.set(q0.x(), q0.y(), q0.z(), q0.w());
        origin.set(originX, originY, originZ);
        destination.set(targetPosition.x(), targetPosition.y(), targetPosition.z());
        duration = travelTicks(distance(), warpSpeed);
    }

    /** Un pas de charge de plus ; passe en voyage (compteur a 0) au pas {@value #CHARGE_TICKS}. Rend le pas fait. */
    int advanceCharge()
    {
        int n = ++ticks;
        if (n >= CHARGE_TICKS) {
            phase = Phase.TRAVEL;
            ticks = 0;
        }
        return n;
    }

    /** Un pas de voyage de plus, compteur borne a la duree. */
    void advanceTravel()
    {
        if (ticks < duration) ticks++;
    }

    // ---------------------------------------------------------------- affectation

    /** Pas de saut (annulation, arrivee, changement de systeme). */
    public SpaceJump reset()
    {
        phase = Phase.NONE;
        target = 0;
        ticks = 0;
        duration = 0;
        start.identity();
        origin.zero();
        destination.zero();
        return this;
    }

    /** Copie {@code other} dans cet etat. */
    public SpaceJump set(SpaceJump other)
    {
        phase = other.phase;
        target = other.target;
        ticks = other.ticks;
        duration = other.duration;
        start.set(other.start);
        origin.set(other.origin);
        destination.set(other.destination);
        return this;
    }

    /** Vrai si les deux etats sont egaux champ a champ (bit a bit pour les nombres). */
    public boolean sameAs(SpaceJump other)
    {
        return phase == other.phase && target == other.target && ticks == other.ticks && duration == other.duration
                && start.equals(other.start) && origin.equals(other.origin) && destination.equals(other.destination);
    }

    // ---------------------------------------------------------------- fonctions pures

    /**
     * Duree du voyage, en pas : {@code N = max(20, ceil(20 D / v_w))} ({@code D} en al, {@code v_w} en al/s), bornee a
     * {@link Integer#MAX_VALUE} ; vitesse nulle ou non finie : la duree minimale.
     */
    public static int travelTicks(double distanceLy, double warpSpeed)
    {
        if (!(warpSpeed > 0.0) || !Double.isFinite(distanceLy)) return MIN_TRAVEL_TICKS;
        double ticks = StrictMath.ceil(SpaceUnits.TICKS_PER_SECOND * distanceLy / warpSpeed);
        if (!(ticks < Integer.MAX_VALUE)) return Integer.MAX_VALUE;
        return Math.max(MIN_TRAVEL_TICKS, (int) ticks);
    }

    /**
     * Orientation de la charge au pas {@code n} : {@code slerp(Q0, Q_cible, lisse(min(n, 40) / 40))},
     * {@code lisse(u) = u^2 (3 - 2u)}, {@code Q_cible} = plus courte rotation (axes du systeme) qui envoie l'avant du
     * monde {@code Q0 F_W} sur la direction origine -> cible, composee avec {@code Q0} (le roulis n'est pas impose).
     * Ecrite {@code Rot(axe, theta sigma) Q0} : meme chemin que le slerp (rotation autour d'un axe fixe), sans JOML.
     * Sans saut : {@code Q0}.
     *
     * @param work vecteur de travail (ecrase)
     * @return {@code dest}
     */
    public Quaterniond chargeOrientation(int n, SpaceMotionSettings settings, Vector3d work, Quaterniond dest)
    {
        double u = Math.min(Math.max(n, 0), ALIGN_TICKS) / (double) ALIGN_TICKS;
        return align(start, destination.x - origin.x, destination.y - origin.y, destination.z - origin.z,
                SpaceMotion.smoothstep(u), settings, work, dest);
    }

    /**
     * {@code dest = Rot(axe, theta sigma) from} : {@code Rot(axe, theta)} est la plus courte rotation (axes du
     * systeme) qui envoie l'avant du monde {@code from F_W} sur la direction {@code (dx, dy, dz)} (non nulle, pas
     * forcement unitaire). Directions opposees : demi-tour autour du haut du monde {@code from U_W}. Direction nulle ou
     * non finie : {@code from}. {@code dest} peut etre {@code from}. Deterministe, sans allocation.
     *
     * @param sigma fraction de la rotation, {@code [0, 1]} (1 : avant aligne sur la direction)
     * @param work  vecteur de travail (ecrase)
     * @return {@code dest}
     */
    public static Quaterniond align(Quaterniondc from, double dx, double dy, double dz, double sigma,
                                    SpaceMotionSettings settings, Vector3d work, Quaterniond dest)
    {
        double qx = from.x();
        double qy = from.y();
        double qz = from.z();
        double qw = from.w();
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!(length > 0.0) || !Double.isFinite(length) || !(sigma > 0.0)) return dest.set(qx, qy, qz, qw);
        double cx = dx / length;
        double cy = dy / length;
        double cz = dz / length;
        // Avant du monde dans le systeme : f0 = from F_W.
        settings.forward(work);
        rotate(qx, qy, qz, qw, work);
        double fx = work.x;
        double fy = work.y;
        double fz = work.z;
        // Axe f0 x c, angle atan2(|f0 x c|, f0 . c).
        double ax = fy * cz - fz * cy;
        double ay = fz * cx - fx * cz;
        double az = fx * cy - fy * cx;
        double sin = Math.sqrt(ax * ax + ay * ay + az * az);
        double cos = fx * cx + fy * cy + fz * cz;
        double angle;
        if (sin > PARALLEL) {
            angle = StrictMath.atan2(sin, cos);
            ax /= sin;
            ay /= sin;
            az /= sin;
        } else if (cos > 0.0) {
            return dest.set(qx, qy, qz, qw);
        } else {
            // Directions opposees : demi-tour autour du haut du monde (perpendiculaire a l'avant).
            settings.up(work);
            rotate(qx, qy, qz, qw, work);
            double ul = Math.sqrt(work.x * work.x + work.y * work.y + work.z * work.z);
            ax = work.x / ul;
            ay = work.y / ul;
            az = work.z / ul;
            angle = Math.PI;
        }
        double half = 0.5 * angle * Math.min(sigma, 1.0);
        double s = StrictMath.sin(half);
        double c = StrictMath.cos(half);
        double bx = ax * s;
        double by = ay * s;
        double bz = az * s;
        // dest = b * from (rotation dans les axes du systeme, appliquee apres from).
        double rx = c * qx + bx * qw + by * qz - bz * qy;
        double ry = c * qy - bx * qz + by * qw + bz * qx;
        double rz = c * qz + bx * qy - by * qx + bz * qw;
        double rw = c * qw - bx * qx - by * qy - bz * qz;
        double norm = Math.sqrt(rx * rx + ry * ry + rz * rz + rw * rw);
        return dest.set(rx / norm, ry / norm, rz / norm, rw / norm);
    }

    /**
     * Distance d'arrivee {@code r_a} a l'etoile principale du systeme, en km (C42, C59) :
     * {@value #ARRIVAL_STAR_RADII} rayons de l'etoile principale, quelle que soit la portee des corps. Sans allocation.
     */
    public static double arrivalDistance(SpaceSystem system)
    {
        return ARRIVAL_STAR_RADII * system.primaryStar().radius();
    }

    /**
     * Pose d'arrivee dans le systeme cible (D10, C59) : {@code u} = direction origine -> cible (axes galactiques, ceux
     * du systeme) ; position {@code s = -u r_a} ({@link #arrivalDistance}, l'etoile principale etant a l'origine du
     * systeme), orientation : plus courte rotation de {@code before} qui tourne l'avant du monde vers {@code u}
     * ({@code Q F_W = u}, roulis le plus proche de celui d'avant). {@code before} peut etre l'orientation de
     * {@code dest}.
     *
     * @param work vecteur de travail (ecrase)
     * @return {@code dest}
     */
    public SpacePose arrivalPose(SpaceSystem system, Quaterniondc before, SpaceMotionSettings settings, Vector3d work,
                                 SpacePose dest)
    {
        double dx = destination.x - origin.x;
        double dy = destination.y - origin.y;
        double dz = destination.z - origin.z;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double ux = 0.0;
        double uy = 0.0;
        double uz = -1.0;
        if (length > 0.0 && Double.isFinite(length)) {
            ux = dx / length;
            uy = dy / length;
            uz = dz / length;
        }
        align(before, ux, uy, uz, 1.0, settings, work, dest.orientation());
        double distance = arrivalDistance(system);
        dest.position().set(-ux * distance, -uy * distance, -uz * distance);
        return dest;
    }

    /**
     * Cible au reticule (C41, {@code docs/space/04-mathematiques.md} par. 8, T22) : id de l'etoile de fond dont la
     * direction vue depuis la pose de {@code frame} ({@link SpaceSkyFrame#galacticDirection(SpaceSystem,
     * BackgroundStar, Vector3f)} : meme calcul que le ciel) fait le plus petit angle avec le regard
     * {@code (lookX, lookY, lookZ)} (unitaire, axes du monde), si cet angle est au plus {@value #TARGET_CONE} degres ;
     * {@code 0} sinon (aucune etoile assez proche, pas de systeme courant). Calcul d'affichage en {@code float}
     * (client : choix de la cible a l'appui, aide a la visee), sans allocation.
     *
     * @param work vecteur de travail (ecrase)
     */
    public static int pickTarget(SpaceSkyFrame frame, SpaceSystem current, List<BackgroundStar> stars, float lookX,
                                 float lookY, float lookZ, Vector3f work)
    {
        if (current == null) return 0;
        int best = 0;
        double bestDot = COS_TARGET_CONE;
        for (int i = 0, n = stars.size(); i < n; i++) {
            BackgroundStar star = stars.get(i);
            if (star.id() == current.id() || !(frame.galacticDirection(current, star, work) > 0.0)) continue;
            double dot = (double) work.x * lookX + (double) work.y * lookY + (double) work.z * lookZ;
            if (dot >= bestDot) {
                bestDot = dot;
                best = star.id();
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- reseau

    /**
     * Phase (octet) ; si elle n'est pas {@code NONE} : cible (varint), pas de la phase (varint), duree (varint),
     * {@code Q0} (4 double), origine (3 double), cible (3 double). 1 octet sans saut ; pendant un saut 81 octets plus
     * les trois varints ({@link #size()}) : 84 au moins, 86 pour Sol -> Alpha du Centaure (duree 175 : 2 octets).
     */
    public void write(FriendlyByteBuf buf)
    {
        buf.writeByte(phase.ordinal());
        if (phase == Phase.NONE) return;
        buf.writeVarInt(target);
        buf.writeVarInt(ticks);
        buf.writeVarInt(duration);
        buf.writeDouble(start.x);
        buf.writeDouble(start.y);
        buf.writeDouble(start.z);
        buf.writeDouble(start.w);
        buf.writeDouble(origin.x);
        buf.writeDouble(origin.y);
        buf.writeDouble(origin.z);
        buf.writeDouble(destination.x);
        buf.writeDouble(destination.y);
        buf.writeDouble(destination.z);
    }

    /** Taille exacte de {@link #write}, en octets. */
    public int size()
    {
        if (phase == Phase.NONE) return 1;
        return 1 + FriendlyByteBuf.getVarIntSize(target) + FriendlyByteBuf.getVarIntSize(ticks)
                + FriendlyByteBuf.getVarIntSize(duration) + 10 * Double.BYTES;
    }

    /**
     * Lecture stricte <b>dans cet etat</b> : leve (sans rien modifier) sur une phase inconnue, une cible
     * {@code <= 0}, un compteur hors de sa phase, une duree {@code < 1}, une orientation non unitaire ou une position
     * non finie. Les nombres sont gardes bit a bit (rejeu deterministe).
     */
    public SpaceJump read(FriendlyByteBuf buf)
    {
        Phase p = Phase.of(buf.readUnsignedByte());
        if (p == null) throw new IllegalArgumentException("Phase de saut inconnue");
        if (p == Phase.NONE) return reset();
        int t = buf.readVarInt();
        int n = buf.readVarInt();
        int d = buf.readVarInt();
        double qx = buf.readDouble();
        double qy = buf.readDouble();
        double qz = buf.readDouble();
        double qw = buf.readDouble();
        double ox = buf.readDouble();
        double oy = buf.readDouble();
        double oz = buf.readDouble();
        double tx = buf.readDouble();
        double ty = buf.readDouble();
        double tz = buf.readDouble();
        String problem = problem(p, t, n, d, qx, qy, qz, qw, ox, oy, oz, tx, ty, tz);
        if (problem != null) throw new IllegalArgumentException("Saut invalide : " + problem);
        return assign(p, t, n, d, qx, qy, qz, qw, ox, oy, oz, tx, ty, tz);
    }

    // ---------------------------------------------------------------- NBT

    /**
     * Format : {@code { phase, target, ticks, dur, qx, qy, qz, qw, ox, oy, oz, tx, ty, tz }} (cles courtes et
     * stables) ; a n'ecrire que pendant un saut (absent = pas de saut).
     */
    public CompoundTag save()
    {
        CompoundTag tag = new CompoundTag();
        tag.putByte("phase", (byte) phase.ordinal());
        tag.putInt("target", target);
        tag.putInt("ticks", ticks);
        tag.putInt("dur", duration);
        tag.putDouble("qx", start.x);
        tag.putDouble("qy", start.y);
        tag.putDouble("qz", start.z);
        tag.putDouble("qw", start.w);
        tag.putDouble("ox", origin.x);
        tag.putDouble("oy", origin.y);
        tag.putDouble("oz", origin.z);
        tag.putDouble("tx", destination.x);
        tag.putDouble("ty", destination.y);
        tag.putDouble("tz", destination.z);
        return tag;
    }

    /**
     * Lecture tolerante <b>dans cet etat</b> : tag vide, phase inconnue ou etat incoherent = pas de saut ; orientation
     * renormalisee si sa norme s'ecarte de 1 de plus de 1e-12 (sinon gardee bit a bit). Ne leve jamais.
     *
     * @return {@code null} si l'etat est lu (ou absent), sinon le motif du rejet (pour le log)
     */
    public String load(CompoundTag tag)
    {
        reset();
        if (tag.isEmpty()) return null;
        Phase p = Phase.of(tag.getByte("phase"));
        if (p == null) return "phase inconnue";
        if (p == Phase.NONE) return null;
        double qx = tag.getDouble("qx");
        double qy = tag.getDouble("qy");
        double qz = tag.getDouble("qz");
        double qw = tag.contains("qw", Tag.TAG_ANY_NUMERIC) ? tag.getDouble("qw") : 1.0;
        double norm = Math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
        // Renormalisee seulement si elle s'ecarte de 1 : un aller-retour NBT garde les bits (rejeu identique).
        if (norm > 0.0 && Double.isFinite(norm) && Math.abs(norm - 1.0) > RENORMALIZE) {
            qx /= norm;
            qy /= norm;
            qz /= norm;
            qw /= norm;
        }
        String problem = problem(p, tag.getInt("target"), tag.getInt("ticks"), tag.getInt("dur"), qx, qy, qz, qw,
                tag.getDouble("ox"), tag.getDouble("oy"), tag.getDouble("oz"), tag.getDouble("tx"),
                tag.getDouble("ty"), tag.getDouble("tz"));
        if (problem != null) return problem;
        assign(p, tag.getInt("target"), tag.getInt("ticks"), tag.getInt("dur"), qx, qy, qz, qw, tag.getDouble("ox"),
                tag.getDouble("oy"), tag.getDouble("oz"), tag.getDouble("tx"), tag.getDouble("ty"),
                tag.getDouble("tz"));
        return null;
    }

    // ---------------------------------------------------------------- interne

    /** Motif d'incoherence d'un etat de saut actif, {@code null} s'il est valide. */
    private static String problem(Phase p, int t, int n, int d, double qx, double qy, double qz, double qw, double ox,
                                  double oy, double oz, double tx, double ty, double tz)
    {
        if (t <= 0) return "cible " + t;
        if (d < 1) return "duree " + d;
        if (p == Phase.CHARGE && (n < 0 || n >= CHARGE_TICKS)) return "pas de charge " + n;
        if (p == Phase.TRAVEL && (n < 0 || n > d)) return "pas de voyage " + n;
        double norm = qx * qx + qy * qy + qz * qz + qw * qw;
        if (!Double.isFinite(norm) || Math.abs(norm - 1.0) > NORM_TOLERANCE) {
            return "orientation (norme^2 " + norm + ")";
        }
        if (!Double.isFinite(ox) || !Double.isFinite(oy) || !Double.isFinite(oz) || !Double.isFinite(tx)
                || !Double.isFinite(ty) || !Double.isFinite(tz)) {
            return "position non finie";
        }
        return null;
    }

    private SpaceJump assign(Phase p, int t, int n, int d, double qx, double qy, double qz, double qw, double ox,
                             double oy, double oz, double tx, double ty, double tz)
    {
        phase = p;
        target = t;
        ticks = n;
        duration = d;
        start.set(qx, qy, qz, qw);
        origin.set(ox, oy, oz);
        destination.set(tx, ty, tz);
        return this;
    }

    /** {@code v <- q v q^-1} pour {@code q} unitaire (en place). */
    private static void rotate(double qx, double qy, double qz, double qw, Vector3d v)
    {
        SpaceRotations.rotate(qx, qy, qz, qw, v.x, v.y, v.z, v);
    }

    private static double clamp01(double value)
    {
        return value < 0.0 ? 0.0 : Math.min(value, 1.0);
    }

    @Override
    public String toString()
    {
        return "SpaceJump[" + phase + (phase == Phase.NONE ? "]"
                : " -> " + target + ", " + ticks + " / " + (phase == Phase.CHARGE ? CHARGE_TICKS : duration) + "]");
    }
}
