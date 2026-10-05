package com.amynna.hololib.holo.capture;

/**
 * Codes de touches et modificateurs recus par {@link HoloCaptor#onKeyPress} : valeurs de GLFW, recopiees pour que le
 * code commun n'ait pas a referencer LWJGL (absent d'un serveur dedie). Les touches de lettres et de chiffres valent
 * leur caractere ASCII majuscule ({@code 'A'}, {@code '0'}), selon la position sur un clavier QWERTY : pour du texte,
 * utiliser {@link HoloCaptor#onCharTyped}.
 */
public final class HoloKeys
{
    public static final int SPACE = 32;
    public static final int ESCAPE = 256;
    public static final int ENTER = 257;
    public static final int TAB = 258;
    public static final int BACKSPACE = 259;
    public static final int INSERT = 260;
    public static final int DELETE = 261;
    public static final int RIGHT = 262;
    public static final int LEFT = 263;
    public static final int DOWN = 264;
    public static final int UP = 265;
    public static final int PAGE_UP = 266;
    public static final int PAGE_DOWN = 267;
    public static final int HOME = 268;
    public static final int END = 269;
    public static final int KP_ENTER = 335;

    public static final int MOD_SHIFT = 0x1;
    public static final int MOD_CONTROL = 0x2;
    public static final int MOD_ALT = 0x4;
    public static final int MOD_SUPER = 0x8;

    private HoloKeys() {}

    /** Vrai pour Entree ou Entree du pave numerique. */
    public static boolean isEnter(int key) { return key == ENTER || key == KP_ENTER; }

    /**
     * Vrai si Ctrl (ou Cmd / Super) est enfonce sans Alt : raccourcis type Ctrl+V. AltGr arrive comme Ctrl+Alt sous
     * Windows et sert a taper des caracteres : ce n'est pas un raccourci.
     */
    public static boolean isShortcut(int modifiers)
    {
        return (modifiers & (MOD_CONTROL | MOD_SUPER)) != 0 && (modifiers & MOD_ALT) == 0;
    }
}
