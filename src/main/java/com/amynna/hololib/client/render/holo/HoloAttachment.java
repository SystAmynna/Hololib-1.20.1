package com.amynna.hololib.client.render.holo;

import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.Objects;

/**
 * Objet client (sans module, jamais synchronise) dessine a cote d'un objet hote, dans la famille de l'hote : boutons
 * du mode edition par exemple. Enregistre par {@link HoloRouting#attach}, il est route tant que son hote l'est
 * (jamais dans le HUD), et interactible comme un objet ordinaire si son acces le permet.
 * <p>
 * Placement propre ({@link #place}), independant de la rotation et de l'echelle de l'hote, calcule au <b>premier</b>
 * placement (amorce de l'edition : l'overlay est cree a l'ouverture de la session ou quand l'objet devient editable)
 * puis <b>fige</b> : l'overlay ne cherche plus a suivre le regard ni la camera, il garde son orientation, sa taille et sa
 * position : depuis le centre de l'hote dans le monde (il suit sa translation), depuis l'oeil dans la vue et le ciel
 * (reperes centres sur l'oeil : l'hote peut s'y deplacer sans l'emporter). {@link #replace()} le fait recalculer (apres
 * une transformation de l'hote).
 * <p>
 * Au premier placement, l'overlay est pose sur une sphere centree sur le point de vue, a cote de la <b>silhouette</b>
 * de l'hote vue de ce point (hors de son dessin), face au point de vue et sans roulis par rapport a l'ecran. Son
 * transform recoit ce placement, comme un {@code Local} : {@code x}/{@code y} = yaw/pitch depuis la direction de l'hote
 * (repere de la camera), {@code z} = distance. Taille : une unite du repere de l'overlay vaut {@link #PIXEL_ANGLE} bloc
 * par bloc de distance a ce moment (une {@code HoloWindow} d'overlay prend donc {@code pixelScale = 1}).
 * <p>
 * Silhouette : rectangle {@code halfWidth} x {@code halfHeight} projete pour un objet plat ({@link HoloObject#flat()}),
 * sinon sphere englobante. Point de vue dans l'hote : l'overlay est pose a {@link #NEAR_DISTANCE} blocs dans la
 * direction de son centre, devant son dessin. Dans le ciel, toujours a cette distance : la taille etant proportionnelle,
 * rien ne change a l'ecran, mais les autres objets du ciel (souvent immenses) ne le cachent plus.
 */
public final class HoloAttachment
{
    /** Cote de l'hote ou l'overlay est pose. */
    public enum Side
    {
        RIGHT, LEFT, TOP_RIGHT, TOP_LEFT
    }

    /** Blocs par unite de l'overlay, par bloc de distance entre l'oeil et l'overlay. */
    public static final float PIXEL_ANGLE = 0.0036f;
    /** Ecart angulaire entre la silhouette de l'hote et l'overlay, en unites de l'overlay. */
    private static final float MARGIN = 4f;
    /** Distance de l'overlay quand le point de vue est dans l'hote, ou dans le ciel, en blocs. */
    public static final float NEAR_DISTANCE = 2f;
    /** Rapport rayon / distance au-dela duquel le point de vue est considere dans l'hote. */
    private static final float INSIDE_RATIO = 0.98f;
    private static final float MIN_DISTANCE = 1e-4f;

    // Fil client unique (rendu et picking) : temporaires partages.
    private static final Vector3f DIRECTION = new Vector3f();
    private static final Vector3f LEFT_AXIS = new Vector3f();
    private static final Vector3f UP_AXIS = new Vector3f();
    private static final Vector3f CORNER = new Vector3f();
    private static final Matrix3f BASIS = new Matrix3f();
    private static final Quaternionf FRAME = new Quaternionf();
    private static final Quaternionf FRAME_INVERSE = new Quaternionf();
    private static final Quaternionf TMP = new Quaternionf();

    private final HoloObject overlay;
    private final HoloObject host;
    private final Side side;
    /**
     * Placement fige au premier {@link #place} : decalage depuis le centre de l'hote, position relative au point de
     * vue, orientation, echelle.
     */
    private boolean placed;
    private final Vector3f frozenOffset = new Vector3f();
    private final Vector3f frozenPosition = new Vector3f();
    private final Quaternionf frozenOrientation = new Quaternionf();
    private float frozenScale;

    public HoloAttachment(HoloObject overlay, HoloObject host, Side side)
    {
        this.overlay = Objects.requireNonNull(overlay);
        this.host = Objects.requireNonNull(host);
        this.side = Objects.requireNonNull(side);
    }

    public HoloObject overlay() { return overlay; }

    public HoloObject host() { return host; }

    public Side side() { return side; }

    /** Oublie le placement fige : recalcule au prochain {@link #place}, face au point de vue. */
    public void replace() { placed = false; }

