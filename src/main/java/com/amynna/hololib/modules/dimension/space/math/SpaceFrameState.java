package com.amynna.hololib.modules.dimension.space.math;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Etat du referentiel du monde (voir {@code docs/space/04-mathematiques.md} par. 5 point 0, decisions D8 puis D29 :
 * spheres d'influence depuis T39) : corps de reference, corps precedent (fondu du mouvement d'entrainement), ticks
 * depuis la derniere bascule (cooldown et fondu), candidat a la bascule (corps vise par les spheres d'influence,
 * different du referentiel) et nombre de ticks consecutifs de sa candidature (delai). Les corps sont designes par leur
 * id stable ({@code 0} = aucun).
 * <p>
 * Objet <b>mutable</b>, reutilisable : {@link SpaceMotion#step} le lit au debut du pas et le met a jour en place, sans
 * allocation. Chaque suite de pas a le sien (serveur, rejeu client, prediction du pilote, affichage). Etat simule comme
 * la pose : synchronise ({@link #write} / {@link #read}) et sauvegarde ({@link #save} / {@link #load}).
 * <p>
 * Compteurs satures : {@link #sinceSwitch()} a {@link #SATURATION} (au-dela, plus de cooldown ni de fondu),
 * {@link #candidateTicks()} a {@link SpaceMotion#SWITCH_DELAY}.
 */
public final class SpaceFrameState
{
    /** Saturation de {@link #sinceSwitch()} : au moins le cooldown et la duree du fondu. */
    public static final int SATURATION = Math.max(SpaceMotion.SWITCH_COOLDOWN, SpaceMotion.BLEND_TICKS);

    private int reference;
    private int previous;
    private int sinceSwitch = SATURATION;
    private int candidate;
    private int candidateTicks;

    /** Aucun referentiel (recalcule au pas suivant), ni fondu, ni candidat. */
    public SpaceFrameState() {}

    // ---------------------------------------------------------------- lecture

    /**
     * Id du corps de reference ({@code 0} = aucun : le corps vise par les spheres d'influence au pas suivant, sans
     * delai ni fondu).
     */
    public int reference() { return reference; }

    /** Id du referentiel d'avant la derniere bascule ({@code 0} = aucun) : origine du fondu. */
    public int previous() { return previous; }

    /** Ticks (pas) ecoules depuis la derniere bascule, sature a {@link #SATURATION}. */
    public int sinceSwitch() { return sinceSwitch; }

    /** Id du corps candidat a la bascule ({@code 0} = aucun). */
    public int candidate() { return candidate; }

    /** Ticks consecutifs pendant lesquels {@link #candidate()} a battu le referentiel, sature au delai. */
    public int candidateTicks() { return candidateTicks; }

    /** Vrai si le pas suivant melange le mouvement de {@link #previous()} et de {@link #reference()}. */
    public boolean blending()
    {
        return previous != 0 && previous != reference && sinceSwitch < SpaceMotion.BLEND_TICKS;
    }

    /**
     * Poids du referentiel courant dans le mouvement d'entrainement du pas suivant, {@code [0, 1]} :
     * {@code smoothstep(sinceSwitch / BLEND_TICKS)} pendant un fondu, {@code 1} sinon.
     */
    public double blend()
    {
        return blending() ? SpaceMotion.smoothstep(sinceSwitch / (double) SpaceMotion.BLEND_TICKS) : 1.0;
    }

    /**
     * Poids du referentiel courant pendant le pas <b>qui vient d'etre fait</b> (etat lu apres
     * {@link SpaceMotion#step}) : {@code smoothstep((sinceSwitch - 1) / BLEND_TICKS)} pendant un fondu, {@code 1}
     * sinon. Sert a l'interpolation d'affichage entre les deux derniers ticks.
     */
    public double lastStepBlend()
    {
        if (previous == 0 || previous == reference || sinceSwitch < 1 || sinceSwitch - 1 >= SpaceMotion.BLEND_TICKS) {
            return 1.0;
        }
        return SpaceMotion.smoothstep((sinceSwitch - 1) / (double) SpaceMotion.BLEND_TICKS);
    }

    // ---------------------------------------------------------------- affectation

    /** Copie {@code other} dans cet etat. */
    public SpaceFrameState set(SpaceFrameState other)
    {
        reference = other.reference;
        previous = other.previous;
        sinceSwitch = other.sinceSwitch;
        candidate = other.candidate;
        candidateTicks = other.candidateTicks;
        return this;
    }

    /**
     * Referentiel pose d'un coup (pose par defaut, corps absent remplace) : pas de fondu, pas de cooldown, pas de
     * candidat.
     */
    public SpaceFrameState reset(int reference)
    {
        this.reference = Math.max(0, reference);
        previous = 0;
        sinceSwitch = SATURATION;
        candidate = 0;
        candidateTicks = 0;
        return this;
    }

    /** Bascule vers {@code reference} : l'ancien referentiel devient l'origine du fondu, cooldown relance. */
    void switchTo(int reference)
    {
        previous = this.reference;
        this.reference = reference;
        sinceSwitch = 0;
        candidate = 0;
        candidateTicks = 0;
    }

    /** Candidature de {@code body} pour ce pas : compteur incremente (sature) s'il etait deja candidat, sinon 1. */
    void nominate(int body)
    {
        if (candidate == body) {
            if (candidateTicks < SpaceMotion.SWITCH_DELAY) candidateTicks++;
        } else {
            candidate = body;
            candidateTicks = 1;
        }
    }

    /** Aucun candidat a ce pas. */
    void clearCandidate()
    {
        candidate = 0;
        candidateTicks = 0;
    }

    /** Fin d'un pas : un tick de plus depuis la bascule (sature). */
    void advance()
    {
        if (sinceSwitch < SATURATION) sinceSwitch++;
    }

    /** Vrai si les deux etats sont egaux champ a champ. */
    public boolean sameAs(SpaceFrameState other)
    {
        return reference == other.reference && previous == other.previous && sinceSwitch == other.sinceSwitch
                && candidate == other.candidate && candidateTicks == other.candidateTicks;
    }

    // ---------------------------------------------------------------- reseau (ordre : ref, prev, since, cand, candt)

    /** Cinq varints : referentiel, precedent, ticks depuis la bascule, candidat, ticks de candidature. */
    public void write(FriendlyByteBuf buf)
    {
        buf.writeVarInt(reference);
        buf.writeVarInt(previous);
        buf.writeVarInt(sinceSwitch);
        buf.writeVarInt(candidate);
        buf.writeVarInt(candidateTicks);
    }

    /**
     * Lecture stricte <b>dans cet etat</b> : leve (sans rien modifier) sur une valeur negative ; compteurs bornes a
     * leur saturation.
     */
    public SpaceFrameState read(FriendlyByteBuf buf)
    {
        int ref = buf.readVarInt();
        int prev = buf.readVarInt();
        int since = buf.readVarInt();
        int cand = buf.readVarInt();
        int candTicks = buf.readVarInt();
        if (ref < 0 || prev < 0 || since < 0 || cand < 0 || candTicks < 0) {
            throw new IllegalArgumentException("Etat de referentiel invalide");
        }
        reference = ref;
        previous = prev;
        sinceSwitch = Math.min(since, SATURATION);
        candidate = cand;
        candidateTicks = Math.min(candTicks, SpaceMotion.SWITCH_DELAY);
        return this;
    }

    // ---------------------------------------------------------------- NBT

    /** Format : {@code { ref, prev, since, cand, candt }} (cles courtes et stables). */
    public CompoundTag save()
    {
        CompoundTag tag = new CompoundTag();
        tag.putInt("ref", reference);
        tag.putInt("prev", previous);
        tag.putInt("since", sinceSwitch);
        tag.putInt("cand", candidate);
        tag.putInt("candt", candidateTicks);
        return tag;
    }

    /**
     * Lecture tolerante <b>dans cet etat</b> : cle manquante ou negative = valeur neutre (aucun corps, compteur sature
     * pour {@code since}, nul pour {@code candt}), compteurs bornes. Ne leve jamais.
     */
    public SpaceFrameState load(CompoundTag tag)
    {
        reference = Math.max(0, tag.getInt("ref"));
        previous = Math.max(0, tag.getInt("prev"));
        int since = tag.contains("since", Tag.TAG_ANY_NUMERIC) ? tag.getInt("since") : SATURATION;
        sinceSwitch = since < 0 ? SATURATION : Math.min(since, SATURATION);
        candidate = Math.max(0, tag.getInt("cand"));
        candidateTicks = Math.max(0, Math.min(tag.getInt("candt"), SpaceMotion.SWITCH_DELAY));
        return this;
    }

    @Override
    public String toString()
    {
        return "SpaceFrameState[ref " + reference + ", prev " + previous + ", since " + sinceSwitch + ", cand "
                + candidate + " x" + candidateTicks + "]";
    }
}
