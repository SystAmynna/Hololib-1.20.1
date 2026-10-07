package com.amynna.hololib.holo.edit;

import com.amynna.hololib.holo.memory.HoloScope;
import com.amynna.hololib.holo.module.HoloModule;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Regles d'edition, communes au client (ce qu'il propose) et au serveur (ce qu'il accepte). Le serveur verifie en plus
 * que le joueur voit l'objet (proprietaire, ou objet {@code PUBLIC} d'une memoire qu'il recoit).
 */
public final class HoloEditRules
{
    /** Ecart tolere sur une composante avant de la considerer modifiee (arrondis des conversions). */
    private static final float EPSILON = 1e-3f;
    /** Ecart tolere sur le produit scalaire de deux rotations. */
    private static final float ROTATION_EPSILON = 1e-5f;

    private HoloEditRules() {}

    /** Capacites des modules editeurs actifs parmi {@code modules} (memoire du joueur). */
    public static Set<HoloEditorCapability> capabilities(Collection<HoloModule> modules)
    {
        Set<HoloEditorCapability> capabilities = EnumSet.noneOf(HoloEditorCapability.class);
        for (HoloModule module : modules) {
            if (module instanceof HoloEditorModule editor && editor.editorEnabled()) {
                capabilities.add(editor.editorCapability());
            }
        }
        return capabilities;
    }

    /**
     * Vrai si le joueur peut editer l'objet :
     * <ul>
     *     <li>l'objet est {@link HoloEditable}, et le joueur operateur si {@code opRequired} ;</li>
     *     <li>un {@link HoloEditorUi} de sa propre memoire l'est toujours ;</li>
     *     <li>sinon il faut la capacite de la portee ({@link HoloEditorCapability#of}) ;</li>
     *     <li>{@code ownRequired} : le joueur doit etre {@code editOwner}, sauf hors de sa memoire avec
     *     {@link HoloEditorCapability#OTHER} et en etant operateur.</li>
     * </ul>
     *
     * @param op           joueur operateur (niveau 2)
     * @param scope        portee de la memoire de l'objet
     * @param editOwner    proprietaire pour l'edition ({@code HoloModule.editOwner()}), peut etre nul
     * @param capabilities capacites du joueur ({@link #capabilities})
     */
    public static boolean mayEdit(UUID player, boolean op, HoloScope scope, UUID editOwner, HoloObject object,
                                  Set<HoloEditorCapability> capabilities)
    {
        if (!(object instanceof HoloEditable editable) || player == null) return false;
        if (editable.opRequired() && !op) return false;
        if (object instanceof HoloEditorUi && player.equals(scope.owner())) return true;
        HoloEditorCapability needed = HoloEditorCapability.of(scope, player);
        if (needed == null || !capabilities.contains(needed)) return false;
        if (!editable.ownRequired() || player.equals(editOwner)) return true;
        return needed != HoloEditorCapability.OWN && op && capabilities.contains(HoloEditorCapability.OTHER);
    }

    /** Vrai si l'objet propose au moins un outil d'edition (boutons du mode edition). */
    public static boolean hasTools(HoloObject object)
    {
        return object instanceof HoloEditTranslation || object instanceof HoloEditRotation
                || object instanceof HoloEditScale || object instanceof HoloEditVisibility
                || object instanceof HoloEditFixOrDynamic;
    }

    /** Vrai si la bascule fixe / dynamique s'applique a l'objet (memoire de joueur, ancrage {@code Local}). */
    public static boolean fixOrDynamic(HoloObject object, HoloAnchor anchor, boolean playerScope)
    {
        return object instanceof HoloEditFixOrDynamic && playerScope && anchor instanceof HoloAnchor.Local;
    }

    /**
     * Vrai si le nouvel etat demande ne modifie que ce que les interfaces de l'objet permettent, et reste dans les
     * bornes de l'ancrage. Changement d'ancrage : seulement {@code Local} relatif <-> follow par
     * {@link HoloEditFixOrDynamic} (memoire de joueur), qui autorise alors translation et rotation (conversion).
     *
     * @param openOffset nouvelle position relative ({@link HoloRelativeToOpen}), {@code null} = inchangee
     * @param editRange  portee d'edition demandee (changeable pour un objet ancre {@code World})
     */
    public static boolean accepts(HoloObject object, boolean playerScope, HoloAnchor anchor, HoloTransform transform,
                                  HoloDisplay display, HoloTransform openOffset, float editRange)
    {
        if (!Float.isFinite(editRange)) return false;
        // Portee d'edition : objets ancres dans le monde seulement, dans ses bornes.
        if (Math.abs(editRange - object.editRange()) > EPSILON && !(anchor instanceof HoloAnchor.World
                && editRange >= HoloObject.MIN_EDIT_RANGE && editRange <= HoloObject.MAX_EDIT_RANGE)) {
            return false;
        }
        if (!anchor.acceptable(transform)) return false;
        HoloEditMath math = new HoloEditMath();
        HoloTransform current = object.transform();
        boolean switched = !anchor.equals(object.anchor());
        if (switched && !(fixOrDynamic(object, object.anchor(), playerScope) && anchor instanceof HoloAnchor.Local)) {
            return false;
        }
        if (!sameTranslation(current, transform) && !(switched || object instanceof HoloEditTranslation)) {
            return false;
        }
        if (!math.sameRotation(current, transform, ROTATION_EPSILON)
                && !(switched || object instanceof HoloEditRotation)) {
            return false;
        }
        if (!sameScale(current, transform) && !(object instanceof HoloEditScale)) return false;
        if (display != object.display() && !(object instanceof HoloEditVisibility)) return false;
        if (openOffset != null) {
            HoloTransform offset = object.openOffset();
            if (offset == null) return false;
            // L'offset est un placement a part entiere : memes outils que le transform.
            if (!sameTranslation(offset, openOffset) && !(object instanceof HoloEditTranslation)) return false;
            if (!math.sameRotation(offset, openOffset, ROTATION_EPSILON) && !(object instanceof HoloEditRotation)) {
                return false;
            }
            if (!sameScale(offset, openOffset) && !(object instanceof HoloEditScale)) return false;
            if (!new HoloAnchor.Local(true).acceptable(openOffset)) return false;
        }
        return true;
    }

    public static boolean sameTranslation(HoloTransform a, HoloTransform b)
    {
        return close(a.x(), b.x()) && close(a.y(), b.y()) && close(a.z(), b.z());
    }

    public static boolean sameScale(HoloTransform a, HoloTransform b)
    {
        return close(a.scaleX(), b.scaleX()) && close(a.scaleY(), b.scaleY()) && close(a.scaleZ(), b.scaleZ());
    }

    private static boolean close(float a, float b) { return Math.abs(a - b) <= EPSILON * Math.max(1f, Math.abs(a)); }
}
