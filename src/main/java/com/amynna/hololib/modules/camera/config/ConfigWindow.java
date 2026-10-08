package com.amynna.hololib.modules.camera.config;

import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.edit.HoloDisplay;
import com.amynna.hololib.holo.edit.HoloEditRotation;
import com.amynna.hololib.holo.edit.HoloEditScale;
import com.amynna.hololib.holo.edit.HoloEditTranslation;
import com.amynna.hololib.holo.edit.HoloRelativeToOpen;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.object.HoloVisibility;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.holo.widget.HoloLabel;
import com.amynna.hololib.holo.widget.HoloPanel;
import com.amynna.hololib.holo.widget.control.HoloWidgetButton;
import com.amynna.hololib.holo.window.HoloWindow;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Fenetre ouverte par Config : fenetre de configuration d'un {@link Configurable}, ou la liste de Config elle-meme.
 * Une barre de titre (titre, epingle {@link ConfigPinButton}, croix) puis {@link #content()}, ou le module met ses
 * widgets. Ancree {@code Local} fixe, affichage {@code FOCUS} (non editable) ; deplacable, tournable et
 * redimensionnable dans l'editor ({@link HoloRelativeToOpen} : on edite sa position relative au regard).
 * <p>
 * Ouverte ({@link #shown()}) ou non : {@code HoloObject.opened()}, decide par le client du proprietaire et
 * transmis aux observateurs (fenetre publique : un joueur qui inspecte le proprietaire la voit, sans pouvoir s'en
 * servir). {@link #open()} la place devant le regard courant d'apres sa position relative ; une fenetre epinglee se
 * rouvre avec le focus (placee devant le regard a l'ouverture), les autres se ferment avec lui. L'epingle est l'etat
 * du module Config, relie par {@link #link}.
 */
public class ConfigWindow extends HoloWindow implements HoloRelativeToOpen, HoloEditTranslation, HoloEditRotation,
        HoloEditScale
{
    public static final int BAR_HEIGHT = 16;
    public static final int BACKGROUND = 0xC0101828;
    public static final int ACCENT = 0xFF40C0FF;
    private static final int BAR_BACKGROUND = 0x4040C0FF;
    private static final int BUTTON = 12;
    private static final int MARGIN = 2;

    private static Backend backend;

    private final HoloPanel content;
    private final ConfigPinButton pin;
    private Consumer<Boolean> link;

    /** Partie client : placement devant le regard du joueur local. */
    public interface Backend
    {
        /** Place l'objet ({@code HoloRelativeToOpen} de la memoire du joueur local) devant le regard courant. */
        void placeInFront(HoloObject object);
    }

    /** Usage interne : branche la partie client, au setup client. */
    public static void install(Backend backend) { ConfigWindow.backend = Objects.requireNonNull(backend); }

    /**
     * @param transform     position relative au regard a l'ouverture ({@code Local} follow : {@code x} / {@code y} =
     *                      yaw / pitch, {@code z} = distance)
     * @param contentWidth  largeur de la fenetre, en pixels
     * @param contentHeight hauteur de {@link #content()}, sous la barre de titre, en pixels
     * @param pixelScale    taille d'un pixel en blocs
     */
    public ConfigWindow(String key, Component title, HoloTransform transform, int contentWidth, int contentHeight,
                        float pixelScale)
    {
        super(key, new HoloAnchor.Local(false), transform, contentWidth, contentHeight + BAR_HEIGHT, pixelScale);
        setDisplay(HoloDisplay.FOCUS);
        setVisibility(HoloVisibility.PUBLIC);
        setOpened(false);
        setInterceptedInputs(HoloControl.LEFT_CLICK, HoloControl.RIGHT_CLICK);
        root().setBackground(BACKGROUND).setBorder(ACCENT, 1f);
        root().add(new HoloPanel(0, 0, contentWidth, BAR_HEIGHT).setBackground(BAR_BACKGROUND));
        float buttonY = (BAR_HEIGHT - BUTTON) / 2f;
        float closeX = contentWidth - MARGIN - BUTTON;
        float pinX = closeX - MARGIN - BUTTON;
        root().add(new HoloLabel(4, 2, pinX - 6, BAR_HEIGHT - 4, title).setColor(ACCENT));
        pin = root().add(new ConfigPinButton(pinX, buttonY, BUTTON, BUTTON, this::pinRequested));
        root().add(new CloseButton(closeX, buttonY, BUTTON, BUTTON, button -> close()));
        content = root().add(new HoloPanel(0, BAR_HEIGHT, contentWidth, contentHeight));
    }

    /** Zone sous la barre de titre, pour les widgets du module. */
    public final HoloPanel content() { return content; }

    /** Vrai si la fenetre est ouverte. */
    public final boolean isOpen() { return opened(); }

    /** Cote client (proprietaire) : ouvre la fenetre (ou la ramene) devant le regard courant. */
    public final void open()
    {
        if (backend != null) backend.placeInFront(this);
        requestOpened(true);
    }

    /** Cote client (proprietaire) : ferme la fenetre. */
    public final void close() { requestOpened(false); }

    /** Cote client (proprietaire) : ouvre ou ferme sans la deplacer (reouverture avec le focus, deja placee). */
    public final void setOpen(boolean open) { requestOpened(open); }

    public final boolean pinned() { return pin.isOn(); }

    /** Etat de l'epingle, recopie depuis le module Config. */
    public final void setPinned(boolean pinned) { pin.setOn(pinned); }

    /** Usage interne (Config) : recoit les demandes d'epinglage du joueur. */
    public final void link(Consumer<Boolean> link) { this.link = link; }

    private void pinRequested(boolean pinned)
    {
        if (link != null) link.accept(pinned);
    }

    @Override
    public boolean shown() { return opened(); }

    /** Croix de fermeture : deux barres en diagonale. */
    private static final class CloseButton extends HoloWidgetButton
    {
        private static final float BAR = 1.2f;
        private static final float INSET = 3f;

        CloseButton(float x, float y, float width, float height, Consumer<? super HoloWidgetButton> onPress)
        {
            super(x, y, width, height, Component.empty(), onPress);
        }

        @Override
        protected void draw(HoloCanvas canvas)
        {
            if (pressed() || hovered()) canvas.fill(0, 0, width(), height(), pressed() ? 0x80FF4040 : 0x50FF4040);
            float x0 = INSET;
            float y0 = INSET;
            float x1 = width() - INSET;
            float y1 = height() - INSET;
            int color = hovered() ? 0xFFFFFFFF : 0xFFFF8080;
            canvas.quad(x0 - BAR, y0, x0, y0 - BAR, x1 + BAR, y1, x1, y1 + BAR, color);
            canvas.quad(x1, y0 - BAR, x1 + BAR, y0, x0, y1 + BAR, x0 - BAR, y1, color);
        }
    }
}
