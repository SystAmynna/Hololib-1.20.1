package com.amynna.hololib.client.render.holo;

import com.amynna.hololib.client.holo.ClientHoloContainer;
import com.amynna.hololib.client.render.world.WorldRenderable;
import com.amynna.hololib.holo.memory.HoloScope;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import net.minecraft.client.Camera;
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
 * Un overlay ({@link HoloAttachment}) est place depuis son hote : {@link #placed()} donne l'objet dont l'ancrage et le
 * transform servent a le situer, puis il prend son propre placement, face a la camera. Une instance par objet, reutilisee de frame en frame par {@link HoloRouting}.
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
    /** Non nul pour un overlay : place depuis {@code attachment.host()}. */
    HoloAttachment attachment;
    int generation;

    private final Quaternionf orientation = new Quaternionf();
    private final Quaternionf tmp = new Quaternionf();
    private final Vector3f offset = new Vector3f();
    private final Quaternionf hostOrientation = new Quaternionf();
    private final Vector3f hostCenter = new Vector3f();
    private boolean available;
    private Vec3 position = Vec3.ZERO;
    private float overlayScale;

    ResolvedHolo(HoloObject object)
    {
        this.object = object;
    }

    public HoloObject object() { return object; }

    /** Objet dont l'ancrage et le transform placent le dessin : l'hote d'un overlay, sinon l'objet lui-meme. */
    public HoloObject placed() { return attachment != null ? attachment.host() : object; }

    /** Famille effective pour le joueur local. */
    public HoloAnchor.Family family() { return family; }

    /** Portee de l'objet (de l'hote pour un overlay). */
    public ClientHoloContainer container() { return container; }

    /** Vrai si le joueur local est proprietaire de la memoire de l'objet. */
    public boolean ownerView() { return ownerView; }

    /** Overlay : son rattachement ; {@code null} pour un objet ordinaire. */
    public HoloAttachment attachment() { return attachment; }

    /** Faux si la position ne peut pas etre calculee (joueur de reference non charge). */
    public boolean available() { return available; }

    /** Orientation monde de l'objet (ou de l'overlay), valide apres {@link #position(float)}. */
    public Quaternionf orientation() { return orientation; }

    /** Echelle d'un overlay (blocs par unite), valide apres {@link #position(float)}. */
    float overlayScale() { return overlayScale; }

    /**
     * Position dans le monde de l'objet. Un overlay est place depuis son hote ({@link HoloAttachment#place}) a partir
     * de la camera courante.
     */
    @Override
    public Vec3 position(float partialTick)
    {
        Vec3 host = hostPosition(partialTick);
        if (attachment == null || !available) return host;
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 view = camera.getPosition();
        hostOrientation.set(orientation);
        hostCenter.set((float) (host.x - view.x), (float) (host.y - view.y), (float) (host.z - view.z));
        overlayScale = attachment.place(hostCenter, hostOrientation, camera.getUpVector(), offset, orientation,
                Float.MAX_VALUE, true);
        position = view.add(offset.x, offset.y, offset.z);
        return position;
    }

    /** Position et orientation de l'objet (de l'hote pour un overlay). */
    private Vec3 hostPosition(float partialTick)
    {
        HoloObject placed = placed();
        HoloAnchor anchor = placed.anchor();
        HoloTransform transform = placed.transform();
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
        double r = attachment != null ? object.boundingRadius() * overlayScale
                : object.boundingRadius() * object.transform().maxScale();
        return new AABB(position.x - r, position.y - r, position.z - r, position.x + r, position.y + r,
                position.z + r);
    }

    private Player referenceEntity()
    {
        ClientLevel level = Minecraft.getInstance().level;
        return level != null && reference != null ? level.getPlayerByUUID(reference) : null;
    }
}
