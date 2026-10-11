package com.amynna.hololib.modules.dimension.space.model;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Rotation propre d'un corps celeste (immuable, T28, decision C71). Voir {@code docs/space/03-modele-donnees.md}
 * par. 2 et {@code 04-mathematiques.md} par. 10 ; calcul de l'orientation : {@code math/SpaceSpin}.
 * <p>
 * <b>Pole</b> : incline de {@code obliquity} depuis le haut {@code +Y} du <b>repere de reference</b> (repere de
 * l'orbite pour un corps qui a un parent, repere du systeme pour un corps sans parent), autour de la ligne des noeuds de
 * direction {@code (cos node, 0, sin node)} dans le plan de reference (angle compte de {@code +X}, le periapse, vers
 * {@code +Z}, sens de parcours des orbites, comme le {@code yaw} d'une orbite). <b>Sens</b> : periode {@code > 0} =
 * rotation directe (main droite) autour du pole, {@code < 0} = retrograde ; {@code (obliquite, noeud, P)} et
 * {@code (180 - obliquite, noeud + 180, -P)} decrivent le meme axe tournant dans le meme sens (seul le meridien
 * origine differe). Les donnees par defaut suivent la convention IAU des planetes : pole nord du cote {@code +Y}
 * (obliquite au plus 90), retrograde par le signe (Venus, Uranus, Pluton).
 * <p>
 * <b>Phase</b> : angle du meridien origine (axe local {@code +X} du corps) compte depuis la ligne des noeuds a
 * {@code t = 0}. <b>Synchrone</b> ({@code period == 0}) : l'axe {@code +X} du corps vise son parent a chaque instant
 * (projete sur l'equateur), plus la phase ; sans parent, le corps ne tourne pas (angle = phase).
 * <p>
 * Les quatre valeurs sont bornees par {@link SpaceLimits} et ramenees a la precision d'un {@code float} (reseau
 * compact, aller-retour exact) ; le constructeur leve sur une valeur non finie.
 *
 * @param period    periode siderale, heures ; {@code < 0} retrograde ; {@code 0} synchrone
 * @param obliquity angle entre le pole et le haut du plan de reference, degres {@code [0, 180]}
 * @param node      longitude de la ligne des noeuds de l'equateur dans le plan de reference, degres {@code [-180, 180[}
 * @param phase     angle du meridien origine a {@code t = 0}, degres {@code [0, 360[}
 */
public record SpaceRotation(double period, double obliquity, double node, double phase)
{
    /** Taille sur le reseau, en octets (4 float). */
    public static final int NETWORK_BYTES = 16;

    /** Etoile ajoutee : periode siderale du Soleil (25,38 jours). */
    private static final SpaceRotation DEFAULT_STAR = new SpaceRotation(609.12, 0.0, 0.0, 0.0);
    /** Planete ajoutee : une journee terrestre, axe droit. */
    private static final SpaceRotation DEFAULT_PLANET = new SpaceRotation(24.0, 0.0, 0.0, 0.0);
    /** Lune ajoutee : synchrone (meme face vers son parent). */
    private static final SpaceRotation DEFAULT_MOON = new SpaceRotation(0.0, 0.0, 0.0, 0.0);
    /** Objet mineur ajoute : periode de Ceres. */
    private static final SpaceRotation DEFAULT_MINOR = new SpaceRotation(9.07417, 0.0, 0.0, 0.0);

    public SpaceRotation
    {
        period = SpaceLimits.sanitizeRotationPeriod(period);
        obliquity = SpaceLimits.sanitizeObliquity(obliquity);
        node = SpaceLimits.sanitizeRotationNode(node);
        phase = SpaceLimits.sanitizeRotationPhase(phase);
    }

    /** Rotation par defaut d'un corps du type {@code kind} (corps ajoute, ou sauvegarde sans rotation). */
    public static SpaceRotation defaultFor(SpaceBodyKind kind)
    {
        return switch (kind) {
            case STAR -> DEFAULT_STAR;
            case PLANET -> DEFAULT_PLANET;
            case MOON -> DEFAULT_MOON;
            case MINOR -> DEFAULT_MINOR;
        };
    }

    /** Rotation synchrone (verrouillee sur le parent), pole et meridien donnes. */
    public static SpaceRotation synchronous(double obliquity, double node, double phase)
    {
        return new SpaceRotation(0.0, obliquity, node, phase);
    }

    /** Vrai si la rotation est synchrone (periode nulle). */
    public boolean isSynchronous() { return period == 0.0; }

    /** Vrai si la rotation est retrograde (periode negative). */
    public boolean isRetrograde() { return period < 0.0; }

    // ---------------------------------------------------------------- copies modifiees

    public SpaceRotation withPeriod(double period) { return new SpaceRotation(period, obliquity, node, phase); }

    public SpaceRotation withPole(double obliquity, double node)
    {
        return new SpaceRotation(period, obliquity, node, phase);
    }

    public SpaceRotation withPhase(double phase) { return new SpaceRotation(period, obliquity, node, phase); }

    // ---------------------------------------------------------------- reseau (period, obl, node, phase ; float)

    public void write(FriendlyByteBuf buf)
    {
        buf.writeFloat((float) period);
        buf.writeFloat((float) obliquity);
        buf.writeFloat((float) node);
        buf.writeFloat((float) phase);
    }

    /** Lecture stricte : leve sur une valeur non finie. */
    public static SpaceRotation read(FriendlyByteBuf buf)
    {
        double period = buf.readFloat();
        double obliquity = buf.readFloat();
        double node = buf.readFloat();
        double phase = buf.readFloat();
        return new SpaceRotation(period, obliquity, node, phase);
    }

    // ---------------------------------------------------------------- NBT

    /** Format : {@code { period, obl, node, phase }} (float ; valeurs deja a la precision float : aller-retour exact). */
    public CompoundTag save()
    {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("period", (float) period);
        tag.putFloat("obl", (float) obliquity);
        tag.putFloat("node", (float) node);
        tag.putFloat("phase", (float) phase);
        return tag;
    }

    /**
     * Lecture tolerante : une cle manquante prend la valeur de {@code fallback} (rotation par defaut du type), une
     * valeur hors bornes est bornee. Leve seulement sur une valeur non finie (l'appelant garde alors {@code fallback}).
     */
    public static SpaceRotation load(CompoundTag tag, SpaceRotation fallback)
    {
        return new SpaceRotation(number(tag, "period", fallback.period), number(tag, "obl", fallback.obliquity),
                number(tag, "node", fallback.node), number(tag, "phase", fallback.phase));
    }

    private static double number(CompoundTag tag, String key, double fallback)
    {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getDouble(key) : fallback;
    }
}
