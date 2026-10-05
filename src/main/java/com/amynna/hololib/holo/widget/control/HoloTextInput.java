package com.amynna.hololib.holo.widget.control;

import com.amynna.hololib.holo.capture.HoloCapture;
import com.amynna.hololib.holo.capture.HoloCaptor;
import com.amynna.hololib.holo.capture.HoloCaptures;
import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.capture.HoloKeys;
import com.amynna.hololib.holo.interact.HoloInteraction;

/**
 * Trait : champ de saisie de texte. Un clic gauche ou droit demarre une capture de tout le clavier (liee a l'objet) ;
 * les caracteres et les touches d'edition modifient {@link #buffer()} (Retour arriere, Suppr, fleches, Debut / Fin,
 * Tab, Ctrl+V coller, Ctrl+C copier tout ; Entree = nouvelle ligne si le buffer en accepte plusieurs). La saisie se
 * termine par Echap, par Entree sur une seule ligne, ou quand le regard quitte l'element au-dela de
 * {@link #hoverMargin()} (24 px par defaut) ; le texte est alors remis a {@link #onSubmit}.
 * <p>
 * A implementer : le buffer, l'etat de survol et {@code onSubmit}. {@link #onEdited} permet de reagir a chaque
 * modification (ex. relancer le clignotement du curseur).
 */
public interface HoloTextInput extends HoloClickable, HoloHoverable, HoloCaptor
{
    HoloTextBuffer buffer();

    /** Fin de la saisie : texte final (a envoyer au serveur, qui le bornera par {@link HoloTextBuffer#sanitize}). */
    void onSubmit(String text);

    /** Apres chaque modification ou deplacement du curseur. */
    default void onEdited() {}

    default boolean multiline() { return buffer().maxHardLines() > 1; }

    /** Vrai pendant la saisie. */
    default boolean editing() { return HoloCaptures.isCapturing(this); }

    @Override
    default float hoverMargin() { return 24f; }

    @Override
    default boolean clickableBy(HoloControl button)
    {
        return button == HoloControl.LEFT_CLICK || button == HoloControl.RIGHT_CLICK;
    }

    @Override
    default void onClick(HoloInteraction ctx, HoloControl button) { ctx.capture(this); }

    /** Le regard est parti trop loin : fin de la saisie. */
    @Override
    default void onHoverChanged(HoloInteraction ctx, boolean hovered)
    {
        if (!hovered && editing()) HoloCaptures.end(this);
    }

    @Override
    default boolean captures(HoloControl control) { return control.equals(HoloControl.KEYBOARD); }

    @Override
    default void onCaptureStart(HoloCapture ctx)
    {
        buffer().moveToEnd();
        onEdited();
    }

    @Override
    default void onCaptureEnd(HoloCapture ctx) { onSubmit(buffer().text()); }

    @Override
    default void onCharTyped(HoloCapture ctx, int codePoint, int modifiers)
    {
        buffer().insert(Character.toString(codePoint));
        onEdited();
    }

    @Override
    default void onKeyPress(HoloCapture ctx, int key, int scanCode, int modifiers)
    {
        HoloTextBuffer buffer = buffer();
        if (HoloKeys.isShortcut(modifiers)) {
            if (key == 'V') buffer.insert(ctx.clipboard());
            else if (key == 'C') ctx.setClipboard(buffer.text());
            else return;
            onEdited();
            return;
        }
        switch (key) {
            case HoloKeys.ESCAPE -> {
                ctx.release();
                return;
            }
            case HoloKeys.ENTER, HoloKeys.KP_ENTER -> {
                if (!multiline()) {
                    ctx.release();
                    return;
                }
                buffer.insert("\n");
            }
            case HoloKeys.TAB -> buffer.insert("  ");
            case HoloKeys.BACKSPACE -> buffer.deleteBackward();
            case HoloKeys.DELETE -> buffer.deleteForward();
            case HoloKeys.LEFT -> buffer.left();
            case HoloKeys.RIGHT -> buffer.right();
            case HoloKeys.HOME -> buffer.home();
            case HoloKeys.END -> buffer.end();
            case HoloKeys.UP -> buffer.up();
            case HoloKeys.DOWN -> buffer.down();
            default -> {
                return;
            }
        }
        onEdited();
    }
}
