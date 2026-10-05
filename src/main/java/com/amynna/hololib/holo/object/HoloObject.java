package com.amynna.hololib.holo.object;

import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.network.HoloMoveObjectPacket;
import com.amynna.hololib.network.HoloNetwork;
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
 */
public abstract class HoloObject
{
    private final String key;
    private HoloModule module;
    private HoloAnchor anchor;
    private final HoloTransform transform;
    private HoloVisibility visibility = HoloVisibility.PRIVATE;
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
    }

    public final String key() { return key; }

    /** Module proprietaire ; {@code null} tant que l'objet n'est pas ajoute. */
    public final HoloModule module() { return module; }

    public final HoloAnchor anchor() { return anchor; }

    /** Transform vivant de l'objet, interprete selon l'ancrage ; le modifier deplace l'objet. */
    public final HoloTransform transform() { return transform; }

    public final HoloVisibility visibility() { return visibility; }

    /** Rayon d'une sphere centree sur l'origine locale contenant tout le dessin, en blocs (culling). */
    public abstract float boundingRadius();

    /** Appele chaque tick client (animations d'etat) ; jamais pendant le rendu. */
    public void tick() {}

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
     * Usage interne : etat recu du serveur ou relu d'une sauvegarde ; {@code anchor} ou {@code transform} nul =
     * inchange. Le transform est copie dans l'instance de l'objet.
     */
    @ApiStatus.Internal
    public final void restore(HoloVisibility visibility, HoloAnchor anchor, HoloTransform transform)
    {
        this.visibility = visibility;
        if (anchor != null) this.anchor = anchor;
        if (transform != null) this.transform.set(transform);
        replicated = true;
    }

    /** Usage interne : vrai quand l'ancrage, le transform ou la visibilite doivent etre envoyes aux clients. */
    @ApiStatus.Internal
    public final boolean syncDirty() { return syncDirty || transform.dirty(); }

    @ApiStatus.Internal
    public final void clearSyncDirty()
    {
        syncDirty = false;
        transform.clearDirty();
    }

    /** Usage interne (client) : vrai une fois l'etat recu du serveur ; un objet non replique n'est pas dessine. */
    @ApiStatus.Internal
    public final boolean replicated() { return replicated; }
}
