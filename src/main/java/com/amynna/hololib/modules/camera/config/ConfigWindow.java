package com.amynna.hololib.modules.camera.config;

import com.amynna.hololib.holo.theme.HoloColor;
import com.amynna.hololib.holo.capture.HoloControl;
import com.amynna.hololib.holo.edit.HoloDisplay;
import com.amynna.hololib.holo.edit.HoloEditRotation;
import com.amynna.hololib.holo.edit.HoloEditScale;
import com.amynna.hololib.holo.edit.HoloEditTranslation;
import com.amynna.hololib.holo.edit.HoloRelativeToOpen;
import com.amynna.hololib.holo.interact.HoloInteractible;
import com.amynna.hololib.holo.interact.HoloInteraction;
import com.amynna.hololib.holo.object.HoloAnchor;
import com.amynna.hololib.holo.object.HoloObject;
import com.amynna.hololib.holo.object.HoloTransform;
import com.amynna.hololib.holo.object.HoloVisibility;
import com.amynna.hololib.holo.widget.HoloCanvas;
import com.amynna.hololib.holo.widget.HoloLabel;
import com.amynna.hololib.holo.widget.HoloPanel;
import com.amynna.hololib.holo.widget.control.HoloClickable;
import com.amynna.hololib.holo.widget.control.HoloHoverable;
import com.amynna.hololib.holo.widget.control.HoloWidgetButton;
import com.amynna.hololib.holo.window.HoloWindow;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Fenetre ouverte par Config : fenetre de configuration d'un {@link Configurable}, ou la liste de Config elle-meme.
 * Une barre de titre (titre, epingle {@link ConfigPinButton}, croix) puis {@link #content()}, ou le module met ses
 * widgets. Ancree {@code Local} fixe, affichage {@code FOCUS} (non editable) ; deplacable, tournable et
 * redimensionnable dans l'editor ({@link HoloRelativeToOpen} : on edite sa position relative au regard).
 * <p>
 * Deplacable aussi hors de l'editor par son proprietaire : clic gauche maintenu sur le <b>cadre</b> (bande de
 * {@value #FRAME} px du bord, ou fond de la barre de titre ; les boutons et widgets gardent la priorite), la fenetre
 * suit le regard en gardant l'ecart pris au clic ; au lacher, le placement est envoye au serveur et la position
 * relative au regard est recalculee ({@link Backend#storeOffset}) puis transmise par Config ({@link #linkMoved}) :
 * les reouvertures la placent au meme endroit.
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
    /** Opacite du fond des fenetres (motif du theme). */
    public static final int BACKGROUND_ALPHA = 0xC0;
    /** Bordure et titres des fenetres. */
    public static final HoloColor ACCENT = HoloColor.primary(0xFF);
    /** Largeur de la bande du bord par laquelle on attrape la fenetre, en pixels. */
    public static final int FRAME = 3;
    private static final HoloColor ACCENT_GRABBED = HoloColor.primary(0xFF).lighter(0.51f);
    private static final HoloColor BAR_BACKGROUND = HoloColor.primary(0x40);
    private static final int BUTTON = 12;
    private static final int MARGIN = 2;

    private static Backend backend;

    private final HoloPanel content;
    private final ConfigPinButton pin;
    private final FrameGrip grip = new FrameGrip();
    private Consumer<Boolean> link;
    private Runnable moved;

    /** Partie client : placement devant le regard du joueur local. */
    public interface Backend
    {
        /** Place l'objet ({@code HoloRelativeToOpen} de la memoire du joueur local) devant le regard courant. */
        void placeInFront(HoloObject object);

        /** Recalcule la position relative au regard de l'objet d'apres son placement (apres un deplacement). */
        void storeOffset(HoloObject object);
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
        root().setPattern(BACKGROUND_ALPHA).setBorder(ACCENT, 1f);
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

    /** Usage interne (Config) : recoit la fin d'un deplacement au cadre (position relative a envoyer). */
    public final void linkMoved(Runnable moved) { this.moved = moved; }

    private void pinRequested(boolean pinned)
    {
        if (link != null) link.accept(pinned);
    }

    @Override
    public boolean shown() { return opened(); }

    /** Widget interactible vise s'il y en a un, sinon le cadre ({@link #FRAME}, barre de titre), sinon la fenetre. */
    @Override
    public HoloInteractible interactibleAt(float localX, float localY)
    {
        HoloInteractible hit = super.interactibleAt(localX, localY);
        if (hit != this) return hit;
        float px = pixelX(localX);
        float py = pixelY(localY);
        boolean frame = px < FRAME || px > width() - FRAME || py < BAR_HEIGHT || py > height() - FRAME;
        return frame ? grip : this;
    }

    /** Fin d'un deplacement au cadre : placement envoye, position relative recalculee puis transmise. */
    private void moved()
    {
        requestTransform();
        if (backend != null) backend.storeOffset(this);
        if (moved != null) moved.run();
    }

    /**
     * Cadre de la fenetre : clic gauche maintenu = deplacement au regard (meme regle que la translation d'un
     * {@code Local} dans l'editor : l'ecart angulaire pris au clic est garde). Bord eclairci au survol.
     */
    private final class FrameGrip implements HoloClickable, HoloHoverable
    {
        private boolean hovered;
        private boolean dragging;
        private float grabYaw;
        private float grabPitch;

        @Override
        public boolean hovered() { return hovered; }

        @Override
        public void setHovered(boolean hovered)
        {
            this.hovered = hovered;
            refreshBorder();
        }

        @Override
        public boolean clickableBy(HoloControl button) { return button == HoloControl.LEFT_CLICK; }

        @Override
        public void onClick(HoloInteraction ctx, HoloControl button)
        {
            if (!(anchor() instanceof HoloAnchor.Local) || module() == null || !module().host().canAct()) return;
            HoloTransform t = transform();
            grabYaw = Mth.wrapDegrees(t.x() - ctx.yaw());
            grabPitch = t.y() - ctx.pitch();
            dragging = true;
            refreshBorder();
        }

        @Override
        public void onDrag(HoloInteraction ctx, HoloControl button)
        {
            if (!dragging) return;
            HoloTransform t = transform();
            t.setTranslation(Mth.wrapDegrees(ctx.yaw() + grabYaw), Mth.clamp(ctx.pitch() + grabPitch, -90f, 90f),
                    t.z());
        }

        @Override
        public void onClickRelease(HoloInteraction ctx, HoloControl button)
        {
            if (!dragging) return;
            dragging = false;
            refreshBorder();
            moved();
        }

        private void refreshBorder()
        {
            boolean active = hovered || dragging;
            root().setBorder(active ? ACCENT_GRABBED : ACCENT, active ? 2f : 1f);
        }
    }

    /** Croix de fermeture : deux barres en diagonale. */
    private static final class CloseButton extends HoloWidgetButton
    {
        private static final HoloColor PRESSED = HoloColor.critical(0x80);
        private static final HoloColor HOVERED = HoloColor.critical(0x50);
        private static final HoloColor CROSS = HoloColor.critical(0xFF).lighter(0.27f);
        private static final HoloColor CROSS_HOVER = HoloColor.special(0xFF);

        private static final float BAR = 1.2f;
        private static final float INSET = 3f;

        CloseButton(float x, float y, float width, float height, Consumer<? super HoloWidgetButton> onPress)
        {
            super(x, y, width, height, Component.empty(), onPress);
        }

        @Override
        protected void draw(HoloCanvas canvas)
        {
            if (pressed() || hovered()) {
                canvas.fill(0, 0, width(), height(), canvas.color(pressed() ? PRESSED : HOVERED));
            }
            float x0 = INSET;
            float y0 = INSET;
            float x1 = width() - INSET;
            float y1 = height() - INSET;
            int color = canvas.color(hovered() ? CROSS_HOVER : CROSS);
            canvas.quad(x0 - BAR, y0, x0, y0 - BAR, x1 + BAR, y1, x1, y1 + BAR, color);
            canvas.quad(x1, y0 - BAR, x1 + BAR, y0, x0, y1 + BAR, x0 - BAR, y1, color);
        }
    }
}
