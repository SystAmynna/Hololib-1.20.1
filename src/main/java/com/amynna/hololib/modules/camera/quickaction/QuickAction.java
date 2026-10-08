package com.amynna.hololib.modules.camera.quickaction;

import com.amynna.hololib.holo.widget.HoloCanvas;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/**
 * Action de la roue de Quick Action, declaree par un module {@link QuickActionProvider}. Par conception, une action a
 * un nom (liste de configuration, centre de la roue) et une icone (secteur de la roue) ; {@link #run()} l'execute.
 * <p>
 * Les actions sont creees par le module dans {@code onLoad} (deux cotes) et gardees tant qu'il existe : la roue les
 * reconnait par identite, et par {@link #id()} pour l'ordre et l'activation sauvegardes.
 */
public interface QuickAction
{
    /** Identifiant stable, unique parmi les actions du module qui la declare. */
    String id();

    /** Nom affiche. */
    Component name();

    /** Icone dans un carre {@code size} x {@code size} pixels, origine en haut a gauche ; chaque frame, sans allouer. */
    void drawIcon(HoloCanvas canvas, float size);

    /**
     * Cote client, au clic sur le secteur. Pour agir sur le jeu, le module declarant envoie une action a son module
     * serveur ({@code sendAction}), qui la valide.
     */
    void run();

    /** Action a partir de ses parties. */
    static QuickAction of(String id, Component name, QuickActionIcon icon, Runnable run)
    {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(icon);
        Objects.requireNonNull(run);
        return new QuickAction()
        {
            @Override
            public String id() { return id; }

            @Override
            public Component name() { return name; }

            @Override
            public void drawIcon(HoloCanvas canvas, float size) { icon.draw(canvas, size); }

            @Override
            public void run() { run.run(); }
        };
    }
}
