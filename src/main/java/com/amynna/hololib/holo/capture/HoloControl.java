package com.amynna.hololib.holo.capture;

import java.util.Objects;

/**
 * Un controle du joueur qu'un {@link HoloCaptor} peut capturer (ou qu'un element interactible peut intercepter au
 * survol). Valeur immuable.
 * <ul>
 *     <li>{@link #LEFT_CLICK}, {@link #RIGHT_CLICK}, {@link #MIDDLE_CLICK} : boutons <b>bruts</b> de la souris, quel
 *     que soit le controle qui leur est associe dans les options.</li>
 *     <li>{@link #SCROLL} : molette ; {@link #MOUSE_MOVE} : mouvements de la souris (la camera ne tourne plus).</li>
 *     <li>{@link #ESCAPE}, {@link #ENTER} : touches brutes (Entree du pave numerique comprise). Echap capture
 *     n'ouvre pas le menu pause.</li>
 *     <li>{@link #mapping(String)} : un controle des options ({@code KeyMapping}), vanilla ou d'un mod, designe par
 *     son nom ({@code "key.jump"}) : le controle suit la touche choisie par le joueur.</li>
 * </ul>
 */
public final class HoloControl
{
    public enum Kind
    {
        MOUSE_BUTTON, SCROLL, MOUSE_MOVE, ESCAPE, ENTER, MAPPING
    }

    public static final HoloControl LEFT_CLICK = new HoloControl(Kind.MOUSE_BUTTON, "left");
    public static final HoloControl RIGHT_CLICK = new HoloControl(Kind.MOUSE_BUTTON, "right");
    public static final HoloControl MIDDLE_CLICK = new HoloControl(Kind.MOUSE_BUTTON, "middle");
    public static final HoloControl SCROLL = new HoloControl(Kind.SCROLL, "scroll");
    public static final HoloControl MOUSE_MOVE = new HoloControl(Kind.MOUSE_MOVE, "mouse_move");
    public static final HoloControl ESCAPE = new HoloControl(Kind.ESCAPE, "escape");
    public static final HoloControl ENTER = new HoloControl(Kind.ENTER, "enter");

    private static final HoloControl[] MOUSE_BUTTONS = {LEFT_CLICK, RIGHT_CLICK, MIDDLE_CLICK};

    private final Kind kind;
    private final String name;

    private HoloControl(Kind kind, String name)
    {
        this.kind = kind;
        this.name = name;
    }

    /** Controle des options designe par son nom ({@code KeyMapping.getName()}, ex. {@code "key.jump"}). */
    public static HoloControl mapping(String name)
    {
        return new HoloControl(Kind.MAPPING, Objects.requireNonNull(name));
    }

    /** Bouton brut de la souris (0 gauche, 1 droit, 2 molette), ou {@code null}. */
    public static HoloControl mouseButton(int button)
    {
        return button >= 0 && button < MOUSE_BUTTONS.length ? MOUSE_BUTTONS[button] : null;
    }

    public Kind kind() { return kind; }

    /** Nom du controle ; pour un mapping, celui du {@code KeyMapping}. */
    public String name() { return name; }

    /** Indice du bouton de souris (0 gauche, 1 droit, 2 molette), ou -1. */
    public int mouseButton()
    {
        for (int i = 0; i < MOUSE_BUTTONS.length; i++) if (MOUSE_BUTTONS[i] == this) return i;
        return -1;
    }

    /** Vrai pour un controle qui s'appuie et se relache (boutons, touches, mappings). */
    public boolean pressable() { return kind != Kind.SCROLL && kind != Kind.MOUSE_MOVE; }

    @Override
    public boolean equals(Object o)
    {
        return this == o || o instanceof HoloControl other && kind == other.kind && name.equals(other.name);
    }

    @Override
    public int hashCode() { return 31 * kind.hashCode() + name.hashCode(); }

    @Override
    public String toString() { return kind == Kind.MAPPING ? "mapping:" + name : name; }
}
