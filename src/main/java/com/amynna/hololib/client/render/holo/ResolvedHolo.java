package com.amynna.hololib.client.render.holo;

import com.amynna.hololib.client.holo.ClientHoloContainer;
import com.amynna.hololib.client.render.world.WorldRenderable;
import com.amynna.hololib.holo.memory.HoloScope;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Un objet holographique tel que le voit le joueur local : famille effective et, pour la famille monde, position et
 * orientation calculees depuis l'ancrage et le transform (l'echelle est appliquee par le pont monde).
 * <ul>
 *     <li>{@code World} : origine = entite du joueur proprietaire (memoire de joueur), marker (memoire de position,
 *     origine recue du serveur) ou origine du monde (dimension), plus la translation du transform.</li>
 *     <li>{@code Local} dessine dans le monde (objet d'un autre joueur, ou vue a la troisieme personne) : meme sphere
 *     qu'en passe vue, autour de l'oeil du joueur de reference et selon son regard.</li>
 * </ul>
 * Une instance par objet, reutilisee de frame en frame par {@link HoloRouting}.
 */
public final class ResolvedHolo implements WorldRenderable
{
    final HoloObject object;
    /** Portee de l'objet (origine des ancrages {@code World} d'une memoire de position). */
    ClientHoloContainer container;
    /** Joueur autour duquel un ancrage {@code Local} est place dans le monde. */
    UUID reference;
    boolean ownerView;
    HoloAnchor.Family family;
    int generation;

    private final Quaternionf orientation = new Quaternionf();
    private final Quaternionf tmp = new Quaternionf();
    private final Vector3f offset = new Vector3f();
    private boolean available;
    private Vec3 position = Vec3.ZERO;

    ResolvedHolo(HoloObject object)
    {
        this.object = object;
    }

    public HoloObject object() { return object; }

    /** Faux si la position ne peut pas etre calculee (joueur de reference non charge). */
    boolean available() { return available; }

    /** Orientation monde de l'objet, valide apres {@link #position(float)}. */
    Quaternionf orientation() { return orientation; }

    @Override
    public Vec3 position(float partialTick)
    {
        HoloAnchor anchor = object.anchor();
        HoloTransform transform = object.transform();
        if (anchor instanceof HoloAnchor.World) {
            Vec3 origin;
            if (container.scope() instanceof HoloScope.Player) {
                Player entity = referenceEntity();
                available = entity != null;
                if (entity == null) return position;
                origin = entity.getPosition(partialTick);
            } else {
                available = true;
                origin = container.origin();
            }
            position = origin.add(transform.x(), transform.y(), transform.z());
            HoloPlacement.rotation(orientation, transform);
            return position;
        }
        if (!(anchor instanceof HoloAnchor.Local local)) {
            available = false;
            return position;
        }
        Player entity = referenceEntity();
        available = entity != null;
        if (entity == null) return position;
        if (local.follow()) {
            HoloPlacement.yawPitch(orientation, entity.getViewYRot(partialTick), entity.getViewXRot(partialTick));
        } else {
            orientation.identity();
        }
        HoloPlacement.onSphere(orientation, offset, tmp, transform);
        position = entity.getEyePosition(partialTick).add(offset.x, offset.y, offset.z);
        return position;
    }

    @Override
    public AABB renderBounds(float partialTick)
    {
        double r = object.boundingRadius() * object.transform().maxScale();
        return new AABB(position.x - r, position.y - r, position.z - r, position.x + r, position.y + r,
                position.z + r);
    }

    private Player referenceEntity()
    {
        ClientLevel level = Minecraft.getInstance().level;
        return level != null && reference != null ? level.getPlayerByUUID(reference) : null;
    }
}