    /**
     * Placement de l'overlay : calcule (et ecrit dans son transform) au premier appel, puis fige ; les appels suivants
     * n'utilisent que {@code center} et {@code followHost}.
     *
     * @param center          centre de l'hote relatif au point de vue, axes du monde
     * @param hostOrientation orientation monde de l'hote
     * @param up              haut de la camera, axes du monde (unitaire)
     * @param position        recoit la position de l'overlay relative au point de vue
     * @param orientation     recoit l'orientation de l'overlay (face {@code +Z} vers le point de vue)
     * @param maxDistance     distance maximale entre le point de vue et l'overlay (blocs)
     * @param followHost      vrai : position depuis le centre de l'hote (famille monde) ; faux : position figee
     *                        relative au point de vue (vue, ciel : l'oeil)
     * @return echelle de l'overlay (blocs par unite)
     */
    float place(Vector3fc center, Quaternionfc hostOrientation, Vector3fc up, Vector3f position,
                Quaternionf orientation, float maxDistance, boolean followHost)
    {
        if (!placed) {
            frozenScale = compute(center, hostOrientation, up, frozenPosition, frozenOrientation, maxDistance);
            frozenPosition.sub(center, frozenOffset);
            placed = true;
        }
        if (followHost) {
            position.set(center).add(frozenOffset);
        } else {
            position.set(frozenPosition);
        }
        orientation.set(frozenOrientation);
        return frozenScale;
    }

    /** Premier placement, face au point de vue ; parametres de {@link #place}. */
    private float compute(Vector3fc center, Quaternionfc hostOrientation, Vector3fc up, Vector3f position,
                          Quaternionf orientation, float maxDistance)
    {
        float distance = center.length();
        // Point de vue au centre de l'hote : direction arbitraire (il est alors "dans" l'hote).
        if (distance > MIN_DISTANCE) {
            center.normalize(DIRECTION);
        } else {
            DIRECTION.set(0f, 0f, 1f);
        }
        frame(up);

        // Etendues angulaires de la silhouette dans le repere : droite, gauche, haut (radians).
        float right;
        float left;
        float top;
        boolean inside;
        HoloTransform transform = host.transform();
        if (host.flat()) {
            right = left = top = -Float.MAX_VALUE;
            inside = false;
            for (int i = 0; i < 4; i++) {
                CORNER.set((i & 1) == 0 ? host.halfWidth() : -host.halfWidth(),
                        (i & 2) == 0 ? host.halfHeight() : -host.halfHeight(), 0f)
                        .mul(transform.scaleX(), transform.scaleY(), transform.scaleZ())
                        .rotate(hostOrientation).add(center).rotate(FRAME_INVERSE);
                if (CORNER.z <= MIN_DISTANCE) {
                    inside = true;
                    break;
                }
                // Repere de Minecraft : +X a gauche, +Y en haut, +Z devant.
                float yaw = (float) Mth.atan2(-CORNER.x, CORNER.z);
                float pitch = (float) Mth.atan2(CORNER.y, Math.sqrt(CORNER.x * CORNER.x + CORNER.z * CORNER.z));
                right = Math.max(right, yaw);
                left = Math.max(left, -yaw);
                top = Math.max(top, pitch);
            }
        } else {
            float radius = host.boundingRadius() * transform.maxScale();
            inside = radius >= INSIDE_RATIO * distance;
            right = left = top = inside ? 0f : (float) Math.asin(radius / distance);
        }
        if (inside) {
            right = left = top = 0f;
            distance = NEAR_DISTANCE;
        }
        distance = Math.min(distance, maxDistance);

        float ow = (float) Math.atan(overlay.halfWidth() * PIXEL_ANGLE);
        float oh = (float) Math.atan(overlay.halfHeight() * PIXEL_ANGLE);
        float margin = MARGIN * PIXEL_ANGLE;
        // Yaw positif vers la droite, pitch positif vers le haut (inverse du pitch de Minecraft).
        float yaw;
        float pitch;
        switch (side) {
            case RIGHT -> {
                yaw = right + margin + ow;
                pitch = top - oh;
            }
            case LEFT -> {
                yaw = -(left + margin + ow);
                pitch = top - oh;
            }
            case TOP_RIGHT -> {
                yaw = right - ow;
                pitch = top + margin + oh;
            }
            default -> {
                yaw = -(left - ow);
                pitch = top + margin + oh;
            }
        }
        float scale = distance * PIXEL_ANGLE;
        HoloTransform own = overlay.transform();
        own.setTranslation(yaw * Mth.RAD_TO_DEG, -pitch * Mth.RAD_TO_DEG, distance);
        own.setRotation(0f, 0f, 0f);
        own.setScale(scale, scale, scale);
        orientation.set(FRAME);
        HoloPlacement.onSphere(orientation, position, TMP, own);
        return scale;
    }

    /**
     * Repere de reference {@link #FRAME} : {@code +Z} vers le centre de l'hote ({@link #DIRECTION}), {@code +Y} au plus
     * pres du haut de la camera, {@code +X} a gauche (comme {@code Camera.rotation()}).
     */
    private static void frame(Vector3fc up)
    {
        UP_AXIS.set(up);
        if (Math.abs(UP_AXIS.dot(DIRECTION)) > 0.999f) {
            // Hote a la verticale de la camera : haut de repli.
            UP_AXIS.set(0f, 0f, 1f);
            if (Math.abs(UP_AXIS.dot(DIRECTION)) > 0.999f) UP_AXIS.set(1f, 0f, 0f);
        }
        UP_AXIS.cross(DIRECTION, LEFT_AXIS).normalize();
        DIRECTION.cross(LEFT_AXIS, UP_AXIS);
        BASIS.set(LEFT_AXIS, UP_AXIS, DIRECTION);
        FRAME.setFromNormalized(BASIS);
        FRAME.conjugate(FRAME_INVERSE);
    }
}
