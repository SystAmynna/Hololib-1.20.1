package com.amynna.hololib.holo.object;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.ApiStatus;

/**
 * Transformation d'un {@link HoloObject} : translation, rotation, echelle. <b>Mutable</b> : un module deplace ses objets
 * en modifiant leur transform ({@link HoloObject#transform()}), sans allocation ; cote serveur, le changement est
 * synchronise au tick suivant. Chaque objet possede sa propre instance, jamais partagee.
 * <p>
 * L'interpretation depend de l'ancrage ({@link HoloAnchor}) :
 * <ul>
 *     <li>{@code Sky} : applique tel quel dans le repere du ciel (centre sur la camera, axes du monde ou celestes).</li>
 *     <li>{@code World} : applique depuis l'origine de l'hote (marker, joueur proprietaire, ou origine du monde pour
 *     une memoire de dimension).</li>
 *     <li>{@code Local} : {@code x} = yaw et {@code y} = pitch (degres) de la direction depuis l'oeil, {@code z} =
 *     distance a l'oeil ; l'objet y est tourne vers l'oeil, puis la rotation et l'echelle s'appliquent.</li>
 *     <li>{@code Hud} : {@code x}, {@code y} = pixels GUI depuis le point d'alignement, {@code z} = ordre de dessin
 *     (plus grand = devant) ; rotation et echelle normales.</li>
 * </ul>
 * Rotation en degres, conventions de Minecraft, appliquee dans l'ordre yaw (axe Y), pitch (axe X), roll (axe Z) :
 * avec {@code roll = 0}, la face {@code +Z} de l'objet pointe dans la direction {@code (yaw, pitch)}.
 */
public final class HoloTransform
{
    /** Echelle maximale sur un axe (le HUD compte en pixels GUI par bloc). */
    public static final float MAX_SCALE = 4096f;

    private float x;
    private float y;
    private float z;
    private float yaw;
    private float pitch;
    private float roll;
    private float scaleX = 1f;
    private float scaleY = 1f;
    private float scaleZ = 1f;
    private boolean dirty;

    /** Transformation identite. */
    public HoloTransform() {}

    /** Raccourci : identite translatee. */
    public static HoloTransform translation(float x, float y, float z)
    {
        return new HoloTransform().setTranslation(x, y, z);
    }

    // ---------------------------------------------------------------- lecture

    public float x() { return x; }

    public float y() { return y; }

    public float z() { return z; }

    public float yaw() { return yaw; }

    public float pitch() { return pitch; }

    public float roll() { return roll; }

    public float scaleX() { return scaleX; }

    public float scaleY() { return scaleY; }

    public float scaleZ() { return scaleZ; }

    /** Plus grande echelle en valeur absolue (rayon englobant). */
    public float maxScale() { return Math.max(Math.abs(scaleX), Math.max(Math.abs(scaleY), Math.abs(scaleZ))); }

