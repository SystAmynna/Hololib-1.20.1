package com.amynna.hololib.holo.object;

import com.amynna.hololib.holo.edit.HoloDisplay;
import com.amynna.hololib.holo.edit.HoloRelativeToOpen;
import com.amynna.hololib.holo.edit.HoloRotationFrame;
import com.amynna.hololib.holo.interact.HoloInteractible;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.network.HoloMoveObjectPacket;
import com.amynna.hololib.network.HoloNetwork;
import com.amynna.hololib.network.HoloOpenObjectPacket;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * Objet holographique : ce qu'un renderer du mod peut dessiner. Code commun (aucune classe client) : le dessin vit
 * dans un {@code HoloObjectRenderer} client enregistre pour la classe de l'objet, comme {@code Entity} /
 * {@code EntityRenderer}.
 * <p>
 * Un objet appartient a un {@link HoloModule}, qui le cree dans {@code onLoad} des deux cotes (serveur et miroirs
 * clients) a partir de son etat. Le framework synchronise et sauvegarde lui-meme l'{@linkplain #anchor() ancrage}, le
 * {@linkplain #transform() transform} et la {@linkplain #visibility() visibilite} ; le reste du contenu decoule de
 * l'etat du module.
 * <p>
 * Pour deplacer l'objet, un module modifie directement son transform ({@code transform().setTranslation(...)}) : cote
 * serveur, le changement est envoye au tick suivant ; cote client, il reste local (voir {@link #requestTransform()}).
 * <p>
 * Un objet interactible surcharge {@link #interactibleAt} ; qui peut l'utiliser est son {@linkplain #access() acces},
 * synchronise par le framework.
 * <p>
 * Un objet editable en jeu implemente les interfaces de {@code holo.edit} ; son {@linkplain #display() affichage}
 * (quand il est dessine) et, pour un {@link HoloRelativeToOpen}, sa {@linkplain #openOffset() position relative} et
 * le {@linkplain #rotationFrame() repere de sa rotation} sont aussi synchronises et sauvegardes par le framework.
 */
public abstract class HoloObject
{
    /** Portee d'edition par defaut d'un objet ancre {@code World}, en blocs. */
    public static final float DEFAULT_EDIT_RANGE = 8f;
    public static final float MIN_EDIT_RANGE = 1f;
    /** Au plus la portee du picking dans le monde. */
    public static final float MAX_EDIT_RANGE = 16f;

    private final String key;
    private HoloModule module;
    private HoloAnchor anchor;
    private final HoloTransform transform;
    private final HoloTransform defaultTransform;
    private final HoloTransform openOffset;
    private HoloVisibility visibility = HoloVisibility.PRIVATE;
    private HoloDisplay display = HoloDisplay.ON;
    private HoloRotationFrame rotationFrame = HoloRotationFrame.PLAYER;
    private float editRange = DEFAULT_EDIT_RANGE;
    private HoloAccess access = HoloAccess.OWNER;
    private boolean opened = true;
    private boolean syncDirty = true;
    private boolean replicated;

    /**
     * @param key       nom unique dans le module, stable entre les sauvegardes et identique des deux cotes
     * @param transform placement initial dans le repere de l'ancrage (copie : l'objet possede son propre transform)
     */
    protected HoloObject(String key, HoloAnchor anchor, HoloTransform transform)
    {
        this.key = Objects.requireNonNull(key);
        this.anchor = Objects.requireNonNull(anchor);
        this.transform = transform.copy();
        this.defaultTransform = transform.copy();
        this.openOffset = this instanceof HoloRelativeToOpen ? transform.copy() : null;
    }

    public final String key() { return key; }

    /** Module proprietaire ; {@code null} tant que l'objet n'est pas ajoute. */
    public final HoloModule module() { return module; }

    public final HoloAnchor anchor() { return anchor; }

    /** Transform vivant de l'objet, interprete selon l'ancrage ; le modifier deplace l'objet. */
    public final HoloTransform transform() { return transform; }

    /**
     * Placement initial (transform passe au constructeur), valeur des RESET du mode edition. Ne pas le modifier.
     */
    public final HoloTransform defaultTransform() { return defaultTransform; }

    /**
     * Position relative au regard a l'ouverture du focus ({@link HoloRelativeToOpen}) : transform vivant, comme
     * {@link #transform()} ; {@code null} si l'objet n'est pas {@code HoloRelativeToOpen}.
     */
    public final HoloTransform openOffset() { return openOffset; }

    public final HoloVisibility visibility() { return visibility; }

    /** Quand l'objet est dessine ({@code ON} par defaut). */
    public final HoloDisplay display() { return display; }

    /**
     * Repere de la rotation quand l'objet est place depuis sa {@linkplain #openOffset() position relative}
     * ({@code PLAYER} par defaut) ; sans effet si l'objet n'est pas {@link HoloRelativeToOpen}.
     */
    public final HoloRotationFrame rotationFrame() { return rotationFrame; }

    /**
     * Portee d'edition (objet ancre {@code World}) : distance maximale entre l'oeil et l'objet pour l'editer en jeu,
     * en blocs, dans {@code [MIN_EDIT_RANGE, MAX_EDIT_RANGE]}. Synchronisee et sauvegardee.
     */
    public final float editRange() { return editRange; }

    /** Joueurs qui peuvent interagir avec l'objet. */
    public final HoloAccess access() { return access; }

    /** Rayon d'une sphere centree sur l'origine locale contenant tout le dessin, en blocs (culling). */
    public abstract float boundingRadius();

    /**
     * Demi-largeur du dessin dans le repere local (blocs). Par defaut {@link #boundingRadius()}.
     */
    public float halfWidth() { return boundingRadius(); }

    /** Demi-hauteur du dessin dans le repere local (blocs). Par defaut {@link #boundingRadius()}. */
    public float halfHeight() { return boundingRadius(); }

    /**
     * Vrai si le dessin est plat, dans le plan local {@code z = 0} et borne par {@link #halfWidth()} x
     * {@link #halfHeight()} ; sinon il est borne par sa sphere {@link #boundingRadius()}. Sert a poser les boutons du
     * mode edition hors de sa silhouette.
     */
    public boolean flat() { return false; }

    /**
     * Ouvert ou ferme (vrai par defaut) : etat d'une fenetre que le proprietaire ouvre et ferme lui-meme. Synchronise
     * vers les observateurs, jamais sauvegarde (valeur du constructeur au chargement) ; le client du proprietaire fait
     * foi ({@link #requestOpened}). L'objet s'en sert typiquement dans {@link #shown()}.
     */
    public final boolean opened() { return opened; }

    /** Change l'etat d'ouverture. Cote serveur, synchronise au tick suivant ; cote client, effet local seulement. */
    public final void setOpened(boolean opened)
    {
        if (opened == this.opened) return;
        this.opened = opened;
        syncDirty = true;
    }

    /**
     * Ouvre ou ferme l'objet au nom du proprietaire. Cote client (miroir d'un module du joueur local) : applique
     * localement puis envoie au serveur, qui le transmet aux observateurs ; sans effet pour une memoire de dimension ou
     * de position. Cote serveur : {@link #setOpened}.
     */
    public final void requestOpened(boolean opened)
    {
        if (opened == this.opened) return;
        if (module == null || !module.host().clientSide())
        {
            setOpened(opened);
            return;
        }
        if (!canRequest()) return;
        setOpened(opened);
        HoloNetwork.CHANNEL.sendToServer(new HoloOpenObjectPacket(module.scope(), module.instanceId(), key, opened));
    }

    /**
     * Cote client : condition d'affichage locale, en plus de {@link #display()} (ex. fenetre ouverte par le joueur).
     * Ni synchronisee ni sauvegardee ; vrai par defaut. Ignoree en session d'edition pour un objet editable (toujours
     * dessine).
     */
    public boolean shown() { return true; }

    /** Appele chaque tick client (animations d'etat) ; jamais pendant le rendu. */
    public void tick() {}

    /**
     * Element interactible sous un point du plan {@code z = 0} du repere local (blocs, {@code y} vers le haut), vu de
     * face ({@code +Z}) ; {@code null} = l'objet n'arrete pas le regard en ce point. Par defaut {@code null} : l'objet
     * n'est pas interactible. Appele cote client, a chaque frame quand le regard croise le plan.
     */
    public HoloInteractible interactibleAt(float localX, float localY) { return null; }

    /** Change l'ancrage. Cote serveur, synchronise au tick suivant ; cote client, effet local seulement. */
    public final void setAnchor(HoloAnchor anchor)
    {
        Objects.requireNonNull(anchor);
        if (anchor.equals(this.anchor)) return;
        this.anchor = anchor;
        syncDirty = true;
    }

    /** Change la visibilite. Cote serveur, synchronise au tick suivant. */
    public final void setVisibility(HoloVisibility visibility)
    {
        Objects.requireNonNull(visibility);
        if (visibility == this.visibility) return;
        this.visibility = visibility;
        syncDirty = true;
    }

    /** Change l'affichage. Cote serveur, synchronise au tick suivant ; cote client, effet local seulement. */
    public final void setDisplay(HoloDisplay display)
    {
        Objects.requireNonNull(display);
        if (display == this.display) return;
        this.display = display;
        syncDirty = true;
    }

    /** Change le repere de rotation. Cote serveur, synchronise au tick suivant ; cote client, effet local seulement. */
    public final void setRotationFrame(HoloRotationFrame frame)
    {
        Objects.requireNonNull(frame);
        if (frame == rotationFrame) return;
        rotationFrame = frame;
        syncDirty = true;
    }

    /** Change la portee d'edition (bornee). Cote serveur, synchronise au tick suivant. */
    public final void setEditRange(float range)
    {
        float bounded = Math.max(MIN_EDIT_RANGE, Math.min(MAX_EDIT_RANGE, range));
        if (!Float.isFinite(range) || bounded == editRange) return;
        editRange = bounded;
        syncDirty = true;
    }

    /** Change qui peut interagir avec l'objet. Cote serveur, synchronise au tick suivant. */
    public final void setAccess(HoloAccess access)
    {
        Objects.requireNonNull(access);
        if (access.equals(this.access)) return;
        this.access = access;
        syncDirty = true;
    }

    /**
     * Demande un nouvel ancrage au nom du proprietaire. Cote client (miroir d'un module du joueur local) : applique
     * localement puis envoie l'ancrage et le transform courant au serveur, qui les valide et les corrige si besoin ;
     * sans effet pour une memoire de dimension ou de position. Cote serveur : {@link #setAnchor}.
     */
    public final void requestAnchor(HoloAnchor anchor)
    {
        if (module == null || !module.host().clientSide())
        {
            setAnchor(anchor);
            return;
        }
        if (!canRequest()) return;
        setAnchor(anchor);
        sendPlacement();
    }

    /**
     * Cote client (miroir d'un module du joueur local) : envoie au serveur le transform courant, deja modifie
     * localement ; le serveur le valide ({@link HoloAnchor#acceptable}) ou renvoie le sien. Sans effet ailleurs (cote
     * serveur, modifier le transform suffit).
     */
    public final void requestTransform()
    {
        if (module != null && module.host().clientSide() && canRequest()) sendPlacement();
    }

    private boolean canRequest() { return module.host().ownerView() && module.owner() != null; }

    private void sendPlacement()
    {
        HoloNetwork.CHANNEL.sendToServer(new HoloMoveObjectPacket(module.scope(), module.instanceId(), key, anchor,
                transform.copy()));
    }

    /** Usage interne : rattachement au module. */
    @ApiStatus.Internal
    public final void attach(HoloModule module)
    {
        if (this.module != null && this.module != module)
        {
            throw new IllegalStateException("HoloObject '" + key + "' appartient deja a un autre module");
        }
        this.module = module;
    }

    /**
     * Usage interne : etat recu du serveur ou relu d'une sauvegarde ; un parametre nul (sauf {@code visibility}) ou
     * une portee {@code NaN} = inchange. Les transforms sont copies dans les instances de l'objet.
     */
    @ApiStatus.Internal
    public final void restore(HoloVisibility visibility, HoloDisplay display, HoloAccess access, HoloAnchor anchor,
                              HoloTransform transform, HoloTransform openOffset, HoloRotationFrame rotationFrame,
                              Boolean opened, float editRange)
    {
        this.visibility = visibility;
        if (opened != null) this.opened = opened;
        if (display != null) this.display = display;
        if (rotationFrame != null) this.rotationFrame = rotationFrame;
        if (Float.isFinite(editRange)) this.editRange = Math.max(MIN_EDIT_RANGE, Math.min(MAX_EDIT_RANGE, editRange));
        if (access != null) this.access = access;
        if (anchor != null) this.anchor = anchor;
        if (transform != null) this.transform.set(transform);
        if (openOffset != null && this.openOffset != null) this.openOffset.set(openOffset);
        replicated = true;
    }

    /**
     * Usage interne : vrai quand l'ancrage, le transform, la visibilite, l'affichage, l'acces, la position relative,
     * le repere de rotation ou l'ouverture doivent etre envoyes.
     */
    @ApiStatus.Internal
    public final boolean syncDirty()
    {
        return syncDirty || transform.dirty() || openOffset != null && openOffset.dirty();
    }

    @ApiStatus.Internal
    public final void clearSyncDirty()
    {
        syncDirty = false;
        transform.clearDirty();
        if (openOffset != null) openOffset.clearDirty();
    }

    /** Usage interne (client) : vrai une fois l'etat recu du serveur ; un objet non replique n'est pas dessine. */
    @ApiStatus.Internal
    public final boolean replicated() { return replicated; }
}
