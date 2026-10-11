package com.amynna.hololib.modules.monde.spacemap;

import com.amynna.hololib.modules.dimension.space.model.SpaceSystem.SystemHeader;

import java.util.List;

/**
 * Coordonnees affichees par la Space Map (T26 dans le Configurator, deplacee en T37 ;
 * {@code docs/space/04-mathematiques.md} par. 7, C50) : origine des coordonnees galactiques et format des nombres,
 * communs aux fenetres et au renderer du cube (memes chaines des deux cotes).
 * <ul>
 *     <li><b>origine</b> : systeme choisi par l'action « Origine » de la vue (id sauvegarde et synchronise) ; sans
 *     choix ({@code 0}) ou si ce systeme n'existe plus, le systeme nomme {@value #DEFAULT_ORIGIN_NAME}, sinon le
 *     premier de la galaxie ;</li>
 *     <li><b>format</b> : {@value #DECIMALS} decimales, virgule decimale, signe moins seulement si la valeur arrondie
 *     n'est pas nulle (jamais {@code -0,000}), {@code ?} pour une valeur non finie. {@link #write} n'alloue rien
 *     (renderer, chaque frame) ; {@link #format} cree la chaine (fenetre, a la synchronisation).</li>
 * </ul>
 */
public final class SpaceCoordinates
{
    /** Nom du systeme origine par defaut. */
    public static final String DEFAULT_ORIGIN_NAME = "Sol";
    /** Nombre de decimales affichees. */
    public static final int DECIMALS = 3;
    /** Longueur maximale ecrite par {@link #write} (signe, 19 chiffres, virgule, decimales). */
    public static final int MAX_LENGTH = 1 + 19 + 1 + DECIMALS;
    private static final double SCALE = 1000.0;
    private static final double LIMIT = 1e15;

    private SpaceCoordinates() {}

    /**
     * Systeme origine des coordonnees parmi {@code headers} : celui d'id {@code originId} s'il existe ({@code > 0}),
     * sinon celui nomme {@value #DEFAULT_ORIGIN_NAME}, sinon le premier ; {@code null} sans systeme. Sans allocation.
     */
    public static SystemHeader origin(List<SystemHeader> headers, int originId)
    {
        if (headers.isEmpty()) return null;
        SystemHeader named = null;
        for (int i = 0; i < headers.size(); i++) {
            SystemHeader header = headers.get(i);
            if (originId > 0 && header.id() == originId) return header;
            if (named == null && DEFAULT_ORIGIN_NAME.equals(header.name())) named = header;
        }
        return named != null ? named : headers.get(0);
    }

    /**
     * Ecrit {@code value} a {@value #DECIMALS} decimales dans {@code dest} a partir de {@code at} (au plus
     * {@link #MAX_LENGTH} caracteres) et rend l'indice qui suit le dernier caractere ecrit. Sans allocation.
     */
    public static int write(double value, char[] dest, int at)
    {
        if (!Double.isFinite(value)) {
            dest[at] = '?';
            return at + 1;
        }
        // Borne tres au-dela des valeurs du modele : le long ne deborde jamais.
        long n = Math.round(Math.max(-LIMIT, Math.min(LIMIT, value)) * SCALE);
        if (n < 0L) {
            dest[at++] = '-';
            n = -n;
        }
        long whole = n / (long) SCALE;
        int fraction = (int) (n % (long) SCALE);
        int start = at;
        do {
            dest[at++] = (char) ('0' + whole % 10L);
            whole /= 10L;
        } while (whole > 0L);
        // Chiffres ecrits du moins au plus significatif : remis dans l'ordre.
        for (int i = start, j = at - 1; i < j; i++, j--) {
            char c = dest[i];
            dest[i] = dest[j];
            dest[j] = c;
        }
        dest[at++] = ',';
        dest[at++] = (char) ('0' + fraction / 100);
        dest[at++] = (char) ('0' + fraction / 10 % 10);
        dest[at++] = (char) ('0' + fraction % 10);
        return at;
    }

    /** {@code value} au format de {@link #write}, en chaine (fenetre). */
    public static String format(double value)
    {
        char[] buffer = new char[MAX_LENGTH];
        return new String(buffer, 0, write(value, buffer, 0));
    }
}
