package com.amynna.hololib.modules.dimension.space.math;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Entree du pilote, quantifiee (voir {@code docs/space/04-mathematiques.md} par. 5 et {@code 05-synchronisation.md}
 * par. 3) : gaz et roulis dans {@code {-1, 0, 1}}, taux de lacet et de tangage en octets signes {@code [-127, 127]}
 * ({@code taux = octet / 127}, dans {@code [-1, 1]}), frein (booleen, touche Saut maintenue), <b>visee</b> (ecart du
 * regard au cercle avant, en lacet et en tangage, octets signes {@code [-127, 127]} quantifies sur le cone :
 * {@code degres = octet x} {@value #AIM_RANGE} {@code / 127}, pas ~0,24 degre). Sept octets sur le reseau ; la
 * quantification rend l'entree identique sur le serveur et sur les clients (simulation deterministe).
 * <p>
 * La visee choisit le referentiel ({@code docs/space/04-mathematiques.md} par. 5 point 0, D8) : c'est l'ecart
 * <b>reel</b> du regard (pas le taux : elle n'est pas nulle dans la zone morte du joystick). Direction visee dans les
 * axes du monde : regard {@code (yaw_F + aimYaw, pitch_F - aimPitch)} (pitch Minecraft) ; visee nulle = axe avant
 * du monde (aussi sans pilote, {@link #ZERO}).
 * <p>
 * Signes : {@code throttle > 0} (Avancer) pousse la vitesse signee du monde vers {@code +V_max} (en freinant d'abord
 * si elle est negative), {@code throttle < 0} (Reculer) la pousse vers {@code -V_max} (marche arriere) ;
 * {@code brake} ramene la vitesse vers 0 des deux cotes et prime sur {@code throttle}, qui est alors ignore par
 * {@link SpaceMotion#step} (il reste transmis tel quel) ; {@code roll > 0} roule a droite (touche Droite) ;
 * {@code yaw > 0} tourne l'avant du monde vers la droite (regard a droite du cercle avant) ; {@code pitch > 0} leve
 * l'avant (regard au-dessus du cercle avant). Roulis et taux restent actifs pendant le frein. {@code aimYaw > 0} :
 * regard a droite du cercle avant ; {@code aimPitch > 0} : regard au-dessus (meme sens que {@code pitch}).
 * <p>
 * Le constructeur accepte n'importe quels octets ; les accesseurs de taux et de signe ({@link #yawRate()},
 * {@link #throttleSign()}...) bornent toujours, et {@link #sanitize()} rend une entree canonique (celle que
 * {@link #read} produit).
 */
public record SpacePilotInput(byte throttle, byte roll, byte yaw, byte pitch, boolean brake, byte aimYaw,
                              byte aimPitch)
{
    /** Pas de quantification des taux : {@code taux = octet / RATE_STEPS}. */
    public static final int RATE_STEPS = 127;
    /** Taille sur le reseau, en octets. */
    public static final int BYTES = 7;
    /** Octet de drapeaux sur le reseau : bit du frein (les autres bits doivent etre nuls). */
    public static final int FLAG_BRAKE = 1;
    /**
     * Ecart maximal de la visee, degres, par composante : demi-angle du cone du joystick
     * ({@code SpacePilotPad.CONE_ANGLE}). Un octet de visee vaut {@code AIM_RANGE / 127} degres.
     */
    public static final double AIM_RANGE = 30.0;
    /** Pas de quantification de la visee, degres. */
    public static final double AIM_STEP = AIM_RANGE / RATE_STEPS;
    /**
     * Entree nulle : pas de gaz, pas de rotation, pas de frein (le monde garde sa vitesse nominale), visee sur l'axe
     * avant.
     */
    public static final SpacePilotInput ZERO = new SpacePilotInput((byte) 0, (byte) 0, (byte) 0, (byte) 0, false,
            (byte) 0, (byte) 0);

    /**
     * Entree quantifiee, visee sur l'axe avant.
     *
     * @param throttle  signe du gaz (seul le signe compte) ; ignore par le pas si {@code brake}
     * @param roll      signe du roulis (seul le signe compte)
     * @param yawRate   taux de lacet, borne a {@code [-1, 1]} ({@code NaN} = 0)
     * @param pitchRate taux de tangage, borne a {@code [-1, 1]} ({@code NaN} = 0)
     * @param brake     frein (touche Saut maintenue)
     */
    public static SpacePilotInput of(int throttle, int roll, double yawRate, double pitchRate, boolean brake)
    {
        return of(throttle, roll, yawRate, pitchRate, brake, (byte) 0, (byte) 0);
    }

    /**
     * Entree quantifiee avec visee deja quantifiee ({@link #quantizeAim}).
     *
     * @param aimYaw   octet de visee en lacet (positif : a droite), borne a {@code [-127, 127]}
     * @param aimPitch octet de visee en tangage (positif : au-dessus), borne a {@code [-127, 127]}
     */
    public static SpacePilotInput of(int throttle, int roll, double yawRate, double pitchRate, boolean brake,
                                     byte aimYaw, byte aimPitch)
    {
        byte t = (byte) Integer.signum(throttle);
        byte r = (byte) Integer.signum(roll);
        byte y = quantize(yawRate);
        byte p = quantize(pitchRate);
        byte ay = clampRate(aimYaw);
        byte ap = clampRate(aimPitch);
        if (t == 0 && r == 0 && y == 0 && p == 0 && !brake && ay == 0 && ap == 0) return ZERO;
        return new SpacePilotInput(t, r, y, p, brake, ay, ap);
    }

    /**
     * Ecart de visee en degres -> octet {@code [-127, 127]} (arrondi au plus proche, borne a {@link #AIM_RANGE} ;
     * {@code NaN} = 0).
     */
    public static byte quantizeAim(double degrees)
    {
        return quantize(degrees / AIM_RANGE);
    }

    /** Taux dans {@code [-1, 1]} -> octet {@code [-127, 127]} (arrondi au plus proche). */
    public static byte quantize(double rate)
    {
        if (Double.isNaN(rate)) return 0;
        double clamped = Math.max(-1.0, Math.min(1.0, rate));
        return (byte) Math.round(clamped * RATE_STEPS);
    }

    // ---------------------------------------------------------------- lecture bornee

    /** Signe du gaz : {@code -1}, {@code 0} ou {@code 1} (meme pendant le frein : c'est le pas qui l'ignore). */
    public int throttleSign() { return Integer.signum(throttle); }

    /** Signe du roulis : {@code -1}, {@code 0} ou {@code 1}. */
    public int rollSign() { return Integer.signum(roll); }

    /** Taux de lacet dans {@code [-1, 1]}. */
    public double yawRate() { return rate(yaw); }

    /** Taux de tangage dans {@code [-1, 1]}. */
    public double pitchRate() { return rate(pitch); }

    /** Visee en lacet, degres ({@code [-30, 30]}, positif : a droite du cercle avant). */
    public double aimYawDegrees() { return clampRate(aimYaw) * AIM_STEP; }

    /** Visee en tangage, degres ({@code [-30, 30]}, positif : au-dessus du cercle avant). */
    public double aimPitchDegrees() { return clampRate(aimPitch) * AIM_STEP; }

    /** Vrai si aucune commande n'est active (frein compris) et si la visee est sur l'axe avant : {@link #ZERO}. */
    public boolean isZero()
    {
        return throttle == 0 && roll == 0 && yaw == 0 && pitch == 0 && !brake && aimYaw == 0 && aimPitch == 0;
    }

    /** Vrai si une rotation est commandee (lacet, tangage ou roulis). */
    public boolean rotating() { return roll != 0 || yaw != 0 || pitch != 0; }

    /**
     * Entree canonique : signes pour gaz et roulis, taux et visee bornes a {@code [-127, 127]}, frein garde ;
     * {@code this} si deja valide.
     */
    public SpacePilotInput sanitize()
    {
        byte t = (byte) throttleSign();
        byte r = (byte) rollSign();
        byte y = clampRate(yaw);
        byte p = clampRate(pitch);
        byte ay = clampRate(aimYaw);
        byte ap = clampRate(aimPitch);
        if (t == throttle && r == roll && y == yaw && p == pitch && ay == aimYaw && ap == aimPitch) return this;
        if (t == 0 && r == 0 && y == 0 && p == 0 && !brake && ay == 0 && ap == 0) return ZERO;
        return new SpacePilotInput(t, r, y, p, brake, ay, ap);
    }

    private static double rate(byte value) { return clampRate(value) / (double) RATE_STEPS; }

    private static byte clampRate(byte value) { return value < -RATE_STEPS ? (byte) -RATE_STEPS : value; }

    // ---------------------------------------------------------------- reseau (gaz, roulis, taux, frein, visee)

    /** Sept octets : gaz, roulis, lacet, tangage, drapeaux ({@link #FLAG_BRAKE}), visee en lacet, visee en tangage. */
    public void write(FriendlyByteBuf buf)
    {
        buf.writeByte(throttle);
        buf.writeByte(roll);
        buf.writeByte(yaw);
        buf.writeByte(pitch);
        buf.writeByte(brake ? FLAG_BRAKE : 0);
        buf.writeByte(aimYaw);
        buf.writeByte(aimPitch);
    }

    /**
     * Lecture : sept octets, entree rendue canonique ({@link #sanitize()}). Lecture stricte : leve sur un tampon trop
     * court et sur un octet de drapeaux portant un bit inconnu.
     */
    public static SpacePilotInput read(FriendlyByteBuf buf)
    {
        byte throttle = buf.readByte();
        byte roll = buf.readByte();
        byte yaw = buf.readByte();
        byte pitch = buf.readByte();
        int flags = buf.readUnsignedByte();
        if ((flags & ~FLAG_BRAKE) != 0) throw new IllegalArgumentException("Drapeaux d'entree invalides : " + flags);
        byte aimYaw = buf.readByte();
        byte aimPitch = buf.readByte();
        return new SpacePilotInput(throttle, roll, yaw, pitch, (flags & FLAG_BRAKE) != 0, aimYaw, aimPitch)
                .sanitize();
    }
}