    /** Toutes les composantes finies, echelles dans {@code ]0, MAX_SCALE]}. */
    public boolean isValid()
    {
        return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z) && Float.isFinite(yaw)
                && Float.isFinite(pitch) && Float.isFinite(roll) && validScale(scaleX) && validScale(scaleY)
                && validScale(scaleZ);
    }

    public HoloTransform copy() { return new HoloTransform().set(this); }

    // ---------------------------------------------------------------- ecriture

    public HoloTransform setTranslation(float x, float y, float z)
    {
        if (this.x != x || this.y != y || this.z != z) {
            this.x = x;
            this.y = y;
            this.z = z;
            dirty = true;
        }
        return this;
    }

    public HoloTransform setRotation(float yaw, float pitch, float roll)
    {
        if (this.yaw != yaw || this.pitch != pitch || this.roll != roll) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
            dirty = true;
        }
        return this;
    }

    public HoloTransform setScale(float scale) { return setScale(scale, scale, scale); }

    public HoloTransform setScale(float x, float y, float z)
    {
        if (scaleX != x || scaleY != y || scaleZ != z) {
            scaleX = x;
            scaleY = y;
            scaleZ = z;
            dirty = true;
        }
        return this;
    }

    /** Copie toutes les composantes de {@code other}. */
    public HoloTransform set(HoloTransform other)
    {
        setTranslation(other.x, other.y, other.z);
        setRotation(other.yaw, other.pitch, other.roll);
        return setScale(other.scaleX, other.scaleY, other.scaleZ);
    }

    /**
     * Place l'objet a {@code distance} de l'origine dans la direction {@code (yaw, pitch)}, face {@code +Z} tournee
     * vers l'origine (roll nul). Pour le ciel : comme le soleil autour de la camera.
     */
    public HoloTransform setLookingAtOrigin(float yaw, float pitch, float distance)
    {
        float yawRad = yaw * Mth.DEG_TO_RAD;
        float pitchRad = pitch * Mth.DEG_TO_RAD;
        float horizontal = Mth.cos(pitchRad) * distance;
        setTranslation(-Mth.sin(yawRad) * horizontal, -Mth.sin(pitchRad) * distance, Mth.cos(yawRad) * horizontal);
        return setRotation(yaw + 180f, -pitch, 0f);
    }

    // ---------------------------------------------------------------- serialisation

    public void write(FriendlyByteBuf buf)
    {
        buf.writeFloat(x);
        buf.writeFloat(y);
        buf.writeFloat(z);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
        buf.writeFloat(roll);
        buf.writeFloat(scaleX);
        buf.writeFloat(scaleY);
        buf.writeFloat(scaleZ);
    }

    public static HoloTransform read(FriendlyByteBuf buf)
    {
        HoloTransform transform = new HoloTransform();
        transform.x = buf.readFloat();
        transform.y = buf.readFloat();
        transform.z = buf.readFloat();
        transform.yaw = buf.readFloat();
        transform.pitch = buf.readFloat();
        transform.roll = buf.readFloat();
        transform.scaleX = buf.readFloat();
        transform.scaleY = buf.readFloat();
        transform.scaleZ = buf.readFloat();
        return transform;
    }

    public CompoundTag save()
    {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("x", x);
        tag.putFloat("y", y);
        tag.putFloat("z", z);
        tag.putFloat("yaw", yaw);
        tag.putFloat("pitch", pitch);
        tag.putFloat("roll", roll);
        tag.putFloat("scaleX", scaleX);
        tag.putFloat("scaleY", scaleY);
        tag.putFloat("scaleZ", scaleZ);
        return tag;
    }

    /** @return le transform sauvegarde, ou {@code null} si le tag est vide (ancienne sauvegarde) */
    public static HoloTransform load(CompoundTag tag)
    {
        if (tag.isEmpty()) return null;
        HoloTransform transform = new HoloTransform();
        transform.x = tag.getFloat("x");
        transform.y = tag.getFloat("y");
        transform.z = tag.getFloat("z");
        transform.yaw = tag.getFloat("yaw");
        transform.pitch = tag.getFloat("pitch");
        transform.roll = tag.getFloat("roll");
        transform.scaleX = tag.contains("scaleX") ? tag.getFloat("scaleX") : 1f;
        transform.scaleY = tag.contains("scaleY") ? tag.getFloat("scaleY") : 1f;
        transform.scaleZ = tag.contains("scaleZ") ? tag.getFloat("scaleZ") : 1f;
        return transform;
    }

    // ---------------------------------------------------------------- interne

    /** Usage interne : vrai si une composante a change depuis le dernier envoi. */
    @ApiStatus.Internal
    public boolean dirty() { return dirty; }

    @ApiStatus.Internal
    public void clearDirty() { dirty = false; }

    @Override
    public String toString()
    {
        return String.format("HoloTransform[t=(%.3f, %.3f, %.3f), r=(%.1f, %.1f, %.1f), s=(%.3f, %.3f, %.3f)]", x, y,
                z, yaw, pitch, roll, scaleX, scaleY, scaleZ);
    }

    private static boolean validScale(float scale) { return scale > 0f && scale <= MAX_SCALE; }
}
